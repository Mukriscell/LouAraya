package cl.duoc.louaraya.identity;

import cl.duoc.louaraya.common.ApiFailure;
import cl.duoc.louaraya.common.Bearer;
import cl.duoc.louaraya.common.Profile;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

@Service
class IdentityService implements ApplicationRunner {
    record Login(String alias, String password) {}
    record Registration(String name, String alias, String password, Integer avatar, String contact) {}
    record Session(String token, Profile profile) {}
    private record Credentials(long id, String hash) {}

    private static final String PROFILE_COLUMNS = """
        p.id, p.name, p.alias, p.role, p.avatar, p.contact, p.notifications_enabled, p.preferred_category
        """;
    private static final Set<String> CATEGORIES = Set.of("INTERIOR_DECORATION", "WEDDINGS", "FESTIVALS", "TRAVEL");

    private final JdbcClient db;
    private final SecureRandom random = new SecureRandom();
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(11);
    private final String dummyHash = passwords.encode("comprobacion-sin-cuenta");
    private final long sessionSeconds;
    private final String demoPassword;

    IdentityService(JdbcClient db, @Value("${louaraya.session-hours}") long sessionHours,
                    @Value("${louaraya.demo-password}") String demoPassword) {
        this.db = db;
        this.sessionSeconds = Math.multiplyExact(sessionHours, 3600);
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (demoPassword.isBlank()) {
            return;
        }
        validatePassword(demoPassword);
        seed(1, "Cliente de demostración", "cliente.demo", Profile.Role.CLIENT, 0);
        seed(2, "Administración de Lou Araya", "admin.demo", Profile.Role.ADMIN, 1);
        seed(3, "Segundo cliente", "cliente2.demo", Profile.Role.CLIENT, 2);
    }

    private void seed(long id, String name, String alias, Profile.Role role, int avatar) {
        boolean exists = db.sql("SELECT count(*) FROM profiles WHERE id = :id OR alias = :alias")
                .param("id", id).param("alias", alias).query(Integer.class).single() > 0;
        if (!exists) {
            db.sql("""
                    INSERT INTO profiles (id, name, alias, password_hash, role, avatar, contact)
                    VALUES (:id, :name, :alias, :hash, :role, :avatar, '')
                    """)
                    .param("id", id).param("name", name).param("alias", alias)
                    .param("hash", passwords.encode(demoPassword)).param("role", role.name()).param("avatar", avatar).update();
        }
    }

    @Transactional
    public Session login(Login input) {
        String alias = normalizeAlias(input.alias());
        String password = input.password();
        if (password == null || password.length() > 100 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw invalidCredentials();
        }
        Credentials credentials = db.sql("SELECT id, password_hash FROM profiles WHERE alias = :alias")
                .param("alias", alias)
                .query((row, n) -> new Credentials(row.getLong(1), row.getString(2)))
                .optional().orElse(null);
        if (!passwords.matches(password, credentials == null ? dummyHash : credentials.hash()) || credentials == null) {
            throw invalidCredentials();
        }
        return issue(profileById(credentials.id()));
    }

    @Transactional
    public Session register(Registration input) {
        String alias = validateAlias(input.alias());
        String name = validateName(input.name());
        String password = validatePassword(input.password());
        String contact = validateContact(input.contact());
        int avatar = validateAvatar(input.avatar() == null ? 0 : input.avatar());

        try {
            var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
            db.sql("""
                    INSERT INTO profiles (name, alias, password_hash, role, avatar, contact)
                    VALUES (:name, :alias, :hash, 'CLIENT', :avatar, :contact)
                    """)
                    .param("name", name).param("alias", alias).param("hash", passwords.encode(password))
                    .param("avatar", avatar).param("contact", contact).update(key, "id");
            return issue(profileById(key.getKey().longValue()));
        } catch (DuplicateKeyException conflict) {
            throw ApiFailure.field("alias", "Ese nombre de usuario ya está registrado.");
        }
    }

    Profile profile(String authorization) {
        String digest = digest(Bearer.require(authorization));
        return db.sql("SELECT " + PROFILE_COLUMNS + """
                 FROM profiles p JOIN sessions s ON s.profile_id = p.id
                 WHERE s.token_hash = :token AND s.expires_at > :now
                """)
                .param("token", digest).param("now", Instant.now().getEpochSecond())
                .query(Profile.class).optional().orElseThrow(ApiFailure::unauthorized);
    }

    @Transactional
    public void logout(String authorization) {
        db.sql("DELETE FROM sessions WHERE token_hash = :token")
                .param("token", digest(Bearer.require(authorization))).update();
    }

    @Transactional
    public Profile update(String authorization, JsonNode updates) {
        Profile current = profile(authorization);
        if (!updates.isObject()) {
            throw ApiFailure.badRequest("El perfil no es válido.");
        }
        String name = updates.has("name") ? validateName(text(updates, "name")) : current.name();
        String alias = updates.has("alias") ? validateAlias(text(updates, "alias")) : current.alias();
        String contact = updates.has("contact") ? validateContact(text(updates, "contact")) : current.contact();
        int avatar = current.avatar();
        if (updates.has("avatar")) {
            JsonNode field = updates.get("avatar");
            if (!field.isIntegralNumber() || !field.canConvertToInt()) {
                throw ApiFailure.field("avatar", "Elige un avatar válido.");
            }
            avatar = validateAvatar(field.intValue());
        }
        boolean notifications = current.notificationsEnabled();
        if (updates.has("notificationsEnabled")) {
            JsonNode field = updates.get("notificationsEnabled");
            if (!field.isBoolean()) {
                throw ApiFailure.badRequest("La preferencia de avisos no es válida.");
            }
            notifications = field.booleanValue();
        }
        String category = current.preferredCategory();
        if (updates.has("preferredCategory")) {
            category = text(updates, "preferredCategory");
            if (category != null && !CATEGORIES.contains(category)) {
                throw ApiFailure.field("preferredCategory", "Selecciona una categoría válida.");
            }
        }

        try {
            db.sql("""
                    UPDATE profiles SET name = :name, alias = :alias, avatar = :avatar, contact = :contact,
                        notifications_enabled = :notifications, preferred_category = :category WHERE id = :id
                    """)
                    .param("name", name).param("alias", alias).param("avatar", avatar).param("contact", contact)
                    .param("notifications", notifications).param("category", category).param("id", current.id()).update();
        } catch (DuplicateKeyException conflict) {
            throw ApiFailure.field("alias", "Ese nombre de usuario ya está registrado.");
        }
        return profileById(current.id());
    }

    private Profile profileById(long id) {
        return db.sql("SELECT " + PROFILE_COLUMNS + " FROM profiles p WHERE id = :id")
                .param("id", id).query(Profile.class).single();
    }

    private Session issue(Profile profile) {
        byte[] entropy = new byte[32];
        random.nextBytes(entropy);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        long now = Instant.now().getEpochSecond();
        db.sql("DELETE FROM sessions WHERE expires_at <= :now").param("now", now).update();
        db.sql("INSERT INTO sessions (token_hash, profile_id, expires_at) VALUES (:hash, :profile, :expires)")
                .param("hash", digest(token)).param("profile", profile.id()).param("expires", now + sessionSeconds).update();
        return new Session(token, profile);
    }

    private static String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static ApiFailure invalidCredentials() {
        return new ApiFailure(HttpStatus.UNAUTHORIZED, "El nombre de usuario o la contraseña no coinciden.");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw ApiFailure.field(field, "El campo no es válido.");
        }
        return value.textValue();
    }

    private static String normalizeAlias(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String validateAlias(String value) {
        String alias = normalizeAlias(value);
        if (!alias.matches("[a-z0-9._-]{3,40}")) {
            throw ApiFailure.field("alias", "Usa entre 3 y 40 letras sin tildes, números, puntos o guiones.");
        }
        return alias;
    }

    private static String validateName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.length() < 2 || name.length() > 80) {
            throw ApiFailure.field("name", "Escribe un nombre de entre 2 y 80 caracteres.");
        }
        return name;
    }

    private static String validateContact(String value) {
        String contact = value == null ? "" : value.trim();
        if (contact.length() > 100) {
            throw ApiFailure.field("contact", "El contacto admite hasta 100 caracteres.");
        }
        return contact;
    }

    private static int validateAvatar(int value) {
        if (value < 0 || value >= 8) {
            throw ApiFailure.field("avatar", "Elige un avatar válido.");
        }
        return value;
    }

    private static String validatePassword(String value) {
        if (value == null || value.length() < 8 || value.length() > 20
                || value.getBytes(StandardCharsets.UTF_8).length > 72
                || value.chars().noneMatch(Character::isUpperCase)
                || value.chars().noneMatch(Character::isDigit)
                || value.chars().noneMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c))) {
            throw ApiFailure.field("password", "Usa entre 8 y 20 caracteres con una mayúscula, un número y un símbolo.");
        }
        return value;
    }
}

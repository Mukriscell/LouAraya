package cl.duoc.louaraya.taller;

import cl.duoc.louaraya.common.ApiFailure;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.HttpEce;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Pattern;

@Component
class PushBroker {
    record Subscription(String endpoint, String publicKey, String authSecret) {}

    private static final Pattern TOPIC = Pattern.compile("/up[A-Za-z0-9]{12}");
    private static final SecureRandom RANDOM = new SecureRandom();
    private final URI advertisedOrigin;
    private final URI transportOrigin;
    private final HttpClient http;

    PushBroker(@Value("${louaraya.push-origin}") String origin, @Value("${louaraya.push-transport}") String transport) {
        advertisedOrigin = parseOrigin(origin);
        transportOrigin = parseOrigin(transport);
        if ((advertisedOrigin == null) != (transportOrigin == null)) {
            throw new IllegalArgumentException("Configura LOUARAYA_PUSH_ORIGIN y LOUARAYA_PUSH_TRANSPORT juntos.");
        }
        if (transportOrigin != null && "http".equals(transportOrigin.getScheme())
                && !("127.0.0.1".equals(transportOrigin.getHost()) || "localhost".equals(transportOrigin.getHost()))) {
            throw new IllegalArgumentException("El transporte push HTTP debe estar en el equipo local.");
        }
        if (enabled() && Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        http = enabled() ? HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build() : null;
    }

    boolean enabled() {
        return advertisedOrigin != null;
    }

    void validate(Subscription subscription) {
        target(subscription.endpoint());
        try {
            publicKey(subscription.publicKey());
            decode(subscription.authSecret(), 16);
        } catch (GeneralSecurityException | IllegalArgumentException invalid) {
            throw ApiFailure.badRequest("Las claves de cifrado de la instalación no son válidas.");
        }
    }

    URI target(String endpoint) {
        if (!enabled() || endpoint == null || endpoint.length() > 300) {
            throw ApiFailure.badRequest("El receptor push no corresponde al distribuidor configurado.");
        }
        try {
            URI uri = URI.create(endpoint);
            String topic = uri.getRawPath();
            if (topic != null && TOPIC.matcher(topic).matches()
                    && endpoint.equals(advertisedOrigin.toString() + topic + "?up=1")) {
                return URI.create(transportOrigin.toString() + topic + "?up=1");
            }
        } catch (IllegalArgumentException ignored) {
            // Return the same validation error for malformed and non-approved endpoints.
        }
        throw ApiFailure.badRequest("El receptor push no corresponde al distribuidor configurado.");
    }

    int publish(Subscription subscription, long noticeId)
            throws IOException, InterruptedException, GeneralSecurityException {
        HttpRequest request = HttpRequest.newBuilder(target(subscription.endpoint()))
                .timeout(Duration.ofSeconds(4))
                .header("Content-Type", "application/octet-stream")
                .header("Content-Encoding", "aes128gcm")
                .header("X-UnifiedPush", "1")
                .header("X-Cache", "no")
                .header("TTL", "60")
                .POST(HttpRequest.BodyPublishers.ofByteArray(encrypt(subscription, noticeId)))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    static byte[] encrypt(Subscription subscription, long noticeId) throws GeneralSecurityException {
        if (noticeId <= 0) {
            throw new IllegalArgumentException("Se requiere un identificador de aviso positivo.");
        }
        ECPublicKey recipient = publicKey(subscription.publicKey());
        byte[] auth = decode(subscription.authSecret(), 16);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair sender = generator.generateKeyPair();
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        var ece = new HttpEce(Map.of("sender", sender), Map.of("sender", "P-256"));
        byte[] message = ("{\"noticeId\":" + noticeId + "}").getBytes(StandardCharsets.UTF_8);
        return ece.encrypt(message, salt, null, "sender", recipient, auth, Encoding.AES_128_GCM);
    }

    private static ECPublicKey publicKey(String encoded) throws GeneralSecurityException {
        byte[] bytes = decode(encoded, 65);
        if (bytes[0] != 4) {
            throw new IllegalArgumentException("Formato de clave pública inválido.");
        }
        return (ECPublicKey) Utils.loadPublicKey(bytes);
    }

    private static byte[] decode(String value, int expectedLength) {
        if (value == null || value.length() > 90 || !value.matches("[A-Za-z0-9_-]+={0,2}")) {
            throw new IllegalArgumentException("Formato de clave inválido.");
        }
        byte[] bytes = Base64.getUrlDecoder().decode(value);
        if (bytes.length != expectedLength) {
            throw new IllegalArgumentException("Tamaño de clave inválido.");
        }
        return bytes;
    }

    private static URI parseOrigin(String value) {
        if (value.isBlank()) {
            return null;
        }
        URI origin = URI.create(value.strip().replaceFirst("/$", ""));
        if (origin.getHost() == null || origin.getRawUserInfo() != null || origin.getRawQuery() != null
                || origin.getRawFragment() != null || !origin.getRawPath().isEmpty() || origin.getPort() > 65535
                || !("https".equals(origin.getScheme()) || "http".equals(origin.getScheme()))) {
            throw new IllegalArgumentException("Configura una URL HTTP o HTTPS de ntfy sin rutas ni credenciales.");
        }
        return origin;
    }
}

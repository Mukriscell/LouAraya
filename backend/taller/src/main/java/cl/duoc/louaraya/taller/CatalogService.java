package cl.duoc.louaraya.taller;

import cl.duoc.louaraya.common.ApiFailure;
import cl.duoc.louaraya.common.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static cl.duoc.louaraya.taller.TallerModels.*;

@Service
class CatalogService {
    static final String PRODUCT_COLUMNS = "id, name, width_cm, length_cm, material, price, campaign_category, "
            + "NULL AS image_reference, description, availability, active, stock";

    private final JdbcClient db;
    private final MemberData members;

    CatalogService(JdbcClient db, MemberData members) {
        this.db = db;
        this.members = members;
    }

    List<Product> products(Profile actor) {
        String filter = isAdmin(actor) ? "" : " WHERE active = true";
        return db.sql("SELECT " + PRODUCT_COLUMNS + " FROM products" + filter + " ORDER BY id")
                .query(Product.class).list();
    }

    Product product(long id, Profile actor) {
        Product product = product(id, false);
        if (!product.active() && !isAdmin(actor)) {
            throw productMissing();
        }
        return product;
    }

    Product product(long id, boolean lock) {
        return db.sql("SELECT " + PRODUCT_COLUMNS + " FROM products WHERE id = :id" + (lock ? " FOR UPDATE" : ""))
                .param("id", id).query(Product.class).optional().orElseThrow(CatalogService::productMissing);
    }

    @Transactional
    public Product createProduct(Profile actor, ProductDraft draft) {
        actor.requireAdmin();
        ProductDraft validated = validateProduct(draft);
        var key = new GeneratedKeyHolder();
        productParams(db.sql("""
                INSERT INTO products
                    (name, width_cm, length_cm, material, price, campaign_category, description, availability, active)
                VALUES (:name, :width, :length, :material, :price, :category, :description, :availability, :active)
                """), validated).update(key, "id");
        return product(key.getKey().longValue(), false);
    }

    @Transactional
    public Product updateProduct(Profile actor, long id, ProductDraft draft) {
        actor.requireAdmin();
        Product current = product(id, true);
        ProductDraft validated = validateProduct(new ProductDraft(
                prefer(draft.name(), current.name()), prefer(draft.widthCm(), current.widthCm()),
                prefer(draft.lengthCm(), current.lengthCm()), prefer(draft.material(), current.material()),
                prefer(draft.price(), current.price()), prefer(draft.campaignCategory(), current.campaignCategory()),
                prefer(draft.description(), current.description()), prefer(draft.availability(), current.availability()),
                prefer(draft.active(), current.active())));
        productParams(db.sql("""
                UPDATE products SET name = :name, width_cm = :width, length_cm = :length, material = :material,
                    price = :price, campaign_category = :category, description = :description,
                    availability = :availability, active = :active WHERE id = :id
                """), validated).param("id", id).update();
        return product(id, false);
    }

    @Transactional
    public void deactivateProduct(Profile actor, long id) {
        actor.requireAdmin();
        int updated = db.sql("UPDATE products SET active = false WHERE id = :id").param("id", id).update();
        if (updated == 0) {
            throw productMissing();
        }
    }

    List<Collection> collections(Profile actor) {
        return db.sql("SELECT id, name, description FROM atelier_collections ORDER BY id")
                .query((row, count) -> collection(row.getLong("id"), row.getString("name"),
                        row.getString("description"), isAdmin(actor))).list();
    }

    private Collection collection(long id, boolean includeInactive) {
        return db.sql("SELECT id, name, description FROM atelier_collections WHERE id = :id").param("id", id)
                .query((r, n) -> collection(r.getLong(1), r.getString(2), r.getString(3), includeInactive))
                .optional().orElseThrow(() -> ApiFailure.notFound("La colección no existe."));
    }

    private Collection collection(long id, String name, String description, boolean includeInactive) {
        String activeFilter = includeInactive ? "" : " AND p.active = true";
        List<Long> products = db.sql("""
                SELECT cp.product_id FROM collection_products cp JOIN products p ON p.id = cp.product_id
                WHERE cp.collection_id = :id
                """ + activeFilter + " ORDER BY cp.product_id")
                .param("id", id).query(Long.class).list();
        return new Collection(id, name, description, products);
    }

    @Transactional
    public Collection saveCollection(Profile actor, Long id, Collection input) {
        actor.requireAdmin();
        String name = required(input.name(), "name", 2, 100);
        String description = optional(input.description(), "description", 2000, "");
        List<Long> products = input.productIds();
        if (products == null || products.size() > 500 || products.stream().anyMatch(p -> p == null || p <= 0)) {
            throw ApiFailure.field("productIds", "Selecciona productos válidos.");
        }
        var distinct = new LinkedHashSet<>(products);
        if (!distinct.isEmpty()) {
            int existing = db.sql("SELECT count(*) FROM products WHERE id IN (:ids)")
                    .param("ids", distinct).query(Integer.class).single();
            if (existing != distinct.size()) {
                throw ApiFailure.field("productIds", "Una pieza no existe.");
            }
        }
        if (id == null) {
            var key = new GeneratedKeyHolder();
            db.sql("INSERT INTO atelier_collections (name, description) VALUES (:name, :description)")
                    .param("name", name).param("description", description).update(key, "id");
            id = key.getKey().longValue();
        } else {
            int updated = db.sql("UPDATE atelier_collections SET name = :name, description = :description WHERE id = :id")
                    .param("id", id).param("name", name).param("description", description).update();
            if (updated == 0) {
                throw ApiFailure.notFound("La colección no existe.");
            }
            db.sql("DELETE FROM collection_products WHERE collection_id = :id").param("id", id).update();
        }
        for (long product : distinct) {
            db.sql("INSERT INTO collection_products (collection_id, product_id) VALUES (:id, :product)")
                    .param("id", id).param("product", product).update();
        }
        return collection(id, true);
    }

    List<Campaign> campaigns(Profile actor) {
        String filter = isAdmin(actor) ? "" : " WHERE published = true";
        return db.sql("SELECT * FROM campaigns" + filter + " ORDER BY starts_on DESC, id").query(Campaign.class).list();
    }

    private Campaign campaign(long id, boolean lock) {
        return db.sql("SELECT * FROM campaigns WHERE id = :id" + (lock ? " FOR UPDATE" : ""))
                .param("id", id).query(Campaign.class).optional()
                .orElseThrow(() -> ApiFailure.notFound("La campaña no existe."));
    }

    @Transactional
    public Campaign saveCampaign(Profile actor, Long id, Campaign input) {
        actor.requireAdmin();
        String name = required(input.name(), "name", 2, 100);
        LocalDate start = date(input.startsOn(), "start");
        LocalDate end = date(input.endsOn(), "end");
        if (start.isAfter(end)) {
            throw ApiFailure.field("end", "La fecha final debe ser posterior o igual al inicio.");
        }
        collection(input.collectionId(), true);
        boolean previouslyPublished = id != null && campaign(id, true).published();
        String sql = id == null ? """
                INSERT INTO campaigns (name, collection_id, starts_on, ends_on, published, target_category)
                VALUES (:name, :collection, :start, :end, :published, :target)
                """ : """
                UPDATE campaigns SET name = :name, collection_id = :collection, starts_on = :start,
                    ends_on = :end, published = :published, target_category = :target WHERE id = :id
                """;
        var statement = db.sql(sql).param("name", name).param("collection", input.collectionId())
                .param("start", start.toString()).param("end", end.toString()).param("published", input.published())
                .param("target", input.targetCategory() == null ? null : input.targetCategory().name());
        if (id == null) {
            var key = new GeneratedKeyHolder();
            statement.update(key, "id");
            id = key.getKey().longValue();
        } else {
            statement.param("id", id).update();
        }
        if (input.published() && !previouslyPublished) {
            members.campaignNotice(id, name, input.targetCategory(), UUID.randomUUID().toString());
        }
        return campaign(id, false);
    }

    boolean isCampaignProduct(long campaignId, long collectionId, long productId) {
        return db.sql("""
                SELECT count(*) FROM campaigns c JOIN collection_products cp ON cp.collection_id = c.collection_id
                WHERE c.id = :campaign AND c.collection_id = :collection AND cp.product_id = :product AND c.published = true
                """).param("campaign", campaignId).param("collection", collectionId).param("product", productId)
                .query(Integer.class).single() == 1;
    }

    private static JdbcClient.StatementSpec productParams(JdbcClient.StatementSpec sql, ProductDraft product) {
        return sql.param("name", product.name()).param("width", product.widthCm()).param("length", product.lengthCm())
                .param("material", product.material()).param("price", product.price())
                .param("category", product.campaignCategory().name()).param("description", product.description())
                .param("availability", product.availability().name()).param("active", product.active());
    }

    private static ProductDraft validateProduct(ProductDraft draft) {
        if (draft.price() == null || draft.price() < 0) {
            throw ApiFailure.field("price", "El precio debe ser un entero igual o mayor a cero.");
        }
        if (draft.campaignCategory() == null) {
            throw ApiFailure.field("category", "Selecciona una categoría.");
        }
        return new ProductDraft(required(draft.name(), "name", 2, 100), dimension(draft.widthCm(), "width"),
                dimension(draft.lengthCm(), "length"), required(draft.material(), "material", 1, 100),
                draft.price(), draft.campaignCategory(), optional(draft.description(), "description", 2000, ""),
                prefer(draft.availability(), Availability.AVAILABLE), prefer(draft.active(), true));
    }

    static boolean isAdmin(Profile actor) {
        return actor != null && actor.role() == Profile.Role.ADMIN;
    }

    static double dimension(Double value, String field) {
        if (value == null || !Double.isFinite(value) || value <= 0) {
            throw ApiFailure.field(field, "Escribe una medida mayor a cero.");
        }
        return value;
    }

    static String required(String value, String field, int minimum, int maximum) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < minimum || normalized.length() > maximum) {
            throw ApiFailure.field(field, "Escribe entre " + minimum + " y " + maximum + " caracteres.");
        }
        return normalized;
    }

    static String optional(String value, String field, int maximum, String whenEmpty) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maximum) {
            throw ApiFailure.field(field, "Usa como máximo " + maximum + " caracteres.");
        }
        return normalized.isEmpty() ? whenEmpty : normalized;
    }

    static LocalDate date(String value, String field) {
        try {
            if (value != null && value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(value);
            }
        } catch (DateTimeParseException ignored) {
            // Send the validation error below for invalid calendar dates.
        }
        throw ApiFailure.field(field, "Ingresa una fecha válida (AAAA-MM-DD).");
    }

    private static <T> T prefer(T value, T fallback) {
        return value == null ? fallback : value;
    }

    private static ApiFailure productMissing() {
        return ApiFailure.notFound("La pieza no existe o no está disponible.");
    }
}

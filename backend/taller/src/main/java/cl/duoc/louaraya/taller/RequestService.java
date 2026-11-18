package cl.duoc.louaraya.taller;

import cl.duoc.louaraya.common.ApiFailure;
import cl.duoc.louaraya.common.Profile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static cl.duoc.louaraya.taller.TallerModels.*;

@Service
class RequestService {
    private static final String REQUEST_COLUMNS = """
            id, date, anonymized_client, total, status, product_id, intention, comment, campaign_category, owner_id,
            customization, campaign_id, source_collection_id, journey_id, agreed_total, agreed_at, quantity
            """;
    private static final String STOCK_COLUMNS = """
            id, product_id, quantity_delta, stock_after, type, date, actor_id, note, request_id
            """;
    private static final ZoneId CHILE = ZoneId.of("America/Santiago");

    private final JdbcClient db;
    private final ObjectMapper json;
    private final CatalogService catalog;
    private final MemberData members;

    RequestService(JdbcClient db, ObjectMapper json, CatalogService catalog, MemberData members) {
        this.db = db;
        this.json = json;
        this.catalog = catalog;
        this.members = members;
    }

    List<CommercialRequest> list(Profile actor) {
        var statement = db.sql("SELECT " + REQUEST_COLUMNS + " FROM commercial_requests"
                + (CatalogService.isAdmin(actor) ? "" : " WHERE owner_id = :actor") + " ORDER BY date DESC, id DESC");
        if (!CatalogService.isAdmin(actor)) {
            statement.param("actor", actor.id());
        }
        return statement.query(CommercialRequest.class).list();
    }

    CommercialRequest get(Profile actor, long id) {
        CommercialRequest request = request(id, false);
        requireOwnerOrAdmin(actor, request);
        return request;
    }

    private CommercialRequest request(long id, boolean lock) {
        return db.sql("SELECT " + REQUEST_COLUMNS + " FROM commercial_requests WHERE id = :id" + (lock ? " FOR UPDATE" : ""))
                .param("id", id).query(CommercialRequest.class).optional()
                .orElseThrow(() -> ApiFailure.notFound("La solicitud no existe."));
    }

    @Transactional
    public CommercialRequest create(Profile actor, RequestDraft draft) {
        actor.requireClient();
        String operationKey = operationKey(draft.idempotencyKey());
        String hash = fingerprint(draft);
        lockWrites("request");
        var replay = db.sql("SELECT id, operation_hash FROM commercial_requests WHERE owner_id = :owner AND operation_key = :key")
                .param("owner", actor.id()).param("key", operationKey).query(RequestService::storedOperation).optional();
        if (replay.isPresent()) {
            return request(replay.get().requireMatch(hash), false);
        }

        if (draft.productId() == null || draft.productId() <= 0) {
            throw ApiFailure.field("productId", "Selecciona una pieza.");
        }
        if (draft.intention() == null) {
            throw ApiFailure.field("intention", "Selecciona compra, reserva o cotización.");
        }
        int quantity = positiveQuantity(draft.quantity());
        Product product = catalog.product(draft.productId(), true);
        if (!product.active()) {
            throw ApiFailure.conflict("La pieza ya no está disponible.");
        }
        if (draft.displayedPrice() == null || draft.displayedPrice() != product.price()) {
            throw ApiFailure.conflict("El precio cambió. Vuelve al detalle de la pieza.");
        }

        long total;
        try {
            total = Math.multiplyExact(product.price(), quantity);
        } catch (ArithmeticException overflow) {
            throw ApiFailure.field("quantity", "El importe de esta cantidad supera el máximo permitido.");
        }
        String comment = CatalogService.optional(draft.comment(), "comment", 500, null);
        String customization = draft.intention() == Intention.QUOTATION ? quotation(draft.details()) : null;
        validateTrace(draft, product);

        var key = new GeneratedKeyHolder();
        db.sql("""
                INSERT INTO commercial_requests (
                    date, anonymized_client, total, status, product_id, intention, comment, campaign_category, owner_id,
                    customization, campaign_id, source_collection_id, journey_id, quantity, operation_key, operation_hash
                ) VALUES (
                    :date, :anonymized, :total, 'PENDING', :product, :intention, :comment, :category, :owner,
                    :customization, :campaign, :collection, :journey, :quantity, :key, :hash
                )
                """).param("date", now()).param("anonymized", "CLIENTE-" + actor.id()).param("total", total)
                .param("product", product.id()).param("intention", draft.intention().name()).param("comment", comment)
                .param("category", product.campaignCategory().name()).param("owner", actor.id())
                .param("customization", customization).param("campaign", draft.campaignId())
                .param("collection", draft.sourceCollectionId()).param("journey", draft.journeyId())
                .param("quantity", quantity).param("key", operationKey).param("hash", hash).update(key, "id");
        long id = key.getKey().longValue();
        members.adminNotice("Nueva solicitud #" + id, product.name(), id, "request/" + id);
        return request(id, false);
    }

    @Transactional
    public CommercialRequest updateStatus(Profile actor, long id, Status status) {
        actor.requireAdmin();
        if (status == null) {
            throw ApiFailure.field("status", "Selecciona un estado válido.");
        }
        CommercialRequest current = request(id, true);
        if (current.status() == status) {
            return current;
        }
        if (current.status() == Status.CANCELLED) {
            throw ApiFailure.conflict("La solicitud está cancelada y no puede reactivarse.");
        }
        if (status == Status.CANCELLED) {
            requireCancellable(current);
        }
        if (status == Status.DELIVERED && current.intention() != Intention.QUOTATION && !recordedSale(id)) {
            Product product = catalog.product(current.productId(), true);
            recordStock(actor, product, -current.quantity(), StockType.REQUEST_SALE,
                    "Entrega de solicitud #" + id, id, null, null);
        }

        setStatus(id, status);
        members.clientNotice(current.ownerId(), "Solicitud #" + id + " actualizada",
                "La atención de tu solicitud cambió. Consulta su nuevo estado.", id, event("status", id));
        return request(id, false);
    }

    @Transactional
    public CommercialRequest cancel(Profile actor, long id) {
        actor.requireClient();
        CommercialRequest current = request(id, true);
        requireOwnerOrAdmin(actor, current);
        if (current.status() != Status.CANCELLED) {
            requireCancellable(current);
            setStatus(id, Status.CANCELLED);
            members.adminNotice("Solicitud #" + id + " cancelada", "El cliente canceló su solicitud.", id, "cancel/" + id);
        }
        return request(id, false);
    }

    @Transactional
    public CommercialRequest updateAgreement(Profile actor, long id, Long amount) {
        actor.requireAdmin();
        if (amount == null || amount < 0 || amount > 9_999_999_999L) {
            throw ApiFailure.field("agreedTotal", "Escribe un importe entero entre 0 y 9.999.999.999 CLP.");
        }
        CommercialRequest current = request(id, true);
        if (current.status() == Status.CANCELLED) {
            throw ApiFailure.conflict("La solicitud está cancelada. No se puede modificar su importe.");
        }
        if (!amount.equals(current.agreedTotal())) {
            db.sql("UPDATE commercial_requests SET agreed_total = :amount, agreed_at = :date WHERE id = :id")
                    .param("id", id).param("amount", amount).param("date", now()).update();
            members.clientNotice(current.ownerId(), "Importe de solicitud #" + id,
                    "El taller registró un importe acordado de $" + amount + " CLP.", id, event("agreement", id));
        }
        return request(id, false);
    }

    @Transactional
    public StockMovement manualStock(Profile actor, long productId, StockDraft draft) {
        actor.requireAdmin();
        int quantity = positiveQuantity(draft.quantity());
        if (draft.type() == null || draft.type() == StockType.REQUEST_SALE) {
            throw ApiFailure.field("type", "Selecciona un movimiento de bodega válido.");
        }
        String note = CatalogService.optional(draft.note(), "stockNote", 250, "");
        String key = operationKey(draft.idempotencyKey());
        String hash = fingerprint(new ManualOperation(productId, quantity, draft.type(), note));
        lockWrites("stock");
        var replay = db.sql("SELECT id, operation_hash FROM stock_movements WHERE actor_id = :actor AND operation_key = :key")
                .param("actor", actor.id()).param("key", key).query(RequestService::storedOperation).optional();
        if (replay.isPresent()) {
            return movement(replay.get().requireMatch(hash));
        }
        Product product = catalog.product(productId, true);
        int delta = draft.type() == StockType.ENTRY ? quantity : -quantity;
        return recordStock(actor, product, delta, draft.type(), note, null, key, hash);
    }

    List<StockMovement> movements(Profile actor) {
        if (!CatalogService.isAdmin(actor)) {
            return List.of();
        }
        return db.sql("SELECT " + STOCK_COLUMNS + " FROM stock_movements ORDER BY date DESC, id DESC")
                .query(StockMovement.class).list();
    }

    private StockMovement recordStock(Profile actor, Product product, int delta, StockType type, String note,
                                      Long requestId, String operationKey, String hash) {
        long updated = (long) product.stock() + delta;
        if (updated < 0) {
            throw ApiFailure.conflict("Stock insuficiente: hay " + product.stock() + " unidades en bodega.");
        }
        if (updated > Integer.MAX_VALUE) {
            throw ApiFailure.field("quantity", "La cantidad supera el máximo permitido en bodega.");
        }
        db.sql("UPDATE products SET stock = :stock WHERE id = :id")
                .param("id", product.id()).param("stock", (int) updated).update();
        var key = new GeneratedKeyHolder();
        db.sql("""
                INSERT INTO stock_movements (
                    product_id, quantity_delta, stock_after, type, date, actor_id, note, request_id, operation_key, operation_hash
                ) VALUES (:product, :delta, :stock, :type, :date, :actor, :note, :request, :key, :hash)
                """).param("product", product.id()).param("delta", delta).param("stock", (int) updated)
                .param("type", type.name()).param("date", now()).param("actor", actor.id()).param("note", note)
                .param("request", requestId).param("key", operationKey).param("hash", hash).update(key, "id");
        return movement(key.getKey().longValue());
    }

    private StockMovement movement(long id) {
        return db.sql("SELECT " + STOCK_COLUMNS + " FROM stock_movements WHERE id = :id")
                .param("id", id).query(StockMovement.class).single();
    }

    private boolean recordedSale(long id) {
        return db.sql("SELECT count(*) FROM stock_movements WHERE request_id = :id")
                .param("id", id).query(Integer.class).single() > 0;
    }

    private void setStatus(long id, Status status) {
        db.sql("UPDATE commercial_requests SET status = :status WHERE id = :id")
                .param("id", id).param("status", status.name()).update();
    }

    private void requireCancellable(CommercialRequest request) {
        if (request.status() == Status.DELIVERED || recordedSale(request.id())) {
            throw ApiFailure.conflict("Una solicitud entregada no se puede cancelar.");
        }
    }

    // A stable lock row protects the idempotency lookup and insert even when the key is still absent.
    private void lockWrites(String operation) {
        db.sql("SELECT operation FROM write_guards WHERE operation = :operation FOR UPDATE")
                .param("operation", operation).query(String.class).single();
    }

    private String fingerprint(Object command) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(command)));
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new IllegalStateException("No se pudo identificar la operación.", e);
        }
    }

    private static void requireOwnerOrAdmin(Profile actor, CommercialRequest request) {
        if (!CatalogService.isAdmin(actor) && request.ownerId() != actor.id()) {
            throw ApiFailure.notFound("La solicitud no está disponible.");
        }
    }

    private void validateTrace(RequestDraft draft, Product product) {
        if (draft.campaignId() == null && draft.sourceCollectionId() == null && draft.journeyId() == null) {
            return;
        }
        if (draft.campaignId() == null || draft.sourceCollectionId() == null || draft.journeyId() == null
                || !draft.journeyId().matches("[A-Za-z0-9_-]{1,100}")
                || !catalog.isCampaignProduct(draft.campaignId(), draft.sourceCollectionId(), product.id())) {
            throw ApiFailure.badRequest("La pieza no pertenece a una campaña publicada de este recorrido.");
        }
    }

    private static String quotation(QuotationDetails details) {
        if (details == null) {
            throw ApiFailure.field("event", "Completa los datos de personalización.");
        }
        LocalDate date = CatalogService.date(details.requiredOn(), "date");
        if (date.isBefore(LocalDate.now(CHILE))) {
            throw ApiFailure.field("date", "La fecha deseada no puede ser anterior a hoy.");
        }
        return "Evento: " + CatalogService.required(details.event(), "event", 1, 100)
                + "\nMedidas: " + CatalogService.dimension(details.widthCm(), "width") + " × "
                + CatalogService.dimension(details.lengthCm(), "length") + " cm"
                + "\nMaterial: " + CatalogService.required(details.material(), "material", 1, 100)
                + "\nColores: " + CatalogService.required(details.colors(), "colors", 1, 100)
                + "\nFecha requerida: " + date;
    }

    private static int positiveQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw ApiFailure.field("quantity", "Escribe una cantidad entera mayor a cero.");
        }
        return quantity;
    }

    private static String operationKey(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_:-]{1,100}")) {
            throw ApiFailure.badRequest("Se requiere un identificador de operación válido.");
        }
        return value;
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static String event(String kind, long id) {
        return kind + "/" + id + "/" + UUID.randomUUID();
    }

    private static StoredOperation storedOperation(java.sql.ResultSet row, int n) throws java.sql.SQLException {
        return new StoredOperation(row.getLong(1), row.getString(2));
    }

    private record StoredOperation(long id, String hash) {
        long requireMatch(String expected) {
            if (!hash.equals(expected)) {
                throw ApiFailure.conflict("Este identificador de operación ya se usó con otros datos.");
            }
            return id;
        }
    }

    private record ManualOperation(long productId, int quantity, StockType type, String note) {}
}

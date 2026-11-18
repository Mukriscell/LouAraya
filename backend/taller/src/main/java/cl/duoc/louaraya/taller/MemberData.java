package cl.duoc.louaraya.taller;

import cl.duoc.louaraya.common.ApiFailure;
import cl.duoc.louaraya.common.Profile;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static cl.duoc.louaraya.taller.TallerModels.*;

@Service
class MemberData {
    private final JdbcClient db;
    private final ApplicationEventPublisher events;

    MemberData(JdbcClient db, ApplicationEventPublisher events) {
        this.db = db;
        this.events = events;
    }

    Set<Long> favorites(Profile actor, String type) {
        return db.sql("SELECT target_id FROM favorites WHERE owner_id = :actor AND target_type = :type")
                .param("actor", actor.id()).param("type", type).query(Long.class).set();
    }

    @Transactional
    public void setFavorite(Profile actor, String type, long id, boolean selected) {
        actor.requireClient();
        lockPersonalChanges();
        if (selected) {
            String find = type.equals("PRODUCT")
                    ? "SELECT count(*) FROM products WHERE id = :id AND active = true"
                    : "SELECT count(*) FROM atelier_collections WHERE id = :id";
            if (db.sql(find).param("id", id).query(Integer.class).single() == 0) {
                throw ApiFailure.notFound("El elemento no está disponible.");
            }
            if (!favorites(actor, type).contains(id)) {
                db.sql("INSERT INTO favorites (owner_id, target_type, target_id) VALUES (:actor, :type, :id)")
                        .param("actor", actor.id()).param("type", type).param("id", id).update();
            }
        } else {
            db.sql("DELETE FROM favorites WHERE owner_id = :actor AND target_type = :type AND target_id = :id")
                    .param("actor", actor.id()).param("type", type).param("id", id).update();
        }
    }

    Set<Long> hiddenRequests(Profile actor) {
        return db.sql("SELECT request_id FROM hidden_requests WHERE owner_id = :actor")
                .param("actor", actor.id()).query(Long.class).set();
    }

    @Transactional
    public void hideRequests(Profile actor, List<Long> ids) {
        actor.requireAdmin();
        lockPersonalChanges();
        Set<Long> existing = hiddenRequests(actor);
        for (long id : validatedIds(ids)) {
            int found = db.sql("SELECT count(*) FROM commercial_requests WHERE id = :id")
                    .param("id", id).query(Integer.class).single();
            if (found == 0) {
                throw ApiFailure.notFound("La solicitud no existe.");
            }
            if (!existing.contains(id)) {
                db.sql("INSERT INTO hidden_requests (owner_id, request_id) VALUES (:actor, :id)")
                        .param("actor", actor.id()).param("id", id).update();
            }
        }
    }

    List<Notice> notices(Profile actor) {
        return db.sql("""
                SELECT n.id, :actor AS user_id, n.title, n.body, n.destination, n.date,
                    COALESCE(r.read, false) AS read, n.event_key, COALESCE(r.delivered, false) AS delivered
                FROM notices n LEFT JOIN notice_receipts r ON r.notice_id = n.id AND r.owner_id = :actor
                WHERE (n.recipient_id = :actor OR (n.recipient_role = :role AND (
                        n.target_category IS NULL OR n.target_category = :preference OR EXISTS (
                            SELECT 1 FROM favorites f JOIN products p ON p.id = f.target_id
                            WHERE f.owner_id = :actor AND f.target_type = 'PRODUCT'
                                AND p.campaign_category = n.target_category
                        ) OR EXISTS (
                            SELECT 1 FROM favorites f JOIN collection_products cp ON cp.collection_id = f.target_id
                                JOIN products p ON p.id = cp.product_id
                            WHERE f.owner_id = :actor AND f.target_type = 'COLLECTION'
                                AND p.campaign_category = n.target_category
                        ))))
                    AND COALESCE(r.dismissed, false) = false
                ORDER BY n.date DESC, n.id DESC
                """).param("actor", actor.id()).param("role", actor.role().name())
                .param("preference", actor.preferredCategory(), java.sql.Types.VARCHAR).query(Notice.class).list();
    }

    @Transactional
    public void read(Profile actor, long id) {
        markNotices(actor, List.of(id), "read");
    }

    @Transactional
    public void delivered(Profile actor, long id) {
        markNotices(actor, List.of(id), "delivered");
    }

    @Transactional
    public void dismiss(Profile actor, List<Long> ids) {
        markNotices(actor, ids, "dismissed");
    }

    void clientNotice(long client, String title, String body, long requestId, String eventKey) {
        notice(client, null, null, title, body, "request/" + requestId, eventKey);
    }

    void adminNotice(String title, String body, long requestId, String eventKey) {
        notice(null, Profile.Role.ADMIN, null, title, body, "request/" + requestId, eventKey);
    }

    void campaignNotice(long id, String name, Category target, String serial) {
        long noticeId = notice(null, Profile.Role.CLIENT, target, "Nueva campaña", name,
                "campaign/" + id, "campaign/" + id + "/" + serial);
        events.publishEvent(new CampaignPush.Published(noticeId));
    }

    private long notice(Long recipient, Profile.Role role, Category category, String title,
                        String body, String destination, String eventKey) {
        var key = new GeneratedKeyHolder();
        db.sql("""
                INSERT INTO notices (recipient_id, recipient_role, target_category, title, body, destination, date, event_key)
                VALUES (:recipient, :role, :category, :title, :body, :destination, :date, :key)
                """).param("recipient", recipient).param("role", role == null ? null : role.name())
                .param("category", category == null ? null : category.name()).param("title", title)
                .param("body", body).param("destination", destination).param("date", System.currentTimeMillis())
                .param("key", eventKey).update(key, "id");
        return key.getKey().longValue();
    }

    // Column names are selected by the three methods above, never provided by a caller.
    private void markNotices(Profile actor, List<Long> ids, String column) {
        lockPersonalChanges();
        var requested = validatedIds(ids);
        var visible = notices(actor).stream().map(Notice::id).collect(java.util.stream.Collectors.toSet());
        for (long id : requested) {
            if (!visible.contains(id)) {
                if (column.equals("dismissed") && isDismissed(actor, id)) {
                    continue;
                }
                throw ApiFailure.notFound("El aviso no está disponible.");
            }
            int updated = db.sql("UPDATE notice_receipts SET " + column + " = true WHERE owner_id = :actor AND notice_id = :notice")
                    .param("actor", actor.id()).param("notice", id).update();
            if (updated == 0) {
                db.sql("INSERT INTO notice_receipts (owner_id, notice_id, " + column + ") VALUES (:actor, :notice, true)")
                        .param("actor", actor.id()).param("notice", id).update();
            }
        }
    }

    private boolean isDismissed(Profile actor, long notice) {
        return db.sql("SELECT dismissed FROM notice_receipts WHERE owner_id = :actor AND notice_id = :notice")
                .param("actor", actor.id()).param("notice", notice).query(Boolean.class).optional().orElse(false);
    }

    private void lockPersonalChanges() {
        db.sql("SELECT operation FROM write_guards WHERE operation = 'personal' FOR UPDATE").query(String.class).single();
    }

    private static Set<Long> validatedIds(List<Long> ids) {
        if (ids == null || ids.size() > 1000 || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw ApiFailure.badRequest("Los identificadores no son válidos.");
        }
        return Set.copyOf(ids);
    }
}

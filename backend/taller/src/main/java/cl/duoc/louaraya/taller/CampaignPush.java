package cl.duoc.louaraya.taller;

import cl.duoc.louaraya.common.ApiFailure;
import cl.duoc.louaraya.common.Profile;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
class CampaignPush {
    record Published(long noticeId) {}
    private static final Logger LOG = LoggerFactory.getLogger(CampaignPush.class);

    private final JdbcClient db;
    private final PushBroker broker;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), command -> {
                var worker = new Thread(command, "louaraya-campaign-push");
                worker.setDaemon(true);
                return worker;
            });

    CampaignPush(JdbcClient db, PushBroker broker) {
        this.db = db;
        this.broker = broker;
        executor.allowCoreThreadTimeOut(true);
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }

    @Transactional
    public void register(Profile actor, String installation, PushBroker.Subscription subscription) {
        actor.requireClient();
        String id = validateId(installation);
        if (!actor.notificationsEnabled()) {
            unregister(actor, id);
            return;
        }
        if (!broker.enabled()) {
            throw new ApiFailure(HttpStatus.SERVICE_UNAVAILABLE, "El distribuidor de avisos no está configurado.");
        }
        broker.validate(subscription);
        db.sql("SELECT operation FROM write_guards WHERE operation = 'push_devices' FOR UPDATE")
                .query(String.class).single();
        try {
            int updated = params(db.sql("""
                    UPDATE push_devices SET endpoint = :endpoint, public_key = :key, auth_secret = :auth,
                        preferred_category = :category WHERE owner_id = :owner AND installation_id = :id
                    """), actor, id, subscription).update();
            if (updated == 0) {
                params(db.sql("""
                        INSERT INTO push_devices (owner_id, installation_id, endpoint, public_key, auth_secret, preferred_category)
                        VALUES (:owner, :id, :endpoint, :key, :auth, :category)
                        """), actor, id, subscription).update();
            }
        } catch (DuplicateKeyException e) {
            throw ApiFailure.conflict("Este receptor push ya está registrado en otra instalación.");
        }
    }

    @Transactional
    public void unregister(Profile actor, String installation) {
        actor.requireClient();
        db.sql("DELETE FROM push_devices WHERE owner_id = :owner AND installation_id = :id")
                .param("owner", actor.id()).param("id", validateId(installation)).update();
    }

    @TransactionalEventListener
    void sendAfterCommit(Published event) {
        if (!broker.enabled()) {
            return;
        }
        try {
            executor.execute(() -> send(event.noticeId()));
        } catch (RejectedExecutionException full) {
            LOG.warn("Cola de avisos ocupada para aviso {}", event.noticeId());
        }
    }

    private void send(long noticeId) {
        try {
            for (var subscription : recipients(noticeId)) {
                try {
                    int status = broker.publish(subscription, noticeId);
                    if (status < 200 || status >= 300) {
                        LOG.warn("El distribuidor respondió HTTP {} para aviso {}", status, noticeId);
                    }
                } catch (InterruptedException stopping) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception offline) {
                    LOG.warn("No se entregó el aviso {} al distribuidor ({})", noticeId, offline.getClass().getSimpleName());
                }
            }
        } catch (RuntimeException offline) {
            LOG.warn("No se pudieron resolver receptores del aviso {} ({})", noticeId, offline.getClass().getSimpleName());
        }
    }

    private List<PushBroker.Subscription> recipients(long noticeId) {
        return db.sql("""
                SELECT d.endpoint, d.public_key, d.auth_secret FROM push_devices d
                CROSS JOIN notices n
                LEFT JOIN notice_receipts r ON r.notice_id = n.id AND r.owner_id = d.owner_id
                WHERE n.id = :id AND n.recipient_role = 'CLIENT' AND n.destination LIKE 'campaign/%'
                    AND COALESCE(r.dismissed, false) = false
                    AND (n.target_category IS NULL OR n.target_category = d.preferred_category OR EXISTS (
                        SELECT 1 FROM favorites f JOIN products p ON p.id = f.target_id
                        WHERE f.owner_id = d.owner_id AND f.target_type = 'PRODUCT'
                            AND p.campaign_category = n.target_category
                    ) OR EXISTS (
                        SELECT 1 FROM favorites f JOIN collection_products cp ON cp.collection_id = f.target_id
                            JOIN products p ON p.id = cp.product_id
                        WHERE f.owner_id = d.owner_id AND f.target_type = 'COLLECTION'
                            AND p.campaign_category = n.target_category
                    ))
                ORDER BY d.owner_id, d.installation_id
                """).param("id", noticeId).query(PushBroker.Subscription.class).list();
    }

    private static JdbcClient.StatementSpec params(JdbcClient.StatementSpec sql, Profile actor, String id,
                                                 PushBroker.Subscription subscription) {
        return sql.param("owner", actor.id()).param("id", id).param("endpoint", subscription.endpoint())
                .param("key", subscription.publicKey()).param("auth", subscription.authSecret())
                .param("category", actor.preferredCategory());
    }

    private static String validateId(String id) {
        if (!id.matches("[A-Za-z0-9_-]{1,80}")) {
            throw ApiFailure.badRequest("La instalación no es válida.");
        }
        return id;
    }
}

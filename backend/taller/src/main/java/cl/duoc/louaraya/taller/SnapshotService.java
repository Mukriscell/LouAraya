package cl.duoc.louaraya.taller;

import cl.duoc.louaraya.common.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static cl.duoc.louaraya.taller.TallerModels.Snapshot;

@Service
class SnapshotService {
    private final CatalogService catalog;
    private final RequestService requests;
    private final MemberData members;

    SnapshotService(CatalogService catalog, RequestService requests, MemberData members) {
        this.catalog = catalog;
        this.requests = requests;
        this.members = members;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Snapshot read(Profile actor) {
        return new Snapshot(actor, catalog.products(actor), requests.list(actor), members.favorites(actor, "PRODUCT"),
                catalog.collections(actor), catalog.campaigns(actor), List.of(), members.notices(actor),
                members.favorites(actor, "COLLECTION"), List.of(), List.of(), requests.movements(actor),
                members.hiddenRequests(actor));
    }
}

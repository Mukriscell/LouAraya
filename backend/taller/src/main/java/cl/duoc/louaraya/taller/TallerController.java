package cl.duoc.louaraya.taller;

import cl.duoc.louaraya.common.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cl.duoc.louaraya.taller.TallerModels.*;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@RestController
class TallerController {
    private final IdentityClient identities;
    private final CatalogService catalog;
    private final RequestService requests;
    private final MemberData members;
    private final SnapshotService snapshots;

    TallerController(IdentityClient identities, CatalogService catalog, RequestService requests, MemberData members,
                     SnapshotService snapshots) {
        this.identities = identities;
        this.catalog = catalog;
        this.requests = requests;
        this.members = members;
        this.snapshots = snapshots;
    }

    @GetMapping("/products")
    List<Product> products(@RequestHeader(value = AUTHORIZATION, required = false) String auth) {
        return catalog.products(optionalProfile(auth));
    }

    @GetMapping("/products/{id}")
    Product product(@RequestHeader(value = AUTHORIZATION, required = false) String auth, @PathVariable long id) {
        return catalog.product(id, optionalProfile(auth));
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    Product createProduct(@RequestHeader(value = AUTHORIZATION, required = false) String auth,
                          @RequestBody ProductDraft draft) {
        return catalog.createProduct(actor(auth), draft);
    }

    @PatchMapping("/products/{id}")
    Product changeProduct(@RequestHeader(value = AUTHORIZATION, required = false) String auth,
                          @PathVariable long id, @RequestBody ProductDraft draft) {
        return catalog.updateProduct(actor(auth), id, draft);
    }

    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteProduct(@RequestHeader(value = AUTHORIZATION, required = false) String auth, @PathVariable long id) {
        catalog.deactivateProduct(actor(auth), id);
    }

    @GetMapping("/snapshot")
    Snapshot snapshot(@RequestHeader(value = AUTHORIZATION, required = false) String auth) {
        return snapshots.read(actor(auth));
    }

    @GetMapping("/requests")
    List<CommercialRequest> requests(@RequestHeader(value = AUTHORIZATION, required = false) String auth) {
        return requests.list(actor(auth));
    }

    @GetMapping("/requests/{id}")
    CommercialRequest request(@RequestHeader(value = AUTHORIZATION, required = false) String auth, @PathVariable long id) {
        return requests.get(actor(auth), id);
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    CommercialRequest request(@RequestHeader(value = AUTHORIZATION, required = false) String auth,
                              @RequestBody RequestDraft draft) {
        return requests.create(actor(auth), draft);
    }

    @PatchMapping("/requests/{id}/status")
    CommercialRequest changeStatus(@RequestHeader(value = AUTHORIZATION, required = false) String auth, @PathVariable long id,
                                   @RequestBody StatusChange change) {
        return requests.updateStatus(actor(auth), id, change.status());
    }

    @PostMapping("/requests/{id}/cancel")
    CommercialRequest cancel(@RequestHeader(value = AUTHORIZATION, required = false) String auth, @PathVariable long id) {
        return requests.cancel(actor(auth), id);
    }

    @PatchMapping("/requests/{id}/agreement")
    CommercialRequest agreement(@RequestHeader(value = AUTHORIZATION, required = false) String auth, @PathVariable long id,
                                @RequestBody Agreement agreement) {
        return requests.updateAgreement(actor(auth), id, agreement.amount());
    }

    @PostMapping("/products/{id}/stock-movements")
    @ResponseStatus(HttpStatus.CREATED)
    StockMovement moveStock(@RequestHeader(value = AUTHORIZATION, required = false) String auth, @PathVariable long id,
                            @RequestBody StockDraft draft) {
        return requests.manualStock(actor(auth), id, draft);
    }

    @GetMapping("/stock-movements")
    List<StockMovement> movements(@RequestHeader(value = AUTHORIZATION, required = false) String auth) {
        Profile actor = actor(auth);
        actor.requireAdmin();
        return requests.movements(actor);
    }

    @GetMapping("/collections")
    List<Collection> collections(@RequestHeader(value = AUTHORIZATION, required = false) String auth) {
        return catalog.collections(actor(auth));
    }

    @PostMapping("/collections")
    @ResponseStatus(HttpStatus.CREATED)
    Collection createCollection(@RequestHeader(value = AUTHORIZATION, required = false) String auth,
                                @RequestBody Collection input) {
        return catalog.saveCollection(actor(auth), null, input);
    }

    @PatchMapping("/collections/{id}")
    Collection updateCollection(@RequestHeader(value = AUTHORIZATION, required = false) String auth,
                                @PathVariable long id, @RequestBody Collection input) {
        return catalog.saveCollection(actor(auth), id, input);
    }

    @GetMapping("/campaigns")
    List<Campaign> campaigns(@RequestHeader(value = AUTHORIZATION, required = false) String auth) {
        return catalog.campaigns(actor(auth));
    }

    @PostMapping("/campaigns")
    @ResponseStatus(HttpStatus.CREATED)
    Campaign createCampaign(@RequestHeader(value = AUTHORIZATION, required = false) String auth, @RequestBody Campaign input) {
        return catalog.saveCampaign(actor(auth), null, input);
    }

    @PatchMapping("/campaigns/{id}")
    Campaign updateCampaign(@RequestHeader(value = AUTHORIZATION, required = false) String auth,
                            @PathVariable long id, @RequestBody Campaign input) {
        return catalog.saveCampaign(actor(auth), id, input);
    }

    @PutMapping("/favorites/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void favoriteProduct(@RequestHeader(value = AUTHORIZATION, required = false) String auth, @PathVariable long id,
                         @RequestBody Favorite favorite) {
        members.setFavorite(actor(auth), "PRODUCT", id, favorite.selected());
    }

    @PutMapping("/favorites/collections/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void favoriteCollection(@RequestHeader(value = AUTHORIZATION, required = false) String auth, @PathVariable long id,
                            @RequestBody Favorite favorite) {
        members.setFavorite(actor(auth), "COLLECTION", id, favorite.selected());
    }

    @PatchMapping("/notices/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void readNotice(@RequestHeader(value = AUTHORIZATION, required = false) String auth, @PathVariable long id) {
        members.read(actor(auth), id);
    }

    @PatchMapping("/notices/{id}/delivered")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delivered(@RequestHeader(value = AUTHORIZATION, required = false) String auth, @PathVariable long id) {
        members.delivered(actor(auth), id);
    }

    @PostMapping("/notices/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void dismissNotices(@RequestHeader(value = AUTHORIZATION, required = false) String auth, @RequestBody Ids body) {
        members.dismiss(actor(auth), body.ids());
    }

    @PostMapping("/requests/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void dismissRequests(@RequestHeader(value = AUTHORIZATION, required = false) String auth, @RequestBody Ids body) {
        members.hideRequests(actor(auth), body.ids());
    }

    private Profile optionalProfile(String authorization) {
        return authorization == null ? null : actor(authorization);
    }

    private Profile actor(String authorization) {
        return identities.requireProfile(authorization);
    }
}

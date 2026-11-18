package cl.duoc.louaraya.taller;

import cl.duoc.louaraya.common.Profile;

import java.util.List;
import java.util.Set;

final class TallerModels {
    private TallerModels() {}

    enum Category { INTERIOR_DECORATION, WEDDINGS, FESTIVALS, TRAVEL }
    enum Availability { AVAILABLE, MADE_TO_ORDER }
    enum Intention { PURCHASE, RESERVATION, QUOTATION }
    enum Status { EXPLORATION, PENDING, CONFIRMED, DELIVERED, CANCELLED }
    enum StockType { ENTRY, DIRECT_SALE, WITHDRAWAL, REQUEST_SALE }

    record Product(
            long id, String name, double widthCm, double lengthCm, String material, long price,
            Category campaignCategory, String imageReference, String description, Availability availability,
            boolean active, int stock
    ) {}

    record ProductDraft(
            String name, Double widthCm, Double lengthCm, String material, Long price, Category campaignCategory,
            String description, Availability availability, Boolean active
    ) {}

    record CommercialRequest(
            long id, long date, String anonymizedClient, long total, Status status, long productId,
            Intention intention, String comment, Category campaignCategory, long ownerId, String customization,
            Long campaignId, Long sourceCollectionId, String journeyId, Long agreedTotal, Long agreedAt, int quantity
    ) {}

    record QuotationDetails(String event, Double widthCm, Double lengthCm, String material, String colors,
                            String requiredOn) {}
    record RequestDraft(
            Long productId, Intention intention, Long displayedPrice, Integer quantity, String comment,
            QuotationDetails details, String idempotencyKey, Long campaignId, Long sourceCollectionId, String journeyId
    ) {}

    record StatusChange(Status status) {}
    record Agreement(Long amount) {}
    record StockDraft(Integer quantity, StockType type, String note, String idempotencyKey) {}

    record StockMovement(
            long id, long productId, int quantityDelta, int stockAfter, StockType type, long date, long actorId,
            String note, Long requestId
    ) {}

    record Collection(long id, String name, String description, List<Long> productIds) {}
    record Campaign(long id, String name, long collectionId, String startsOn, String endsOn, boolean published,
                    Category targetCategory) {}
    record Notice(long id, long userId, String title, String body, String destination, long date, boolean read,
                  String eventKey, boolean delivered) {}
    record Favorite(boolean selected) {}
    record Ids(List<Long> ids) {}

    record Snapshot(
            Profile profile, List<Product> products, List<CommercialRequest> requests, Set<Long> favoriteIds,
            List<Collection> collections, List<Campaign> campaigns, List<?> events, List<Notice> notices,
            Set<Long> favoriteCollectionIds, List<?> journeys, List<?> explorationEvents,
            List<StockMovement> stockMovements, Set<Long> hiddenRequestIds
    ) {}
}

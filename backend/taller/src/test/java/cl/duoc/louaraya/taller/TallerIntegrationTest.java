package cl.duoc.louaraya.taller;

import cl.duoc.louaraya.common.ApiFailure;
import cl.duoc.louaraya.common.Profile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:taller_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.flyway.default-schema=public",
        "spring.flyway.schemas=public"
})
@AutoConfigureMockMvc
class TallerIntegrationTest {
    private static final String CLIENT = "Bearer client";
    private static final String OTHER = "Bearer other";
    private static final String ADMIN = "Bearer admin";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean IdentityClient identities;
    @MockitoBean PushBroker push;

    @BeforeEach
    void sessions() {
        when(identities.requireProfile(CLIENT)).thenReturn(profile(1, Profile.Role.CLIENT, "WEDDINGS"));
        when(identities.requireProfile(OTHER)).thenReturn(profile(3, Profile.Role.CLIENT, null));
        when(identities.requireProfile(ADMIN)).thenReturn(profile(2, Profile.Role.ADMIN, null));
        when(identities.requireProfile(null)).thenThrow(ApiFailure.unauthorized());
    }

    @Test
    void adminCanCreateReadUpdateAndDeactivateWhileClientCannotWrite() throws Exception {
        mvc.perform(get("/products")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Tapiz Aurora"));
        mvc.perform(get("/snapshot")).andExpect(status().isUnauthorized());
        perform(post("/products"), CLIENT, product("Pieza ajena")).andExpect(status().isForbidden());

        long id = createProduct("Pieza CRUD");
        mvc.perform(get("/products/{id}", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.price").value(15000));
        perform(patch("/products/{id}", id), ADMIN, Map.of("price", 17990))
                .andExpect(status().isOk()).andExpect(jsonPath("$.price").value(17990))
                .andExpect(jsonPath("$.name").value("Pieza CRUD"));
        mvc.perform(delete("/products/{id}", id).header("Authorization", ADMIN)).andExpect(status().isNoContent());
        mvc.perform(get("/products/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(get("/products/{id}", id).header("Authorization", ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void requestAndStockWritesAreIdempotentAndDeliveryRollsBackOnShortage() throws Exception {
        long product = createProduct("Pieza bodega");
        long request = createRequest(product, "request-stock", 2);
        assertThat(createRequest(product, "request-stock", 2)).isEqualTo(request);
        perform(post("/requests"), CLIENT, request(product, "request-stock", 3)).andExpect(status().isConflict());
        perform(post("/requests"), CLIENT, Map.of("productId", product, "intention", "PURCHASE",
                "displayedPrice", 111, "quantity", 1, "idempotencyKey", "outdated-price"))
                .andExpect(status().isConflict());
        mvc.perform(get("/requests/{id}", request).header("Authorization", OTHER)).andExpect(status().isNotFound());
        perform(patch("/requests/{id}/status", request), CLIENT, Map.of("status", "DELIVERED"))
                .andExpect(status().isForbidden());
        perform(patch("/requests/{id}/status", request), ADMIN, Map.of("status", "DELIVERED"))
                .andExpect(status().isConflict());
        mvc.perform(get("/requests/{id}", request).header("Authorization", CLIENT))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));

        Map<String, Object> movement = Map.of("quantity", 4, "type", "ENTRY", "idempotencyKey", "entry-stock");
        long movementId = body(perform(post("/products/{id}/stock-movements", product), ADMIN, movement)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.stockAfter").value(4)).andReturn()).path("id").asLong();
        perform(post("/products/{id}/stock-movements", product), ADMIN, movement)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(movementId));
        perform(patch("/requests/{id}/status", request), ADMIN, Map.of("status", "DELIVERED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED"));
        perform(patch("/requests/{id}/status", request), ADMIN, Map.of("status", "CONFIRMED"))
                .andExpect(status().isOk());
        perform(patch("/requests/{id}/status", request), ADMIN, Map.of("status", "DELIVERED"))
                .andExpect(status().isOk());
        mvc.perform(get("/products/{id}", product)).andExpect(status().isOk()).andExpect(jsonPath("$.stock").value(2));
        mvc.perform(get("/requests/{id}", request).header("Authorization", CLIENT))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.total").value(30000));
        mvc.perform(post("/requests/{id}/cancel", request).header("Authorization", CLIENT))
                .andExpect(status().isConflict());
    }

    @Test
    void snapshotsSeparatePersonalDataAndCampaignsCreateTargetedNotices() throws Exception {
        perform(put("/favorites/products/1"), CLIENT, Map.of("selected", true)).andExpect(status().isNoContent());
        JsonNode collection = body(perform(post("/collections"), ADMIN,
                Map.of("name", "Colección de prueba", "productIds", new long[] {1, 2}))
                .andExpect(status().isCreated()).andReturn());
        long collectionId = collection.path("id").asLong();
        long campaignId = body(perform(post("/campaigns"), ADMIN,
                Map.of("name", "Campaña de prueba", "collectionId", collectionId, "startsOn", "2026-01-01",
                        "endsOn", "2030-12-31", "published", true, "targetCategory", "WEDDINGS"))
                .andExpect(status().isCreated()).andReturn()).path("id").asLong();
        JsonNode client = body(mvc.perform(get("/snapshot").header("Authorization", CLIENT))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profile.id").value(1))
                .andExpect(jsonPath("$.favoriteIds[0]").value(1)).andReturn());
        long noticeId = 0;
        for (JsonNode notice : client.path("notices")) {
            if (notice.path("destination").asText().equals("campaign/" + campaignId)) {
                noticeId = notice.path("id").asLong();
            }
        }
        assertThat(noticeId).isPositive();
        mvc.perform(patch("/notices/{id}/read", noticeId).header("Authorization", OTHER))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/notices/{id}/read", noticeId).header("Authorization", CLIENT))
                .andExpect(status().isNoContent());
        mvc.perform(get("/snapshot").header("Authorization", OTHER)).andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.id").value(3)).andExpect(jsonPath("$.favoriteIds").isEmpty());
    }

    @Test
    void campaignPushSelectsOnlyRegisteredOwnerAndPublisherFailureKeepsNotice() throws Exception {
        when(push.enabled()).thenReturn(true);
        when(push.publish(any(), anyLong())).thenReturn(503);
        String endpoint = "http://10.0.2.2:8090/upTARGET000001?up=1";
        String other = "http://10.0.2.2:8090/upOTHER0000001?up=1";
        Map<String, Object> subscription = Map.of("endpoint", endpoint, "publicKey", "target-key", "authSecret", "target-auth");
        Map<String, Object> otherSubscription = Map.of("endpoint", other, "publicKey", "other-key", "authSecret", "other-auth");

        perform(put("/push/devices/device-1"), ADMIN, subscription).andExpect(status().isForbidden());
        perform(put("/push/devices/device-1"), CLIENT, subscription).andExpect(status().isNoContent());
        mvc.perform(delete("/push/devices/device-1").header("Authorization", OTHER)).andExpect(status().isNoContent());
        perform(put("/push/devices/device-2"), OTHER, otherSubscription).andExpect(status().isNoContent());

        long campaign = body(perform(post("/campaigns"), ADMIN,
                Map.of("name", "Campaña push", "collectionId", 1, "startsOn", "2026-01-01",
                        "endsOn", "2030-12-31", "published", true, "targetCategory", "WEDDINGS"))
                .andExpect(status().isCreated()).andReturn()).path("id").asLong();
        JsonNode snapshot = body(mvc.perform(get("/snapshot").header("Authorization", CLIENT))
                .andExpect(status().isOk()).andReturn());
        long noticeId = 0;
        for (JsonNode notice : snapshot.path("notices")) {
            if (notice.path("destination").asText().equals("campaign/" + campaign)) {
                noticeId = notice.path("id").asLong();
            }
        }
        assertThat(noticeId).isPositive();
        verify(push, timeout(5_000).times(1)).publish(
                new PushBroker.Subscription(endpoint, "target-key", "target-auth"), noticeId);
        verify(push, timeout(5_000).times(1)).publish(any(), org.mockito.ArgumentMatchers.eq(noticeId));
        mvc.perform(delete("/push/devices/device-1").header("Authorization", CLIENT)).andExpect(status().isNoContent());
        mvc.perform(delete("/push/devices/device-2").header("Authorization", OTHER)).andExpect(status().isNoContent());
    }

    private long createProduct(String name) throws Exception {
        return body(perform(post("/products"), ADMIN, product(name)).andExpect(status().isCreated())
                .andReturn()).path("id").asLong();
    }

    private long createRequest(long product, String key, int quantity) throws Exception {
        return body(perform(post("/requests"), CLIENT, request(product, key, quantity))
                .andExpect(status().isCreated()).andReturn()).path("id").asLong();
    }

    private static Map<String, Object> product(String name) {
        return Map.of("name", name, "price", 15000, "widthCm", 40, "lengthCm", 50, "material", "Algodón",
                "campaignCategory", "WEDDINGS");
    }

    private static Map<String, Object> request(long product, String key, int quantity) {
        return Map.of("productId", product, "intention", "PURCHASE", "displayedPrice", 15000,
                "quantity", quantity, "idempotencyKey", key);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String auth, Map<String, Object> input)
            throws Exception {
        return mvc.perform(request.header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input)));
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private static Profile profile(long id, Profile.Role role, String category) {
        return new Profile(id, "Cuenta " + id, "cuenta" + id, role, 0, "", true, category);
    }
}

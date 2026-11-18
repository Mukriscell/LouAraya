package cl.duoc.louaraya.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:identity_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.flyway.default-schema=public",
        "spring.flyway.schemas=public",
        "louaraya.demo-password=LouDemo2026!"
})
@AutoConfigureMockMvc
class IdentityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcClient db;

    @Test
    void registrationCreatesClientAndStoresOnlyHashes() throws Exception {
        String input = """
                {"name":"Cliente de prueba","alias":"Caso.CLIENTE","password":"Prueba2026!","avatar":3,"role":"ADMIN"}
                """;
        String response = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.profile.alias").value("caso.cliente"))
                .andExpect(jsonPath("$.profile.role").value("CLIENT"))
                .andExpect(jsonPath("$.profile.avatar").value(3))
                .andExpect(jsonPath("$.profile.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(response).path("token").asText();
        assertThat(token).hasSize(43);
        assertThat(db.sql("SELECT password_hash FROM profiles WHERE alias = 'caso.cliente'").query(String.class).single())
                .startsWith("$2a$").doesNotContain("Prueba2026!");
        long id = json.readTree(response).path("profile").path("id").asLong();
        assertThat(db.sql("SELECT token_hash FROM sessions WHERE profile_id = :id")
                .param("id", id).query(String.class).single()).hasSize(64).isNotEqualTo(token);
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.alias").exists());
    }

    @Test
    void editingProfileRespectsRoleAndLogoutRevokesOnlyThatSession() throws Exception {
        String token = demoToken("cliente.demo");
        String otherToken = demoToken("cliente.demo");
        mvc.perform(patch("/profiles/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cliente renombrado\",\"role\":\"ADMIN\",\"preferredCategory\":\"WEDDINGS\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Cliente renombrado"))
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andExpect(jsonPath("$.preferredCategory").value("WEDDINGS"));
        mvc.perform(patch("/profiles/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"preferredCategory\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.preferredCategory").doesNotExist());
        mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(get("/profiles/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/profiles/me").header("Authorization", "Bearer " + otherToken)).andExpect(status().isOk());
    }

    @Test
    void authenticationRejectsMissingAndIncorrectCredentials() throws Exception {
        mvc.perform(get("/profiles/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alias\":\"admin.demo\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").isString());
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alias\":\"sin-clave\",\"name\":\"Inválido\",\"password\":\"corta\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    private String demoToken(String alias) throws Exception {
        String response = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new IdentityService.Login(alias, "LouDemo2026!"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("token").asText();
    }
}

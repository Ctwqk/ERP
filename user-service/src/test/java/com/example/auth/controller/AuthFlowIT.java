package com.example.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.auth.ContainersConfig;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.UUID;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** End-to-end auth behaviour on PostgreSQL + Redis: bootstrap admin, login, JWKS, role rules, logout. */
@SpringBootTest(properties = {
        "erp.bootstrap.admin.email=admin@erp.test",
        "erp.bootstrap.admin.password=admin-test-password"
})
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthFlowIT {

    private static final String ADMIN_PASSWORD = "admin-test-password";

    @Autowired MockMvc mvc;

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private String token(String email, String password) throws Exception {
        String body = login(email, password).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private String createUser(String adminToken, String name, String email, String password) throws Exception {
        String body = mvc.perform(post("/api/users/createUser").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(name, email, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    @Order(1)
    void loginIssuesRs256TokenVerifiableWithPublishedJwks() throws Exception {
        String token = token("admin@erp.test", ADMIN_PASSWORD);

        String jwks = mvc.perform(get("/.well-known/jwks.json")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        SignedJWT jwt = SignedJWT.parse(token);
        var key = JWKSet.parse(jwks).getKeyByKeyId(jwt.getHeader().getKeyID());
        assertThat(key).isNotNull();
        assertThat(jwt.verify(new com.nimbusds.jose.crypto.RSASSAVerifier(key.toRSAKey()))).isTrue();
        assertThat(jwt.getJWTClaimsSet().getStringListClaim("roles")).contains("ROLE_ADMIN");
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo("admin@erp.test");
    }

    @Test
    @Order(2)
    void badCredentialsAndMissingTokenAreRejectedUniformly() throws Exception {
        login("admin@erp.test", "wrong").andExpect(status().isUnauthorized());
        login("nobody@erp.test", "whatever").andExpect(status().isUnauthorized());
        login("", "").andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer not.a.jwt")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @Order(3)
    void adminManagesUsersAndRolesWhileOrdinaryUsersCannot() throws Exception {
        String admin = token("admin@erp.test", ADMIN_PASSWORD);
        String carolId = createUser(admin, "Carol", "carol@erp.test", "carol-password");
        String carol = token("carol@erp.test", "carol-password");

        // duplicate e-mail
        mvc.perform(post("/api/users/createUser").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Carol2\",\"email\":\"carol@erp.test\",\"password\":\"carol-password\"}"))
                .andExpect(status().isConflict());
        // weak password
        mvc.perform(post("/api/users/createUser").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"D\",\"email\":\"d@erp.test\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/users/me").header("Authorization", bearer(carol)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("carol@erp.test"));
        mvc.perform(get("/api/users/{id}", carolId).header("Authorization", bearer(carol))).andExpect(status().isOk());
        mvc.perform(get("/api/users").header("Authorization", bearer(carol))).andExpect(status().isForbidden());
        mvc.perform(post("/api/users/createUser").header("Authorization", bearer(carol))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"E\",\"email\":\"e@erp.test\",\"password\":\"e-password-1\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/roles/{id}", UUID.randomUUID()).header("Authorization", bearer(carol)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/roles").header("Authorization", bearer(carol)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8));

        mvc.perform(get("/api/users").param("keyword", "carol").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/users").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));

        // a user may edit themselves (but cannot flip their own active flag); not other people
        mvc.perform(put("/api/users").header("Authorization", bearer(carol)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"%s\",\"name\":\"Carol C.\",\"active\":false}".formatted(carolId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Carol C."))
                .andExpect(jsonPath("$.active").value(true));
        String adminId = JsonPath.read(mvc.perform(get("/api/users/me").header("Authorization", bearer(admin)))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(put("/api/users").header("Authorization", bearer(carol)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"%s\",\"name\":\"hacked\"}".formatted(adminId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(4)
    void hrCanManageAccountsButNotAdministrators() throws Exception {
        String admin = token("admin@erp.test", ADMIN_PASSWORD);
        String hrId = createUser(admin, "Hannah", "hr@erp.test", "hannah-password");
        String bobId = createUser(admin, "Bob", "bob@erp.test", "bob-password-1");
        mvc.perform(post("/api/users/assign-role").param("userId", hrId).param("roleName", "hr")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        // roles are read from the token at login, so log in again after the grant
        String hr = token("hr@erp.test", "hannah-password");
        String adminId = JsonPath.read(mvc.perform(get("/api/users/me").header("Authorization", bearer(admin)))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/users").header("Authorization", bearer(hr))).andExpect(status().isOk());
        mvc.perform(post("/api/users/assign-role").param("userId", bobId).param("roleName", "SALE")
                        .header("Authorization", bearer(hr)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users/{id}", bobId).header("Authorization", bearer(hr)))
                .andExpect(jsonPath("$.roles.length()").value(2));

        mvc.perform(post("/api/users/assign-role").param("userId", bobId).param("roleName", "ADMIN")
                        .header("Authorization", bearer(hr)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/users/disableUser").param("userId", adminId).header("Authorization", bearer(hr)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/users/{id}", bobId).header("Authorization", bearer(hr)))
                .andExpect(status().isForbidden());

        // disabling a user stops them logging in
        mvc.perform(post("/api/users/disableUser").param("userId", bobId).header("Authorization", bearer(hr)))
                .andExpect(status().isOk());
        login("bob@erp.test", "bob-password-1").andExpect(status().isForbidden());
        mvc.perform(post("/api/users/enableUser").param("userId", bobId).header("Authorization", bearer(hr)))
                .andExpect(status().isOk());
        login("bob@erp.test", "bob-password-1").andExpect(status().isOk());
    }

    @Test
    @Order(5)
    void passwordChangeRequiresOldPasswordAndTakesEffect() throws Exception {
        String admin = token("admin@erp.test", ADMIN_PASSWORD);
        createUser(admin, "Dave", "dave@erp.test", "dave-password-1");
        String dave = token("dave@erp.test", "dave-password-1");

        mvc.perform(post("/api/users/change-password").header("Authorization", bearer(dave))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"nope\",\"newPassword\":\"dave-password-2\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/users/change-password").header("Authorization", bearer(dave))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"dave-password-1\",\"newPassword\":\"dave-password-2\"}"))
                .andExpect(status().isNoContent());

        login("dave@erp.test", "dave-password-1").andExpect(status().isUnauthorized());
        login("dave@erp.test", "dave-password-2").andExpect(status().isOk());
    }

    @Test
    @Order(6)
    void logoutRevokesTheToken() throws Exception {
        String admin = token("admin@erp.test", ADMIN_PASSWORD);
        createUser(admin, "Erin", "erin@erp.test", "erin-password-1");
        String erin = token("erin@erp.test", "erin-password-1");

        mvc.perform(get("/api/users/me").header("Authorization", bearer(erin))).andExpect(status().isOk());
        mvc.perform(post("/auth/logout").header("Authorization", bearer(erin))).andExpect(status().isNoContent());

        mvc.perform(get("/api/users/me").header("Authorization", bearer(erin))).andExpect(status().isUnauthorized());
        // other sessions of the same user are unaffected
        String again = token("erin@erp.test", "erin-password-1");
        mvc.perform(get("/api/users/me").header("Authorization", bearer(again))).andExpect(status().isOk());
        mvc.perform(post("/auth/logout")).andExpect(status().isUnauthorized());
    }
}

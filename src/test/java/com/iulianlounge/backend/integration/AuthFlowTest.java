package com.iulianlounge.backend.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

@SpringBootTest(properties = {
        "jwt.secret=dGVzdC1zZWNyZXQtcXVlLXRpZW5lLW1hcy1kZS0zMi1ieXRlcyEh",
        "auth.rate-limit.max-per-minute=1000"
})
@AutoConfigureMockMvc
@Transactional
class AuthFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aMemberLogsInRefreshesWithTheCookieAndLogsOut() throws Exception {
        String username = "sesion_" + UUID.randomUUID().toString().substring(0, 8);
        register(username, username + "@lounge.com").andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"12345678"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().secure("refresh_token", true))
                .andExpect(cookie().sameSite("refresh_token", "Strict"))
                .andExpect(cookie().path("refresh_token", "/api/v1/auth"))
                .andReturn();
        String accessToken = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
        Cookie refreshCookie = login.getResponse().getCookie("refresh_token");

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.rank").value("NADIE"));

        MvcResult refresh = mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("refresh_token"))
                .andReturn();
        String renewedToken = JsonPath.read(refresh.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + renewedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));

        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.invalid_token"));

        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh_token", 0))
                .andExpect(cookie().path("refresh_token", "/api/v1/auth"));
    }

    @Test
    void theDatabaseKeepsUsernamesAndEmailsUnique() throws Exception {
        String username = "unico_" + UUID.randomUUID().toString().substring(0, 8);
        register(username, username + "@lounge.com").andExpect(status().isCreated());

        register(username, "otro_" + username + "@lounge.com")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("user.username_taken"));
        register("otro_" + username, username.toUpperCase() + "@LOUNGE.COM")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("user.email_taken"));
    }

    @Test
    void aWrongPasswordIsRejectedWithoutSayingWhichFieldFailed() throws Exception {
        String username = "clave_" + UUID.randomUUID().toString().substring(0, 8);
        register(username, username + "@lounge.com").andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"otra-clave"}
                                """.formatted(username)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.invalid_credentials"))
                .andExpect(cookie().doesNotExist("refresh_token"));
    }

    private ResultActions register(String username, String email)
            throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","email":"%s","password":"12345678","locale":"es"}
                        """.formatted(username, email)));
    }
}

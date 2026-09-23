package com.iulianlounge.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.iulianlounge.backend.config.SecurityConfig;
import com.iulianlounge.backend.service.RegisterService;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterService registerService;

    @Test
    void registerReturns201WithUserId() throws Exception {
        // Arrange: el mock devolverá este UUID cuando le llamen
        UUID userId = UUID.randomUUID();
        when(registerService.register(any())).thenReturn(userId);

        String body = """
                {"username":"cursaito","email":"cursaito@lounge.com","password":"12345678","locale":"es"}
                """;

        // Act + Assert: lanzar el POST y comprobar la respuesta
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }
}

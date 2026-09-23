package com.iulianlounge.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.iulianlounge.backend.config.SecurityConfig;
import com.iulianlounge.backend.exception.DuplicateUserException;
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

    @Test
    void registerReturns409WhenUserIsDuplicated() throws Exception {
        // Arrange: el mock lanza la excepción como si el email ya existiera
        when(registerService.register(any()))
                .thenThrow(new DuplicateUserException("El email ya está en uso"));

        String body = """
                {"username":"cursaito","email":"cursaito@lounge.com","password":"12345678","locale":"es"}
                """;

        // Act + Assert: el handler convierte la excepción en 409 ProblemDetail
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El email ya está en uso"));
    }

    @Test
    void registerReturns409WhenDatabaseRejectsDuplicateInARace() throws Exception {
        // Arrange: dos registros a la vez; el UNIQUE de la BD frena al segundo
        when(registerService.register(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        String body = """
                {"username":"cursaito","email":"cursaito@lounge.com","password":"12345678","locale":"es"}
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El username o el email ya están en uso"));
    }

    @Test
    void registerReturns400WhenBodyIsInvalid() throws Exception {
        // Arrange: email sin @ y contraseña de menos de 8 caracteres
        String body = """
                {"username":"cursaito","email":"no-es-un-email","password":"123","locale":"es"}
                """;

        // Act + Assert: @Valid lo corta antes de llegar al service
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(registerService, never()).register(any());
    }

    @Test
    void registerReturns400WhenUsernameIsTooLong() throws Exception {
        String longUsername = "a".repeat(51);   // la columna es VARCHAR(50)

        expectBadRequest(bodyWith(longUsername, "cursaito@lounge.com", "12345678", "es"));
    }

    @Test
    void registerReturns400WhenUsernameHasForbiddenCharacters() throws Exception {
        expectBadRequest(bodyWith("<script>", "cursaito@lounge.com", "12345678", "es"));
    }

    private String bodyWith(String username, String email, String password, String locale) {
        return """
                {"username":"%s","email":"%s","password":"%s","locale":"%s"}
                """.formatted(username, email, password, locale);
    }

    private void expectBadRequest(String body) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(registerService, never()).register(any());
    }
}

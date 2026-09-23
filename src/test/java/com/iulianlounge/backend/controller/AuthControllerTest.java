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
import com.iulianlounge.backend.dto.LoginResponse;
import com.iulianlounge.backend.dto.RefreshResponse;
import com.iulianlounge.backend.exception.DuplicateUserException;
import com.iulianlounge.backend.exception.InvalidCredentialsException;
import com.iulianlounge.backend.exception.InvalidTokenException;
import com.iulianlounge.backend.security.JwtService;
import com.iulianlounge.backend.service.AuthService;
import com.iulianlounge.backend.service.RegisterService;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterService registerService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;   // SecurityConfig lo necesita para montar el filtro

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

    @Test
    void registerReturns400WhenPasswordIsTooLong() throws Exception {
        String longPassword = "p".repeat(65);   // BCrypt no admite más de 72 bytes

        expectBadRequest(bodyWith("cursaito", "cursaito@lounge.com", longPassword, "es"));
    }

    @Test
    void registerReturns400WhenPasswordExceedsBcrypt72Bytes() throws Exception {
        String multibyte = "ñ".repeat(40);   // 40 caracteres pasan @Size, pero son 80 bytes

        expectBadRequest(bodyWith("cursaito", "cursaito@lounge.com", multibyte, "es"));
    }

    @Test
    void registerReturns400WhenLocaleIsNotSupported() throws Exception {
        expectBadRequest(bodyWith("cursaito", "cursaito@lounge.com", "12345678", "fr"));
    }

    @Test
    void registerReturns400WhenEmailIsTooLong() throws Exception {
        String longEmail = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(63) + ".com";

        expectBadRequest(bodyWith("cursaito", longEmail, "12345678", "es"));
    }

    @Test
    void loginReturns200WithTokens() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponse("access-token", "refresh-token", 900));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"cursaito","password":"12345678"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void loginReturns401WhenCredentialsAreWrong() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"cursaito","password":"mala-clave"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Usuario o contraseña incorrectos"));
    }

    @Test
    void loginReturns400WhenPasswordIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"cursaito"}
                                """))
                .andExpect(status().isBadRequest());

        verify(authService, never()).login(any());
    }

    @Test
    void refreshReturns200WithNewTokens() throws Exception {
        when(authService.refresh(any())).thenReturn(new RefreshResponse("access-nuevo", "refresh-nuevo"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"refresh-valido"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-nuevo"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-nuevo"));
    }

    @Test
    void refreshReturns401WhenTokenIsInvalid() throws Exception {
        when(authService.refresh(any())).thenThrow(new InvalidTokenException("Token inválido o caducado"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"caducado"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Token inválido o caducado"));
    }

    @Test
    void refreshReturns400WhenTokenIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(authService, never()).refresh(any());
    }

    @Test
    void refreshReturns400WhenTokenIsHuge() throws Exception {
        String huge = "a".repeat(1025);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + huge + "\"}"))
                .andExpect(status().isBadRequest());

        verify(authService, never()).refresh(any());
    }

    @Test
    void unknownAuthRouteIsPrivateNotPublic() throws Exception {
        // Antes /api/v1/auth/** era todo público; ahora solo register, login y refresh
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Autenticación requerida"));
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

package com.iulianlounge.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
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
import com.iulianlounge.backend.exception.ErrorCode;
import com.iulianlounge.backend.exception.InvalidCredentialsException;
import com.iulianlounge.backend.exception.InvalidTokenException;
import com.iulianlounge.backend.security.JwtService;
import com.iulianlounge.backend.service.AuthService;
import com.iulianlounge.backend.service.IssuedTokens;
import com.iulianlounge.backend.service.RegisterService;

import jakarta.servlet.http.Cookie;

@WebMvcTest(controllers = AuthController.class, properties = "auth.rate-limit.max-per-minute=1000")
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterService registerService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void registerReturns201WithUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        when(registerService.register(any())).thenReturn(userId);

        String body = """
                {"username":"cursaito","email":"cursaito@lounge.com","password":"12345678","locale":"es"}
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    void registerReturns409WhenUserIsDuplicated() throws Exception {
        when(registerService.register(any()))
                .thenThrow(new DuplicateUserException(ErrorCode.USER_EMAIL_TAKEN));

        String body = """
                {"username":"cursaito","email":"cursaito@lounge.com","password":"12345678","locale":"es"}
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("user.email_taken"))
                .andExpect(jsonPath("$.detail").value("Email already in use"));
    }

    @Test
    void anyOtherDatabaseConflictIsAGenericConflictNotUserAlreadyExists() throws Exception {
        when(registerService.register(any()))
                .thenThrow(new DataIntegrityViolationException("violates foreign key constraint"));

        String body = """
                {"username":"cursaito","email":"cursaito@lounge.com","password":"12345678","locale":"es"}
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("data.conflict"));
    }

    @Test
    void unexpectedExceptionIsA500WithCodeAndNoInternalMessage() throws Exception {
        when(authService.login(any())).thenThrow(new IllegalStateException("pool de conexiones agotado"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"cursaito","password":"12345678"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal.error"))
                .andExpect(jsonPath("$.detail").value("Internal error"));
    }

    @Test
    void emptyUsernameAlwaysReportsNotBlankFirst() throws Exception {
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodyWith("", "cursaito@lounge.com", "", "es")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.username").value("validation.not_blank"))
                    .andExpect(jsonPath("$.errors.password").value("validation.not_blank"));
        }
    }

    @Test
    void unsupportedMediaTypeKeepsItsStatusAndCarriesACode() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("cursaito"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("request.rejected"));
    }

    @Test
    void registerReturns400WhenBodyIsInvalid() throws Exception {
        String body = """
                {"username":"cursaito","email":"no-es-un-email","password":"123","locale":"es"}
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(registerService, never()).register(any());
    }

    @Test
    void registerReturns400WhenUsernameIsTooLong() throws Exception {
        String longUsername = "a".repeat(51);

        expectBadRequest(bodyWith(longUsername, "cursaito@lounge.com", "12345678", "es"));
    }

    @Test
    void registerReturns400WhenUsernameHasForbiddenCharacters() throws Exception {
        expectBadRequest(bodyWith("<script>", "cursaito@lounge.com", "12345678", "es"));
    }

    @Test
    void registerReturns400WhenPasswordIsTooLong() throws Exception {
        String longPassword = "p".repeat(65);

        expectBadRequest(bodyWith("cursaito", "cursaito@lounge.com", longPassword, "es"));
    }

    @Test
    void registerReturns400WhenPasswordExceedsBcrypt72Bytes() throws Exception {
        String multibyte = "ñ".repeat(40);

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
    void loginReturnsAccessInBodyAndRefreshInCookie() throws Exception {
        when(authService.login(any()))
                .thenReturn(new IssuedTokens("access-token", Duration.ofMinutes(15), "refresh-token", Duration.ofDays(7)));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"cursaito","password":"12345678"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().value("refresh_token", "refresh-token"))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().secure("refresh_token", true))
                .andExpect(cookie().sameSite("refresh_token", "Strict"))
                .andExpect(cookie().path("refresh_token", "/api/v1/auth"))
                .andExpect(cookie().maxAge("refresh_token", (int) Duration.ofDays(7).toSeconds()));
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
                .andExpect(jsonPath("$.code").value("auth.invalid_credentials"));
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
    void refreshReadsTheCookieAndRotatesIt() throws Exception {
        when(authService.refresh("refresh-valido"))
                .thenReturn(new IssuedTokens("access-nuevo", Duration.ofMinutes(15), "refresh-nuevo", Duration.ofDays(5)));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", "refresh-valido")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-nuevo"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().value("refresh_token", "refresh-nuevo"))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().maxAge("refresh_token", (int) Duration.ofDays(5).toSeconds()));
    }

    @Test
    void refreshReturns401WhenTokenIsInvalid() throws Exception {
        when(authService.refresh(any())).thenThrow(new InvalidTokenException());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", "caducado")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.invalid_token"))
                .andExpect(cookie().value("refresh_token", ""))
                .andExpect(cookie().maxAge("refresh_token", 0));
    }

    @Test
    void refreshWithoutCookieReachesTheServiceAsNull() throws Exception {
        when(authService.refresh(null)).thenThrow(new InvalidTokenException());

        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutReturns204AndExpiresTheCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().value("refresh_token", ""))
                .andExpect(cookie().path("refresh_token", "/api/v1/auth"))
                .andExpect(cookie().maxAge("refresh_token", 0))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().secure("refresh_token", true))
                .andExpect(cookie().sameSite("refresh_token", "Strict"));
    }

    @Test
    void logoutWorksWithAnExpiredAccessToken() throws Exception {
        when(jwtService.validateAccessToken("caducado")).thenThrow(new InvalidTokenException());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer caducado"))
                .andExpect(status().isNoContent());
    }

    @Test
    void registerBadRequestListsEachInvalidField() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith("cursaito", "no-es-un-email", "123", "es")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.errors.email").value("validation.email"))
                .andExpect(jsonPath("$.errors.password").value("validation.size"))
                .andExpect(jsonPath("$.errors.username").doesNotExist());
    }

    @Test
    void registerReportsTheBcryptByteLimitUnderThePasswordField() throws Exception {
        String multibyte = "ñ".repeat(40);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith("cursaito", "cursaito@lounge.com", multibyte, "es")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("validation.max_utf8_bytes"));
    }

    @Test
    void malformedJsonStillCarriesACode() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.rejected"));
    }

    @Test
    void unknownAuthRouteIsPrivateNotPublic() throws Exception {
        mockMvc.perform(post("/api/v1/auth/no-existe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.required"));
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

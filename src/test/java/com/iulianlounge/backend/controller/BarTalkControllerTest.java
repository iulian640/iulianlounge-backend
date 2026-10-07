package com.iulianlounge.backend.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.iulianlounge.backend.config.ClockConfig;
import com.iulianlounge.backend.config.SecurityConfig;
import com.iulianlounge.backend.domain.Language;
import com.iulianlounge.backend.domain.Role;
import com.iulianlounge.backend.domain.User;
import com.iulianlounge.backend.dto.TalkResponse;
import com.iulianlounge.backend.exception.InvalidTokenException;
import com.iulianlounge.backend.exception.WalletNotFoundException;
import com.iulianlounge.backend.security.JwtService;
import com.iulianlounge.backend.service.TalkService;

@WebMvcTest(BarTalkController.class)
@Import({SecurityConfig.class, JwtService.class, ClockConfig.class})
@TestPropertySource(properties = "jwt.secret=" + MeControllerTest.SECRET)
class BarTalkControllerTest {

    private static final String REMOTE = "127.0.0.1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private TalkService talkService;

    private User user;
    private String bearer;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(Role.USER);
        bearer = "Bearer " + jwtService.generateAccessToken(user);
    }

    @Test
    void theModelsAnswerIsA200WithTheTextAndNoLine() throws Exception {
        when(talkService.talk(user.getId(), "¿Qué me recomiendas?", null, REMOTE))
                .thenReturn(TalkResponse.llm("Un Gin Rickey, fresco y ligero."));

        String body = mockMvc.perform(talk("{\"text\":\"¿Qué me recomiendas?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("LLM"))
                .andExpect(jsonPath("$.text").value("Un Gin Rickey, fresco y ligero."))
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("\"line\":null"), body);
    }

    @Test
    void theFallbackIsA200WithTheBusyLineAndNoText() throws Exception {
        when(talkService.talk(user.getId(), "hola", null, REMOTE)).thenReturn(TalkResponse.fallback("barman.busy"));

        String body = mockMvc.perform(talk("{\"text\":\"hola\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("FALLBACK"))
                .andExpect(jsonPath("$.line").value("barman.busy"))
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("\"text\":null"), body);
    }

    @Test
    void theMemberComesFromTheTokenNeverFromTheBody() throws Exception {
        when(talkService.talk(any(), anyString(), any(), any())).thenReturn(TalkResponse.fallback("barman.busy"));

        mockMvc.perform(talk("{\"text\":\"hola\",\"userId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());

        verify(talkService).talk(user.getId(), "hola", null, REMOTE);
    }

    @Test
    void theAddressOfTheConnectionIsPassedOnForThePerIpCap() throws Exception {
        when(talkService.talk(any(), anyString(), any(), anyString()))
                .thenReturn(TalkResponse.fallback("barman.busy"));

        mockMvc.perform(talk("{\"text\":\"hola\"}").with(request -> {
            request.setRemoteAddr("203.0.113.9");
            return request;
        })).andExpect(status().isOk());

        verify(talkService).talk(user.getId(), "hola", null, "203.0.113.9");
    }

    @Test
    void anAddressInTheBodyOrInAHeaderIsNeverUsed() throws Exception {
        when(talkService.talk(any(), anyString(), any(), anyString()))
                .thenReturn(TalkResponse.fallback("barman.busy"));

        mockMvc.perform(talk("{\"text\":\"hola\",\"ip\":\"198.51.100.1\"}")
                        .header("X-Forwarded-For", "198.51.100.2"))
                .andExpect(status().isOk());

        verify(talkService).talk(user.getId(), "hola", null, REMOTE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"es", "en"})
    void theLocaleOfTheRequestIsPassedOn(String locale) throws Exception {
        when(talkService.talk(any(), anyString(), any(), any())).thenReturn(TalkResponse.fallback("barman.busy"));

        mockMvc.perform(talk("{\"text\":\"hola\",\"locale\":\"" + locale + "\"}")).andExpect(status().isOk());

        verify(talkService).talk(user.getId(), "hola", Language.fromCode(locale), REMOTE);
    }

    @Test
    void aTextOfExactlyTheLimitIsAccepted() throws Exception {
        String text = "a".repeat(280);
        when(talkService.talk(user.getId(), text, null, REMOTE)).thenReturn(TalkResponse.fallback("barman.busy"));

        mockMvc.perform(talk("{\"text\":\"" + text + "\"}")).andExpect(status().isOk());
    }

    @Test
    void withoutTokenItIs401() throws Exception {
        mockMvc.perform(post("/api/v1/bar/talk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hola\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.required"));
        verifyNoInteractions(talkService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\\n\\t"})
    void aBlankTextFailsValidation(String text) throws Exception {
        mockMvc.perform(talk("{\"text\":\"" + text + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.errors.text").value("validation.not_blank"));
        verifyNoInteractions(talkService);
    }

    @Test
    void aMissingTextFailsValidation() throws Exception {
        mockMvc.perform(talk("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.errors.text").value("validation.not_blank"));
        verifyNoInteractions(talkService);
    }

    @Test
    void aTextOf281CharactersFailsValidation() throws Exception {
        mockMvc.perform(talk("{\"text\":\"" + "a".repeat(281) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.errors.text").value("validation.size"));
        verifyNoInteractions(talkService);
    }

    @Test
    void aTextOf561BytesFailsValidationEvenIfItHasFewCharacters() throws Exception {
        String text = "€".repeat(187);

        mockMvc.perform(talk("{\"text\":\"" + text + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.errors.text").value("validation.max_utf8_bytes"));
        verifyNoInteractions(talkService);
    }

    @Test
    void aTextOf560BytesIsAccepted() throws Exception {
        String text = "€".repeat(186) + "ab";
        when(talkService.talk(user.getId(), text, null, REMOTE)).thenReturn(TalkResponse.fallback("barman.busy"));

        mockMvc.perform(talk("{\"text\":\"" + text + "\"}")).andExpect(status().isOk());
    }

    @Test
    void aLocaleOutsideEsAndEnFailsValidation() throws Exception {
        mockMvc.perform(talk("{\"text\":\"hola\",\"locale\":\"fr\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.errors.locale").value("validation.pattern"));
        verifyNoInteractions(talkService);
    }

    @Test
    void brokenJsonIsRejected() throws Exception {
        mockMvc.perform(talk("{\"text\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.rejected"));
        verifyNoInteractions(talkService);
    }

    @Test
    void aMissingBodyIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/bar/talk")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.rejected"));
        verifyNoInteractions(talkService);
    }

    @Test
    void aContentTypeThatIsNotJsonIs415() throws Exception {
        mockMvc.perform(post("/api/v1/bar/talk")
                        .header("Authorization", bearer)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("request.rejected"));
        verifyNoInteractions(talkService);
    }

    @Test
    void aTokenOfADeletedAccountIs401() throws Exception {
        when(talkService.talk(user.getId(), "hola", null, REMOTE)).thenThrow(new InvalidTokenException());

        mockMvc.perform(talk("{\"text\":\"hola\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.invalid_token"));
    }

    @Test
    void aMemberWithoutAWalletIs404() throws Exception {
        when(talkService.talk(user.getId(), "hola", null, REMOTE)).thenThrow(new WalletNotFoundException());

        mockMvc.perform(talk("{\"text\":\"hola\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("wallet.not_found"));
    }

    @Test
    void anUnexpectedFailureOfOursIsA500WithoutDetails() throws Exception {
        when(talkService.talk(user.getId(), "hola", null, REMOTE)).thenThrow(new IllegalStateException("SECRETO"));

        String body = mockMvc.perform(talk("{\"text\":\"hola\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal.error"))
                .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("SECRETO"), body);
    }

    private MockHttpServletRequestBuilder talk(String body) {
        return post("/api/v1/bar/talk")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}

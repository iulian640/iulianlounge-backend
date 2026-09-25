package com.iulianlounge.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.iulianlounge.backend.config.ClockConfig;
import com.iulianlounge.backend.config.SecurityConfig;
import com.iulianlounge.backend.domain.Role;
import com.iulianlounge.backend.domain.TokenTransaction;
import com.iulianlounge.backend.domain.TransactionType;
import com.iulianlounge.backend.domain.User;
import com.iulianlounge.backend.exception.WalletNotFoundException;
import com.iulianlounge.backend.security.JwtService;
import com.iulianlounge.backend.service.WalletService;

// Seguridad REAL, como MeControllerTest: la cartera solo se ve con un access token válido
@WebMvcTest(WalletController.class)
@Import({SecurityConfig.class, JwtService.class, ClockConfig.class})
@TestPropertySource(properties = "jwt.secret=" + MeControllerTest.SECRET)
class WalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private WalletService walletService;

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
    void walletReturnsTheBalanceOfTheTokenOwner() throws Exception {
        when(walletService.getBalance(user.getId())).thenReturn(100L);

        mockMvc.perform(get("/api/v1/wallet").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100));
    }

    @Test
    void walletWithoutTokenIs401() throws Exception {
        mockMvc.perform(get("/api/v1/wallet"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.required"));
    }

    @Test
    void userWithoutWalletIs404WithCode() throws Exception {
        when(walletService.getBalance(user.getId())).thenThrow(new WalletNotFoundException());

        mockMvc.perform(get("/api/v1/wallet").header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("wallet.not_found"));
    }

    @Test
    void transactionsComeAsAPageOfMovements() throws Exception {
        TokenTransaction bonus = new TokenTransaction(UUID.randomUUID(), 100, TransactionType.WELCOME_BONUS, 100, null,
                Instant.parse("2026-09-25T12:00:00Z"));
        when(walletService.getTransactions(eq(user.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(bonus), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/wallet/transactions").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].amount").value(100))
                .andExpect(jsonPath("$.content[0].type").value("WELCOME_BONUS"))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(100))
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-09-25T12:00:00Z"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void pageSizeIsCappedAndNegativePagesStartAtZero() throws Exception {
        // ?size=5000 no puede convertirse en una consulta de 5000 filas
        when(walletService.getTransactions(eq(user.getId()), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/wallet/transactions?page=-3&size=5000").header("Authorization", bearer))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(walletService).getTransactions(eq(user.getId()), pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(WalletController.MAX_PAGE_SIZE, pageable.getValue().getPageSize());
    }
}

package com.iulianlounge.backend.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.iulianlounge.backend.dto.PageResponse;
import com.iulianlounge.backend.dto.TransactionResponse;
import com.iulianlounge.backend.dto.WalletResponse;
import com.iulianlounge.backend.security.AccessTokenClaims;
import com.iulianlounge.backend.service.WalletService;

// Solo lectura: las fichas se mueven desde otros servicios (registro, y más adelante el barman), nunca por HTTP directo
@RestController
@RequestMapping("/api/v1/wallet")
public class WalletController {

    // Tope de filas por página: ?size=5000 no se convierte en una consulta de 5000 filas
    static final int MAX_PAGE_SIZE = 50;

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping
    public WalletResponse wallet(@AuthenticationPrincipal(errorOnInvalidType = true) AccessTokenClaims claims) {
        return new WalletResponse(walletService.getBalance(claims.userId()));
    }

    // Del movimiento más nuevo al más viejo
    @GetMapping("/transactions")
    public PageResponse<TransactionResponse> transactions(
            @AuthenticationPrincipal(errorOnInvalidType = true) AccessTokenClaims claims,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return PageResponse.from(walletService.getTransactions(claims.userId(), pageRequest), TransactionResponse::from);
    }
}

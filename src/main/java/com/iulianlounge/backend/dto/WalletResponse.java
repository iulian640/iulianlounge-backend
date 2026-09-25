package com.iulianlounge.backend.dto;

// ADR-09: fichas como long. El préstamo activo del diseño original queda fuera del PMV
public record WalletResponse(long balance) {
}

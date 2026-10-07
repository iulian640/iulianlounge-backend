package com.iulianlounge.backend.dto;

import jakarta.validation.constraints.NotNull;

import com.iulianlounge.backend.domain.Drink;

public record OrderRequest(@NotNull Drink drink) {
}

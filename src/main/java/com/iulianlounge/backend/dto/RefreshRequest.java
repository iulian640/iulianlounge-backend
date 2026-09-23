package com.iulianlounge.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Un token real ronda 300 caracteres: el tope evita que nos manden megas a decodificar
public record RefreshRequest(@NotBlank @Size(max = 1024) String refreshToken) {
}

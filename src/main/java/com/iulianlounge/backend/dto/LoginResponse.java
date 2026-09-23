package com.iulianlounge.backend.dto;

// expiresIn en segundos: lo que dura el accessToken
public record LoginResponse(String accessToken, String refreshToken, long expiresIn) {
}

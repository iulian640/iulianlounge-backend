package com.iulianlounge.backend.dto;

// Respuesta de login y refresh. expiresIn en segundos: lo que dura el accessToken.
// El refresh no va aquí: viaja en la cookie HttpOnly (ADR-08)
public record AccessTokenResponse(String accessToken, long expiresIn) {

    // Que un log del DTO no deje el token a la vista
    @Override
    public String toString() {
        return "AccessTokenResponse[accessToken=***, expiresIn=" + expiresIn + "]";
    }
}

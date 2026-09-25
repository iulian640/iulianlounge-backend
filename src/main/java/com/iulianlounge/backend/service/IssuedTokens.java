package com.iulianlounge.backend.service;

import java.time.Duration;

// Lo que sale de login y refresh. No es un DTO: el controller reparte el access al cuerpo y el refresh a la cookie.
// Los TTL son lo que le queda de vida a cada token, para que expiresIn y la cookie caduquen a la vez que ellos
public record IssuedTokens(String accessToken, Duration accessTtl, String refreshToken, Duration refreshTtl) {

    // Un log.debug("{}", tokens) no debe dejar una sesión de 7 días escrita en los logs (RGPD)
    @Override
    public String toString() {
        return "IssuedTokens[accessToken=***, accessTtl=" + accessTtl + ", refreshToken=***, refreshTtl=" + refreshTtl + "]";
    }
}

package com.iulianlounge.backend.service;

import java.time.Duration;

public record IssuedTokens(String accessToken, Duration accessTtl, String refreshToken, Duration refreshTtl) {

    @Override
    public String toString() {
        return "IssuedTokens[accessToken=***, accessTtl=" + accessTtl + ", refreshToken=***, refreshTtl=" + refreshTtl + "]";
    }
}

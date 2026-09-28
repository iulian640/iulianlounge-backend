package com.iulianlounge.backend.dto;

public record AccessTokenResponse(String accessToken, long expiresIn) {

    @Override
    public String toString() {
        return "AccessTokenResponse[accessToken=***, expiresIn=" + expiresIn + "]";
    }
}

package com.iulianlounge.backend.dto;

import com.iulianlounge.backend.validation.MaxUtf8Bytes;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "^[A-Za-z0-9_.-]+$") String username,
        @NotBlank @Email @Size(max = 254) String email,
        // 72 = límite de BCrypt en bytes
        @NotBlank @Size(min = 8, max = 64) @MaxUtf8Bytes(72) String password,
        @NotBlank @Pattern(regexp = "es|en") String locale) {
}

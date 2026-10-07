package com.iulianlounge.backend.dto;

import com.iulianlounge.backend.validation.MaxUtf8Bytes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TalkRequest(
        @NotBlank @Size(max = 280) @MaxUtf8Bytes(560) String text,
        @Pattern(regexp = "es|en") String locale) {
}

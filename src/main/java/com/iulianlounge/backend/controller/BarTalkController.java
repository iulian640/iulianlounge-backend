package com.iulianlounge.backend.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.iulianlounge.backend.domain.Language;
import com.iulianlounge.backend.dto.TalkRequest;
import com.iulianlounge.backend.dto.TalkResponse;
import com.iulianlounge.backend.security.AccessTokenClaims;
import com.iulianlounge.backend.service.TalkService;

@RestController
@RequestMapping("/api/v1/bar")
public class BarTalkController {

    private final TalkService talkService;

    public BarTalkController(TalkService talkService) {
        this.talkService = talkService;
    }

    @PostMapping("/talk")
    public TalkResponse talk(@AuthenticationPrincipal(errorOnInvalidType = true) AccessTokenClaims claims,
            @Valid @RequestBody TalkRequest request) {
        Language requested = request.locale() == null ? null : Language.fromCode(request.locale());
        return talkService.talk(claims.userId(), request.text(), requested);
    }
}

package com.iulianlounge.backend.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.iulianlounge.backend.domain.Rank;
import com.iulianlounge.backend.domain.User;
import com.iulianlounge.backend.dto.MeResponse;
import com.iulianlounge.backend.exception.InvalidTokenException;
import com.iulianlounge.backend.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public MeResponse getProfile(UUID userId) {
        // Access token válido de una cuenta ya borrada: se trata como token inválido (401)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException());

        // TODO sprint 9: el rango se calculará con la cartera; hasta entonces todos empiezan en NADIE
        return new MeResponse(user.getId(), user.getUsername(), user.getLocale().code(), Rank.NADIE);
    }
}

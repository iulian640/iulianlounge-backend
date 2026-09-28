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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException());

        return new MeResponse(user.getId(), user.getUsername(), user.getLocale().code(), Rank.NADIE);
    }
}

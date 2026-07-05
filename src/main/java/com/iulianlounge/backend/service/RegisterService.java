package com.iulianlounge.backend.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.iulianlounge.backend.dto.RegisterRequest;
import com.iulianlounge.backend.exception.DuplicateUserException;
import com.iulianlounge.backend.repository.UserRepository;

import java.time.Instant;
import java.util.UUID;

import com.iulianlounge.backend.domain.User;

@Service
public class RegisterService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegisterService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void register(RegisterRequest request) {

        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateUserException("El username ya está en uso");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateUserException("El email ya está en uso");
        }

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole("USER");
        user.setCreatedAt(Instant.now());
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setLocale(request.locale());
        userRepository.save(user);
    }
}
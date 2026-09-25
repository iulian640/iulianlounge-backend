package com.iulianlounge.backend.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iulianlounge.backend.dto.RegisterRequest;
import com.iulianlounge.backend.exception.DuplicateUserException;
import com.iulianlounge.backend.exception.ErrorCode;
import com.iulianlounge.backend.repository.UserRepository;

import java.time.Instant;
import java.util.Locale;
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

    @Transactional
    public UUID register(RegisterRequest request) {


        String email = request.email().toLowerCase(Locale.ROOT);

        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateUserException(ErrorCode.USER_USERNAME_TAKEN);
        }

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateUserException(ErrorCode.USER_EMAIL_TAKEN);
        }

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole( "USER");
        user.setCreatedAt(Instant.now());
        user.setUsername(request.username());
        user.setEmail(email);
        user.setLocale(request.locale());
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Carrera: otro registro igual pasó los existsBy* a la vez y el UNIQUE de la BD lo frenó.
            // Solo aquí sabemos que el choque es de usuario; en otro sitio sería otra cosa
            throw new DuplicateUserException(ErrorCode.USER_ALREADY_EXISTS);
        }
        return user.getId();
    }
}
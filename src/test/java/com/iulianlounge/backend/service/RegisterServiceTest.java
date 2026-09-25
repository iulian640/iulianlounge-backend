package com.iulianlounge.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.iulianlounge.backend.domain.Language;
import com.iulianlounge.backend.domain.Role;
import com.iulianlounge.backend.domain.User;
import com.iulianlounge.backend.dto.RegisterRequest;
import com.iulianlounge.backend.exception.DuplicateUserException;
import com.iulianlounge.backend.exception.ErrorCode;
import com.iulianlounge.backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class RegisterServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private RegisterService registerService;

    @BeforeEach
    void setUp() {
        registerService = new RegisterService(userRepository, passwordEncoder);
    }

    @Test
    void registerTurnsADatabaseDuplicateIntoUserAlreadyExists() {
        // Carrera: dos registros iguales pasan los existsBy* y el UNIQUE de la BD frena al segundo
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        DuplicateUserException ex = assertThrows(DuplicateUserException.class,
                () -> registerService.register(new RegisterRequest("cursaito", "cursaito@lounge.com", "12345678", "es")));
        assertEquals(ErrorCode.USER_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    void registerSavesUserWithHashedPasswordAndLowercaseEmail() {
        // Arrange
        RegisterRequest request = new RegisterRequest("cursaito", "Cursaito@Lounge.COM", "12345678", "es");
        when(passwordEncoder.encode("12345678")).thenReturn("hash-de-mentira");

        // Act
        UUID userId = registerService.register(request);

        // Assert: capturamos el User que se guardó para mirarlo por dentro
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertEquals(userId, saved.getValue().getId());
        assertEquals("cursaito@lounge.com", saved.getValue().getEmail());
        assertEquals("hash-de-mentira", saved.getValue().getPasswordHash());
        assertEquals("cursaito", saved.getValue().getUsername());
        assertEquals(Role.USER, saved.getValue().getRole());
        assertEquals(Language.ES, saved.getValue().getLocale());
    }

    @Test
    void registerThrowsWhenUsernameIsTaken() {
        RegisterRequest request = new RegisterRequest("cursaito", "cursaito@lounge.com", "12345678", "es");
        when(userRepository.existsByUsername("cursaito")).thenReturn(true);

        DuplicateUserException ex = assertThrows(DuplicateUserException.class,
                () -> registerService.register(request));

        assertEquals(ErrorCode.USER_USERNAME_TAKEN, ex.getErrorCode());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerThrowsWhenEmailIsTakenIgnoringCase() {
        RegisterRequest request = new RegisterRequest("cursaito", "CURSAITO@lounge.com", "12345678", "es");
        when(userRepository.existsByEmail("cursaito@lounge.com")).thenReturn(true);

        DuplicateUserException ex = assertThrows(DuplicateUserException.class,
                () -> registerService.register(request));

        assertEquals(ErrorCode.USER_EMAIL_TAKEN, ex.getErrorCode());
        verify(userRepository, never()).saveAndFlush(any());
    }
}

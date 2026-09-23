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
import org.springframework.security.crypto.password.PasswordEncoder;

import com.iulianlounge.backend.domain.User;
import com.iulianlounge.backend.dto.RegisterRequest;
import com.iulianlounge.backend.exception.DuplicateUserException;
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
    void registerSavesUserWithHashedPasswordAndLowercaseEmail() {
        // Arrange
        RegisterRequest request = new RegisterRequest("cursaito", "Cursaito@Lounge.COM", "12345678", "es");
        when(passwordEncoder.encode("12345678")).thenReturn("hash-de-mentira");

        // Act
        UUID userId = registerService.register(request);

        // Assert: capturamos el User que se guardó para mirarlo por dentro
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(userId, saved.getValue().getId());
        assertEquals("cursaito@lounge.com", saved.getValue().getEmail());
        assertEquals("hash-de-mentira", saved.getValue().getPasswordHash());
        assertEquals("cursaito", saved.getValue().getUsername());
    }

    @Test
    void registerThrowsWhenUsernameIsTaken() {
        RegisterRequest request = new RegisterRequest("cursaito", "cursaito@lounge.com", "12345678", "es");
        when(userRepository.existsByUsername("cursaito")).thenReturn(true);

        DuplicateUserException ex = assertThrows(DuplicateUserException.class,
                () -> registerService.register(request));

        assertEquals("El username ya está en uso", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerThrowsWhenEmailIsTakenIgnoringCase() {
        RegisterRequest request = new RegisterRequest("cursaito", "CURSAITO@lounge.com", "12345678", "es");
        when(userRepository.existsByEmail("cursaito@lounge.com")).thenReturn(true);

        DuplicateUserException ex = assertThrows(DuplicateUserException.class,
                () -> registerService.register(request));

        assertEquals("El email ya está en uso", ex.getMessage());
        verify(userRepository, never()).save(any());
    }
}

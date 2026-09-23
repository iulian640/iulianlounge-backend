package com.iulianlounge.backend.repository;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.iulianlounge.backend.domain.User;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesAndRetrievesAUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("cursaito");
        user.setEmail("cursaito@gmail.com");
        user.setPasswordHash("hash-de-mentira");
        user.setRole("PLAYER");
        user.setLocale("es");
        user.setCreatedAt(Instant.now());
        userRepository.save(user);
        User retrieved = userRepository.findById(user.getId()).orElseThrow();
        assertEquals("cursaito", retrieved.getUsername());
        assertEquals("cursaito@gmail.com", retrieved.getEmail());
        assertEquals("hash-de-mentira", retrieved.getPasswordHash());
        assertEquals("PLAYER", retrieved.getRole());
        assertEquals("es", retrieved.getLocale());
        assertEquals(user.getCreatedAt(), retrieved.getCreatedAt());
    }

    @Test
    void findsAUserByUsername() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("dwight");
        user.setEmail("dwight@lounge.com");
        user.setPasswordHash("hash-de-mentira");
        user.setRole("USER");
        user.setLocale("en");
        user.setCreatedAt(Instant.now());
        userRepository.save(user);

        assertEquals(user.getId(), userRepository.findByUsername("dwight").orElseThrow().getId());
        assertTrue(userRepository.findByUsername("nadie").isEmpty());
    }
}
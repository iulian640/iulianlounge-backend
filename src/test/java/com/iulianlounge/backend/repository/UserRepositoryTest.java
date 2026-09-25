package com.iulianlounge.backend.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import com.iulianlounge.backend.domain.Language;
import com.iulianlounge.backend.domain.Role;
import com.iulianlounge.backend.domain.User;

// Contra el Postgres real con las migraciones de Flyway: prueba el mapeo JPA y las restricciones de la BD.
// Cada test hace rollback al acabar
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savesAndReadsBackEveryColumnFromTheDatabase() {
        // Postgres guarda microsegundos; Java da nanosegundos. Truncado, el Instant sobrevive al viaje
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        User user = newUser("cursaito", "cursaito@lounge.com");
        user.setCreatedAt(createdAt);
        userRepository.saveAndFlush(user);
        // Sin clear(), findById devolvería el mismo objeto de la caché de Hibernate sin ir a la BD
        entityManager.clear();

        User retrieved = userRepository.findById(user.getId()).orElseThrow();

        assertEquals("cursaito", retrieved.getUsername());
        assertEquals("cursaito@lounge.com", retrieved.getEmail());
        assertEquals("hash-de-mentira", retrieved.getPasswordHash());
        assertEquals(Role.USER, retrieved.getRole());
        assertEquals(Language.ES, retrieved.getLocale());
        assertEquals(createdAt, retrieved.getCreatedAt());
    }

    @Test
    void findsAUserByUsername() {
        User user = userRepository.saveAndFlush(newUser("dwight", "dwight@lounge.com"));
        entityManager.clear();

        assertEquals(user.getId(), userRepository.findByUsername("dwight").orElseThrow().getId());
        assertTrue(userRepository.findByUsername("nadie").isEmpty());
    }

    @Test
    void emailIsUniqueIgnoringCase() {
        // V2: índice único sobre lower(email)
        userRepository.saveAndFlush(newUser("cursaito", "cursaito@lounge.com"));

        assertThrows(DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(newUser("otro", "CURSAITO@Lounge.com")));
    }

    @Test
    void databaseRejectsARoleOutsideTheEnum() {
        // V3: aunque alguien se salte el enum de Java (SQL a mano, otro servicio), la BD dice que no
        User user = userRepository.saveAndFlush(newUser("cursaito", "cursaito@lounge.com"));

        assertThrows(Exception.class, () -> entityManager.getEntityManager()
                .createNativeQuery("UPDATE users SET role = 'PLAYER' WHERE id = :id")
                .setParameter("id", user.getId())
                .executeUpdate());
    }

    private static User newUser(String username, String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("hash-de-mentira");
        user.setRole(Role.USER);
        user.setLocale(Language.ES);
        user.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        return user;
    }
}

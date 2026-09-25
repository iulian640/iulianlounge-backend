package com.iulianlounge.backend.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;

import com.iulianlounge.backend.domain.Language;
import com.iulianlounge.backend.domain.Role;
import com.iulianlounge.backend.domain.TokenTransaction;
import com.iulianlounge.backend.domain.TransactionType;
import com.iulianlounge.backend.domain.User;
import com.iulianlounge.backend.domain.Wallet;

// Contra el Postgres real: prueba que las defensas de la V4 están en la BD, no solo en Java
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class WalletRepositoryTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TokenTransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        wallet = walletRepository.saveAndFlush(new Wallet(newUser("cursaito").getId(), NOW));
    }

    @Test
    void findsTheWalletOfAUser() {
        entityManager.clear();

        Wallet found = walletRepository.findByUserId(wallet.getUserId()).orElseThrow();

        assertEquals(wallet.getId(), found.getId());
        assertEquals(0, found.getBalance());
    }

    @Test
    void databaseRejectsANegativeBalance() {
        // CHECK (balance >= 0): la última defensa si Java dejara pasar un débito de más
        assertThrows(Exception.class, () -> entityManager.getEntityManager()
                .createNativeQuery("UPDATE wallet SET balance = -1 WHERE id = :id")
                .setParameter("id", wallet.getId())
                .executeUpdate());
    }

    @Test
    void staleWalletIsRejectedByTheVersion() {
        // Otra transacción cambió la cartera después de que la leyéramos: @Version lo detecta al guardar
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE wallet SET version = version + 1 WHERE id = :id")
                .setParameter("id", wallet.getId())
                .executeUpdate();
        wallet.setBalance(50);

        assertThrows(OptimisticLockingFailureException.class, () -> walletRepository.saveAndFlush(wallet));
    }

    @Test
    void sameIdempotencyKeyTwiceInTheSameWalletIsRejected() {
        transactionRepository.saveAndFlush(welcome(wallet, "clave-1"));

        assertThrows(DataIntegrityViolationException.class,
                () -> transactionRepository.saveAndFlush(welcome(wallet, "clave-1")));
    }

    @Test
    void sameIdempotencyKeyInAnotherWalletIsFine() {
        Wallet other = walletRepository.saveAndFlush(new Wallet(newUser("dwight").getId(), NOW));
        transactionRepository.saveAndFlush(welcome(wallet, "clave-1"));

        transactionRepository.saveAndFlush(welcome(other, "clave-1"));
    }

    @Test
    void historyComesNewestFirst() {
        transactionRepository.saveAndFlush(new TokenTransaction(
                wallet.getId(), 100, TransactionType.WELCOME_BONUS, 100, null, NOW.minusSeconds(60)));
        transactionRepository.saveAndFlush(new TokenTransaction(
                wallet.getId(), 5, TransactionType.WELCOME_BONUS, 105, null, NOW));
        entityManager.clear();

        List<TokenTransaction> history = transactionRepository
                .findByWalletIdOrderByCreatedAtDesc(wallet.getId(), PageRequest.of(0, 10))
                .getContent();

        assertEquals(List.of(105L, 100L), history.stream().map(TokenTransaction::getBalanceAfter).toList());
    }

    private TokenTransaction welcome(Wallet target, String idempotencyKey) {
        return new TokenTransaction(target.getId(), 100, TransactionType.WELCOME_BONUS, 100, idempotencyKey, NOW);
    }

    private User newUser(String username) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username + "-" + UUID.randomUUID().toString().substring(0, 8));
        user.setEmail(user.getUsername() + "@lounge.com");
        user.setPasswordHash("hash-de-mentira");
        user.setRole(Role.USER);
        user.setLocale(Language.ES);
        user.setCreatedAt(NOW);
        return userRepository.saveAndFlush(user);
    }
}

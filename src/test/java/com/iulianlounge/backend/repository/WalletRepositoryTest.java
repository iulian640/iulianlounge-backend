package com.iulianlounge.backend.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;

import jakarta.persistence.PersistenceException;

import com.iulianlounge.backend.domain.Language;
import com.iulianlounge.backend.domain.Role;
import com.iulianlounge.backend.domain.TokenTransaction;
import com.iulianlounge.backend.domain.TransactionType;
import com.iulianlounge.backend.domain.User;
import com.iulianlounge.backend.domain.Wallet;

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
        assertThrows(PersistenceException.class, () -> entityManager.getEntityManager()
                .createNativeQuery("UPDATE wallet SET balance = -1 WHERE id = :id")
                .setParameter("id", wallet.getId())
                .executeUpdate());
    }

    @Test
    void aWalletWithMovementsCannotBeDeleted() {
        transactionRepository.saveAndFlush(welcome(wallet, null));

        assertThrows(PersistenceException.class, () -> entityManager.getEntityManager()
                .createNativeQuery("DELETE FROM wallet WHERE id = :id")
                .setParameter("id", wallet.getId())
                .executeUpdate());
    }

    @Test
    void staleWalletIsRejectedByTheVersion() {
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
                .findByWalletIdOrderByCreatedAtDescIdDesc(wallet.getId(), PageRequest.of(0, 10))
                .getContent();

        assertEquals(List.of(105L, 100L), history.stream().map(TokenTransaction::getBalanceAfter).toList());
    }

    @ParameterizedTest
    @EnumSource(TransactionType.class)
    void everyMovementTypeFitsTheDatabaseCheck(TransactionType type) {
        long amount = type == TransactionType.BAR_ORDER ? -10 : 10;

        transactionRepository.saveAndFlush(new TokenTransaction(wallet.getId(), amount, type, 10, null, NOW));
    }

    @Test
    void databaseRejectsAnUnknownMovementType() {
        assertViolates("token_transaction_type_check", () -> insertMovement("PROPINA", 10));
    }

    @ParameterizedTest
    @CsvSource({"BAR_ORDER, 10", "WELCOME_BONUS, -10", "HOUSE_CREDIT, -10"})
    void databaseRejectsAMovementWhoseSignDoesNotMatchItsType(String type, long amount) {
        assertViolates("token_transaction_amount_sign_check", () -> insertMovement(type, amount));
    }

    @Test
    void theNativeInsertUsedByTheseChecksWorksForAValidMovement() {
        insertMovement("BAR_ORDER", -10);
    }

    @Test
    void addsUpTheMovementsOfOneTypeInOneWallet() {
        Wallet other = walletRepository.saveAndFlush(new Wallet(newUser("dwight").getId(), NOW));
        transactionRepository.saveAndFlush(movement(wallet, 100, TransactionType.WELCOME_BONUS, 100));
        transactionRepository.saveAndFlush(movement(wallet, -40, TransactionType.BAR_ORDER, 60));
        transactionRepository.saveAndFlush(movement(wallet, -10, TransactionType.BAR_ORDER, 50));
        transactionRepository.saveAndFlush(movement(other, -25, TransactionType.BAR_ORDER, 75));

        long sum = transactionRepository.sumAmountByWalletIdAndType(wallet.getId(), TransactionType.BAR_ORDER);

        assertEquals(-50, sum);
    }

    @Test
    void theSumIsZeroWithoutMovementsOfThatType() {
        transactionRepository.saveAndFlush(movement(wallet, 100, TransactionType.WELCOME_BONUS, 100));

        long sum = transactionRepository.sumAmountByWalletIdAndType(wallet.getId(), TransactionType.BAR_ORDER);

        assertEquals(0, sum);
    }

    private static void assertViolates(String constraint, Executable insert) {
        PersistenceException error = assertThrows(PersistenceException.class, insert);
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        assertTrue(cause.getMessage().contains(constraint), cause.getMessage());
    }

    private void insertMovement(String type, long amount) {
        entityManager.getEntityManager()
                .createNativeQuery("""
                        INSERT INTO token_transaction (id, wallet_id, amount, type, balance_after, created_at)
                        VALUES (gen_random_uuid(), :walletId, :amount, :type, 10, now())
                        """)
                .setParameter("walletId", wallet.getId())
                .setParameter("amount", amount)
                .setParameter("type", type)
                .executeUpdate();
    }

    private TokenTransaction movement(Wallet target, long amount, TransactionType type, long balanceAfter) {
        return new TokenTransaction(target.getId(), amount, type, balanceAfter, null, NOW);
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

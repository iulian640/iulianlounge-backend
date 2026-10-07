package com.iulianlounge.backend.service;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongPredicate;
import java.util.function.Supplier;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.iulianlounge.backend.domain.TokenTransaction;
import com.iulianlounge.backend.domain.TransactionType;
import com.iulianlounge.backend.domain.Wallet;
import com.iulianlounge.backend.exception.IdempotencyMismatchException;
import com.iulianlounge.backend.exception.InsufficientFundsException;
import com.iulianlounge.backend.exception.WalletConflictException;
import com.iulianlounge.backend.exception.WalletNotFoundException;
import com.iulianlounge.backend.repository.TokenTransactionRepository;
import com.iulianlounge.backend.repository.WalletRepository;

@Service
public class WalletService {

    public static final long WELCOME_BONUS = 100;

    private final WalletRepository walletRepository;
    private final TokenTransactionRepository transactionRepository;
    private final TransactionOperations transactions;
    private final Clock clock;

    public WalletService(WalletRepository walletRepository, TokenTransactionRepository transactionRepository,
            TransactionOperations transactions, Clock clock) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.transactions = transactions;
        this.clock = clock;
    }

    public TokenTransaction openWallet(UUID userId) {
        return transactions.execute(status -> {
            Wallet wallet = walletRepository.saveAndFlush(new Wallet(userId, clock.instant()));
            return apply(wallet, WELCOME_BONUS, TransactionType.WELCOME_BONUS, null);
        });
    }

    public TokenTransaction credit(UUID userId, long amount, TransactionType type, String idempotencyKey) {
        requireOwnTransaction();
        requirePositive(amount);
        return withOneRetry(() -> applyTo(userId, amount, type, idempotencyKey));
    }

    public Optional<TokenTransaction> creditIf(UUID userId, long amount, TransactionType type, String idempotencyKey,
            LongPredicate allowedBalance) {
        requireOwnTransaction();
        requirePositive(amount);
        Objects.requireNonNull(allowedBalance, "allowedBalance");
        return withOneRetry(() -> {
            Wallet wallet = findWallet(userId);
            Optional<TokenTransaction> previous = previousMovement(wallet, amount, type, idempotencyKey);
            if (previous.isPresent()) {
                return previous;
            }
            if (!allowedBalance.test(wallet.getBalance())) {
                return Optional.empty();
            }
            return Optional.of(apply(wallet, amount, type, idempotencyKey));
        });
    }

    public TokenTransaction debit(UUID userId, long amount, TransactionType type, String idempotencyKey) {
        requireOwnTransaction();
        requirePositive(amount);
        return withOneRetry(() -> applyTo(userId, -amount, type, idempotencyKey));
    }

    public long getBalance(UUID userId) {
        return findWallet(userId).getBalance();
    }

    public long spentOn(UUID userId, TransactionType type) {
        return walletRepository.findByUserId(userId)
                .map(wallet -> -transactionRepository.sumAmountByWalletIdAndType(wallet.getId(), type))
                .orElse(0L);
    }

    public List<TokenTransaction> movementsOf(UUID userId, TransactionType type) {
        return walletRepository.findByUserId(userId)
                .map(wallet -> transactionRepository.findByWalletIdAndTypeOrderByCreatedAtAscIdAsc(wallet.getId(), type))
                .orElse(List.of());
    }

    public boolean hasMovement(UUID userId, String idempotencyKey) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        return walletRepository.findByUserId(userId)
                .flatMap(wallet -> transactionRepository.findByWalletIdAndIdempotencyKey(wallet.getId(), idempotencyKey))
                .isPresent();
    }

    public Page<TokenTransaction> getTransactions(UUID userId, Pageable pageable) {
        return transactionRepository.findByWalletIdOrderByCreatedAtDescIdDesc(findWallet(userId).getId(), pageable);
    }

    private TokenTransaction applyTo(UUID userId, long signedAmount, TransactionType type, String idempotencyKey) {
        Wallet wallet = findWallet(userId);
        return previousMovement(wallet, signedAmount, type, idempotencyKey)
                .orElseGet(() -> apply(wallet, signedAmount, type, idempotencyKey));
    }

    private Optional<TokenTransaction> previousMovement(Wallet wallet, long signedAmount, TransactionType type,
            String idempotencyKey) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        Optional<TokenTransaction> previous =
                transactionRepository.findByWalletIdAndIdempotencyKey(wallet.getId(), idempotencyKey);
        if (previous.isPresent()
                && (previous.get().getAmount() != signedAmount || previous.get().getType() != type)) {
            throw new IdempotencyMismatchException();
        }
        return previous;
    }

    private TokenTransaction apply(Wallet wallet, long signedAmount, TransactionType type, String idempotencyKey) {
        long newBalance = Math.addExact(wallet.getBalance(), signedAmount);
        if (newBalance < 0) {
            throw new InsufficientFundsException();
        }

        wallet.setBalance(newBalance);
        walletRepository.saveAndFlush(wallet);

        return transactionRepository.saveAndFlush(new TokenTransaction(
                wallet.getId(), signedAmount, type, newBalance, idempotencyKey, clock.instant()));
    }

    private <T> T withOneRetry(Supplier<T> movement) {
        try {
            return transactions.execute(status -> movement.get());
        } catch (OptimisticLockingFailureException firstClash) {
            try {
                return transactions.execute(status -> movement.get());
            } catch (OptimisticLockingFailureException secondClash) {
                throw new WalletConflictException();
            }
        }
    }

    private Wallet findWallet(UUID userId) {
        return walletRepository.findByUserId(userId).orElseThrow(WalletNotFoundException::new);
    }

    private static void requireOwnTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("WalletService.credit, creditIf and debit must run in their own transaction");
        }
    }

    private static void requirePositive(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive: " + amount);
        }
    }
}

package com.iulianlounge.backend.service;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
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

// ADR-04: la única puerta a las fichas. Cada movimiento escribe el ledger y el saldo en la MISMA transacción:
// o se guardan los dos, o ninguno
@Service
public class WalletService {

    // Las fichas con las que nace cada cuenta (chikilicuatres / Shrutebucks)
    public static final long WELCOME_BONUS = 100;

    private final WalletRepository walletRepository;
    private final TokenTransactionRepository transactionRepository;
    // TransactionOperations en vez de @Transactional: el reintento necesita una transacción NUEVA,
    // y con @Transactional el segundo intento iría dentro de la primera, ya estropeada
    private final TransactionOperations transactions;
    private final Clock clock;

    public WalletService(WalletRepository walletRepository, TokenTransactionRepository transactionRepository,
            TransactionOperations transactions, Clock clock) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.transactions = transactions;
        this.clock = clock;
    }

    // Cartera + bono en una sola transacción. Desde RegisterService se une a la suya (usuario, cartera y bono
    // van juntos o ninguno); llamado desde otro sitio, abre una propia. Nunca queda una cartera sin su movimiento
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

    public TokenTransaction debit(UUID userId, long amount, TransactionType type, String idempotencyKey) {
        requireOwnTransaction();
        requirePositive(amount);
        return withOneRetry(() -> applyTo(userId, -amount, type, idempotencyKey));
    }

    public long getBalance(UUID userId) {
        return findWallet(userId).getBalance();
    }

    public Page<TokenTransaction> getTransactions(UUID userId, Pageable pageable) {
        return transactionRepository.findByWalletIdOrderByCreatedAtDescIdDesc(findWallet(userId).getId(), pageable);
    }

    private TokenTransaction applyTo(UUID userId, long signedAmount, TransactionType type, String idempotencyKey) {
        Wallet wallet = findWallet(userId);

        // Misma clave que un movimiento anterior: es un reintento del cliente, se devuelve el original sin repetirlo
        if (idempotencyKey != null) {
            Optional<TokenTransaction> previous =
                    transactionRepository.findByWalletIdAndIdempotencyKey(wallet.getId(), idempotencyKey);
            if (previous.isPresent()) {
                // Misma clave para otra cantidad u otro tipo: no es un reintento, es un bug del cliente
                if (previous.get().getAmount() != signedAmount || previous.get().getType() != type) {
                    throw new IdempotencyMismatchException();
                }
                return previous.get();
            }
        }
        return apply(wallet, signedAmount, type, idempotencyKey);
    }

    private TokenTransaction apply(Wallet wallet, long signedAmount, TransactionType type, String idempotencyKey) {
        // addExact: si algún día se desbordara un long, excepción en vez de un saldo negativo absurdo
        long newBalance = Math.addExact(wallet.getBalance(), signedAmount);
        if (newBalance < 0) {
            throw new InsufficientFundsException();
        }

        wallet.setBalance(newBalance);
        // flush ya: si otra transacción cambió la cartera, @Version salta aquí y no al final
        walletRepository.saveAndFlush(wallet);

        return transactionRepository.saveAndFlush(new TokenTransaction(
                wallet.getId(), signedAmount, type, newBalance, idempotencyKey, clock.instant()));
    }

    // ADR-04: si dos escrituras chocan, un reintento con la cartera releída; si vuelve a chocar, 409
    private TokenTransaction withOneRetry(Supplier<TokenTransaction> movement) {
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

    // El reintento solo funciona en una transacción propia: dentro de la de otro, el segundo intento
    // reutilizaría la cartera vieja del primero y la transacción ya estaría marcada para rollback.
    // Quien necesite atomicidad con sus propias escrituras (el barman) tendrá que pasarlas aquí dentro (ADR-04)
    private static void requireOwnTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("WalletService.credit/debit must run in its own transaction");
        }
    }

    private static void requirePositive(long amount) {
        // El signo lo decide credit/debit; una cantidad 0 o negativa es un bug de quien llama
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive: " + amount);
        }
    }
}

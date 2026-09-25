package com.iulianlounge.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

import com.iulianlounge.backend.domain.TokenTransaction;
import com.iulianlounge.backend.domain.TransactionType;
import com.iulianlounge.backend.domain.Wallet;
import com.iulianlounge.backend.exception.InsufficientFundsException;
import com.iulianlounge.backend.exception.WalletConflictException;
import com.iulianlounge.backend.exception.WalletNotFoundException;
import com.iulianlounge.backend.repository.TokenTransactionRepository;
import com.iulianlounge.backend.repository.WalletRepository;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WALLET_ID = UUID.randomUUID();

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TokenTransactionRepository transactionRepository;

    private WalletService walletService;

    @BeforeEach
    void setUp() {
        // Sin transacción real: aquí se prueba la lógica, la atomicidad la prueba la BD
        walletService = new WalletService(walletRepository, transactionRepository,
                TransactionOperations.withoutTransaction(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void openWalletCreatesItWithTheWelcomeBonus() {
        when(walletRepository.saveAndFlush(any())).thenAnswer(call -> withId(call.getArgument(0)));
        when(transactionRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        TokenTransaction bonus = walletService.openWallet(USER_ID);

        assertEquals(WalletService.WELCOME_BONUS, bonus.getAmount());
        assertEquals(TransactionType.WELCOME_BONUS, bonus.getType());
        assertEquals(100, bonus.getBalanceAfter());
        assertEquals(WALLET_ID, bonus.getWalletId());
    }

    @Test
    void creditAddsToTheBalanceAndRecordsItInTheLedger() {
        Wallet wallet = walletWith(50);
        when(walletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(wallet));
        when(transactionRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        TokenTransaction movement = walletService.credit(USER_ID, 30, TransactionType.WELCOME_BONUS, null);

        assertEquals(80, wallet.getBalance());
        assertEquals(30, movement.getAmount());
        assertEquals(80, movement.getBalanceAfter());
        assertEquals(NOW, movement.getCreatedAt());
        verify(walletRepository).saveAndFlush(wallet);
    }

    @Test
    void debitSubtractsAndRecordsANegativeAmount() {
        Wallet wallet = walletWith(50);
        when(walletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(wallet));
        when(transactionRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        TokenTransaction movement = walletService.debit(USER_ID, 20, TransactionType.WELCOME_BONUS, null);

        assertEquals(30, wallet.getBalance());
        assertEquals(-20, movement.getAmount());
        assertEquals(30, movement.getBalanceAfter());
    }

    @Test
    void debitOfMoreThanTheBalanceTouchesNothing() {
        Wallet wallet = walletWith(10);
        when(walletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(wallet));

        assertThrows(InsufficientFundsException.class,
                () -> walletService.debit(USER_ID, 11, TransactionType.WELCOME_BONUS, null));

        assertEquals(10, wallet.getBalance());
        verify(walletRepository, never()).saveAndFlush(any());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void debitOfExactlyTheBalanceLeavesZero() {
        Wallet wallet = walletWith(10);
        when(walletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(wallet));
        when(transactionRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        walletService.debit(USER_ID, 10, TransactionType.WELCOME_BONUS, null);

        assertEquals(0, wallet.getBalance());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -5})
    void amountMustBePositive(long amount) {
        // El signo lo pone credit/debit; quien llama siempre pasa una cantidad positiva
        assertThrows(IllegalArgumentException.class,
                () -> walletService.credit(USER_ID, amount, TransactionType.WELCOME_BONUS, null));
        assertThrows(IllegalArgumentException.class,
                () -> walletService.debit(USER_ID, amount, TransactionType.WELCOME_BONUS, null));
    }

    @Test
    void repeatedIdempotencyKeyReturnsTheOriginalMovementWithoutApplyingItAgain() {
        // Doble clic: la segunda petición con la misma clave no vuelve a sumar
        Wallet wallet = walletWith(80);
        TokenTransaction original = new TokenTransaction(WALLET_ID, 30, TransactionType.WELCOME_BONUS, 80, "clave-1", NOW);
        when(walletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findByWalletIdAndIdempotencyKey(WALLET_ID, "clave-1")).thenReturn(Optional.of(original));

        TokenTransaction result = walletService.credit(USER_ID, 30, TransactionType.WELCOME_BONUS, "clave-1");

        assertSame(original, result);
        assertEquals(80, wallet.getBalance());
        verify(walletRepository, never()).saveAndFlush(any());
    }

    @Test
    void retriesOnceWhenAnotherWriteGotThereFirst() {
        // Primera vuelta: otra transacción cambió la cartera → @Version falla. Segunda: relee y aplica
        when(walletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(walletWith(50)), Optional.of(walletWith(60)));
        when(walletRepository.saveAndFlush(any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Wallet.class, WALLET_ID))
                .thenAnswer(call -> call.getArgument(0));
        when(transactionRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        TokenTransaction movement = walletService.credit(USER_ID, 10, TransactionType.WELCOME_BONUS, null);

        assertEquals(70, movement.getBalanceAfter());   // sobre el saldo releído, no el viejo
        verify(walletRepository, times(2)).findByUserId(USER_ID);
    }

    @Test
    void givesUpWithAConflictAfterTheRetryAlsoFails() {
        when(walletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(walletWith(50)), Optional.of(walletWith(50)));
        when(walletRepository.saveAndFlush(any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Wallet.class, WALLET_ID));

        assertThrows(WalletConflictException.class,
                () -> walletService.credit(USER_ID, 10, TransactionType.WELCOME_BONUS, null));
        verify(walletRepository, times(2)).saveAndFlush(any());
    }

    @Test
    void userWithoutWalletIsAnError() {
        when(walletRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThrows(WalletNotFoundException.class, () -> walletService.getBalance(USER_ID));
    }

    @Test
    void getBalanceReadsTheMaterialisedBalance() {
        when(walletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(walletWith(123)));

        assertEquals(123, walletService.getBalance(USER_ID));
    }

    private static Wallet walletWith(long balance) {
        Wallet wallet = withId(new Wallet(USER_ID, NOW));
        wallet.setBalance(balance);
        return wallet;
    }

    // El id lo pone JPA al guardar; en un test unitario no hay JPA
    private static Wallet withId(Wallet wallet) {
        ReflectionTestUtils.setField(wallet, "id", WALLET_ID);
        return wallet;
    }
}

package com.iulianlounge.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import com.iulianlounge.backend.domain.Bet;
import com.iulianlounge.backend.domain.BlackjackHand;
import com.iulianlounge.backend.domain.BlackjackRules;
import com.iulianlounge.backend.domain.Card;
import com.iulianlounge.backend.domain.HandStatus;
import com.iulianlounge.backend.domain.Outcome;
import com.iulianlounge.backend.domain.StackedDeck;
import com.iulianlounge.backend.domain.TokenTransaction;
import com.iulianlounge.backend.domain.TransactionType;
import com.iulianlounge.backend.dto.BlackjackResponse;
import com.iulianlounge.backend.dto.HandResponse;
import com.iulianlounge.backend.exception.HandFinishedException;
import com.iulianlounge.backend.exception.HandInProgressException;
import com.iulianlounge.backend.exception.HandNotFoundException;
import com.iulianlounge.backend.exception.IdempotencyMismatchException;
import com.iulianlounge.backend.exception.InsufficientFundsException;
import com.iulianlounge.backend.exception.WalletConflictException;
import com.iulianlounge.backend.repository.BlackjackHandRepository;

@ExtendWith(MockitoExtension.class)
class BlackjackServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T20:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID KEY = UUID.randomUUID();
    private static final UUID HAND_ID = UUID.randomUUID();
    private static final String BET_KEY = "blackjack-bet:" + KEY;
    private static final String PAYOUT_KEY = "blackjack-payout:" + HAND_ID;

    @Mock
    private WalletService walletService;

    @Mock
    private BlackjackHandRepository handRepository;

    private List<Card> nextDeck;
    private BlackjackService service;

    @BeforeEach
    void setUp() {
        service = new BlackjackService(walletService, handRepository, deck -> nextDeck,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void dealDebitsTheBetUnderItsKeyAndThenSavesTheDealtHand() {
        stack("9S", "KD", "8H", "5C");
        when(handRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.empty());
        when(handRepository.findByUserIdAndStatus(USER_ID, HandStatus.PLAYER_TURN)).thenReturn(Optional.empty());
        when(walletService.debit(USER_ID, 20, TransactionType.BLACKJACK_BET, BET_KEY)).thenReturn(movement(-20, 80));
        when(handRepository.saveAndFlush(any())).thenAnswer(call -> withId(call.getArgument(0)));
        when(walletService.getBalance(USER_ID)).thenReturn(80L);

        HandResponse response = service.deal(USER_ID, Bet.TWENTY, KEY);

        InOrder order = inOrder(walletService, handRepository);
        order.verify(walletService).debit(USER_ID, 20, TransactionType.BLACKJACK_BET, BET_KEY);
        order.verify(handRepository).saveAndFlush(any());
        assertEquals(HAND_ID, response.hand().id());
        assertEquals(HandStatus.PLAYER_TURN, response.hand().status());
        assertEquals(20, response.hand().bet());
        assertEquals(1, response.hand().dealerCards().size());
        assertEquals(80, response.balance());
        verify(walletService, never()).credit(any(), anyLong(), any(), any());
    }

    @Test
    void theSavedHandCarriesTheKeyTheBetAndTheDealtTable() {
        stack("9S", "KD", "8H", "5C");
        when(handRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.empty());
        when(handRepository.findByUserIdAndStatus(USER_ID, HandStatus.PLAYER_TURN)).thenReturn(Optional.empty());
        when(walletService.debit(USER_ID, 20, TransactionType.BLACKJACK_BET, BET_KEY)).thenReturn(movement(-20, 80));
        when(handRepository.saveAndFlush(any())).thenAnswer(call -> withId(call.getArgument(0)));
        when(walletService.getBalance(USER_ID)).thenReturn(80L);

        service.deal(USER_ID, Bet.TWENTY, KEY);

        ArgumentCaptor<BlackjackHand> saved = ArgumentCaptor.forClass(BlackjackHand.class);
        verify(handRepository).saveAndFlush(saved.capture());
        assertEquals(USER_ID, saved.getValue().getUserId());
        assertEquals(KEY, saved.getValue().getIdempotencyKey());
        assertEquals(Bet.TWENTY, saved.getValue().getBet());
        assertEquals(List.of(Card.fromCode("9S"), Card.fromCode("8H")), saved.getValue().getPlayerCards());
        assertEquals(NOW, saved.getValue().getCreatedAt());
    }

    @Test
    void aPlayerNaturalIsPaidTwoAndAHalfTimesAtOnceAndSettled() {
        stack("AS", "9D", "KS", "8D");
        when(handRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.empty());
        when(handRepository.findByUserIdAndStatus(USER_ID, HandStatus.PLAYER_TURN)).thenReturn(Optional.empty());
        when(walletService.debit(USER_ID, 20, TransactionType.BLACKJACK_BET, BET_KEY)).thenReturn(movement(-20, 80));
        when(handRepository.saveAndFlush(any())).thenAnswer(call -> withId(call.getArgument(0)));
        when(walletService.credit(USER_ID, 50, TransactionType.BLACKJACK_PAYOUT, PAYOUT_KEY))
                .thenReturn(movement(50, 130));
        when(walletService.getBalance(USER_ID)).thenReturn(130L);

        HandResponse response = service.deal(USER_ID, Bet.TWENTY, KEY);

        verify(walletService).credit(USER_ID, 50, TransactionType.BLACKJACK_PAYOUT, PAYOUT_KEY);
        verify(handRepository, times(2)).saveAndFlush(any());
        assertEquals(HandStatus.FINISHED, response.hand().status());
        assertEquals(Outcome.BLACKJACK, response.hand().outcome());
        assertEquals(50L, response.hand().payout());
        assertEquals(130, response.balance());
        ArgumentCaptor<BlackjackHand> saved = ArgumentCaptor.forClass(BlackjackHand.class);
        verify(handRepository, times(2)).saveAndFlush(saved.capture());
        assertTrue(saved.getValue().isSettled());
        assertEquals(NOW, saved.getValue().getSettledAt());
    }

    @Test
    void aDealerNaturalEndsTheHandAsALossSettledWithoutAnyCredit() {
        stack("9S", "AD", "8H", "KC");
        when(handRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.empty());
        when(handRepository.findByUserIdAndStatus(USER_ID, HandStatus.PLAYER_TURN)).thenReturn(Optional.empty());
        when(walletService.debit(USER_ID, 10, TransactionType.BLACKJACK_BET, BET_KEY)).thenReturn(movement(-10, 90));
        when(handRepository.saveAndFlush(any())).thenAnswer(call -> withId(call.getArgument(0)));
        when(walletService.getBalance(USER_ID)).thenReturn(90L);

        HandResponse response = service.deal(USER_ID, Bet.TEN, KEY);

        assertEquals(Outcome.LOSE, response.hand().outcome());
        assertEquals(0L, response.hand().payout());
        assertEquals(2, response.hand().dealerCards().size());
        verify(walletService, never()).credit(any(), anyLong(), any(), any());
        ArgumentCaptor<BlackjackHand> saved = ArgumentCaptor.forClass(BlackjackHand.class);
        verify(handRepository, times(2)).saveAndFlush(saved.capture());
        assertTrue(saved.getValue().isSettled());
    }

    @Test
    void bothNaturalsGiveTheBetBack() {
        stack("AS", "AD", "KS", "KD");
        when(handRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.empty());
        when(handRepository.findByUserIdAndStatus(USER_ID, HandStatus.PLAYER_TURN)).thenReturn(Optional.empty());
        when(walletService.debit(USER_ID, 10, TransactionType.BLACKJACK_BET, BET_KEY)).thenReturn(movement(-10, 90));
        when(handRepository.saveAndFlush(any())).thenAnswer(call -> withId(call.getArgument(0)));
        when(walletService.credit(USER_ID, 10, TransactionType.BLACKJACK_PAYOUT, PAYOUT_KEY))
                .thenReturn(movement(10, 100));
        when(walletService.getBalance(USER_ID)).thenReturn(100L);

        HandResponse response = service.deal(USER_ID, Bet.TEN, KEY);

        assertEquals(Outcome.PUSH, response.hand().outcome());
        verify(walletService).credit(USER_ID, 10, TransactionType.BLACKJACK_PAYOUT, PAYOUT_KEY);
    }

    @Test
    void dealingWithAHandInProgressIsRejectedWithoutCharging() {
        when(handRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.empty());
        when(handRepository.findByUserIdAndStatus(USER_ID, HandStatus.PLAYER_TURN))
                .thenReturn(Optional.of(inPlay(Bet.TEN, UUID.randomUUID())));

        assertThrows(HandInProgressException.class, () -> service.deal(USER_ID, Bet.TWENTY, KEY));

        verify(walletService, never()).debit(any(), anyLong(), any(), any());
        verify(handRepository, never()).saveAndFlush(any());
    }

    @Test
    void notEnoughChipsPropagatesAndNothingIsSaved() {
        stack("9S", "KD", "8H", "5C");
        when(handRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.empty());
        when(handRepository.findByUserIdAndStatus(USER_ID, HandStatus.PLAYER_TURN)).thenReturn(Optional.empty());
        when(walletService.debit(USER_ID, 50, TransactionType.BLACKJACK_BET, BET_KEY))
                .thenThrow(new InsufficientFundsException());

        assertThrows(InsufficientFundsException.class, () -> service.deal(USER_ID, Bet.FIFTY, KEY));

        verify(handRepository, never()).saveAndFlush(any());
    }

    @Test
    void replayingTheKeyReturnsTheSameHandWithoutChargingAgain() {
        BlackjackHand existing = inPlay(Bet.TWENTY, KEY);
        when(handRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.of(existing));
        when(walletService.getBalance(USER_ID)).thenReturn(80L);

        HandResponse response = service.deal(USER_ID, Bet.TWENTY, KEY);

        assertEquals(existing.getId(), response.hand().id());
        assertEquals(80, response.balance());
        verify(walletService, never()).debit(any(), anyLong(), any(), any());
        verify(handRepository, never()).saveAndFlush(any());
    }

    @Test
    void replayingTheKeyWithAnotherBetIsAMismatch() {
        when(handRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.of(inPlay(Bet.TWENTY, KEY)));

        assertThrows(IdempotencyMismatchException.class, () -> service.deal(USER_ID, Bet.FIFTY, KEY));

        verify(walletService, never()).debit(any(), anyLong(), any(), any());
    }

    @Test
    void replayingTheKeyOfAVoidHandAnswersHandInProgressAgain() {
        BlackjackHand voided = withId(BlackjackHand.voided(USER_ID, KEY, Bet.TWENTY, NOW));
        when(handRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.of(voided));

        assertThrows(HandInProgressException.class, () -> service.deal(USER_ID, Bet.TWENTY, KEY));

        verify(walletService, never()).debit(any(), anyLong(), any(), any());
    }

    @Test
    void aTableTakenAtInsertSavesAVoidHandRefundsTheBetAndAnswersHandInProgress() {
        stack("9S", "KD", "8H", "5C");
        when(handRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.empty());
        when(handRepository.findByUserIdAndStatus(USER_ID, HandStatus.PLAYER_TURN)).thenReturn(Optional.empty());
        when(walletService.debit(USER_ID, 20, TransactionType.BLACKJACK_BET, BET_KEY)).thenReturn(movement(-20, 80));
        when(handRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("blackjack_hand_one_in_progress"))
                .thenAnswer(call -> withId(call.getArgument(0)));
        when(walletService.credit(USER_ID, 20, TransactionType.BLACKJACK_PAYOUT, PAYOUT_KEY))
                .thenReturn(movement(20, 100));

        assertThrows(HandInProgressException.class, () -> service.deal(USER_ID, Bet.TWENTY, KEY));

        ArgumentCaptor<BlackjackHand> saved = ArgumentCaptor.forClass(BlackjackHand.class);
        verify(handRepository, times(3)).saveAndFlush(saved.capture());
        BlackjackHand voided = saved.getAllValues().get(1);
        assertEquals(HandStatus.VOID, voided.getStatus());
        assertEquals(KEY, voided.getIdempotencyKey());
        assertEquals(Bet.TWENTY, voided.getBet());
        verify(walletService).credit(USER_ID, 20, TransactionType.BLACKJACK_PAYOUT, PAYOUT_KEY);
        assertTrue(saved.getAllValues().get(2).isSettled());
    }

    @Test
    void aKeyClashAtInsertReturnsTheHandOfTheOtherRequest() {
        stack("9S", "KD", "8H", "5C");
        BlackjackHand other = inPlay(Bet.TWENTY, KEY);
        when(handRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.empty(), Optional.of(other));
        when(handRepository.findByUserIdAndStatus(USER_ID, HandStatus.PLAYER_TURN)).thenReturn(Optional.empty());
        when(walletService.debit(USER_ID, 20, TransactionType.BLACKJACK_BET, BET_KEY)).thenReturn(movement(-20, 80));
        when(handRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("blackjack_hand_user_idempotency_key"));
        when(walletService.getBalance(USER_ID)).thenReturn(80L);

        HandResponse response = service.deal(USER_ID, Bet.TWENTY, KEY);

        assertEquals(other.getId(), response.hand().id());
        verify(walletService, never()).credit(any(), anyLong(), any(), any());
    }

    @Test
    void aViolationOfAnotherConstraintPropagatesInsteadOfPassingForATakenTable() {
        stack("9S", "KD", "8H", "5C");
        when(handRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.empty());
        when(handRepository.findByUserIdAndStatus(USER_ID, HandStatus.PLAYER_TURN)).thenReturn(Optional.empty());
        when(walletService.debit(USER_ID, 20, TransactionType.BLACKJACK_BET, BET_KEY)).thenReturn(movement(-20, 80));
        when(handRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("blackjack_hand_bet_check"));

        assertThrows(DataIntegrityViolationException.class, () -> service.deal(USER_ID, Bet.TWENTY, KEY));

        verify(handRepository, times(1)).saveAndFlush(any());
        verify(walletService, never()).credit(any(), anyLong(), any(), any());
    }

    @Test
    void aKeyViolationWithoutAWinnerPropagatesInsteadOfVoidingTheHand() {
        stack("9S", "KD", "8H", "5C");
        when(handRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.empty());
        when(handRepository.findByUserIdAndStatus(USER_ID, HandStatus.PLAYER_TURN)).thenReturn(Optional.empty());
        when(walletService.debit(USER_ID, 20, TransactionType.BLACKJACK_BET, BET_KEY)).thenReturn(movement(-20, 80));
        when(handRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("blackjack_hand_user_idempotency_key"));

        assertThrows(DataIntegrityViolationException.class, () -> service.deal(USER_ID, Bet.TWENTY, KEY));

        verify(handRepository, times(1)).saveAndFlush(any());
        verify(walletService, never()).credit(any(), anyLong(), any(), any());
    }

    @Test
    void theConstraintNameIsFoundInTheRootCauseOfTheViolation() {
        stack("9S", "KD", "8H", "5C");
        when(handRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.empty());
        when(handRepository.findByUserIdAndStatus(USER_ID, HandStatus.PLAYER_TURN)).thenReturn(Optional.empty());
        when(walletService.debit(USER_ID, 20, TransactionType.BLACKJACK_BET, BET_KEY)).thenReturn(movement(-20, 80));
        when(handRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("could not execute statement",
                        new IllegalStateException("duplicate key value violates unique constraint "
                                + "\"blackjack_hand_one_in_progress\"")))
                .thenAnswer(call -> withId(call.getArgument(0)));
        when(walletService.credit(USER_ID, 20, TransactionType.BLACKJACK_PAYOUT, PAYOUT_KEY))
                .thenReturn(movement(20, 100));

        assertThrows(HandInProgressException.class, () -> service.deal(USER_ID, Bet.TWENTY, KEY));

        verify(walletService).credit(USER_ID, 20, TransactionType.BLACKJACK_PAYOUT, PAYOUT_KEY);
    }

    @Test
    void aKeyClashWhileVoidingTheHandReturnsTheHandOfTheOtherRequest() {
        stack("9S", "KD", "8H", "5C");
        BlackjackHand other = inPlay(Bet.TWENTY, KEY);
        when(handRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.of(other));
        when(handRepository.findByUserIdAndStatus(USER_ID, HandStatus.PLAYER_TURN)).thenReturn(Optional.empty());
        when(walletService.debit(USER_ID, 20, TransactionType.BLACKJACK_BET, BET_KEY)).thenReturn(movement(-20, 80));
        when(handRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("blackjack_hand_one_in_progress"))
                .thenThrow(new DataIntegrityViolationException("blackjack_hand_user_idempotency_key"));
        when(walletService.getBalance(USER_ID)).thenReturn(80L);

        HandResponse response = service.deal(USER_ID, Bet.TWENTY, KEY);

        assertEquals(other.getId(), response.hand().id());
        verify(walletService, never()).credit(any(), anyLong(), any(), any());
    }

    @Test
    void aClashWhileVoidingWithNoHandToBeFoundPropagates() {
        stack("9S", "KD", "8H", "5C");
        when(handRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.empty());
        when(handRepository.findByUserIdAndStatus(USER_ID, HandStatus.PLAYER_TURN)).thenReturn(Optional.empty());
        when(walletService.debit(USER_ID, 20, TransactionType.BLACKJACK_BET, BET_KEY)).thenReturn(movement(-20, 80));
        when(handRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("blackjack_hand_one_in_progress"))
                .thenThrow(new DataIntegrityViolationException("blackjack_hand_user_idempotency_key"));

        assertThrows(DataIntegrityViolationException.class, () -> service.deal(USER_ID, Bet.TWENTY, KEY));

        verify(walletService, never()).credit(any(), anyLong(), any(), any());
    }

    @Test
    void hittingUntilBustSettlesALossWithoutCredit() {
        BlackjackHand hand = inPlay(Bet.TWENTY, KEY, "TS", "KD", "6H", "5C", "KC");
        when(handRepository.findByIdAndUserId(HAND_ID, USER_ID)).thenReturn(Optional.of(hand));
        when(handRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(walletService.getBalance(USER_ID)).thenReturn(80L);

        HandResponse response = service.hit(USER_ID, HAND_ID);

        assertEquals(HandStatus.FINISHED, response.hand().status());
        assertEquals(Outcome.LOSE, response.hand().outcome());
        assertEquals(26, response.hand().playerTotal());
        assertEquals(2, response.hand().dealerCards().size());
        verify(walletService, never()).credit(any(), anyLong(), any(), any());
        assertTrue(hand.isSettled());
    }

    @Test
    void hittingWithoutBustingKeepsTheHandInPlay() {
        BlackjackHand hand = inPlay(Bet.TWENTY, KEY, "5S", "KD", "6H", "5C", "2C");
        when(handRepository.findByIdAndUserId(HAND_ID, USER_ID)).thenReturn(Optional.of(hand));
        when(handRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(walletService.getBalance(USER_ID)).thenReturn(80L);

        HandResponse response = service.hit(USER_ID, HAND_ID);

        assertEquals(HandStatus.PLAYER_TURN, response.hand().status());
        assertEquals(3, response.hand().playerCards().size());
        assertEquals(1, response.hand().dealerCards().size());
        verify(handRepository).saveAndFlush(hand);
        assertFalse(hand.isSettled());
    }

    @Test
    void standingMakesTheDealerPlayAndAWinCreditsDouble() {
        BlackjackHand hand = inPlay(Bet.TWENTY, KEY, "TS", "6D", "9H", "5C", "4D", "3H");
        when(handRepository.findByIdAndUserId(HAND_ID, USER_ID)).thenReturn(Optional.of(hand));
        when(handRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(walletService.credit(USER_ID, 40, TransactionType.BLACKJACK_PAYOUT, PAYOUT_KEY))
                .thenReturn(movement(40, 120));
        when(walletService.getBalance(USER_ID)).thenReturn(120L);

        HandResponse response = service.stand(USER_ID, HAND_ID);

        assertEquals(Outcome.WIN, response.hand().outcome());
        assertEquals(40L, response.hand().payout());
        assertEquals(4, response.hand().dealerCards().size());
        assertEquals(120, response.balance());
        verify(walletService).credit(USER_ID, 40, TransactionType.BLACKJACK_PAYOUT, PAYOUT_KEY);
        assertTrue(hand.isSettled());
    }

    @Test
    void standingOnALosingHandCreditsNothing() {
        BlackjackHand hand = inPlay(Bet.TEN, KEY, "TS", "TD", "7H", "9C");
        when(handRepository.findByIdAndUserId(HAND_ID, USER_ID)).thenReturn(Optional.of(hand));
        when(handRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(walletService.getBalance(USER_ID)).thenReturn(90L);

        HandResponse response = service.stand(USER_ID, HAND_ID);

        assertEquals(Outcome.LOSE, response.hand().outcome());
        verify(walletService, never()).credit(any(), anyLong(), any(), any());
        assertTrue(hand.isSettled());
    }

    @Test
    void hitOrStandOnAFinishedHandIsRejected() {
        BlackjackHand finished = withId(new BlackjackHand(USER_ID, KEY, Bet.TEN,
                BlackjackRules.deal(StackedDeck.startingWith("AS", "9D", "KS", "8D")), NOW));
        when(handRepository.findByIdAndUserId(HAND_ID, USER_ID)).thenReturn(Optional.of(finished));

        assertThrows(HandFinishedException.class, () -> service.hit(USER_ID, HAND_ID));
        assertThrows(HandFinishedException.class, () -> service.stand(USER_ID, HAND_ID));

        verify(handRepository, never()).saveAndFlush(any());
    }

    @Test
    void aHandOfAnotherMemberOrAVoidOneIsNotFound() {
        when(handRepository.findByIdAndUserId(HAND_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(HandNotFoundException.class, () -> service.hit(USER_ID, HAND_ID));
        assertThrows(HandNotFoundException.class, () -> service.stand(USER_ID, HAND_ID));

        BlackjackHand voided = withId(BlackjackHand.voided(USER_ID, KEY, Bet.TEN, NOW));
        when(handRepository.findByIdAndUserId(HAND_ID, USER_ID)).thenReturn(Optional.of(voided));

        assertThrows(HandNotFoundException.class, () -> service.hit(USER_ID, HAND_ID));
    }

    @Test
    void anOptimisticClashReturnsTheHandAsItIsNow() {
        BlackjackHand stale = inPlay(Bet.TWENTY, KEY, "5S", "KD", "6H", "5C", "2C");
        BlackjackHand current = inPlay(Bet.TWENTY, KEY, "5S", "KD", "6H", "5C", "2C");
        current.play(BlackjackRules.hit(current.table()), NOW);
        when(handRepository.findByIdAndUserId(HAND_ID, USER_ID)).thenReturn(Optional.of(stale), Optional.of(current));
        when(handRepository.saveAndFlush(stale))
                .thenThrow(new ObjectOptimisticLockingFailureException(BlackjackHand.class, HAND_ID));
        when(walletService.getBalance(USER_ID)).thenReturn(80L);

        HandResponse response = service.hit(USER_ID, HAND_ID);

        assertEquals(3, response.hand().playerCards().size());
    }

    @Test
    void theTableShowsTheHandInProgressTheBalanceAndTheThreeBets() {
        BlackjackHand hand = inPlay(Bet.TWENTY, KEY);
        when(handRepository.findByUserIdAndStatus(USER_ID, HandStatus.PLAYER_TURN)).thenReturn(Optional.of(hand));
        when(walletService.getBalance(USER_ID)).thenReturn(80L);

        BlackjackResponse response = service.table(USER_ID);

        assertEquals(hand.getId(), response.hand().id());
        assertEquals(80, response.balance());
        assertEquals(List.of(Bet.TEN, Bet.TWENTY, Bet.FIFTY), response.bets().stream().map(bet -> bet.code()).toList());
        assertEquals(List.of(10L, 20L, 50L), response.bets().stream().map(bet -> bet.chips()).toList());
    }

    @Test
    void theTableHasNoHandWhenNoneIsInProgress() {
        when(handRepository.findByUserIdAndStatus(USER_ID, HandStatus.PLAYER_TURN)).thenReturn(Optional.empty());
        when(walletService.getBalance(USER_ID)).thenReturn(100L);

        BlackjackResponse response = service.table(USER_ID);

        assertNull(response.hand());
        assertEquals(100, response.balance());
        assertSame(response.bets().get(0).code(), Bet.TEN);
    }

    @Test
    void whenTheBetSumsMatchNoMovementsAreListed() {
        when(walletService.spentOn(USER_ID, TransactionType.BLACKJACK_BET)).thenReturn(30L);
        when(handRepository.sumBetByUserId(USER_ID)).thenReturn(30L);
        when(handRepository.findByUserIdAndStatus(USER_ID, HandStatus.PLAYER_TURN)).thenReturn(Optional.empty());
        when(walletService.getBalance(USER_ID)).thenReturn(70L);

        service.table(USER_ID);

        verify(walletService, never()).movementsOf(any(), any());
    }

    @Test
    void anOrphanBetIsAdoptedAsADealtHandWithItsKeyAndBet() {
        stack("9S", "KD", "8H", "5C");
        when(walletService.spentOn(USER_ID, TransactionType.BLACKJACK_BET)).thenReturn(20L);
        when(handRepository.sumBetByUserId(USER_ID)).thenReturn(0L);
        when(walletService.movementsOf(USER_ID, TransactionType.BLACKJACK_BET))
                .thenReturn(List.of(orphanBet(20, KEY)));
        when(handRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.empty());
        when(handRepository.saveAndFlush(any())).thenAnswer(call -> withId(call.getArgument(0)));
        when(handRepository.findByUserIdAndStatus(USER_ID, HandStatus.PLAYER_TURN)).thenReturn(Optional.empty());
        when(walletService.getBalance(USER_ID)).thenReturn(80L);

        service.table(USER_ID);

        ArgumentCaptor<BlackjackHand> saved = ArgumentCaptor.forClass(BlackjackHand.class);
        verify(handRepository).saveAndFlush(saved.capture());
        assertEquals(KEY, saved.getValue().getIdempotencyKey());
        assertEquals(Bet.TWENTY, saved.getValue().getBet());
        assertEquals(HandStatus.PLAYER_TURN, saved.getValue().getStatus());
        verify(walletService, never()).debit(any(), anyLong(), any(), any());
    }

    @Test
    void anOrphanBetWithTheTableTakenBecomesAVoidHandAndIsRefunded() {
        stack("9S", "KD", "8H", "5C");
        when(walletService.spentOn(USER_ID, TransactionType.BLACKJACK_BET)).thenReturn(20L);
        when(handRepository.sumBetByUserId(USER_ID)).thenReturn(0L);
        when(walletService.movementsOf(USER_ID, TransactionType.BLACKJACK_BET))
                .thenReturn(List.of(orphanBet(20, KEY)));
        when(handRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY)).thenReturn(Optional.empty());
        when(handRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("blackjack_hand_one_in_progress"))
                .thenAnswer(call -> withId(call.getArgument(0)));
        when(walletService.credit(USER_ID, 20, TransactionType.BLACKJACK_PAYOUT, PAYOUT_KEY))
                .thenReturn(movement(20, 100));
        when(handRepository.findByUserIdAndStatus(USER_ID, HandStatus.PLAYER_TURN)).thenReturn(Optional.empty());
        when(walletService.getBalance(USER_ID)).thenReturn(100L);

        service.table(USER_ID);

        ArgumentCaptor<BlackjackHand> saved = ArgumentCaptor.forClass(BlackjackHand.class);
        verify(handRepository, times(3)).saveAndFlush(saved.capture());
        assertEquals(HandStatus.VOID, saved.getAllValues().get(1).getStatus());
        verify(walletService).credit(USER_ID, 20, TransactionType.BLACKJACK_PAYOUT, PAYOUT_KEY);
    }

    @Test
    void aBetThatAlreadyHasItsHandIsNotAdoptedAgain() {
        when(walletService.spentOn(USER_ID, TransactionType.BLACKJACK_BET)).thenReturn(40L);
        when(handRepository.sumBetByUserId(USER_ID)).thenReturn(20L);
        when(walletService.movementsOf(USER_ID, TransactionType.BLACKJACK_BET))
                .thenReturn(List.of(orphanBet(20, KEY)));
        when(handRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.of(inPlay(Bet.TWENTY, KEY)));
        when(handRepository.findByUserIdAndStatus(USER_ID, HandStatus.PLAYER_TURN)).thenReturn(Optional.empty());
        when(walletService.getBalance(USER_ID)).thenReturn(60L);

        service.table(USER_ID);

        verify(handRepository, never()).saveAndFlush(any());
    }

    @Test
    void aFinishedHandWithoutSettledAtIsPaidAndThenSettled() {
        BlackjackHand won = won();
        when(handRepository.findUnsettledByUserId(USER_ID)).thenReturn(List.of(won));
        when(walletService.credit(USER_ID, 40, TransactionType.BLACKJACK_PAYOUT, PAYOUT_KEY))
                .thenReturn(movement(40, 120));
        when(handRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(handRepository.findByUserIdAndStatus(USER_ID, HandStatus.PLAYER_TURN)).thenReturn(Optional.empty());
        when(walletService.getBalance(USER_ID)).thenReturn(120L);

        service.table(USER_ID);

        InOrder order = inOrder(walletService, handRepository);
        order.verify(walletService).credit(USER_ID, 40, TransactionType.BLACKJACK_PAYOUT, PAYOUT_KEY);
        order.verify(handRepository).saveAndFlush(won);
        assertTrue(won.isSettled());
    }

    @Test
    void whenThePayoutAlreadyExistsTheHandIsSettledOnceWithoutPayingTwice() {
        BlackjackHand won = won();
        TokenTransaction previous = new TokenTransaction(UUID.randomUUID(), 40, TransactionType.BLACKJACK_PAYOUT, 120,
                PAYOUT_KEY, NOW);
        when(handRepository.findUnsettledByUserId(USER_ID)).thenReturn(List.of(won));
        when(walletService.credit(USER_ID, 40, TransactionType.BLACKJACK_PAYOUT, PAYOUT_KEY)).thenReturn(previous);
        when(handRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(handRepository.findByUserIdAndStatus(USER_ID, HandStatus.PLAYER_TURN)).thenReturn(Optional.empty());
        when(walletService.getBalance(USER_ID)).thenReturn(120L);

        service.table(USER_ID);

        verify(walletService, times(1)).credit(any(), anyLong(), any(), any());
        verify(handRepository, times(1)).saveAndFlush(won);
        assertTrue(won.isSettled());
    }

    @Test
    void anUnsettledLossIsSettledWithoutACredit() {
        BlackjackHand lost = inPlay(Bet.TEN, KEY, "TS", "TD", "7H", "9C");
        lost.play(BlackjackRules.stand(lost.table()), NOW);
        when(handRepository.findUnsettledByUserId(USER_ID)).thenReturn(List.of(lost));
        when(handRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(handRepository.findByUserIdAndStatus(USER_ID, HandStatus.PLAYER_TURN)).thenReturn(Optional.empty());
        when(walletService.getBalance(USER_ID)).thenReturn(90L);

        service.table(USER_ID);

        verify(walletService, never()).credit(any(), anyLong(), any(), any());
        assertTrue(lost.isSettled());
    }

    @Test
    void anUnsettledVoidHandIsRefundedInFull() {
        BlackjackHand voided = withId(BlackjackHand.voided(USER_ID, KEY, Bet.FIFTY, NOW));
        when(handRepository.findUnsettledByUserId(USER_ID)).thenReturn(List.of(voided));
        when(walletService.credit(USER_ID, 50, TransactionType.BLACKJACK_PAYOUT, PAYOUT_KEY))
                .thenReturn(movement(50, 100));
        when(handRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(handRepository.findByUserIdAndStatus(USER_ID, HandStatus.PLAYER_TURN)).thenReturn(Optional.empty());
        when(walletService.getBalance(USER_ID)).thenReturn(100L);

        service.table(USER_ID);

        verify(walletService).credit(USER_ID, 50, TransactionType.BLACKJACK_PAYOUT, PAYOUT_KEY);
        assertTrue(voided.isSettled());
    }

    @Test
    void anOptimisticClashWhileSettlingRereadsInsteadOfFailing() {
        BlackjackHand won = won();
        BlackjackHand alreadySettled = won();
        alreadySettled.settle(NOW);
        when(handRepository.findUnsettledByUserId(USER_ID)).thenReturn(List.of(won));
        when(walletService.credit(USER_ID, 40, TransactionType.BLACKJACK_PAYOUT, PAYOUT_KEY))
                .thenReturn(movement(40, 120));
        when(handRepository.saveAndFlush(won))
                .thenThrow(new ObjectOptimisticLockingFailureException(BlackjackHand.class, HAND_ID));
        when(handRepository.findByIdAndUserId(HAND_ID, USER_ID)).thenReturn(Optional.of(alreadySettled));
        when(handRepository.findByUserIdAndStatus(USER_ID, HandStatus.PLAYER_TURN)).thenReturn(Optional.empty());
        when(walletService.getBalance(USER_ID)).thenReturn(120L);

        BlackjackResponse response = service.table(USER_ID);

        assertNull(response.hand());
        verify(handRepository).findByIdAndUserId(HAND_ID, USER_ID);
    }

    @Test
    void aWalletConflictWhilePayingLeavesTheHandUnsettledForTheNextCall() {
        BlackjackHand won = won();
        when(handRepository.findUnsettledByUserId(USER_ID)).thenReturn(List.of(won));
        when(walletService.credit(USER_ID, 40, TransactionType.BLACKJACK_PAYOUT, PAYOUT_KEY))
                .thenThrow(new WalletConflictException());

        assertThrows(WalletConflictException.class, () -> service.table(USER_ID));

        assertFalse(won.isSettled());
        verify(handRepository, never()).saveAndFlush(any());
    }

    @Test
    void tableReconcilesBeforeItAnswers() {
        when(handRepository.findByUserIdAndStatus(USER_ID, HandStatus.PLAYER_TURN)).thenReturn(Optional.empty());
        when(walletService.getBalance(USER_ID)).thenReturn(100L);

        service.table(USER_ID);

        InOrder order = inOrder(walletService, handRepository);
        order.verify(handRepository).findUnsettledByUserId(USER_ID);
        order.verify(handRepository).findByUserIdAndStatus(USER_ID, HandStatus.PLAYER_TURN);
    }

    @Test
    void dealReconcilesBeforeItLooksForAReplay() {
        when(handRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.of(inPlay(Bet.TWENTY, KEY)));
        when(walletService.getBalance(USER_ID)).thenReturn(80L);

        service.deal(USER_ID, Bet.TWENTY, KEY);

        InOrder order = inOrder(walletService, handRepository);
        order.verify(handRepository).findUnsettledByUserId(USER_ID);
        order.verify(handRepository).findByUserIdAndIdempotencyKey(USER_ID, KEY);
    }

    @Test
    void hitAndStandReconcileBeforeTheyLoadTheHand() {
        when(handRepository.findByIdAndUserId(HAND_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(HandNotFoundException.class, () -> service.hit(USER_ID, HAND_ID));
        assertThrows(HandNotFoundException.class, () -> service.stand(USER_ID, HAND_ID));

        InOrder order = inOrder(handRepository);
        order.verify(handRepository).findUnsettledByUserId(USER_ID);
        order.verify(handRepository).findByIdAndUserId(HAND_ID, USER_ID);
        order.verify(handRepository).findUnsettledByUserId(USER_ID);
        order.verify(handRepository).findByIdAndUserId(HAND_ID, USER_ID);
    }

    @Test
    void aReplayAfterACrashBetweenDebitAndInsertReturnsTheAdoptedHandWithoutASecondDebit() {
        stack("9S", "KD", "8H", "5C");
        BlackjackHand adopted = inPlay(Bet.TWENTY, KEY);
        when(walletService.spentOn(USER_ID, TransactionType.BLACKJACK_BET)).thenReturn(20L);
        when(handRepository.sumBetByUserId(USER_ID)).thenReturn(0L);
        when(walletService.movementsOf(USER_ID, TransactionType.BLACKJACK_BET))
                .thenReturn(List.of(orphanBet(20, KEY)));
        when(handRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.empty(), Optional.of(adopted));
        when(handRepository.saveAndFlush(any())).thenAnswer(call -> withId(call.getArgument(0)));
        when(walletService.getBalance(USER_ID)).thenReturn(80L);

        HandResponse response = service.deal(USER_ID, Bet.TWENTY, KEY);

        assertEquals(adopted.getId(), response.hand().id());
        verify(walletService, never()).debit(any(), anyLong(), any(), any());
        verify(handRepository, times(1)).saveAndFlush(any());
    }

    private BlackjackHand won() {
        BlackjackHand hand = inPlay(Bet.TWENTY, KEY, "TS", "6D", "9H", "5C", "4D", "3H");
        hand.play(BlackjackRules.stand(hand.table()), NOW);
        return hand;
    }

    private static TokenTransaction orphanBet(long chips, UUID key) {
        return new TokenTransaction(UUID.randomUUID(), -chips, TransactionType.BLACKJACK_BET, 100 - chips,
                "blackjack-bet:" + key, NOW);
    }

    private void stack(String... codes) {
        nextDeck = StackedDeck.startingWith(codes);
    }

    private static BlackjackHand inPlay(Bet bet, UUID key, String... codes) {
        String[] deck = codes.length == 0 ? new String[] {"9S", "KD", "8H", "5C"} : codes;
        return withId(new BlackjackHand(USER_ID, key, bet,
                BlackjackRules.deal(StackedDeck.startingWith(deck)), NOW));
    }

    private static BlackjackHand withId(BlackjackHand hand) {
        ReflectionTestUtils.setField(hand, "id", HAND_ID);
        return hand;
    }

    private static TokenTransaction movement(long amount, long balanceAfter) {
        TransactionType type = amount < 0 ? TransactionType.BLACKJACK_BET : TransactionType.BLACKJACK_PAYOUT;
        return new TokenTransaction(UUID.randomUUID(), amount, type, balanceAfter, null, NOW);
    }
}

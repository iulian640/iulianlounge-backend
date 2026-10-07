package com.iulianlounge.backend.service;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import com.iulianlounge.backend.domain.Bet;
import com.iulianlounge.backend.domain.BlackjackHand;
import com.iulianlounge.backend.domain.BlackjackRules;
import com.iulianlounge.backend.domain.BlackjackTable;
import com.iulianlounge.backend.domain.Card;
import com.iulianlounge.backend.domain.HandStatus;
import com.iulianlounge.backend.domain.Shuffler;
import com.iulianlounge.backend.domain.TransactionType;
import com.iulianlounge.backend.dto.BetOption;
import com.iulianlounge.backend.dto.BlackjackResponse;
import com.iulianlounge.backend.dto.HandResponse;
import com.iulianlounge.backend.dto.HandView;
import com.iulianlounge.backend.exception.HandFinishedException;
import com.iulianlounge.backend.exception.HandInProgressException;
import com.iulianlounge.backend.exception.HandNotFoundException;
import com.iulianlounge.backend.exception.IdempotencyMismatchException;
import com.iulianlounge.backend.repository.BlackjackHandRepository;

@Service
public class BlackjackService {

    static final String BET_KEY_PREFIX = "blackjack-bet:";
    static final String PAYOUT_KEY_PREFIX = "blackjack-payout:";

    private final WalletService walletService;
    private final BlackjackHandRepository handRepository;
    private final Shuffler shuffler;
    private final Clock clock;

    public BlackjackService(WalletService walletService, BlackjackHandRepository handRepository, Shuffler shuffler,
            Clock clock) {
        this.walletService = walletService;
        this.handRepository = handRepository;
        this.shuffler = shuffler;
        this.clock = clock;
    }

    public BlackjackResponse table(UUID userId) {
        HandView hand = handRepository.findByUserIdAndStatus(userId, HandStatus.PLAYER_TURN)
                .map(HandView::of)
                .orElse(null);
        return new BlackjackResponse(hand, walletService.getBalance(userId), BetOption.all());
    }

    public HandResponse deal(UUID userId, Bet bet, UUID idempotencyKey) {
        Optional<BlackjackHand> replay = handRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (replay.isPresent()) {
            return answerReplay(userId, bet, replay.get());
        }
        if (handRepository.findByUserIdAndStatus(userId, HandStatus.PLAYER_TURN).isPresent()) {
            throw new HandInProgressException();
        }
        walletService.debit(userId, bet.chips(), TransactionType.BLACKJACK_BET, BET_KEY_PREFIX + idempotencyKey);
        BlackjackHand hand = seatDealtHand(userId, idempotencyKey, bet)
                .filter(seated -> seated.getStatus() != HandStatus.VOID)
                .orElseThrow(HandInProgressException::new);
        return respond(userId, settleIfFinished(hand));
    }

    public HandResponse hit(UUID userId, UUID handId) {
        return play(userId, handId, BlackjackRules::hit);
    }

    public HandResponse stand(UUID userId, UUID handId) {
        return play(userId, handId, BlackjackRules::stand);
    }

    private HandResponse answerReplay(UUID userId, Bet bet, BlackjackHand existing) {
        if (existing.getBet() != bet) {
            throw new IdempotencyMismatchException();
        }
        if (existing.getStatus() == HandStatus.VOID) {
            throw new HandInProgressException();
        }
        return respond(userId, existing);
    }

    private HandResponse play(UUID userId, UUID handId, UnaryOperator<BlackjackTable> move) {
        BlackjackHand hand = find(userId, handId);
        if (hand.getStatus() != HandStatus.PLAYER_TURN) {
            throw new HandFinishedException();
        }
        hand.play(move.apply(hand.table()), clock.instant());
        BlackjackHand saved;
        try {
            saved = handRepository.saveAndFlush(hand);
        } catch (OptimisticLockingFailureException clash) {
            return respond(userId, find(userId, handId));
        }
        return respond(userId, settleIfFinished(saved));
    }

    private Optional<BlackjackHand> seatDealtHand(UUID userId, UUID idempotencyKey, Bet bet) {
        BlackjackTable table = BlackjackRules.deal(shuffler.shuffle(Card.deck()));
        try {
            return Optional.of(handRepository.saveAndFlush(
                    new BlackjackHand(userId, idempotencyKey, bet, table, clock.instant())));
        } catch (DataIntegrityViolationException clash) {
            Optional<BlackjackHand> winner = handRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (winner.isPresent()) {
                return winner;
            }
            settle(handRepository.saveAndFlush(BlackjackHand.voided(userId, idempotencyKey, bet, clock.instant())));
            return Optional.empty();
        }
    }

    private BlackjackHand settleIfFinished(BlackjackHand hand) {
        return hand.getStatus() == HandStatus.FINISHED ? settle(hand) : hand;
    }

    private BlackjackHand settle(BlackjackHand hand) {
        if (hand.getPayout() > 0) {
            walletService.credit(hand.getUserId(), hand.getPayout(), TransactionType.BLACKJACK_PAYOUT,
                    PAYOUT_KEY_PREFIX + hand.getId());
        }
        hand.settle(clock.instant());
        try {
            return handRepository.saveAndFlush(hand);
        } catch (OptimisticLockingFailureException clash) {
            return handRepository.findByIdAndUserId(hand.getId(), hand.getUserId()).orElse(hand);
        }
    }

    private BlackjackHand find(UUID userId, UUID handId) {
        return handRepository.findByIdAndUserId(handId, userId)
                .filter(hand -> hand.getStatus() != HandStatus.VOID)
                .orElseThrow(HandNotFoundException::new);
    }

    private HandResponse respond(UUID userId, BlackjackHand hand) {
        return new HandResponse(HandView.of(hand), walletService.getBalance(userId));
    }
}

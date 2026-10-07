package com.iulianlounge.backend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.iulianlounge.backend.domain.BarmanSituation;
import com.iulianlounge.backend.domain.Drink;
import com.iulianlounge.backend.domain.Rank;
import com.iulianlounge.backend.domain.TokenTransaction;
import com.iulianlounge.backend.domain.TransactionType;
import com.iulianlounge.backend.dto.BarResponse;
import com.iulianlounge.backend.dto.DrinkResponse;
import com.iulianlounge.backend.dto.HouseCreditResponse;
import com.iulianlounge.backend.dto.OrderResponse;
import com.iulianlounge.backend.exception.HouseCreditNotNeededException;
import com.iulianlounge.backend.exception.HouseCreditUsedTodayException;

@Service
public class BarService {

    public static final long HOUSE_CREDIT = 50;

    static final ZoneId CLUB_ZONE = ZoneId.of("Europe/Madrid");

    private final WalletService walletService;
    private final Clock clock;

    public BarService(WalletService walletService, Clock clock) {
        this.walletService = walletService;
        this.clock = clock;
    }

    public BarResponse menu(UUID userId) {
        long balance = walletService.getBalance(userId);
        Rank rank = rankOf(userId);
        boolean creditAvailable = creditAvailable(userId, balance);
        BarmanSituation situation = isBroke(balance) ? brokeSituation(creditAvailable) : BarmanSituation.GREETING;
        return new BarResponse(DrinkResponse.menu(), balance, rank, creditAvailable, situation.lineFor(rank));
    }

    public OrderResponse order(UUID userId, Drink drink, UUID idempotencyKey) {
        TokenTransaction movement = walletService.debit(userId, drink.price(), TransactionType.BAR_ORDER,
                "order:" + idempotencyKey);
        long spent = walletService.spentOn(userId, TransactionType.BAR_ORDER);
        Rank after = Rank.forSpent(spent);
        boolean promoted = after != Rank.forSpent(spent - drink.price());
        long balance = movement.getBalanceAfter();
        boolean creditAvailable = creditAvailable(userId, balance);
        BarmanSituation situation = orderSituation(promoted, balance, creditAvailable);
        return new OrderResponse(drink, drink.price(), balance, after, promoted, creditAvailable,
                situation.lineFor(after));
    }

    public HouseCreditResponse houseCredit(UUID userId) {
        if (!isBroke(walletService.getBalance(userId))) {
            throw new HouseCreditNotNeededException();
        }
        String key = houseCreditKey();
        if (walletService.hasMovement(userId, key)) {
            throw new HouseCreditUsedTodayException();
        }
        TokenTransaction credit = walletService.creditIf(userId, HOUSE_CREDIT, TransactionType.HOUSE_CREDIT, key,
                BarService::isBroke).orElseThrow(HouseCreditNotNeededException::new);
        Rank rank = rankOf(userId);
        return new HouseCreditResponse(HOUSE_CREDIT, credit.getBalanceAfter(), rank,
                BarmanSituation.HOUSE_CREDIT.lineFor(rank));
    }

    public Rank rankOf(UUID userId) {
        return Rank.forSpent(walletService.spentOn(userId, TransactionType.BAR_ORDER));
    }

    String houseCreditKey() {
        return "house-credit:" + LocalDate.now(clock.withZone(CLUB_ZONE));
    }

    private boolean creditAvailable(UUID userId, long balance) {
        return isBroke(balance) && !walletService.hasMovement(userId, houseCreditKey());
    }

    private static BarmanSituation orderSituation(boolean promoted, long balance, boolean creditAvailable) {
        if (promoted) {
            return BarmanSituation.PROMOTION;
        }
        if (isBroke(balance)) {
            return brokeSituation(creditAvailable);
        }
        return BarmanSituation.SERVE;
    }

    private static boolean isBroke(long balance) {
        return balance < Drink.cheapestPrice();
    }

    private static BarmanSituation brokeSituation(boolean creditAvailable) {
        return creditAvailable ? BarmanSituation.BROKE : BarmanSituation.NO_CREDIT;
    }
}

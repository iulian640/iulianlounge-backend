package com.iulianlounge.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.iulianlounge.backend.domain.Drink;
import com.iulianlounge.backend.domain.Rank;
import com.iulianlounge.backend.domain.TokenTransaction;
import com.iulianlounge.backend.domain.TransactionType;
import com.iulianlounge.backend.dto.BarResponse;
import com.iulianlounge.backend.dto.HouseCreditResponse;
import com.iulianlounge.backend.dto.OrderResponse;
import com.iulianlounge.backend.exception.HouseCreditNotNeededException;
import com.iulianlounge.backend.exception.HouseCreditUsedTodayException;
import com.iulianlounge.backend.exception.InsufficientFundsException;

@ExtendWith(MockitoExtension.class)
class BarServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant NIGHT_IN_MADRID = Instant.parse("2026-10-07T22:30:00Z");
    private static final String TODAYS_CREDIT = "house-credit:2026-10-08";

    @Mock
    private WalletService walletService;

    private BarService barService;

    @BeforeEach
    void setUp() {
        barService = new BarService(walletService, Clock.fixed(NIGHT_IN_MADRID, ZoneOffset.UTC));
    }

    @Test
    void theMenuListsTheDrinksAndTheBarmanGreetsByRank() {
        when(walletService.getBalance(USER_ID)).thenReturn(60L);
        when(walletService.spentOn(USER_ID, TransactionType.BAR_ORDER)).thenReturn(40L);

        BarResponse bar = barService.menu(USER_ID);

        assertEquals(Drink.values().length, bar.drinks().size());
        assertEquals(60, bar.balance());
        assertEquals(Rank.HABITUAL, bar.rank());
        assertFalse(bar.creditAvailable());
        assertEquals("barman.greeting.habitual", bar.line());
    }

    @Test
    void aBrokeMemberIsOfferedTheHouseCredit() {
        when(walletService.getBalance(USER_ID)).thenReturn(3L);
        when(walletService.spentOn(USER_ID, TransactionType.BAR_ORDER)).thenReturn(97L);
        when(walletService.hasMovement(USER_ID, TODAYS_CREDIT)).thenReturn(false);

        BarResponse bar = barService.menu(USER_ID);

        assertTrue(bar.creditAvailable());
        assertEquals("barman.broke.habitual", bar.line());
    }

    @Test
    void onceTheHouseHasCoveredYouTodayTheBarmanSaysNo() {
        when(walletService.getBalance(USER_ID)).thenReturn(0L);
        when(walletService.spentOn(USER_ID, TransactionType.BAR_ORDER)).thenReturn(100L);
        when(walletService.hasMovement(USER_ID, TODAYS_CREDIT)).thenReturn(true);

        BarResponse bar = barService.menu(USER_ID);

        assertFalse(bar.creditAvailable());
        assertEquals("barman.no_credit", bar.line());
    }

    @Test
    void anOrderIsChargedToTheWalletUnderItsIdempotencyKey() {
        UUID key = UUID.randomUUID();
        when(walletService.spentOn(USER_ID, TransactionType.BAR_ORDER)).thenReturn(0L, 40L);
        when(walletService.getBalance(USER_ID)).thenReturn(60L);

        OrderResponse order = barService.order(USER_ID, Drink.FRENCH_75, key);

        verify(walletService).debit(USER_ID, 40, TransactionType.BAR_ORDER, "order:" + key);
        assertEquals(Drink.FRENCH_75, order.drink());
        assertEquals(40, order.price());
        assertEquals(60, order.balance());
        assertEquals(Rank.HABITUAL, order.rank());
        assertTrue(order.promoted());
        assertEquals("barman.promotion.habitual", order.line());
    }

    @Test
    void anOrderWithoutPromotionGetsTheUsualService() {
        when(walletService.spentOn(USER_ID, TransactionType.BAR_ORDER)).thenReturn(30L, 40L);
        when(walletService.getBalance(USER_ID)).thenReturn(60L);

        OrderResponse order = barService.order(USER_ID, Drink.BEES_KNEES, UUID.randomUUID());

        assertFalse(order.promoted());
        assertFalse(order.creditAvailable());
        assertEquals("barman.serve.habitual", order.line());
    }

    @Test
    void anOrderThatEmptiesTheWalletOffersTheHouseCredit() {
        when(walletService.spentOn(USER_ID, TransactionType.BAR_ORDER)).thenReturn(120L, 125L);
        when(walletService.getBalance(USER_ID)).thenReturn(0L);
        when(walletService.hasMovement(USER_ID, TODAYS_CREDIT)).thenReturn(false);

        OrderResponse order = barService.order(USER_ID, Drink.BATHTUB_GIN, UUID.randomUUID());

        assertFalse(order.promoted());
        assertTrue(order.creditAvailable());
        assertEquals("barman.broke.confianza", order.line());
    }

    @Test
    void aPromotionIsAnnouncedEvenIfTheWalletIsLeftEmpty() {
        when(walletService.spentOn(USER_ID, TransactionType.BAR_ORDER)).thenReturn(95L, 100L);
        when(walletService.getBalance(USER_ID)).thenReturn(0L);
        when(walletService.hasMovement(USER_ID, TODAYS_CREDIT)).thenReturn(false);

        OrderResponse order = barService.order(USER_ID, Drink.BATHTUB_GIN, UUID.randomUUID());

        assertTrue(order.promoted());
        assertTrue(order.creditAvailable());
        assertEquals("barman.promotion.confianza", order.line());
    }

    @Test
    void anOrderWithoutEnoughChipsIsRejected() {
        UUID key = UUID.randomUUID();
        when(walletService.spentOn(USER_ID, TransactionType.BAR_ORDER)).thenReturn(0L);
        doThrow(new InsufficientFundsException())
                .when(walletService).debit(USER_ID, 40, TransactionType.BAR_ORDER, "order:" + key);

        assertThrows(InsufficientFundsException.class, () -> barService.order(USER_ID, Drink.FRENCH_75, key));
    }

    @Test
    void theHouseLendsFiftyChipsUnderTodaysKey() {
        when(walletService.getBalance(USER_ID)).thenReturn(3L);
        when(walletService.hasMovement(USER_ID, TODAYS_CREDIT)).thenReturn(false);
        when(walletService.creditIf(eq(USER_ID), eq(BarService.HOUSE_CREDIT), eq(TransactionType.HOUSE_CREDIT),
                eq(TODAYS_CREDIT), any(), any()))
                .thenReturn(new TokenTransaction(UUID.randomUUID(), 50, TransactionType.HOUSE_CREDIT, 53,
                        TODAYS_CREDIT, NIGHT_IN_MADRID));
        when(walletService.spentOn(USER_ID, TransactionType.BAR_ORDER)).thenReturn(97L);

        HouseCreditResponse credit = barService.houseCredit(USER_ID);

        assertEquals(50, credit.amount());
        assertEquals(53, credit.balance());
        assertEquals(Rank.HABITUAL, credit.rank());
        assertEquals("barman.house_credit", credit.line());
    }

    @Test
    void theHouseDoesNotLendToWhoeverCanStillPay() {
        when(walletService.getBalance(USER_ID)).thenReturn(5L);

        assertThrows(HouseCreditNotNeededException.class, () -> barService.houseCredit(USER_ID));
        verify(walletService, never()).creditIf(any(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void theHouseLendsOncePerClubDay() {
        when(walletService.getBalance(USER_ID)).thenReturn(0L);
        when(walletService.hasMovement(USER_ID, TODAYS_CREDIT)).thenReturn(true);

        assertThrows(HouseCreditUsedTodayException.class, () -> barService.houseCredit(USER_ID));
        verify(walletService, never()).creditIf(any(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void theRankComesFromWhatWasSpentAtTheBar() {
        when(walletService.spentOn(USER_ID, TransactionType.BAR_ORDER)).thenReturn(300L);

        assertEquals(Rank.SOCIO, barService.rankOf(USER_ID));
    }

    @Test
    void theClubDayFollowsMadridTime() {
        assertEquals(TODAYS_CREDIT, barService.houseCreditKey());
    }
}

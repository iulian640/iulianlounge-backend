package com.iulianlounge.backend.repository;

import static com.iulianlounge.backend.repository.ConstraintAssertions.assertViolates;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.OptimisticLockingFailureException;

import com.iulianlounge.backend.domain.Bet;
import com.iulianlounge.backend.domain.BlackjackHand;
import com.iulianlounge.backend.domain.BlackjackRules;
import com.iulianlounge.backend.domain.BlackjackTable;
import com.iulianlounge.backend.domain.Card;
import com.iulianlounge.backend.domain.HandStatus;
import com.iulianlounge.backend.domain.Language;
import com.iulianlounge.backend.domain.Outcome;
import com.iulianlounge.backend.domain.Role;
import com.iulianlounge.backend.domain.StackedDeck;
import com.iulianlounge.backend.domain.User;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class BlackjackHandRepositoryTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private BlackjackHandRepository handRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = newUser("cursaito").getId();
    }

    @Test
    void aDealtHandRoundTripsItsCardsBetAndStatus() {
        BlackjackTable table = inPlay();
        BlackjackHand saved = handRepository.saveAndFlush(
                new BlackjackHand(userId, UUID.randomUUID(), Bet.TWENTY, table, NOW));
        entityManager.clear();

        BlackjackHand found = handRepository.findByIdAndUserId(saved.getId(), userId).orElseThrow();

        assertEquals(Bet.TWENTY, found.getBet());
        assertEquals(HandStatus.PLAYER_TURN, found.getStatus());
        assertEquals(table, found.table());
        assertEquals(table.playerCards(), found.getPlayerCards());
        assertEquals(table.dealerCards(), found.getDealerCards());
        assertEquals(48, found.table().deck().size());
        assertNull(found.getOutcome());
        assertNull(found.getPayout());
        assertNull(found.getFinishedAt());
        assertNull(found.getSettledAt());
        assertEquals(NOW, found.getCreatedAt());
        assertNotNull(found.getVersion());
    }

    @Test
    void aFinishedHandKeepsItsOutcomePayoutAndFinishTime() {
        BlackjackHand saved = handRepository.saveAndFlush(
                new BlackjackHand(userId, UUID.randomUUID(), Bet.FIFTY, naturalBlackjack(), NOW));
        entityManager.clear();

        BlackjackHand found = handRepository.findByIdAndUserId(saved.getId(), userId).orElseThrow();

        assertEquals(HandStatus.FINISHED, found.getStatus());
        assertEquals(Outcome.BLACKJACK, found.getOutcome());
        assertEquals(125L, found.getPayout());
        assertEquals(NOW, found.getFinishedAt());
    }

    @Test
    void aVoidHandRefundsTheWholeBetAndHoldsNoCards() {
        BlackjackHand saved = handRepository.saveAndFlush(
                BlackjackHand.voided(userId, UUID.randomUUID(), Bet.TEN, NOW));
        entityManager.clear();

        BlackjackHand found = handRepository.findByIdAndUserId(saved.getId(), userId).orElseThrow();

        assertEquals(HandStatus.VOID, found.getStatus());
        assertEquals(10L, found.getPayout());
        assertNull(found.getOutcome());
        assertEquals(List.of(), found.getPlayerCards());
        assertEquals(List.of(), found.table().deck());
    }

    @Test
    void findsTheHandOfTheMemberOnlyWhenTheOwnerAsks() {
        BlackjackHand saved = handRepository.saveAndFlush(
                new BlackjackHand(userId, UUID.randomUUID(), Bet.TEN, inPlay(), NOW));

        assertTrue(handRepository.findByIdAndUserId(saved.getId(), userId).isPresent());
        assertTrue(handRepository.findByIdAndUserId(saved.getId(), UUID.randomUUID()).isEmpty());
        assertTrue(handRepository.findByIdAndUserId(UUID.randomUUID(), userId).isEmpty());
    }

    @Test
    void findsTheHandInProgressAndTheHandOfAnIdempotencyKey() {
        UUID key = UUID.randomUUID();
        BlackjackHand saved = handRepository.saveAndFlush(new BlackjackHand(userId, key, Bet.TEN, inPlay(), NOW));

        assertEquals(saved.getId(), handRepository.findByUserIdAndStatus(userId, HandStatus.PLAYER_TURN)
                .orElseThrow().getId());
        assertEquals(saved.getId(), handRepository.findByUserIdAndIdempotencyKey(userId, key).orElseThrow().getId());
        assertTrue(handRepository.findByUserIdAndIdempotencyKey(userId, UUID.randomUUID()).isEmpty());
        assertTrue(handRepository.findByUserIdAndStatus(userId, HandStatus.FINISHED).isEmpty());
    }

    @Test
    void aSecondHandInProgressForTheSameMemberIsRejected() {
        handRepository.saveAndFlush(new BlackjackHand(userId, UUID.randomUUID(), Bet.TEN, inPlay(), NOW));

        assertViolates("blackjack_hand_one_in_progress", () -> handRepository.saveAndFlush(
                new BlackjackHand(userId, UUID.randomUUID(), Bet.TWENTY, inPlay(), NOW)));
    }

    @Test
    void finishedAndVoidHandsDoNotBlockTheTable() {
        handRepository.saveAndFlush(new BlackjackHand(userId, UUID.randomUUID(), Bet.TEN, naturalBlackjack(), NOW));
        handRepository.saveAndFlush(new BlackjackHand(userId, UUID.randomUUID(), Bet.TEN, naturalBlackjack(), NOW));
        handRepository.saveAndFlush(BlackjackHand.voided(userId, UUID.randomUUID(), Bet.TEN, NOW));
        handRepository.saveAndFlush(BlackjackHand.voided(userId, UUID.randomUUID(), Bet.TEN, NOW));

        handRepository.saveAndFlush(new BlackjackHand(userId, UUID.randomUUID(), Bet.TEN, inPlay(), NOW));
    }

    @Test
    void twoMembersCanEachHaveAHandInProgress() {
        UUID other = newUser("dwight").getId();
        handRepository.saveAndFlush(new BlackjackHand(userId, UUID.randomUUID(), Bet.TEN, inPlay(), NOW));

        handRepository.saveAndFlush(new BlackjackHand(other, UUID.randomUUID(), Bet.TEN, inPlay(), NOW));
    }

    @Test
    void theSameIdempotencyKeyTwiceForOneMemberIsRejected() {
        UUID key = UUID.randomUUID();
        handRepository.saveAndFlush(new BlackjackHand(userId, key, Bet.TEN, naturalBlackjack(), NOW));

        assertViolates("blackjack_hand_user_idempotency_key", () -> handRepository.saveAndFlush(
                new BlackjackHand(userId, key, Bet.TEN, naturalBlackjack(), NOW)));
    }

    @Test
    void theSameIdempotencyKeyForAnotherMemberIsFine() {
        UUID key = UUID.randomUUID();
        UUID other = newUser("dwight").getId();
        handRepository.saveAndFlush(new BlackjackHand(userId, key, Bet.TEN, naturalBlackjack(), NOW));

        handRepository.saveAndFlush(new BlackjackHand(other, key, Bet.TEN, naturalBlackjack(), NOW));
    }

    @Test
    void aStaleVersionIsRejected() {
        BlackjackHand hand = handRepository.saveAndFlush(
                new BlackjackHand(userId, UUID.randomUUID(), Bet.TEN, inPlay(), NOW));
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE blackjack_hand SET version = version + 1 WHERE id = :id")
                .setParameter("id", hand.getId())
                .executeUpdate();
        hand.play(BlackjackRules.stand(hand.table()), NOW);

        assertThrows(OptimisticLockingFailureException.class, () -> handRepository.saveAndFlush(hand));
    }

    @Test
    void playingAHandToItsEndRecordsTheOutcomeAndPayout() {
        BlackjackHand hand = handRepository.saveAndFlush(
                new BlackjackHand(userId, UUID.randomUUID(), Bet.TWENTY, standable(), NOW));

        hand.play(BlackjackRules.stand(hand.table()), NOW.plusSeconds(5));
        handRepository.saveAndFlush(hand);
        entityManager.clear();

        BlackjackHand found = handRepository.findByIdAndUserId(hand.getId(), userId).orElseThrow();
        assertEquals(HandStatus.FINISHED, found.getStatus());
        assertEquals(Outcome.WIN, found.getOutcome());
        assertEquals(40L, found.getPayout());
        assertEquals(NOW.plusSeconds(5), found.getFinishedAt());
    }

    @Test
    void settlingAHandMarksWhenItWasPaid() {
        BlackjackHand hand = handRepository.saveAndFlush(
                new BlackjackHand(userId, UUID.randomUUID(), Bet.TEN, naturalBlackjack(), NOW));

        hand.settle(NOW.plusSeconds(1));
        handRepository.saveAndFlush(hand);
        entityManager.clear();

        BlackjackHand found = handRepository.findByIdAndUserId(hand.getId(), userId).orElseThrow();
        assertEquals(NOW.plusSeconds(1), found.getSettledAt());
        assertTrue(found.isSettled());
    }

    @Test
    void addsUpTheBetsOfOneMemberOnly() {
        UUID other = newUser("dwight").getId();
        handRepository.saveAndFlush(new BlackjackHand(userId, UUID.randomUUID(), Bet.TEN, naturalBlackjack(), NOW));
        handRepository.saveAndFlush(BlackjackHand.voided(userId, UUID.randomUUID(), Bet.TWENTY, NOW));
        handRepository.saveAndFlush(new BlackjackHand(userId, UUID.randomUUID(), Bet.FIFTY, inPlay(), NOW));
        handRepository.saveAndFlush(new BlackjackHand(other, UUID.randomUUID(), Bet.TWENTY, inPlay(), NOW));

        assertEquals(80, handRepository.sumBetByUserId(userId));
        assertEquals(20, handRepository.sumBetByUserId(other));
    }

    @Test
    void theBetSumIsZeroWithoutHands() {
        assertEquals(0, handRepository.sumBetByUserId(userId));
    }

    @Test
    void listsOnlyTheClosedHandsThatWereNotSettled() {
        UUID other = newUser("dwight").getId();
        BlackjackHand unpaidWin = handRepository.saveAndFlush(
                new BlackjackHand(userId, UUID.randomUUID(), Bet.TEN, naturalBlackjack(), NOW));
        BlackjackHand paid = handRepository.saveAndFlush(
                new BlackjackHand(userId, UUID.randomUUID(), Bet.TEN, naturalBlackjack(), NOW));
        paid.settle(NOW);
        handRepository.saveAndFlush(paid);
        BlackjackHand unpaidVoid = handRepository.saveAndFlush(BlackjackHand.voided(userId, UUID.randomUUID(), Bet.TEN, NOW));
        handRepository.saveAndFlush(new BlackjackHand(userId, UUID.randomUUID(), Bet.TEN, inPlay(), NOW));
        handRepository.saveAndFlush(new BlackjackHand(other, UUID.randomUUID(), Bet.TEN, naturalBlackjack(), NOW));

        Set<UUID> unsettled = handRepository.findUnsettledByUserId(userId).stream()
                .map(BlackjackHand::getId).collect(Collectors.toSet());

        assertEquals(Set.of(unpaidWin.getId(), unpaidVoid.getId()), unsettled);
    }

    @Test
    void aHandNeverPrintsItsCards() {
        BlackjackHand hand = new BlackjackHand(userId, UUID.randomUUID(), Bet.TEN, inPlay(), NOW);

        for (Card card : hand.table().deck()) {
            assertFalse(hand.toString().contains(card.code()), hand.toString());
        }
        assertFalse(hand.toString().contains("deck"), hand.toString());
    }

    @Test
    void theNativeInsertUsedByTheseChecksWorksForAValidHand() {
        insertHand(10, "PLAYER_TURN", null, null, false, false);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "blackjack_hand_bet_check, 15, PLAYER_TURN, NULL, NULL, false, false",
            "blackjack_hand_bet_check, 0, PLAYER_TURN, NULL, NULL, false, false",
            "blackjack_hand_outcome_when_finished, 10, PLAYER_TURN, WIN, NULL, false, false",
            "blackjack_hand_outcome_when_finished, 10, FINISHED, NULL, 0, true, false",
            "blackjack_hand_outcome_when_finished, 10, VOID, LOSE, 10, true, false",
            "blackjack_hand_payout_check, 20, FINISHED, WIN, 20, true, false",
            "blackjack_hand_payout_check, 20, FINISHED, BLACKJACK, 40, true, false",
            "blackjack_hand_payout_check, 20, FINISHED, LOSE, 20, true, false",
            "blackjack_hand_payout_check, 20, FINISHED, PUSH, 0, true, false",
            "blackjack_hand_payout_check, 20, VOID, NULL, 0, true, false",
            "blackjack_hand_payout_check, 20, PLAYER_TURN, NULL, 0, false, false",
            "blackjack_hand_finished_at_when_closed, 10, PLAYER_TURN, NULL, NULL, true, false",
            "blackjack_hand_finished_at_when_closed, 10, FINISHED, LOSE, 0, false, false",
            "blackjack_hand_settled_only_when_closed, 10, PLAYER_TURN, NULL, NULL, false, true"
    }, nullValues = "NULL")
    void databaseRejectsAHandThatBreaksItsRules(String constraint, long bet, String status, String outcome,
            Long payout, boolean finished, boolean settled) {
        assertViolates(constraint, () -> insertHand(bet, status, outcome, payout, finished, settled));
    }

    @Test
    void databaseRejectsAnUnknownStatus() {
        assertViolates("blackjack_hand_", () -> insertHand(10, "ABANDONED", null, null, true, false));
    }

    @Test
    void theStatusCheckListsExactlyTheThreeStatuses() {
        String definition = (String) entityManager.getEntityManager()
                .createNativeQuery("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                        + "WHERE conname = 'blackjack_hand_status_check'")
                .getSingleResult();

        for (HandStatus status : HandStatus.values()) {
            assertTrue(definition.contains("'" + status.name() + "'"), definition);
        }
        assertEquals(HandStatus.values().length, definition.split(",").length);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "10, FINISHED, BLACKJACK, 25, true",
            "20, FINISHED, BLACKJACK, 50, true",
            "50, FINISHED, BLACKJACK, 125, true",
            "50, FINISHED, WIN, 100, true",
            "50, FINISHED, PUSH, 50, true",
            "50, FINISHED, LOSE, 0, true",
            "50, VOID, NULL, 50, true"
    }, nullValues = "NULL")
    void databaseAcceptsEveryPayoutThePaytableAllows(long bet, String status, String outcome, Long payout,
            boolean finished) {
        insertHand(bet, status, outcome, payout, finished, false);
    }

    private void insertHand(long bet, String status, String outcome, Long payout, boolean finished,
            boolean settled) {
        entityManager.getEntityManager()
                .createNativeQuery("""
                        INSERT INTO blackjack_hand (id, user_id, idempotency_key, bet, status, outcome, payout,
                                                    deck, player_cards, dealer_cards, version, created_at,
                                                    finished_at, settled_at)
                        VALUES (gen_random_uuid(), :userId, gen_random_uuid(), :bet, :status,
                                CAST(:outcome AS text), CAST(:payout AS bigint), '', '', '', 0, now(),
                                CASE WHEN CAST(:finished AS boolean) THEN now() END,
                                CASE WHEN CAST(:settled AS boolean) THEN now() END)
                        """)
                .setParameter("userId", userId)
                .setParameter("bet", bet)
                .setParameter("status", status)
                .setParameter("outcome", outcome)
                .setParameter("payout", payout)
                .setParameter("finished", finished)
                .setParameter("settled", settled)
                .executeUpdate();
    }

    private static BlackjackTable inPlay() {
        return BlackjackRules.deal(StackedDeck.startingWith("9S", "KD", "8H", "5C"));
    }

    private static BlackjackTable standable() {
        return BlackjackRules.deal(StackedDeck.startingWith("TS", "TD", "9H", "7C"));
    }

    private static BlackjackTable naturalBlackjack() {
        return BlackjackRules.deal(StackedDeck.startingWith("AS", "9D", "KS", "8D"));
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

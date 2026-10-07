package com.iulianlounge.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.annotation.Transactional;

import com.iulianlounge.backend.domain.Language;
import com.iulianlounge.backend.domain.Rank;
import com.iulianlounge.backend.domain.TalkSource;
import com.iulianlounge.backend.domain.User;
import com.iulianlounge.backend.dto.DrinkResponse;
import com.iulianlounge.backend.dto.TalkFacts;
import com.iulianlounge.backend.dto.TalkResponse;
import com.iulianlounge.backend.exception.InvalidTokenException;
import com.iulianlounge.backend.exception.WalletNotFoundException;
import com.iulianlounge.backend.llm.FakeLlmClient;
import com.iulianlounge.backend.llm.LlmClient;
import com.iulianlounge.backend.llm.LlmTurn;
import com.iulianlounge.backend.llm.LlmUnavailableException;
import com.iulianlounge.backend.repository.UserRepository;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class TalkServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String HELLO = "¿Qué me pongo si vengo de un día largo?";

    @Mock
    private BarFacts barFacts;

    @Mock
    private UserRepository userRepository;

    private FakeLlmClient llm;
    private TalkBudget budget;
    private TalkService talkService;

    @BeforeEach
    void setUp() {
        llm = new FakeLlmClient();
        budget = new TalkBudget(new SettableClock(Instant.parse("2026-10-07T20:00:00Z")), 500_000, 8);
        talkService = new TalkService(barFacts, userRepository, llm, new BarmanPrompt(), budget);
    }

    @Test
    void whenTheDailyBudgetIsSpentTheBarmanIsBusyWithoutCallingTheModel(CapturedOutput output) {
        talkService = new TalkService(barFacts, userRepository, llm, new BarmanPrompt(),
                new TalkBudget(new SettableClock(Instant.parse("2026-10-07T20:00:00Z")), 0, 8));
        member(Language.ES);

        TalkResponse response = talkService.talk(USER_ID, HELLO, null);

        assertBusy(response);
        assertTrue(llm.calls().isEmpty());
        assertTrue(output.getOut().contains("reason=budget"), output.getOut());
    }

    @Test
    void theRealTokensOfEveryAnswerAreChargedToTheDailyBudget() {
        member(Language.ES);
        llm.willReply("Claro.", 900, 60);

        talkService.talk(USER_ID, HELLO, null);

        assertEquals(900 + 5 * 60, budget.spentToday());
    }

    @Test
    void aFailedCallChargesNothing() {
        member(Language.ES);
        llm.willFail(new LlmUnavailableException(LlmUnavailableException.Reason.HTTP_5XX));

        talkService.talk(USER_ID, HELLO, null);

        assertEquals(0, budget.spentToday());
    }

    @Test
    void theBudgetRunsOutAfterTheCallThatSpendsIt() {
        talkService = new TalkService(barFacts, userRepository, llm, new BarmanPrompt(),
                new TalkBudget(new SettableClock(Instant.parse("2026-10-07T20:00:00Z")), 1000, 8));
        member(Language.ES);
        llm.willReply("Primera.", 900, 60);
        llm.willReply("Segunda.", 900, 60);

        TalkResponse first = talkService.talk(USER_ID, HELLO, null);
        TalkResponse second = talkService.talk(USER_ID, HELLO, null);

        assertEquals(TalkSource.LLM, first.source());
        assertBusy(second);
        assertEquals(1, llm.calls().size());
    }

    @Test
    void theModelsAnswerComesBackAsTheLlmSource() {
        member(Language.ES);
        llm.willReply("Un Gin Rickey: fresco y ligero. Lo tienes en la carta.");

        TalkResponse response = talkService.talk(USER_ID, HELLO, null);

        assertEquals(TalkSource.LLM, response.source());
        assertEquals("Un Gin Rickey: fresco y ligero. Lo tienes en la carta.", response.text());
        assertNull(response.line());
    }

    @Test
    void whenTheClientIsUnavailableTheBarmanIsBusy(CapturedOutput output) {
        member(Language.ES);
        llm.willFail(new LlmUnavailableException(LlmUnavailableException.Reason.TIMEOUT));

        TalkResponse response = talkService.talk(USER_ID, HELLO, null);

        assertBusy(response);
        assertTrue(output.getOut().contains("reason=timeout"), output.getOut());
    }

    @Test
    void anyOtherFailureOfTheClientIsAlsoTheBusyLineAndLeaksNothingToTheLogs(CapturedOutput output) {
        member(Language.ES);
        llm.willFail(new IllegalStateException("SECRETO-USUARIO"));

        TalkResponse response = talkService.talk(USER_ID, HELLO, null);

        assertBusy(response);
        assertFalse(output.getAll().contains("SECRETO-USUARIO"), output.getAll());
        assertFalse(output.getAll().contains(HELLO), output.getAll());
    }

    @Test
    void aDeletedAccountIsAnInvalidTokenAndNeverReachesTheModel() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class, () -> talkService.talk(USER_ID, HELLO, null));

        verifyNoInteractions(barFacts);
        assertTrue(llm.calls().isEmpty());
    }

    @Test
    void aMemberWithoutAWalletGetsTheWalletError() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(Language.ES)));
        when(barFacts.factsFor(USER_ID)).thenThrow(new WalletNotFoundException());

        assertThrows(WalletNotFoundException.class, () -> talkService.talk(USER_ID, HELLO, null));

        assertTrue(llm.calls().isEmpty());
    }

    @Test
    void whatTheMemberWritesTravelsOnlyAsAUserTurnNeverInTheSystemPrompt() {
        member(Language.ES);
        llm.willReply("Claro.");

        talkService.talk(USER_ID, HELLO, null);

        FakeLlmClient.Call call = llm.lastCall();
        assertEquals(List.of(new LlmTurn(LlmTurn.Role.USER, HELLO)), call.turns());
        assertFalse(call.systemPrompt().contains(HELLO));
        assertTrue(call.systemPrompt().contains("- Saldo: 60 chikilicuatres."));
    }

    @Test
    void theLanguageOfTheRequestWinsOverTheOneOfTheAccount() {
        member(Language.ES);
        llm.willReply("Sure.");

        talkService.talk(USER_ID, HELLO, Language.EN);

        assertTrue(llm.lastCall().systemPrompt().contains("- Responde en inglés,"));
        assertTrue(llm.lastCall().systemPrompt().contains("Shrutebucks"));
    }

    @Test
    void withoutALanguageInTheRequestTheAccountLanguageIsUsed() {
        member(Language.EN);
        llm.willReply("Sure.");

        talkService.talk(USER_ID, HELLO, null);

        assertTrue(llm.lastCall().systemPrompt().contains("- Responde en inglés,"));
    }

    @Test
    void controlCharactersAndLineBreaksBecomeSingleSpaces() {
        member(Language.ES);
        llm.willReply("  Hola\u0007,\r\n\n   buenas\t noches \u0000 ");

        TalkResponse response = talkService.talk(USER_ID, HELLO, null);

        assertEquals("Hola, buenas noches", response.text());
    }

    @Test
    void markupIsLeftAsInertPlainText() {
        member(Language.ES);
        llm.willReply("<b>Hola</b> <script>alert(1)</script>");

        TalkResponse response = talkService.talk(USER_ID, HELLO, null);

        assertEquals(TalkSource.LLM, response.source());
        assertEquals("<b>Hola</b> <script>alert(1)</script>", response.text());
    }

    @Test
    void anAnswerThatIsEmptyOnceCleanedIsTheBusyLine() {
        member(Language.ES);
        llm.willReply(" \n\t \u0007 ");

        assertBusy(talkService.talk(USER_ID, HELLO, null));
    }

    @Test
    void aNullTextFromTheClientIsTheBusyLine() {
        member(Language.ES);
        llm.willReply(null);

        assertBusy(talkService.talk(USER_ID, HELLO, null));
    }

    @Test
    void aLongAnswerIsCutAtTheLastEndOfSentenceThatFits() {
        member(Language.ES);
        String answer = "Una frase corta que se repite sin parar. ".repeat(15).trim();
        llm.willReply(answer);

        TalkResponse response = talkService.talk(USER_ID, HELLO, null);

        assertTrue(response.text().length() <= 400);
        assertTrue(response.text().endsWith("."));
        assertEquals(answer.substring(0, response.text().length()), response.text());
        assertFalse(response.text().endsWith("…"));
    }

    @Test
    void everyEndOfSentenceMarkCountsWhenCutting() {
        member(Language.ES);
        String answer = "Hola. ¿Qué tal? ¡Genial! Y sigo… " + "palabra ".repeat(60);
        llm.willReply(answer);

        TalkResponse response = talkService.talk(USER_ID, HELLO, null);

        assertEquals("Hola. ¿Qué tal? ¡Genial! Y sigo…", response.text());
    }

    @Test
    void withoutAnyEndOfSentenceItIsCutAtTheLastSpaceWithAnEllipsis() {
        member(Language.ES);
        String answer = "palabra ".repeat(80).trim();
        llm.willReply(answer);

        TalkResponse response = talkService.talk(USER_ID, HELLO, null);

        assertTrue(response.text().length() <= 400);
        assertTrue(response.text().endsWith("palabra…"));
    }

    @Test
    void withoutEndOfSentenceNorSpacesItIsCutHardWithAnEllipsis() {
        member(Language.ES);
        llm.willReply("a".repeat(500));

        TalkResponse response = talkService.talk(USER_ID, HELLO, null);

        assertEquals(400, response.text().length());
        assertTrue(response.text().endsWith("a…"));
    }

    @Test
    void anAnswerOfExactlyTheLimitIsLeftAlone() {
        member(Language.ES);
        String answer = "a".repeat(400);
        llm.willReply(answer);

        assertEquals(answer, talkService.talk(USER_ID, HELLO, null).text());
    }

    @Test
    void aCutNeverSplitsASurrogatePair() {
        member(Language.ES);
        llm.willReply("a".repeat(398) + "😀😀😀");

        String text = talkService.talk(USER_ID, HELLO, null).text();

        assertTrue(text.length() <= 400);
        assertTrue(text.endsWith("…"));
        assertFalse(Character.isHighSurrogate(text.charAt(text.length() - 2)));
    }

    @Test
    void theServiceOnlyHasTheReadOnlyFactsNeverTheWalletOrTheBar() {
        Class<?>[] parameters = Arrays.stream(TalkService.class.getConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .toArray(Class<?>[]::new);

        List<Class<?>> types = List.of(parameters);

        assertTrue(types.contains(BarFacts.class));
        assertTrue(types.contains(LlmClient.class));
        assertFalse(types.contains(WalletService.class));
        assertFalse(types.contains(BarService.class));
    }

    @Test
    void theServiceIsNotTransactional() {
        assertNull(TalkService.class.getAnnotation(Transactional.class));
    }

    private void member(Language language) {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(language)));
        when(barFacts.factsFor(USER_ID)).thenReturn(
                new TalkFacts(DrinkResponse.menu(), 60, Rank.HABITUAL, 40, false));
    }

    private static User user(Language language) {
        User user = new User();
        user.setId(USER_ID);
        user.setLocale(language);
        return user;
    }

    private static void assertBusy(TalkResponse response) {
        assertEquals(TalkSource.FALLBACK, response.source());
        assertNull(response.text());
        assertEquals("barman.busy", response.line());
    }
}

package com.iulianlounge.backend.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.iulianlounge.backend.domain.BarmanSituation;
import com.iulianlounge.backend.domain.Language;
import com.iulianlounge.backend.domain.User;
import com.iulianlounge.backend.dto.TalkFacts;
import com.iulianlounge.backend.dto.TalkResponse;
import com.iulianlounge.backend.exception.InvalidTokenException;
import com.iulianlounge.backend.llm.LlmClient;
import com.iulianlounge.backend.llm.LlmReply;
import com.iulianlounge.backend.llm.LlmTurn;
import com.iulianlounge.backend.llm.LlmUnavailableException;
import com.iulianlounge.backend.repository.UserRepository;

@Service
public class TalkService {

    private static final Logger log = LoggerFactory.getLogger(TalkService.class);

    private static final String EMPTY = "empty";
    private static final String UNEXPECTED = "unexpected";

    private final BarFacts barFacts;
    private final UserRepository userRepository;
    private final LlmClient llmClient;
    private final BarmanPrompt barmanPrompt;

    public TalkService(BarFacts barFacts, UserRepository userRepository, LlmClient llmClient,
            BarmanPrompt barmanPrompt) {
        this.barFacts = barFacts;
        this.userRepository = userRepository;
        this.llmClient = llmClient;
        this.barmanPrompt = barmanPrompt;
    }

    public TalkResponse talk(UUID userId, String text, Language requested) {
        User user = userRepository.findById(userId).orElseThrow(InvalidTokenException::new);
        TalkFacts facts = barFacts.factsFor(userId);
        Language language = requested != null ? requested : user.getLocale();
        String systemPrompt = barmanPrompt.build(facts, language);
        List<LlmTurn> turns = List.of(new LlmTurn(LlmTurn.Role.USER, text));
        try {
            LlmReply reply = llmClient.reply(systemPrompt, turns);
            String answer = TalkText.clean(reply.text());
            return answer.isEmpty() ? busy(facts, EMPTY) : TalkResponse.llm(answer);
        } catch (LlmUnavailableException e) {
            return busy(facts, e.reason().code());
        } catch (RuntimeException e) {
            return busy(facts, UNEXPECTED);
        }
    }

    private static TalkResponse busy(TalkFacts facts, String reason) {
        log.info("Barman talk fallback reason={}", reason);
        return TalkResponse.fallback(BarmanSituation.BUSY.lineFor(facts.rank()));
    }
}

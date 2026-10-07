package com.iulianlounge.backend.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.iulianlounge.backend.domain.Drink;
import com.iulianlounge.backend.domain.Language;
import com.iulianlounge.backend.domain.Rank;
import com.iulianlounge.backend.dto.DrinkResponse;
import com.iulianlounge.backend.dto.TalkFacts;

@Component
public class BarmanPrompt {

    private static final String TEMPLATE_PATH = "barman/system-prompt.txt";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z]+)\\}");
    private static final String SEPARATOR = ", ";

    private final String template;

    public BarmanPrompt() {
        try {
            this.template = new ClassPathResource(TEMPLATE_PATH).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Barman system prompt is missing", e);
        }
    }

    public String build(TalkFacts facts, Language language) {
        Map<String, String> values = Map.ofEntries(
                Map.entry("language", languageName(language)),
                Map.entry("currency", currency(language)),
                Map.entry("currencyCasual", currencyCasual(language)),
                Map.entry("rankName", rankName(facts.rank(), language)),
                Map.entry("rankLadder", rankLadder(language)),
                Map.entry("rankThresholds", rankThresholds(language)),
                Map.entry("spent", String.valueOf(facts.spent())),
                Map.entry("nextRank", nextRank(facts.spent(), language)),
                Map.entry("balance", String.valueOf(facts.balance())),
                Map.entry("houseCredit", houseCredit(facts.creditAvailable(), language)),
                Map.entry("menu", menu(facts)));
        return fill(values);
    }

    private String fill(Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder filled = new StringBuilder();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            if (value == null) {
                throw new IllegalStateException("Unknown prompt placeholder: " + matcher.group(1));
            }
            matcher.appendReplacement(filled, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(filled);
        return filled.toString();
    }

    private static String languageName(Language language) {
        return switch (language) {
            case ES -> "español de España";
            case EN -> "inglés";
        };
    }

    private static String currency(Language language) {
        return switch (language) {
            case ES -> "chikilicuatres";
            case EN -> "Shrutebucks";
        };
    }

    private static String currencyCasual(Language language) {
        return switch (language) {
            case ES -> "fichas";
            case EN -> "chips";
        };
    }

    private static String rankName(Rank rank, Language language) {
        return switch (language) {
            case ES -> switch (rank) {
                case NADIE -> "Recién llegado";
                case HABITUAL -> "Cliente";
                case CONFIANZA -> "Habitual";
                case SOCIO -> "Socio";
            };
            case EN -> switch (rank) {
                case NADIE -> "Newcomer";
                case HABITUAL -> "Customer";
                case CONFIANZA -> "Regular";
                case SOCIO -> "Member";
            };
        };
    }

    private static String rankLadder(Language language) {
        return Arrays.stream(Rank.values())
                .map(rank -> rankName(rank, language))
                .collect(Collectors.joining(SEPARATOR));
    }

    private static String rankThresholds(Language language) {
        List<String> thresholds = Arrays.stream(Rank.values())
                .filter(rank -> rank.minSpent() > 0)
                .map(rank -> rankName(rank, language) + " " + from(language) + " " + rank.minSpent())
                .collect(Collectors.toCollection(ArrayList::new));
        thresholds.set(0, thresholds.get(0) + " " + spentWord(language));
        return String.join(SEPARATOR, thresholds);
    }

    private static String from(Language language) {
        return switch (language) {
            case ES -> "desde";
            case EN -> "from";
        };
    }

    private static String spentWord(Language language) {
        return switch (language) {
            case ES -> "gastados";
            case EN -> "spent";
        };
    }

    private static String nextRank(long spent, Language language) {
        Optional<Rank> next = Arrays.stream(Rank.values()).filter(rank -> rank.minSpent() > spent).findFirst();
        return next.map(rank -> missingFor(rank.minSpent() - spent, rank, language))
                .orElseGet(() -> switch (language) {
                    case ES -> "Ya está en el rango más alto.";
                    case EN -> "Already at the highest rank.";
                });
    }

    private static String missingFor(long missing, Rank rank, Language language) {
        return switch (language) {
            case ES -> "Le faltan " + missing + " para " + rankName(rank, language) + ".";
            case EN -> "They need " + missing + " more for " + rankName(rank, language) + ".";
        };
    }

    private static String houseCredit(boolean available, Language language) {
        return switch (language) {
            case ES -> available ? "disponible" : "no disponible";
            case EN -> available ? "available" : "not available";
        };
    }

    private static String menu(TalkFacts facts) {
        return facts.drinks().stream()
                .map(BarmanPrompt::menuEntry)
                .collect(Collectors.joining(SEPARATOR));
    }

    private static String menuEntry(DrinkResponse drink) {
        return drinkName(drink.code()) + " " + drink.price();
    }

    private static String drinkName(Drink drink) {
        return switch (drink) {
            case BATHTUB_GIN -> "Bathtub Gin";
            case BEES_KNEES -> "Bee's Knees";
            case GIN_RICKEY -> "Gin Rickey";
            case SIDECAR -> "Sidecar";
            case FRENCH_75 -> "French 75";
        };
    }
}

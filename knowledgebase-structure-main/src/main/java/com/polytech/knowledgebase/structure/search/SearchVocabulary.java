package com.polytech.knowledgebase.structure.search;

import com.polytech.knowledgebase.structure.domain.ConditionKind;
import com.polytech.knowledgebase.structure.domain.StatementCategory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SearchVocabulary {
    private final Map<StatementCategory, List<String>> categories = new LinkedHashMap<>();
    private final Map<ConditionKind, List<String>> conditions = new LinkedHashMap<>();

    private static final String NUMBER = "(-?\\d+(?:[.,]\\d+)?)";
    private static final String VALUE_SEPARATOR = "\\s*(?:[:=]|около|примерно)?\\s*";
    private static final Map<ConditionKind, Pattern> NUMERIC_PATTERNS = Map.of(
            ConditionKind.WIND_SPEED, numericPattern(
                    "(?:скорость\\s+ветра|ветер|ветра)" + VALUE_SEPARATOR + NUMBER + "\\s*м\\s*/\\s*с"),
            ConditionKind.WAVE_HEIGHT, numericPattern(
                    "(?:высота\\s+волн|волны|волна)" + VALUE_SEPARATOR + NUMBER + "\\s*м(?:етр[а-я]*)?\\b"),
            ConditionKind.WATER_TEMPERATURE, numericPattern(
                    "(?:температура\\s+воды|вода)" + VALUE_SEPARATOR + NUMBER + "\\s*(?:°\\s*[cс]|градус[а-я]*)"),
            ConditionKind.AIR_TEMPERATURE, numericPattern(
                    "(?:температура\\s+воздуха|воздух)" + VALUE_SEPARATOR + NUMBER + "\\s*(?:°\\s*[cс]|градус[а-я]*)"));

    public SearchVocabulary() throws IOException {
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(
                new ClassPathResource("search-dictionary.properties").getInputStream(), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        for (String key : properties.getProperty("category.priority").split(",")) {
            StatementCategory category = StatementCategory.valueOf(key.strip());
            categories.put(category, phrases(properties, "category." + category));
        }
        for (ConditionKind kind : ConditionKind.values()) {
            conditions.put(kind, phrases(properties, "condition." + kind));
        }
    }

    private List<String> phrases(Properties properties, String key) {
        return Arrays.stream(properties.getProperty(key, "").split(","))
                .map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
    public Optional<StatementCategory> category(String text) {
        return categories(text).stream().findFirst();
    }

    public List<StatementCategory> categories(String text) {
        return categories.entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                        .anyMatch(phrase -> TextNormalizer.containsUnnegatedPhrase(text, phrase)))
                .map(Map.Entry::getKey)
                .toList();
    }

    public Set<ConditionKind> conditions(String text) {
        Set<ConditionKind> result = EnumSet.noneOf(ConditionKind.class);
        conditions.forEach((kind, phrases) -> {
            if (phrases.stream().anyMatch(phrase -> TextNormalizer.containsUnnegatedPhrase(text, phrase))) {
                result.add(kind);
            }
        });
        return result;
    }

    public Set<ConditionKind> negatedConditions(String text) {
        Set<ConditionKind> result = EnumSet.noneOf(ConditionKind.class);
        conditions.forEach((kind, phrases) -> {
            if (phrases.stream().anyMatch(phrase -> TextNormalizer.containsPhrase(text, phrase)
                    && !TextNormalizer.containsUnnegatedPhrase(text, phrase))) {
                result.add(kind);
            }
        });
        return result;
    }

    public Map<ConditionKind, Double> numericConditions(String question) {
        Map<ConditionKind, Double> result = new EnumMap<>(ConditionKind.class);
        String text = Normalizer.normalize(question, Normalizer.Form.NFKC);
        NUMERIC_PATTERNS.forEach((kind, pattern) -> {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                double value = Double.parseDouble(matcher.group(1).replace(',', '.'));
                if (Double.isFinite(value)
                        && (value >= 0 || kind == ConditionKind.WATER_TEMPERATURE
                            || kind == ConditionKind.AIR_TEMPERATURE)) {
                    result.put(kind, value);
                }
            }
        });
        return result;
    }

    private static Pattern numericPattern(String expression) {
        return Pattern.compile("\\b" + expression,
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);
    }
}

package com.polytech.knowledgebase.structure.search;

import com.polytech.knowledgebase.structure.domain.ConditionKind;
import com.polytech.knowledgebase.structure.domain.StatementCategory;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record SearchRequest(
        String question,
        String topicId,
        List<StatementCategory> categories,
        Set<ConditionKind> conditions,
        Map<ConditionKind, Double> numericConditions,
        boolean includeDemo,
        int page,
        int size) {

    public SearchRequest {
        question = question == null ? "" : question.strip();
        topicId = topicId == null || topicId.isBlank() ? null : topicId.strip();
        categories = categories == null ? List.of() : List.copyOf(categories);
        conditions = conditions == null ? Set.of() : Set.copyOf(conditions);
        numericConditions = numericConditions == null ? Map.of() : Map.copyOf(numericConditions);

        if (question.length() > 2000) {
            throw new IllegalArgumentException("Вопрос должен содержать не более 2000 символов.");
        }

        if (page < 0 || size < 1 || size > 50) {
            throw new IllegalArgumentException("Номер страницы должен быть неотрицательным, размер — от 1 до 50.");
        }

        if (conditions.stream().anyMatch(ConditionKind::isNumeric)) {
            throw new IllegalArgumentException("Для числового условия укажите значение в numericConditions.");
        }

        numericConditions.forEach((kind, value) -> {
            if (!kind.isNumeric() || !Double.isFinite(value)) {
                throw new IllegalArgumentException("Числовые условия должны содержать конечные числовые значения.");
            }

            if (value < 0 && (kind == ConditionKind.WIND_SPEED || kind == ConditionKind.WAVE_HEIGHT)) {
                throw new IllegalArgumentException("Скорость ветра и высота волн не могут быть отрицательными.");
            }
        });
    }
}

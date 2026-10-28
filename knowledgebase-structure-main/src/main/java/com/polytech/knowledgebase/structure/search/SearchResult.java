package com.polytech.knowledgebase.structure.search;

import com.polytech.knowledgebase.structure.domain.ConditionKind;
import com.polytech.knowledgebase.structure.domain.StatementCategory;
import com.polytech.knowledgebase.structure.domain.StatementStatus;

import java.time.Instant;
import java.util.List;

// Detached snapshots keep console and HTTP output independent of lazy JPA associations.
public record SearchResult(
        List<TopicView> topics,
        StatementCategory category,
        List<StatementView> statements,
        String message,
        long total,
        int page,
        int size,
        List<String> recognizedConditions,
        List<String> suggestions) {

    public SearchResult {
        topics = List.copyOf(topics);
        statements = List.copyOf(statements);
        recognizedConditions = List.copyOf(recognizedConditions);
        suggestions = List.copyOf(suggestions);
    }

    public SearchResult(List<TopicView> topics, StatementCategory category,
                        List<StatementView> statements, String message) {
        this(topics, category, statements, message, statements.size(), 0, 5, List.of(), List.of());
    }

    public record TopicView(String id, String name) {}
    public record SourceView(String title, String location, boolean demonstration,
                             String fragment, String locator) {}

    public record ConditionView(ConditionKind kind, String text, Double minValue,
                                Double maxValue, String unit) {}

    public record ReviewView(String reviewer, Instant reviewedAt, String note) {}

    public record StatementView(
            String id,
            String text,
            StatementCategory category,
            StatementStatus status,
            List<String> conditions,
            List<SourceView> sources,
            TopicView topic,
            List<TopicView> referencedTopics,
            List<ConditionView> structuredConditions,
            String matchReason,
            String applicability,
            ReviewView review) {

        public StatementView {
            conditions = List.copyOf(conditions);
            sources = List.copyOf(sources);
            referencedTopics = List.copyOf(referencedTopics);
            structuredConditions = List.copyOf(structuredConditions);
        }

        public StatementView(String id, String text, StatementCategory category,
                             StatementStatus status, List<String> conditions, List<SourceView> sources) {
            this(id, text, category, status, conditions, sources, null, List.of(), List.of(),
                    "", "UNSPECIFIED", null);
        }
    }
}

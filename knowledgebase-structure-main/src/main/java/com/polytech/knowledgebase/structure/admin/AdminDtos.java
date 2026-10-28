package com.polytech.knowledgebase.structure.admin;

import com.polytech.knowledgebase.structure.domain.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AdminDtos {
    private AdminDtos() {}

    public record TopicInput(String name, TopicType type, List<String> synonyms) {}
    public record SourceInput(String title, String location, boolean demonstration,
                              String author, LocalDate publishedOn, LocalDate accessedOn) {}
    public record RelationInput(String fromTopicId, String toTopicId, RelationType type) {}
    public record ConditionInput(ConditionKind kind, String text, Double minValue, Double maxValue) {}
    public record EvidenceInput(String sourceId, String fragment, String locator) {}
    public record StatementInput(String topicId, String text, StatementCategory category,
                                 List<String> referencedTopicIds, List<ConditionInput> conditions,
                                 List<EvidenceInput> evidence, boolean demonstration, Long version) {}
    public record ReviewInput(StatementStatus status, String reviewer, String note, Long version) {}

    public record TopicRef(String id, String name) {}
    public record TopicDto(String id, String name, TopicType type, List<String> synonyms) {}
    public record SourceDto(String id, String title, String location, boolean demonstration,
                            String author, LocalDate publishedOn, LocalDate accessedOn) {}
    public record RelationDto(String id, TopicRef fromTopic, TopicRef toTopic, RelationType type) {}
    public record ConditionDto(ConditionKind kind, String text, Double minValue, Double maxValue, String unit) {}
    public record EvidenceDto(String id, String sourceId, String title, String location,
                              boolean demonstration, String fragment, String locator) {}
    public record ReviewDto(StatementStatus status, String reviewer, Instant reviewedAt, String note) {}
    public record StatementDto(String id, TopicRef topic, String text, StatementCategory category,
                               StatementStatus status, long version, List<TopicRef> referencedTopics,
                               List<ConditionDto> conditions, List<EvidenceDto> evidence,
                               String reviewedBy, Instant reviewedAt, String reviewNote, List<ReviewDto> reviews) {}
    public record PageDto<T>(List<T> items, long total, int page, int size) {}
    public record ConditionKindDto(ConditionKind kind, String label, String dimension, String unit, boolean numeric) {}
    public record Catalog(List<TopicDto> topics, List<SourceDto> sources, List<StatementCategory> categories,
                          List<StatementStatus> statuses, List<ConditionKindDto> conditionKinds,
                          List<RelationType> relationTypes, List<TopicType> topicTypes) {}
    public record Dashboard(long topics, long statements, long sources,
                            long candidates, long verified, long demo, long rejected) {}
}

package com.polytech.knowledgebase.structure.admin;

import com.polytech.knowledgebase.structure.domain.*;
import com.polytech.knowledgebase.structure.repository.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static com.polytech.knowledgebase.structure.admin.AdminDtos.*;

@Service
@Transactional(readOnly = true)
public class KnowledgeAdminService {
    private final TopicRepository topics;
    private final SourceRepository sources;
    private final StatementRepository statements;
    private final TopicRelationRepository relations;
    private final EvidenceRepository evidence;

    public KnowledgeAdminService(TopicRepository topics, SourceRepository sources,
                                 StatementRepository statements, TopicRelationRepository relations,
                                 EvidenceRepository evidence) {
        this.topics = topics;
        this.sources = sources;
        this.statements = statements;
        this.relations = relations;
        this.evidence = evidence;
    }

    public Catalog catalog() {
        return new Catalog(
                topics.findAllByOrderByIdAsc().stream().map(this::topicDto).toList(),
                sources.findAll(Sort.by("title")).stream().map(this::sourceDto).toList(),
                List.of(StatementCategory.values()), List.of(StatementStatus.values()),
                Arrays.stream(ConditionKind.values()).map(kind -> new ConditionKindDto(
                        kind, kind.getLabel(), kind.getDimension(), kind.getUnit(), kind.isNumeric())).toList(),
                List.of(RelationType.values()), List.of(TopicType.values()));
    }

    public Dashboard dashboard() {
        Map<StatementStatus, Long> byStatus = new EnumMap<>(StatementStatus.class);
        long total = 0;
        for (Object[] row : statements.countGroupedByStatus()) {
            long count = (Long) row[1];
            byStatus.put((StatementStatus) row[0], count);
            total += count;
        }

        return new Dashboard(topics.count(), total, sources.count(),
                byStatus.getOrDefault(StatementStatus.CANDIDATE, 0L),
                byStatus.getOrDefault(StatementStatus.VERIFIED, 0L),
                byStatus.getOrDefault(StatementStatus.DEMO, 0L),
                byStatus.getOrDefault(StatementStatus.REJECTED, 0L));
    }

    public PageDto<StatementDto> listStatements(StatementStatus status, String topicId, String queryText,
                                               int page, int size) {
        validatePage(page, size);
        Specification<Statement> specification = (root, query, cb) -> {
            List<Predicate> filters = new ArrayList<>();
            if (status != null) filters.add(cb.equal(root.get("status"), status));
            if (topicId != null && !topicId.isBlank()) filters.add(cb.equal(root.get("topic").get("id"), topicId));
            if (queryText != null && !queryText.isBlank()) {
                String escaped = queryText.strip().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                filters.add(cb.like(cb.lower(root.get("text")), "%" + escaped + "%", '\\'));
            }
            return cb.and(filters.toArray(Predicate[]::new));
        };

        var result = statements.findAll(specification, PageRequest.of(page, size, Sort.by("id")));
        return new PageDto<>(result.getContent().stream().map(this::statementDto).toList(),
                result.getTotalElements(), page, size);
    }

    public StatementDto getStatement(String id) {
        return statementDto(statement(id));
    }

    @Transactional
    public StatementDto createStatement(StatementInput input) {
        validateStatementInput(input);
        StatementStatus status = input.demonstration() ? StatementStatus.DEMO : StatementStatus.CANDIDATE;
        Statement statement = new Statement(id(), topic(input.topicId()), input.text(), input.category(), status);
        replaceDetails(statement, input);
        return statementDto(statements.saveAndFlush(statement));
    }

    @Transactional
    public StatementDto updateStatement(String id, StatementInput input) {
        validateStatementInput(input);
        Statement statement = statement(id);
        requireVersion(statement, input.version());
        if (input.demonstration() != (statement.getStatus() == StatementStatus.DEMO)) {
            throw new InvalidInputException("demonstration", "Учебный режим записи нельзя изменить при редактировании.");
        }

        statement.revise(topic(input.topicId()), input.text(), input.category());
        statement.clearReferences();
        statement.clearConditions();
        statement.clearEvidence();
        // Delete orphan rows before inserting replacements with the same unique keys.
        statements.flush();
        replaceDetails(statement, input);
        statements.flush();
        return statementDto(statement);
    }

    @Transactional
    public StatementDto reviewStatement(String id, ReviewInput input) {
        Statement statement = statement(id);
        requireVersion(statement, input.version());
        required(input.reviewer(), "reviewer", 200);
        required(input.note(), "note", 2000);
        requiredEnum(input.status(), "status");
        if (statement.getStatus() == StatementStatus.DEMO) {
            throw new InvalidInputException("status", "Учебные записи не проходят проверку. Создайте отдельное утверждение.");
        }

        statement.review(input.status(), input.reviewer(), input.note());
        statements.flush();
        return statementDto(statement);
    }

    private void replaceDetails(Statement statement, StatementInput input) {
        for (String referenceId : safe(input.referencedTopicIds())) {
            Topic reference = topic(referenceId);
            if (!referenceId.equals(statement.getTopic().getId())) statement.addReference(reference);
        }
        for (ConditionInput condition : safe(input.conditions())) {
            statement.addCondition(id(), condition.kind(), condition.text(), condition.minValue(), condition.maxValue());
        }
        for (EvidenceInput evidence : safe(input.evidence())) {
            statement.addEvidence(id(), source(evidence.sourceId()), evidence.fragment(), evidence.locator());
        }
    }

    @Transactional
    public TopicDto createTopic(TopicInput input) {
        validateTopic(input);
        Topic topic = new Topic(id(), input.name(), input.type());
        addSynonyms(topic, input.synonyms());
        return topicDto(topics.saveAndFlush(topic));
    }

    @Transactional
    public TopicDto updateTopic(String id, TopicInput input) {
        validateTopic(input);
        Topic topic = topic(id);
        topic.revise(input.name(), input.type());
        topic.clearSynonyms();
        topics.flush();
        addSynonyms(topic, input.synonyms());
        topics.flush();
        return topicDto(topic);
    }

    private void addSynonyms(Topic topic, List<String> synonyms) {
        safe(synonyms).stream().map(String::strip).distinct().forEach(name -> topic.addSynonym(id(), name));
    }

    @Transactional
    public SourceDto createSource(SourceInput input) {
        validateSource(input);
        Source source = new Source(id(), input.title(), input.location(), input.demonstration());
        source.revise(input.title(), input.location(), input.demonstration(),
                input.author(), input.publishedOn(), input.accessedOn());
        return sourceDto(sources.saveAndFlush(source));
    }

    @Transactional
    public SourceDto updateSource(String id, SourceInput input) {
        validateSource(input);
        Source source = source(id);
        if (source.isDemonstration() != input.demonstration()) {
            throw new InvalidInputException("demonstration", "Учебный режим источника нельзя изменить после создания.");
        }

        if (!source.getLocation().equals(input.location().strip()) && evidence.existsBySourceId(id)) {
            throw new InvalidInputException("location", "На источник уже ссылаются утверждения. Для другого документа создайте новый источник.");
        }

        source.revise(input.title(), input.location(), input.demonstration(),
                input.author(), input.publishedOn(), input.accessedOn());
        sources.flush();
        return sourceDto(source);
    }

    public List<RelationDto> listRelations() {
        return relations.findAll(Sort.by("id")).stream().map(relation -> new RelationDto(
                relation.getId(), topicRef(relation.getFromTopic()),
                topicRef(relation.getToTopic()), relation.getType())).toList();
    }

    @Transactional
    public RelationDto createRelation(RelationInput input) {
        required(input.fromTopicId(), "fromTopicId", 120);
        required(input.toTopicId(), "toTopicId", 120);
        requiredEnum(input.type(), "type");
        TopicRelation relation = new TopicRelation(id(), topic(input.fromTopicId()),
                topic(input.toTopicId()), input.type());
        relations.saveAndFlush(relation);
        return new RelationDto(relation.getId(), topicRef(relation.getFromTopic()),
                topicRef(relation.getToTopic()), relation.getType());
    }

    private void validateStatementInput(StatementInput input) {
        required(input.topicId(), "topicId", 120);
        required(input.text(), "text", 4000);
        requiredEnum(input.category(), "category");
        limited(input.referencedTopicIds(), "referencedTopicIds", 30);
        limited(input.conditions(), "conditions", 30);
        limited(input.evidence(), "evidence", 30);

        Set<ConditionKind> kinds = EnumSet.noneOf(ConditionKind.class);
        Set<String> dimensions = new HashSet<>();
        for (ConditionInput condition : safe(input.conditions())) {
            if (condition == null) throw new InvalidInputException("conditions", "Условие не может быть пустым.");
            requiredEnum(condition.kind(), "conditions");
            required(condition.text(), "conditions", 500);
            if (!kinds.add(condition.kind())) {
                throw new InvalidInputException("conditions", "Один тип условия можно указать только один раз.");
            }
            if (!condition.kind().isNumeric() && !dimensions.add(condition.kind().getDimension())) {
                throw new InvalidInputException("conditions", "Нельзя сочетать противоречивые условия одной группы.");
            }
        }

        Set<String> evidenceKeys = new HashSet<>();
        for (EvidenceInput evidence : safe(input.evidence())) {
            if (evidence == null) throw new InvalidInputException("evidence", "Подтверждение не может быть пустым.");
            required(evidence.sourceId(), "evidence", 120);
            required(evidence.fragment(), "evidence", 4000);
            required(evidence.locator(), "evidence", 300);
            if (!evidenceKeys.add(evidence.sourceId().strip() + "\n" + evidence.locator().strip())) {
                throw new InvalidInputException("evidence", "Источник и позиция подтверждения повторяются.");
            }
        }
        for (String referenceId : safe(input.referencedTopicIds())) required(referenceId, "referencedTopicIds", 120);
    }

    private void validateTopic(TopicInput input) {
        required(input.name(), "name", 200);
        requiredEnum(input.type(), "type");
        limited(input.synonyms(), "synonyms", 50);
        for (String synonym : safe(input.synonyms())) required(synonym, "synonyms", 200);
    }

    private void validateSource(SourceInput input) {
        required(input.title(), "title", 300);
        required(input.location(), "location", 1000);
        if (input.author() != null && input.author().length() > 300) {
            throw new InvalidInputException("author", "Автор: не более 300 символов.");
        }
    }

    private void requireVersion(BaseEntity entity, Long version) {
        if (version == null) throw new InvalidInputException("version", "Для изменения записи нужна её версия.");
        if (entity.getVersion() != version) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Запись уже изменена. Обновите её перед сохранением.");
        }
    }

    private Topic topic(String id) {
        return topics.findById(id).orElseThrow(() -> missing("Тема"));
    }

    private Source source(String id) {
        return sources.findById(id).orElseThrow(() -> missing("Источник"));
    }

    private Statement statement(String id) {
        return statements.findById(id).orElseThrow(() -> missing("Утверждение"));
    }

    private ResponseStatusException missing(String name) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, name + " не найдено.");
    }

    private TopicRef topicRef(Topic topic) {
        return new TopicRef(topic.getId(), topic.getName());
    }

    private TopicDto topicDto(Topic topic) {
        return new TopicDto(topic.getId(), topic.getName(), topic.getType(),
                topic.getSynonyms().stream().map(TopicSynonym::getName).toList());
    }

    private SourceDto sourceDto(Source source) {
        return new SourceDto(source.getId(), source.getTitle(), source.getLocation(), source.isDemonstration(),
                source.getAuthor(), source.getPublishedOn(), source.getAccessedOn());
    }

    private StatementDto statementDto(Statement statement) {
        return new StatementDto(statement.getId(), topicRef(statement.getTopic()), statement.getText(),
                statement.getCategory(), statement.getStatus(), statement.getVersion(),
                statement.getReferencedTopics().stream().map(this::topicRef).toList(),
                statement.getConditions().stream().map(c -> new ConditionDto(
                        c.getKind(), c.getText(), c.getMinValue(), c.getMaxValue(), c.getUnit())).toList(),
                statement.getEvidence().stream().map(e -> new EvidenceDto(e.getId(), e.getSource().getId(),
                        e.getSource().getTitle(), e.getSource().getLocation(), e.getSource().isDemonstration(),
                        e.getFragment(), e.getLocator())).toList(),
                statement.getReviewedBy(), statement.getReviewedAt(), statement.getReviewNote(),
                statement.getReviews().stream().map(review -> new ReviewDto(review.getStatus(),
                        review.getReviewer(), review.getReviewedAt(), review.getNote())).toList());
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static void required(String value, String field, int length) {
        if (value == null || value.isBlank() || value.length() > length) {
            throw new InvalidInputException(field, "Заполните поле «" + field + "» (до " + length + " символов).");
        }
    }

    private static void requiredEnum(Object value, String field) {
        if (value == null) throw new InvalidInputException(field, "Выберите значение поля «" + field + "».");
    }

    private static void limited(List<?> values, String field, int maximum) {
        if (values != null && values.size() > maximum) {
            throw new InvalidInputException(field, "Слишком много элементов: максимум " + maximum + ".");
        }
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new InvalidInputException("page", "Страница должна быть >= 0, размер страницы — от 1 до 50.");
        }
    }
}

package com.polytech.knowledgebase.structure;

import com.polytech.knowledgebase.structure.console.ConsoleRenderer;
import com.polytech.knowledgebase.structure.domain.*;
import com.polytech.knowledgebase.structure.repository.*;
import com.polytech.knowledgebase.structure.search.*;
import com.polytech.knowledgebase.structure.seed.DemoDataSeeder;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.io.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StructureApplicationTests {
    @Autowired DemoDataSeeder seeder;
    @Autowired KnowledgeSearchService search;
    @Autowired TopicRepository topics;
    @Autowired TopicSynonymRepository synonyms;
    @Autowired StatementRepository statements;
    @Autowired StatementConditionRepository conditions;
    @Autowired SourceRepository sources;
    @Autowired EvidenceRepository evidence;
    @Autowired TopicRelationRepository relations;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired ConsoleRenderer renderer;
    @Autowired ApplicationContext context;

    @BeforeEach
    void prepare() throws IOException {
        seeder.seed();
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void recognizesSynonymsCaseWhitespaceAndYo() {
        var result = search.search("  Что нужно НОВИЧКУ для   SUP?! ", true);
        assertThat(result.topics()).extracting(SearchResult.TopicView::id).containsExactly("demo-topic-sup");
        assertThat(result.category()).isEqualTo(StatementCategory.GETTING_STARTED);
        assertThat(result.statements()).extracting(SearchResult.StatementView::id).containsExactly("demo-sup-start");
        assertThat(search.search("Что учитывать в ВИНДСЁРФИНГЕ?", true).topics())
                .extracting(SearchResult.TopicView::id).containsExactly("demo-topic-windsurf");
    }

    @Test
    void doesNotMatchInsideAnotherWord() {
        assertThat(search.search("сапоги супер SUPERMAN антисапбординг", true).topics()).isEmpty();
    }

    @Test
    void asksForClarificationForTwoSportsWithoutReturningClaims() {
        var result = search.search("Чем отличаются каякинг и сапбординг?", true);
        assertThat(result.topics()).extracting(SearchResult.TopicView::id)
                .containsExactlyInAnyOrder("demo-topic-kayak", "demo-topic-sup");
        assertThat(result.statements()).isEmpty();
        assertThat(result.message()).contains("Уточните");
    }

    @Test
    void reportsUnknownAndBlankInput() {
        assertThat(search.search("Как заниматься неизвестным видом спорта?", true).message()).contains("Тема не найдена");
        assertThat(search.search("   ", true).statements()).isEmpty();
        assertThat(search.search(null, true).message()).contains("непустой");
    }

    @Test
    void explicitSportTakesPriorityOverMentionedEquipment() {
        var result = search.search("Какое весло используют в каякинге?", true);
        assertThat(result.topics()).extracting(SearchResult.TopicView::id).containsExactly("demo-topic-kayak");
        assertThat(result.category()).isEqualTo(StatementCategory.EQUIPMENT);
        assertThat(result.statements()).extracting(SearchResult.StatementView::id).containsExactly("demo-kayak-equipment");
    }

    @Test
    void excludesCandidateAndRejectedAndAllowsDisablingDemo() {
        Topic topic = topics.findById("demo-topic-sup").orElseThrow();
        for (StatementStatus status : List.of(StatementStatus.CANDIDATE, StatementStatus.REJECTED, StatementStatus.VERIFIED))
            statements.save(new Statement("test-" + status, topic, "Тестовая запись " + status,
                    StatementCategory.GENERAL, status));
        entityManager.flush();
        entityManager.clear();
        var ordinary = search.search("сапбординг", false);
        assertThat(ordinary.statements()).extracting(SearchResult.StatementView::status)
                .containsExactly(StatementStatus.VERIFIED);
        assertThat(search.search("сапбординг", true).statements()).extracting(SearchResult.StatementView::status)
                .containsExactlyInAnyOrder(StatementStatus.VERIFIED, StatementStatus.DEMO);
    }

    @Test
    void appliesExperienceAndWaterConditionsAndRetainsUnmentionedRestrictions() {
        var beginner = search.search("Что нужно новичку для сапбординга?", true);
        assertThat(beginner.statements()).extracting(SearchResult.StatementView::id).containsExactly("demo-sup-start");
        assertThat(beginner.statements().getFirst().conditions()).containsExactlyInAnyOrder("Для начинающих", "На спокойной воде");
        assertThat(search.search("Как начать сапбординг на волнах?", true).statements()).isEmpty();
        assertThat(search.search("Первые шаги в сапбординге опытному", true).statements())
                .extracting(SearchResult.StatementView::id).containsExactly("demo-sup-advanced");
    }

    @Test
    void rejectsContradictoryConditionHints() {
        var result = search.search("Как начать сапбординг на спокойной воде и на волнах?", true);
        assertThat(result.statements()).isEmpty();
        assertThat(result.message()).contains("Уточните условия");
    }

    @Test
    void prioritizesMatchingConditionsOverUnconditionalClaims() {
        Topic topic = topics.findById("demo-topic-sup").orElseThrow();
        statements.save(new Statement("aaa-unconditional", topic, "Общий учебный сценарий.",
                StatementCategory.GETTING_STARTED, StatementStatus.DEMO));
        var result = search.search("Что нужно новичку для сапбординга?", true);
        assertThat(result.statements()).extracting(SearchResult.StatementView::id)
                .containsExactly("demo-sup-start", "aaa-unconditional");
    }

    @Test
    void fallsBackOnlyToGeneralWithBoundedResults() {
        Topic topic = topics.findById("demo-topic-sup").orElseThrow();
        for (int i = 0; i < 8; i++) statements.save(new Statement("test-general-" + i, topic,
                "Общие сведения " + i, StatementCategory.GENERAL, StatementStatus.DEMO));
        var result = search.search("Расскажи про сапбординг", true);
        assertThat(result.category()).isNull();
        assertThat(result.statements()).hasSize(5).allMatch(s -> s.category() == StatementCategory.GENERAL);
    }

    @Test
    void repeatedSeedPreservesCountsIdsAndDoesNotOverwriteExistingRows() throws IOException {
        List<Long> before = counts();
        Set<String> ids = new HashSet<>(statements.findAll().stream().map(Statement::getId).toList());
        jdbc.update("update statements set text = ? where id = ?", "Изменённый учебный текст", "demo-sup-general");
        seeder.seed();
        entityManager.flush();
        entityManager.clear();
        assertThat(counts()).isEqualTo(before);
        assertThat(before).containsExactly(8L, 31L, 18L, 13L, 1L, 18L, 6L);
        assertThat(statements.findAll()).extracting(Statement::getId).containsExactlyInAnyOrderElementsOf(ids);
        assertThat(statements.findById("demo-sup-general").orElseThrow().getText()).isEqualTo("Изменённый учебный текст");
    }

    @Test
    void preservesReferencesMultipleEvidenceAndConsoleConditions() {
        Statement statement = statements.findById("demo-sup-start").orElseThrow();
        Source second = sources.save(new Source("test-source", "Второй учебный документ", "test:source", true));
        statement.addEvidence("test-evidence", second, "Дополнительный учебный фрагмент.", "Раздел 2");
        entityManager.flush();
        entityManager.clear();
        var result = search.search("Что нужно новичку для сапбординга?", true);
        entityManager.clear();
        assertThat(result.statements().getFirst().sources()).hasSize(2);
        assertThat(statements.findById("demo-sup-start").orElseThrow().getReferencedTopics())
                .extracting(Topic::getId).containsExactlyInAnyOrder("demo-topic-board", "demo-topic-paddle");
        StringWriter output = new StringWriter();
        renderer.render(result, new PrintWriter(output));
        assertThat(output.toString()).contains("DEMO — демонстрационные данные", "Для начинающих",
                "На спокойной воде", "classpath:demo-statements.tsv", "Дополнительный учебный фрагмент.", "Раздел 2");
    }

    @Test
    void databaseRejectsBlankRequiredFields() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into sources (id, title, location, demonstration) values (?, ?, ?, ?)",
                "invalid-source", "  ", "test:invalid", true)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsDuplicateSynonymWithinTopic() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into topic_synonyms (id, topic_id, name) values (?, ?, ?)",
                "duplicate-synonym", "demo-topic-sup", "sup")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void modelRejectsMissingTextAndSelfRelations() {
        assertThatThrownBy(() -> new Topic("id", " ", TopicType.SPORT)).isInstanceOf(IllegalArgumentException.class);
        Topic topic = new Topic("id", "Тема", TopicType.SPORT);
        assertThatThrownBy(() -> new TopicRelation("relation", topic, topic, RelationType.USES))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void contextHasNoWebServerOrInteractiveRunner() {
        assertThat(context.getClass().getName()).doesNotContain("WebServer");
        assertThat(context.containsBean("knowledgeConsole")).isFalse();
    }

    private List<Long> counts() {
        return List.of(topics.count(), synonyms.count(), statements.count(), conditions.count(),
                sources.count(), evidence.count(), relations.count());
    }
}

package com.polytech.knowledgebase.structure.search;

import com.polytech.knowledgebase.structure.domain.ConditionKind;
import com.polytech.knowledgebase.structure.domain.RelationType;
import com.polytech.knowledgebase.structure.domain.Source;
import com.polytech.knowledgebase.structure.domain.Statement;
import com.polytech.knowledgebase.structure.domain.StatementCategory;
import com.polytech.knowledgebase.structure.domain.StatementStatus;
import com.polytech.knowledgebase.structure.domain.Topic;
import com.polytech.knowledgebase.structure.domain.TopicRelation;
import com.polytech.knowledgebase.structure.domain.TopicType;
import com.polytech.knowledgebase.structure.repository.SourceRepository;
import com.polytech.knowledgebase.structure.repository.StatementRepository;
import com.polytech.knowledgebase.structure.repository.TopicRelationRepository;
import com.polytech.knowledgebase.structure.repository.TopicRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class KnowledgeSearchEnhancementTests {
    @Autowired KnowledgeSearchService search;
    @Autowired TopicRepository topics;
    @Autowired StatementRepository statements;
    @Autowired TopicRelationRepository relations;
    @Autowired SourceRepository sources;
    @Autowired EntityManager entityManager;

    @Test
    void findsReferencedTopicsAndRanksDirectClaimsFirst() {
        Topic sport = topic("sport", "Тестовый спорт", TopicType.SPORT);
        Topic paddle = topic("paddle", "Тестовое весло", TopicType.EQUIPMENT);
        Statement referenced = statement("referenced", sport, StatementCategory.EQUIPMENT, StatementStatus.VERIFIED);
        referenced.addReference(paddle);
        Statement direct = statement("direct", paddle, StatementCategory.EQUIPMENT, StatementStatus.DEMO);
        flushAndDetach();

        var result = search.search(request("", paddle, List.of(StatementCategory.EQUIPMENT), Set.of(), Map.of(), 0, 10));

        assertThat(result.statements()).extracting(SearchResult.StatementView::id)
                .containsExactly(direct.getId(), referenced.getId());
        assertThat(result.statements().getLast().matchReason()).contains("ссылается");
        entityManager.clear();
        assertThat(result.statements().getLast().referencedTopics()).extracting(SearchResult.TopicView::id)
                .containsExactly(paddle.getId());
    }

    @Test
    void followsBothRelationDirectionsButStopsAfterOneHop() {
        Topic sport = topic("sport", "Тестовый спорт", TopicType.SPORT);
        Topic paddle = topic("paddle", "Тестовое весло", TopicType.EQUIPMENT);
        Topic water = topic("water", "Тестовая вода", TopicType.ENVIRONMENT);
        Topic distant = topic("distant", "Дальний спорт", TopicType.SPORT);
        relations.save(new TopicRelation("search-r1", sport, paddle, RelationType.USES));
        relations.save(new TopicRelation("search-r2", paddle, water, RelationType.DEPENDS_ON));
        relations.save(new TopicRelation("search-r3", distant, water, RelationType.PRACTICED_ON));
        Statement incoming = statement("incoming", sport, StatementCategory.EQUIPMENT, StatementStatus.DEMO);
        Statement outgoing = statement("outgoing", water, StatementCategory.EQUIPMENT, StatementStatus.DEMO);
        Statement twoHops = statement("two-hops", distant, StatementCategory.EQUIPMENT, StatementStatus.DEMO);
        flushAndDetach();

        var result = search.search(request("", paddle, List.of(StatementCategory.EQUIPMENT), Set.of(), Map.of(), 0, 10));

        assertThat(result.statements()).extracting(SearchResult.StatementView::id)
                .containsExactlyInAnyOrder(incoming.getId(), outgoing.getId()).doesNotContain(twoHops.getId());
        assertThat(result.statements()).allMatch(view -> view.matchReason().contains("связана"));
    }

    @Test
    void explicitSportDoesNotPullInOtherSportsThroughSharedEquipment() {
        Topic first = topic("first", "Первый спорт", TopicType.SPORT);
        Topic second = topic("second", "Второй спорт", TopicType.SPORT);
        Topic paddle = topic("paddle", "Тестовое весло", TopicType.EQUIPMENT);
        relations.save(new TopicRelation("search-r1", first, paddle, RelationType.USES));
        relations.save(new TopicRelation("search-r2", second, paddle, RelationType.USES));
        Statement selected = statement("selected", first, StatementCategory.EQUIPMENT, StatementStatus.DEMO);
        Statement other = statement("other", second, StatementCategory.EQUIPMENT, StatementStatus.DEMO);
        other.addReference(paddle);
        flushAndDetach();

        var result = search.search(request("", first, List.of(StatementCategory.EQUIPMENT), Set.of(), Map.of(), 0, 10));

        assertThat(result.statements()).extracting(SearchResult.StatementView::id).containsExactly(selected.getId());
    }

    @Test
    void distinguishesConfirmedConditionsUnknownRestrictionsAndUnspecifiedClaims() {
        Topic sport = topic("sport", "Тестовый спорт", TopicType.SPORT);
        Statement ranged = statement("ranged", sport, StatementCategory.CONDITIONS, StatementStatus.CANDIDATE);
        ranged.addCondition("search-wind", ConditionKind.WIND_SPEED, "Ветер от 0 до 5 м/с", 0.0, 5.0);
        ranged.addCondition("search-supervision", ConditionKind.WITH_INSTRUCTOR, "С инструктором");
        Source source = sources.save(new Source("search-source", "Тестовый источник", "https://example.org/test", false));
        ranged.addEvidence("search-evidence", source, "Тестовый фрагмент", "Раздел 1");
        ranged.review(StatementStatus.VERIFIED, "Проверяющий", "Проверено по источнику");
        Statement conditional = statement("conditional", sport, StatementCategory.CONDITIONS, StatementStatus.DEMO);
        conditional.addCondition("search-water-temp", ConditionKind.WATER_TEMPERATURE,
                "Температура воды от 10 до 20 °C", 10.0, 20.0);
        Statement unspecified = statement("unspecified", sport, StatementCategory.CONDITIONS, StatementStatus.DEMO);
        flushAndDetach();

        var result = search.search(request("ветер 5 м/с", sport, List.of(StatementCategory.CONDITIONS),
                Set.of(ConditionKind.WITH_INSTRUCTOR), Map.of(), 0, 10));

        assertThat(result.statements().getFirst().id()).isEqualTo(ranged.getId());
        assertThat(result.statements().getFirst().applicability()).isEqualTo("MATCHED");
        assertThat(result.statements().getFirst().review().reviewer()).isEqualTo("Проверяющий");
        assertThat(result.statements().getFirst().structuredConditions()).anySatisfy(condition -> {
            assertThat(condition.kind()).isEqualTo(ConditionKind.WIND_SPEED);
            assertThat(condition.maxValue()).isEqualTo(5.0);
            assertThat(condition.unit()).isEqualTo("м/с");
        });
        assertThat(result.statements()).filteredOn(view -> view.id().equals(conditional.getId()))
                .extracting(SearchResult.StatementView::applicability).containsExactly("CONDITIONAL");
        assertThat(result.statements()).filteredOn(view -> view.id().equals(unspecified.getId()))
                .extracting(SearchResult.StatementView::applicability).containsExactly("UNSPECIFIED");

        var outside = search.search(request("", sport, List.of(StatementCategory.CONDITIONS), Set.of(),
                Map.of(ConditionKind.WIND_SPEED, 5.01), 0, 10));
        assertThat(outside.statements()).extracting(SearchResult.StatementView::id).doesNotContain(ranged.getId());
    }

    @Test
    void structuredFiltersOverrideConflictingTextAndDetectsMultipleCategories() {
        Topic sport = topic("sport", "Тестовый спорт", TopicType.SPORT);
        Statement safety = statement("safety", sport, StatementCategory.SAFETY, StatementStatus.DEMO);
        Statement equipment = statement("equipment", sport, StatementCategory.EQUIPMENT, StatementStatus.DEMO);
        Statement general = statement("general", sport, StatementCategory.GENERAL, StatementStatus.DEMO);
        general.addCondition("search-experience", ConditionKind.EXPERIENCED, "Для опытных");
        flushAndDetach();

        var combined = search.search(request("безопасность и снаряжение", sport, List.of(), Set.of(), Map.of(), 0, 10));
        assertThat(combined.statements()).extracting(SearchResult.StatementView::id)
                .containsExactlyInAnyOrder(safety.getId(), equipment.getId());

        var explicit = search.search(request("безопасность новичку", sport, List.of(StatementCategory.GENERAL),
                Set.of(ConditionKind.EXPERIENCED), Map.of(), 0, 10));
        assertThat(explicit.statements()).extracting(SearchResult.StatementView::id).containsExactly(general.getId());
        assertThat(explicit.recognizedConditions()).containsExactly("Для опытных");
    }

    @Test
    void negativeExperienceDoesNotMatchBeginnerRestrictions() {
        Topic sport = topic("sport", "Тестовый спорт", TopicType.SPORT);
        Statement beginner = statement("beginner", sport, StatementCategory.SAFETY, StatementStatus.DEMO);
        beginner.addCondition("search-beginner", ConditionKind.BEGINNER, "Для начинающих");
        Statement unrestricted = statement("unrestricted", sport, StatementCategory.SAFETY, StatementStatus.DEMO);
        flushAndDetach();

        var result = search.search(request("безопасность: я не новичок", sport, List.of(), Set.of(), Map.of(), 0, 10));

        assertThat(result.statements()).extracting(SearchResult.StatementView::id).containsExactly(unrestricted.getId());
        assertThat(result.recognizedConditions()).containsExactly("Исключено: Для начинающих");
    }

    @Test
    void suggestsSingleCharacterTyposWithoutSilentlyChangingTheTopic() {
        topic("kayak", "Тесткаякинг", TopicType.SPORT);
        var result = search.search(new SearchRequest("тесткаякннг", null, null, null, null, true, 0, 10));

        assertThat(result.topics()).isEmpty();
        assertThat(result.statements()).isEmpty();
        assertThat(result.suggestions()).containsExactly("Тесткаякинг");
    }

    @Test
    void reportsTotalAndUsesStablePaginationWithoutDuplicatingReferences() {
        Topic sport = topic("sport", "Тестовый спорт", TopicType.SPORT);
        Topic paddle = topic("paddle", "Тестовое весло", TopicType.EQUIPMENT);
        Topic water = topic("water", "Тестовая вода", TopicType.ENVIRONMENT);
        for (int index = 0; index < 5; index++) {
            Statement statement = statement("page-" + index, sport, StatementCategory.GENERAL, StatementStatus.DEMO);
            statement.addReference(paddle);
            statement.addReference(water);
        }
        flushAndDetach();

        var result = search.search(request("", sport, List.of(), Set.of(), Map.of(), 1, 2));

        assertThat(result.total()).isEqualTo(5);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.statements()).extracting(SearchResult.StatementView::id)
                .containsExactly("search-page-2", "search-page-3");
    }

    @Test
    void rejectsInvalidPagingAndNumericFilters() {
        assertThatThrownBy(() -> new SearchRequest("", null, null, null, null, false, -1, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SearchRequest("", null, null, null, null, false, 0, 51))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SearchRequest("", null, null, Set.of(ConditionKind.WIND_SPEED), null, false, 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SearchRequest("", null, null, null,
                Map.of(ConditionKind.WIND_SPEED, Double.NaN), false, 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Topic topic(String id, String name, TopicType type) {
        return topics.save(new Topic("search-" + id, name, type));
    }

    private Statement statement(String id, Topic topic, StatementCategory category, StatementStatus status) {
        return statements.save(new Statement("search-" + id, topic, "Тестовое утверждение: " + id, category, status));
    }

    private SearchRequest request(String question, Topic topic, List<StatementCategory> categories,
                                  Set<ConditionKind> conditions, Map<ConditionKind, Double> numeric, int page, int size) {
        return new SearchRequest(question, topic.getId(), categories, conditions, numeric, true, page, size);
    }

    private void flushAndDetach() {
        entityManager.flush();
        entityManager.clear();
    }
}

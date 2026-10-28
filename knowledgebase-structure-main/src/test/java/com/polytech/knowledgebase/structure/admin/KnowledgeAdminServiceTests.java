package com.polytech.knowledgebase.structure.admin;

import com.polytech.knowledgebase.structure.domain.ConditionKind;
import com.polytech.knowledgebase.structure.domain.StatementCategory;
import com.polytech.knowledgebase.structure.domain.StatementStatus;
import com.polytech.knowledgebase.structure.domain.TopicType;
import com.polytech.knowledgebase.structure.seed.DemoDataSeeder;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static com.polytech.knowledgebase.structure.admin.AdminDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class KnowledgeAdminServiceTests {
    private static final String TOPIC_ID = "demo-topic-sup";

    @Autowired
    private KnowledgeAdminService admin;

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void prepare() throws IOException {
        seeder.seed();
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void createsCandidateWithoutManufacturingReviewMetadata() {
        StatementDto created = admin.createStatement(input("New guidance", List.of(), List.of(), null));
        entityManager.clear();

        StatementDto stored = admin.getStatement(created.id());
        assertThat(stored.status()).isEqualTo(StatementStatus.CANDIDATE);
        assertThat(stored.reviewedBy()).isNull();
        assertThat(stored.reviewedAt()).isNull();
        assertThat(stored.reviewNote()).isNull();
        assertThat(stored.reviews()).isEmpty();
        assertThat(admin.listStatements(StatementStatus.CANDIDATE, TOPIC_ID, "New guidance", 0, 10).items())
                .extracting(StatementDto::id).containsExactly(created.id());
    }

    @Test
    void refusesVerificationWithoutEvidence() {
        StatementDto candidate = admin.createStatement(input("Unsupported guidance", List.of(), List.of(), null));

        assertThatThrownBy(() -> admin.reviewStatement(candidate.id(),
                new ReviewInput(StatementStatus.VERIFIED, "Reviewer", "Checked", candidate.version())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("из источника, не отмеченного как демонстрационный");
    }

    @Test
    void refusesVerificationWithOnlyDemonstrationEvidence() {
        List<EvidenceInput> evidence = List.of(new EvidenceInput("demo-source-v1", "Example fragment", "Section 1"));
        StatementDto candidate = admin.createStatement(input("Demonstration evidence", List.of(), evidence, null));

        assertThatThrownBy(() -> admin.reviewStatement(candidate.id(),
                new ReviewInput(StatementStatus.VERIFIED, "Reviewer", "Checked", candidate.version())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("из источника, не отмеченного как демонстрационный");
    }

    @Test
    void persistsReviewerNoteTimestampAndReviewHistory() {
        StatementDto candidate = candidateWithRealEvidence();
        StatementDto verified = admin.reviewStatement(candidate.id(),
                new ReviewInput(StatementStatus.VERIFIED, "  Marina Reviewer  ", "  Checked against section 1  ",
                        candidate.version()));
        entityManager.clear();

        StatementDto stored = admin.getStatement(verified.id());
        assertThat(stored.status()).isEqualTo(StatementStatus.VERIFIED);
        assertThat(stored.version()).isGreaterThan(candidate.version());
        assertThat(stored.reviewedBy()).isEqualTo("Marina Reviewer");
        assertThat(stored.reviewedAt()).isNotNull();
        assertThat(stored.reviewNote()).isEqualTo("Checked against section 1");
        assertThat(stored.reviews()).containsExactly(new ReviewDto(StatementStatus.VERIFIED,
                "Marina Reviewer", stored.reviewedAt(), "Checked against section 1"));
    }

    @Test
    void requiresReviewerForEveryReviewDecision() {
        StatementDto candidate = admin.createStatement(input("Pending guidance", List.of(), List.of(), null));

        assertThatThrownBy(() -> admin.reviewStatement(candidate.id(),
                new ReviewInput(StatementStatus.REJECTED, "  ", "No supporting source", candidate.version())))
                .isInstanceOf(InvalidInputException.class)
                .satisfies(error -> assertThat(((InvalidInputException) error).getField()).isEqualTo("reviewer"));
    }

    @Test
    void requiresNoteForEveryReviewDecision() {
        StatementDto candidate = admin.createStatement(input("Pending guidance", List.of(), List.of(), null));

        assertThatThrownBy(() -> admin.reviewStatement(candidate.id(),
                new ReviewInput(StatementStatus.REJECTED, "Reviewer", "  ", candidate.version())))
                .isInstanceOf(InvalidInputException.class)
                .satisfies(error -> assertThat(((InvalidInputException) error).getField()).isEqualTo("note"));
    }

    @Test
    void editingVerifiedGuidanceRequiresNewReviewAndPreservesThePreviousDecision() {
        StatementDto candidate = candidateWithRealEvidence();
        StatementDto verified = admin.reviewStatement(candidate.id(),
                new ReviewInput(StatementStatus.VERIFIED, "Reviewer", "Source checked", candidate.version()));

        StatementDto revised = admin.updateStatement(verified.id(),
                input("Revised guidance", List.of(), evidenceInputs(verified), verified.version()));
        entityManager.clear();

        StatementDto stored = admin.getStatement(revised.id());
        assertThat(stored.text()).isEqualTo("Revised guidance");
        assertThat(stored.status()).isEqualTo(StatementStatus.CANDIDATE);
        assertThat(stored.reviewedBy()).isNull();
        assertThat(stored.reviewedAt()).isNull();
        assertThat(stored.reviewNote()).isNull();
        assertThat(stored.reviews()).hasSize(1);
        assertThat(stored.reviews().getFirst().status()).isEqualTo(StatementStatus.VERIFIED);
        assertThat(stored.reviews().getFirst().reviewer()).isEqualTo("Reviewer");
        assertThat(stored.reviews().getFirst().note()).isEqualTo("Source checked");
    }

    @Test
    void changingOnlyConditionsAndEvidenceInvalidatesVerification() {
        SourceDto source = realSource();
        StatementDto candidate = admin.createStatement(input("Unchanged guidance",
                List.of(condition(ConditionKind.WIND_SPEED, 0.0, 5.0)),
                List.of(new EvidenceInput(source.id(), "Original fragment", "Section 1")), null));
        StatementDto verified = admin.reviewStatement(candidate.id(),
                new ReviewInput(StatementStatus.VERIFIED, "Reviewer", "Original conditions checked", candidate.version()));

        admin.updateStatement(verified.id(), input(verified.text(),
                List.of(condition(ConditionKind.WIND_SPEED, 0.0, 3.0)),
                List.of(new EvidenceInput(source.id(), "Revised fragment", "Section 1")), verified.version()));
        entityManager.clear();

        StatementDto stored = admin.getStatement(verified.id());
        assertThat(stored.text()).isEqualTo(verified.text());
        assertThat(stored.status()).isEqualTo(StatementStatus.CANDIDATE);
        assertThat(stored.reviewedBy()).isNull();
        assertThat(stored.reviewedAt()).isNull();
        assertThat(stored.reviewNote()).isNull();
        assertThat(stored.conditions().getFirst().maxValue()).isEqualTo(3.0);
        assertThat(stored.evidence().getFirst().fragment()).isEqualTo("Revised fragment");
        assertThat(stored.reviews()).extracting(ReviewDto::status).containsExactly(StatementStatus.VERIFIED);
    }

    @Test
    void editingRejectedGuidanceReturnsItToCandidates() {
        StatementDto candidate = admin.createStatement(input("Pending guidance", List.of(), List.of(), null));
        StatementDto rejected = admin.reviewStatement(candidate.id(),
                new ReviewInput(StatementStatus.REJECTED, "Reviewer", "Needs a source", candidate.version()));

        StatementDto revised = admin.updateStatement(rejected.id(),
                input("Corrected guidance", List.of(), List.of(), rejected.version()));
        entityManager.clear();

        StatementDto stored = admin.getStatement(revised.id());
        assertThat(stored.status()).isEqualTo(StatementStatus.CANDIDATE);
        assertThat(stored.reviews()).extracting(ReviewDto::status).containsExactly(StatementStatus.REJECTED);
        assertThat(stored.reviewedBy()).isNull();
    }

    @Test
    void replacesConditionsAndEvidenceWithoutViolatingTheirUniqueKeys() {
        SourceDto source = realSource();
        StatementDto original = admin.createStatement(input("Initial guidance",
                List.of(condition(ConditionKind.WIND_SPEED, 0.0, 5.0)),
                List.of(new EvidenceInput(source.id(), "Original fragment", "Section 1")), null));

        StatementDto revised = admin.updateStatement(original.id(), input("Revised guidance",
                List.of(condition(ConditionKind.WIND_SPEED, 1.0, 4.0)),
                List.of(new EvidenceInput(source.id(), "Corrected fragment", "Section 1")), original.version()));
        entityManager.clear();

        StatementDto stored = admin.getStatement(revised.id());
        assertThat(stored.conditions()).containsExactly(
                new ConditionDto(ConditionKind.WIND_SPEED, "Wind range", 1.0, 4.0, "м/с"));
        assertThat(stored.evidence()).hasSize(1);
        assertThat(stored.evidence().getFirst().fragment()).isEqualTo("Corrected fragment");
        assertThat(stored.evidence().getFirst().id()).isNotEqualTo(original.evidence().getFirst().id());
        assertThat(stored.version()).isGreaterThan(original.version());
    }

    @Test
    void rejectsAReviewBasedOnAStaleVersion() {
        StatementDto candidate = admin.createStatement(input("Pending guidance", List.of(), List.of(), null));
        admin.reviewStatement(candidate.id(),
                new ReviewInput(StatementStatus.REJECTED, "Reviewer", "Needs a source", candidate.version()));

        assertThatThrownBy(() -> admin.reviewStatement(candidate.id(),
                new ReviewInput(StatementStatus.CANDIDATE, "Editor", "Try again", candidate.version())))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void rejectsAnEditBasedOnAStaleVersion() {
        StatementDto candidate = admin.createStatement(input("Pending guidance", List.of(), List.of(), null));
        admin.updateStatement(candidate.id(), input("First edit", List.of(), List.of(), candidate.version()));

        assertThatThrownBy(() -> admin.updateStatement(candidate.id(),
                input("Stale edit", List.of(), List.of(), candidate.version())))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void rejectsContradictoryCategoricalConditions() {
        List<ConditionInput> contradictory = List.of(
                new ConditionInput(ConditionKind.WITH_INSTRUCTOR, "With an instructor", null, null),
                new ConditionInput(ConditionKind.SOLO, "On your own", null, null));

        assertThatThrownBy(() -> admin.createStatement(input("Contradictory guidance", contradictory, List.of(), null)))
                .isInstanceOf(InvalidInputException.class)
                .satisfies(error -> assertThat(((InvalidInputException) error).getField()).isEqualTo("conditions"));
    }

    @Test
    void rejectsAnInvertedNumericRange() {
        assertThatThrownBy(() -> admin.createStatement(input("Invalid range",
                List.of(condition(ConditionKind.WIND_SPEED, 8.0, 3.0)), List.of(), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Нижняя граница");
    }

    @Test
    void rejectsNonFiniteNumericBounds() {
        assertThatThrownBy(() -> admin.createStatement(input("Invalid range",
                List.of(condition(ConditionKind.WIND_SPEED, 0.0, Double.POSITIVE_INFINITY)), List.of(), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("конечными числами");
    }

    @Test
    void rejectsANumericConditionWithoutBounds() {
        assertThatThrownBy(() -> admin.createStatement(input("Invalid range",
                List.of(condition(ConditionKind.WATER_TEMPERATURE, null, null)), List.of(), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("хотя бы одну границу");
    }

    @Test
    void rejectsNegativeWaveHeights() {
        assertThatThrownBy(() -> admin.createStatement(input("Invalid range",
                List.of(condition(ConditionKind.WAVE_HEIGHT, -1.0, 2.0)), List.of(), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("отрицательными");
    }

    @Test
    void rejectsNumericBoundsOnACategoricalCondition() {
        assertThatThrownBy(() -> admin.createStatement(input("Invalid range",
                List.of(condition(ConditionKind.BEGINNER, 1.0, 2.0)), List.of(), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("категориального условия");
    }

    @Test
    void supportsOpenEndedTemperatureRangesAndExpandedCategoricalConditions() {
        List<ConditionInput> conditions = List.of(
                new ConditionInput(ConditionKind.WITH_INSTRUCTOR, "With an instructor", null, null),
                new ConditionInput(ConditionKind.FRESH_WATER, "Fresh water", null, null),
                new ConditionInput(ConditionKind.INLAND_WATER, "Inland lake", null, null),
                new ConditionInput(ConditionKind.AIR_TEMPERATURE, "Cold air", null, -2.0));
        StatementDto created = admin.createStatement(input("Conditions guidance", conditions, List.of(), null));
        entityManager.clear();

        StatementDto stored = admin.getStatement(created.id());
        assertThat(stored.conditions()).hasSize(4);
        assertThat(stored.conditions()).filteredOn(condition -> condition.kind() == ConditionKind.AIR_TEMPERATURE)
                .containsExactly(new ConditionDto(ConditionKind.AIR_TEMPERATURE, "Cold air", null, -2.0, "°C"));
    }

    @Test
    void refusesToTurnADemonstrationSourceIntoRealEvidence() {
        SourceDto demo = admin.catalog().sources().stream().filter(SourceDto::demonstration).findFirst().orElseThrow();

        assertThatThrownBy(() -> admin.updateSource(demo.id(), new SourceInput(demo.title(), demo.location(),
                false, "New author", null, null)))
                .isInstanceOf(InvalidInputException.class)
                .satisfies(error -> assertThat(((InvalidInputException) error).getField()).isEqualTo("demonstration"));
    }

    @Test
    void refusesToMoveVerifiedEvidenceToAnotherDocumentByChangingTheSourceLocation() {
        StatementDto candidate = candidateWithRealEvidence();
        StatementDto verified = admin.reviewStatement(candidate.id(),
                new ReviewInput(StatementStatus.VERIFIED, "Reviewer", "Original document checked", candidate.version()));
        EvidenceDto originalEvidence = verified.evidence().getFirst();
        SourceDto originalSource = admin.catalog().sources().stream()
                .filter(source -> source.id().equals(originalEvidence.sourceId())).findFirst().orElseThrow();

        assertThatThrownBy(() -> admin.updateSource(originalSource.id(), new SourceInput("Another document",
                "https://example.org/another-document", false, "Another author", null, null)))
                .isInstanceOf(InvalidInputException.class)
                .satisfies(error -> assertThat(((InvalidInputException) error).getField()).isEqualTo("location"));
        entityManager.clear();

        StatementDto stored = admin.getStatement(verified.id());
        SourceDto storedSource = admin.catalog().sources().stream()
                .filter(source -> source.id().equals(originalSource.id())).findFirst().orElseThrow();
        assertThat(storedSource).isEqualTo(originalSource);
        assertThat(stored.status()).isEqualTo(StatementStatus.VERIFIED);
        assertThat(stored.version()).isEqualTo(verified.version());
        assertThat(stored.evidence()).containsExactly(originalEvidence);
        assertThat(stored.reviewedBy()).isEqualTo("Reviewer");
        assertThat(stored.reviewNote()).isEqualTo("Original document checked");
        assertThat(stored.reviews()).extracting(ReviewDto::status).containsExactly(StatementStatus.VERIFIED);
    }

    @Test
    void refusesReviewsOfDemonstrationStatements() {
        StatementDto demo = admin.getStatement("demo-sup-general");

        assertThatThrownBy(() -> admin.reviewStatement(demo.id(),
                new ReviewInput(StatementStatus.VERIFIED, "Reviewer", "Checked", demo.version())))
                .isInstanceOf(InvalidInputException.class)
                .satisfies(error -> assertThat(((InvalidInputException) error).getField()).isEqualTo("status"));
    }

    @Test
    void retainsSourceMetadataAfterCreationAndEditing() {
        LocalDate published = LocalDate.of(2025, 3, 20);
        LocalDate accessed = LocalDate.of(2025, 4, 2);
        SourceDto created = admin.createSource(new SourceInput("Official guide", "https://example.org/guide",
                false, "  Sports association  ", published, accessed));
        entityManager.clear();

        SourceDto stored = admin.catalog().sources().stream()
                .filter(source -> source.id().equals(created.id())).findFirst().orElseThrow();
        assertThat(stored.author()).isEqualTo("Sports association");
        assertThat(stored.publishedOn()).isEqualTo(published);
        assertThat(stored.accessedOn()).isEqualTo(accessed);

        admin.updateSource(created.id(), new SourceInput("Revised official guide", created.location(),
                false, "Another association", published, accessed.plusDays(1)));
        entityManager.clear();

        SourceDto revised = admin.catalog().sources().stream()
                .filter(source -> source.id().equals(created.id())).findFirst().orElseThrow();
        assertThat(revised.title()).isEqualTo("Revised official guide");
        assertThat(revised.author()).isEqualTo("Another association");
        assertThat(revised.accessedOn()).isEqualTo(accessed.plusDays(1));
    }

    @Test
    void rejectsDuplicateTopicNames() {
        admin.createTopic(new TopicInput("Test sport", TopicType.SPORT, List.of("Test alias")));

        assertThatThrownBy(() -> admin.createTopic(new TopicInput("Test sport", TopicType.EQUIPMENT, List.of())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void canReplaceSynonymsWhileRetainingAnExistingName() {
        TopicDto created = admin.createTopic(new TopicInput("Test sport", TopicType.SPORT,
                List.of("Retained alias", "Old alias")));

        admin.updateTopic(created.id(), new TopicInput("Renamed sport", TopicType.SPORT,
                List.of("Retained alias", "New alias")));
        entityManager.clear();

        TopicDto stored = admin.catalog().topics().stream()
                .filter(topic -> topic.id().equals(created.id())).findFirst().orElseThrow();
        assertThat(stored.name()).isEqualTo("Renamed sport");
        assertThat(stored.synonyms()).containsExactlyInAnyOrder("Retained alias", "New alias");
    }

    private StatementDto candidateWithRealEvidence() {
        SourceDto source = realSource();
        return admin.createStatement(input("Source-backed guidance", List.of(),
                List.of(new EvidenceInput(source.id(), "Supporting fragment", "Section 1")), null));
    }

    private SourceDto realSource() {
        return admin.createSource(new SourceInput("Official test guide", "https://example.org/test-guide",
                false, "Sports association", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 2, 1)));
    }

    private StatementInput input(String text, List<ConditionInput> conditions,
                                 List<EvidenceInput> evidence, Long version) {
        return new StatementInput(TOPIC_ID, text, StatementCategory.SAFETY,
                List.of("demo-topic-paddle"), conditions, evidence, false, version);
    }

    private ConditionInput condition(ConditionKind kind, Double minValue, Double maxValue) {
        return new ConditionInput(kind, "Wind range", minValue, maxValue);
    }

    private List<EvidenceInput> evidenceInputs(StatementDto statement) {
        return statement.evidence().stream()
                .map(evidence -> new EvidenceInput(evidence.sourceId(), evidence.fragment(), evidence.locator()))
                .toList();
    }
}

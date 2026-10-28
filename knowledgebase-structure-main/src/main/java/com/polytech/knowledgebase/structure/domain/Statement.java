package com.polytech.knowledgebase.structure.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "statements", indexes = @Index(name = "idx_statement_search", columnList = "topic_id,status,category"))
@Check(constraints = "length(trim(id)) > 0 and length(trim(text)) > 0")
public class Statement extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(nullable = false, length = 4000)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatementCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatementStatus status;

    @Column(length = 200)
    private String reviewedBy;

    private Instant reviewedAt;

    @Column(length = 2000)
    private String reviewNote;

    @ManyToMany
    @JoinTable(name = "statement_references",
            joinColumns = @JoinColumn(name = "statement_id"),
            inverseJoinColumns = @JoinColumn(name = "topic_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"statement_id", "topic_id"}))
    private Set<Topic> referencedTopics = new LinkedHashSet<>();

    @OneToMany(mappedBy = "statement", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private Set<StatementCondition> conditions = new LinkedHashSet<>();

    @OneToMany(mappedBy = "statement", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private Set<Evidence> evidence = new LinkedHashSet<>();

    @OneToMany(mappedBy = "statement", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @OrderBy("reviewedAt, id")
    private Set<StatementReview> reviews = new LinkedHashSet<>();

    protected Statement() {
    }

    public Statement(String id, Topic topic, String text, StatementCategory category, StatementStatus status) {
        super(id);
        this.topic = Objects.requireNonNull(topic);
        this.text = required(text, "text", 4000);
        this.category = Objects.requireNonNull(category);
        this.status = Objects.requireNonNull(status);
    }

    public void revise(Topic topic, String text, StatementCategory category) {
        Topic revisedTopic = Objects.requireNonNull(topic);
        String revisedText = required(text, "text", 4000);
        StatementCategory revisedCategory = Objects.requireNonNull(category);

        this.topic = revisedTopic;
        this.text = revisedText;
        this.category = revisedCategory;

        if (status == StatementStatus.VERIFIED || status == StatementStatus.REJECTED) {
            status = StatementStatus.CANDIDATE;
            reviewedBy = null;
            reviewedAt = null;
            reviewNote = null;
        }
    }

    public void review(StatementStatus targetStatus, String reviewer, String note) {
        Objects.requireNonNull(targetStatus);
        String validatedReviewer = required(reviewer, "reviewer", 200);
        String validatedNote = required(note, "review note", 2000);

        if (targetStatus == StatementStatus.DEMO) {
            throw new IllegalArgumentException("Проверка не может присвоить статус демонстрационных данных.");
        }

        if (targetStatus == StatementStatus.VERIFIED) {
            if (status == StatementStatus.DEMO) {
                throw new IllegalArgumentException("Демонстрационные утверждения нельзя подтверждать.");
            }

            boolean hasRealEvidence = evidence.stream().anyMatch(item -> !item.getSource().isDemonstration());
            if (!hasRealEvidence) {
                throw new IllegalArgumentException(
                        "Для подтверждения нужен фрагмент из источника, не отмеченного как демонстрационный.");
            }
        }

        Instant now = Instant.now();
        reviews.add(new StatementReview("review-" + UUID.randomUUID(), this, targetStatus,
                validatedReviewer, now, validatedNote));

        status = targetStatus;
        reviewedBy = validatedReviewer;
        reviewedAt = now;
        reviewNote = validatedNote;
    }

    public void addReference(Topic topic) {
        referencedTopics.add(Objects.requireNonNull(topic));
    }

    public void clearReferences() {
        referencedTopics.clear();
    }

    public void addCondition(String id, ConditionKind kind, String text) {
        conditions.add(new StatementCondition(id, this, kind, text));
    }

    public void addCondition(String id, ConditionKind kind, String text, Double minValue, Double maxValue) {
        conditions.add(new StatementCondition(id, this, kind, text, minValue, maxValue));
    }

    public void clearConditions() {
        conditions.clear();
    }

    public void addEvidence(String id, Source source, String fragment, String locator) {
        evidence.add(new Evidence(id, this, source, fragment, locator));
    }

    public void clearEvidence() {
        evidence.clear();
    }

    public Set<Topic> getReferencedTopics() {
        return Collections.unmodifiableSet(referencedTopics);
    }

    public Set<StatementCondition> getConditions() {
        return Collections.unmodifiableSet(conditions);
    }

    public Set<Evidence> getEvidence() {
        return Collections.unmodifiableSet(evidence);
    }

    public Set<StatementReview> getReviews() {
        return Collections.unmodifiableSet(reviews);
    }

    public Topic getTopic() {
        return topic;
    }

    public String getText() {
        return text;
    }

    public StatementCategory getCategory() {
        return category;
    }

    public StatementStatus getStatus() {
        return status;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getReviewNote() {
        return reviewNote;
    }
}

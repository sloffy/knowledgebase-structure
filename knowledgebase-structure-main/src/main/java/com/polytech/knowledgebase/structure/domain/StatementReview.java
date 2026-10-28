package com.polytech.knowledgebase.structure.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "statement_reviews", indexes = @Index(name = "idx_review_statement", columnList = "statement_id,reviewed_at"))
@Check(constraints = "length(trim(id)) > 0 and length(trim(reviewer)) > 0 and length(trim(note)) > 0")
public class StatementReview extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "statement_id", nullable = false, updatable = false)
    private Statement statement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private StatementStatus status;

    @Column(nullable = false, length = 200, updatable = false)
    private String reviewer;

    @Column(nullable = false, updatable = false)
    private Instant reviewedAt;

    @Column(nullable = false, length = 2000, updatable = false)
    private String note;

    protected StatementReview() {
    }

    StatementReview(String id, Statement statement, StatementStatus status,
                    String reviewer, Instant reviewedAt, String note) {
        super(id);
        this.statement = Objects.requireNonNull(statement);
        this.status = Objects.requireNonNull(status);
        this.reviewer = required(reviewer, "reviewer", 200);
        this.reviewedAt = Objects.requireNonNull(reviewedAt);
        this.note = required(note, "review note", 2000);
    }

    public Statement getStatement() {
        return statement;
    }

    public StatementStatus getStatus() {
        return status;
    }

    public String getReviewer() {
        return reviewer;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getNote() {
        return note;
    }
}

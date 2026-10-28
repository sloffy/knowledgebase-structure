package com.polytech.knowledgebase.structure.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "evidence", uniqueConstraints = @UniqueConstraint(columnNames = {"statement_id", "source_id", "locator"}))
@Check(constraints = "length(trim(id)) > 0 and length(trim(fragment)) > 0 and length(trim(locator)) > 0")
public class Evidence extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "statement_id", nullable = false)
    private Statement statement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Column(nullable = false, length = 4000)
    private String fragment;

    @Column(nullable = false, length = 300)
    private String locator;

    protected Evidence() {
    }

    public Evidence(String id, Statement statement, Source source, String fragment, String locator) {
        super(id);
        this.statement = Objects.requireNonNull(statement);
        this.source = Objects.requireNonNull(source);
        this.fragment = required(fragment, "fragment", 4000);
        this.locator = required(locator, "locator", 300);
    }

    public Statement getStatement() {
        return statement;
    }

    public Source getSource() {
        return source;
    }

    public String getFragment() {
        return fragment;
    }

    public String getLocator() {
        return locator;
    }
}

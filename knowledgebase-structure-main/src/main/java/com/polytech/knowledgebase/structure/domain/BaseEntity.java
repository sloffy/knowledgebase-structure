package com.polytech.knowledgebase.structure.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import java.time.Instant;

@MappedSuperclass
public abstract class BaseEntity {
    @Id
    @Column(length = 120, nullable = false, updatable = false)
    private String id;

    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long version;

    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    protected BaseEntity() {
    }

    protected BaseEntity(String id) {
        this.id = required(id, "id", 120);
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    protected static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " must be non-blank and at most " + maxLength + " characters");
        }

        return value.strip();
    }

    protected static String optional(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return required(value, field, maxLength);
    }
}

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
@Table(name = "topic_synonyms", uniqueConstraints = @UniqueConstraint(columnNames = {"topic_id", "name"}))
@Check(constraints = "length(trim(id)) > 0 and length(trim(name)) > 0")
public class TopicSynonym extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(nullable = false, length = 200)
    private String name;

    protected TopicSynonym() {
    }

    public TopicSynonym(String id, Topic topic, String name) {
        super(id);
        this.topic = Objects.requireNonNull(topic);
        this.name = required(name, "name", 200);
    }

    public Topic getTopic() {
        return topic;
    }

    public String getName() {
        return name;
    }
}

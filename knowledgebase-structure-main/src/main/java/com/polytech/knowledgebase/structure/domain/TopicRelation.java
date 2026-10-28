package com.polytech.knowledgebase.structure.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "topic_relations", uniqueConstraints = @UniqueConstraint(columnNames = {"from_topic_id", "to_topic_id", "type"}))
@Check(constraints = "length(trim(id)) > 0 and from_topic_id <> to_topic_id")
public class TopicRelation extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_topic_id", nullable = false)
    private Topic fromTopic;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_topic_id", nullable = false)
    private Topic toTopic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RelationType type;

    protected TopicRelation() {
    }

    public TopicRelation(String id, Topic from, Topic to, RelationType type) {
        super(id);
        this.fromTopic = Objects.requireNonNull(from);
        this.toTopic = Objects.requireNonNull(to);
        this.type = Objects.requireNonNull(type);

        if (from.getId().equals(to.getId())) {
            throw new IllegalArgumentException("A relation needs two distinct topics");
        }
    }

    public Topic getFromTopic() {
        return fromTopic;
    }

    public Topic getToTopic() {
        return toTopic;
    }

    public RelationType getType() {
        return type;
    }
}

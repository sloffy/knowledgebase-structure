package com.polytech.knowledgebase.structure.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "topics")
@Check(constraints = "length(trim(id)) > 0 and length(trim(name)) > 0")
public class Topic extends BaseEntity {
    @Column(nullable = false, length = 200, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TopicType type;

    @OneToMany(mappedBy = "topic", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private Set<TopicSynonym> synonyms = new LinkedHashSet<>();

    protected Topic() {
    }

    public Topic(String id, String name, TopicType type) {
        super(id);
        revise(name, type);
    }

    public void revise(String name, TopicType type) {
        String revisedName = required(name, "name", 200);
        TopicType revisedType = Objects.requireNonNull(type);

        this.name = revisedName;
        this.type = revisedType;
    }

    public void addSynonym(String id, String name) {
        synonyms.add(new TopicSynonym(id, this, name));
    }

    public void clearSynonyms() {
        synonyms.clear();
    }

    public Set<TopicSynonym> getSynonyms() {
        return Collections.unmodifiableSet(synonyms);
    }

    public String getName() {
        return name;
    }

    public TopicType getType() {
        return type;
    }
}

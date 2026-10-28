package com.polytech.knowledgebase.structure.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "sources")
@Check(constraints = "length(trim(id)) > 0 and length(trim(title)) > 0 and length(trim(location)) > 0")
public class Source extends BaseEntity {
    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, length = 1000, unique = true)
    private String location;

    @Column(nullable = false)
    private boolean demonstration;

    @Column(length = 300)
    private String author;

    private LocalDate publishedOn;
    private LocalDate accessedOn;

    protected Source() {
    }

    public Source(String id, String title, String location, boolean demonstration) {
        this(id, title, location, demonstration, null, null, null);
    }

    public Source(String id, String title, String location, boolean demonstration,
                  String author, LocalDate publishedOn, LocalDate accessedOn) {
        super(id);
        revise(title, location, demonstration, author, publishedOn, accessedOn);
    }

    public void revise(String title, String location, boolean demonstration) {
        revise(title, location, demonstration, author, publishedOn, accessedOn);
    }

    public void revise(String title, String location, boolean demonstration,
                       String author, LocalDate publishedOn, LocalDate accessedOn) {
        String revisedTitle = required(title, "title", 300);
        String revisedLocation = required(location, "location", 1000);
        String revisedAuthor = optional(author, "author", 300);

        this.title = revisedTitle;
        this.location = revisedLocation;
        this.demonstration = demonstration;
        this.author = revisedAuthor;
        this.publishedOn = publishedOn;
        this.accessedOn = accessedOn;
    }

    public String getTitle() {
        return title;
    }

    public String getLocation() {
        return location;
    }

    public boolean isDemonstration() {
        return demonstration;
    }

    public String getAuthor() {
        return author;
    }

    public LocalDate getPublishedOn() {
        return publishedOn;
    }

    public LocalDate getAccessedOn() {
        return accessedOn;
    }
}

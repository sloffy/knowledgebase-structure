package com.polytech.knowledgebase.structure.web;

import com.polytech.knowledgebase.structure.admin.KnowledgeAdminService;
import com.polytech.knowledgebase.structure.domain.StatementStatus;
import com.polytech.knowledgebase.structure.search.KnowledgeSearchService;
import com.polytech.knowledgebase.structure.search.SearchRequest;
import com.polytech.knowledgebase.structure.search.SearchResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.polytech.knowledgebase.structure.admin.AdminDtos.*;

@RestController
@RequestMapping("/api")
public class KnowledgeController {
    private final KnowledgeAdminService admin;
    private final KnowledgeSearchService search;

    public KnowledgeController(KnowledgeAdminService admin, KnowledgeSearchService search) {
        this.admin = admin;
        this.search = search;
    }

    @GetMapping("/catalog")
    public Catalog catalog() {
        return admin.catalog();
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard() {
        return admin.dashboard();
    }

    @PostMapping("/search")
    public SearchResult search(@RequestBody SearchRequest request) {
        return search.search(request);
    }

    @GetMapping("/statements")
    public PageDto<StatementDto> statements(@RequestParam(required = false) StatementStatus status,
                                           @RequestParam(required = false) String topicId,
                                           @RequestParam(name = "q", required = false) String query,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return admin.listStatements(status, topicId, query, page, size);
    }

    @GetMapping("/statements/{id}")
    public StatementDto statement(@PathVariable String id) {
        return admin.getStatement(id);
    }

    @PostMapping("/statements")
    @ResponseStatus(HttpStatus.CREATED)
    public StatementDto createStatement(@RequestBody StatementInput input) {
        return admin.createStatement(input);
    }

    @PutMapping("/statements/{id}")
    public StatementDto updateStatement(@PathVariable String id, @RequestBody StatementInput input) {
        return admin.updateStatement(id, input);
    }

    @PostMapping("/statements/{id}/review")
    public StatementDto review(@PathVariable String id, @RequestBody ReviewInput input) {
        return admin.reviewStatement(id, input);
    }

    @PostMapping("/topics")
    @ResponseStatus(HttpStatus.CREATED)
    public TopicDto createTopic(@RequestBody TopicInput input) {
        return admin.createTopic(input);
    }

    @PutMapping("/topics/{id}")
    public TopicDto updateTopic(@PathVariable String id, @RequestBody TopicInput input) {
        return admin.updateTopic(id, input);
    }

    @PostMapping("/sources")
    @ResponseStatus(HttpStatus.CREATED)
    public SourceDto createSource(@RequestBody SourceInput input) {
        return admin.createSource(input);
    }

    @PutMapping("/sources/{id}")
    public SourceDto updateSource(@PathVariable String id, @RequestBody SourceInput input) {
        return admin.updateSource(id, input);
    }

    @GetMapping("/relations")
    public List<RelationDto> relations() {
        return admin.listRelations();
    }

    @PostMapping("/relations")
    @ResponseStatus(HttpStatus.CREATED)
    public RelationDto createRelation(@RequestBody RelationInput input) {
        return admin.createRelation(input);
    }
}

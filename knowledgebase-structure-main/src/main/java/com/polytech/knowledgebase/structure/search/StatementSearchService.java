package com.polytech.knowledgebase.structure.search;

import com.polytech.knowledgebase.structure.domain.Statement;
import com.polytech.knowledgebase.structure.repository.StatementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StatementSearchService {

    private final StatementRepository statementRepository;

    private Runnable searchBreakpoint;

    public void setSearchBreakpoint(Runnable breakpoint) {
        this.searchBreakpoint = breakpoint;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
    public List<Statement> searchTwoStep(String topicId) {

        List<String> foundIds = statementRepository.findIdsByTopic(topicId);
        
        if (foundIds.isEmpty()) {
            return List.of();
        }
        if (searchBreakpoint != null) {
            searchBreakpoint.run();
        }
        return statementRepository.findAllByIdInWithCollections(foundIds);
    }
}

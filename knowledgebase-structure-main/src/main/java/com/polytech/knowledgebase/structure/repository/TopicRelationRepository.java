package com.polytech.knowledgebase.structure.repository;

import com.polytech.knowledgebase.structure.domain.TopicRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TopicRelationRepository extends JpaRepository<TopicRelation, String> {
    @Query("""
            select relation from TopicRelation relation
            join fetch relation.fromTopic
            join fetch relation.toTopic
            where relation.fromTopic.id = :topicId or relation.toTopic.id = :topicId
            order by relation.id
            """)
    List<TopicRelation> findAdjacent(@Param("topicId") String topicId);
}

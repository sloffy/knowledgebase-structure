package com.polytech.knowledgebase.structure.repository;

import com.polytech.knowledgebase.structure.domain.ConditionKind;
import com.polytech.knowledgebase.structure.domain.Statement;
import com.polytech.knowledgebase.structure.domain.StatementCategory;
import com.polytech.knowledgebase.structure.domain.StatementStatus;
import com.polytech.knowledgebase.structure.domain.TopicType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface StatementRepository extends JpaRepository<Statement, String>, JpaSpecificationExecutor<Statement> {
    List<Statement> findByTopicIdAndStatusInOrderByIdAsc(String topicId, Collection<StatementStatus> statuses);

    /**
     * Predicates shared by the page query and the total count, so both always agree.
     * <p>
     * The categorical condition kinds are passed as ready lists because a condition group
     * ({@link ConditionKind#getDimension()}) is a property of the enum, not a database column.
     * The numeric bounds are compared per kind, so every numeric kind brings its own threshold.
     * A null threshold disables the check for that kind.
     */
    String SEARCH_PREDICATES = """
            s.status in :statuses
                and s.category in :categories
                and (s.topic.id in :primaryTopicIds
                    or s.id in (select mentioned.id from Statement mentioned
                                join mentioned.referencedTopics linked where linked.id in :referenceTopicIds))
                and (:restrictToSportTopic = false
                    or s.topic.type <> :sportType
                    or s.topic.id = :topicId)
                and not exists (select 1 from StatementCondition c
                               where c.statement = s and c.kind in :excludedKinds)
                and not exists (select 1 from StatementCondition c
                               where c.statement = s and c.kind in :conflictingKinds)
                and not exists (select 1 from StatementCondition c
                               where c.statement = s and c.kind = :windSpeedKind
                                 and ((c.minValue is not null and c.minValue > :windSpeedValue)
                                   or (c.maxValue is not null and c.maxValue < :windSpeedValue)))
                and not exists (select 1 from StatementCondition c
                               where c.statement = s and c.kind = :waveHeightKind
                                 and ((c.minValue is not null and c.minValue > :waveHeightValue)
                                   or (c.maxValue is not null and c.maxValue < :waveHeightValue)))
                and not exists (select 1 from StatementCondition c
                               where c.statement = s and c.kind = :waterTemperatureKind
                                 and ((c.minValue is not null and c.minValue > :waterTemperatureValue)
                                   or (c.maxValue is not null and c.maxValue < :waterTemperatureValue)))
                and not exists (select 1 from StatementCondition c
                               where c.statement = s and c.kind = :airTemperatureKind
                                 and ((c.minValue is not null and c.minValue > :airTemperatureValue)
                                   or (c.maxValue is not null and c.maxValue < :airTemperatureValue)))
            """;

    /**
     * Ranking, in the order the in-memory comparator used: own topic first, then a statement that
     * references the topic, then a merely related one; verified claims before the rest; more
     * matching conditions before fewer; more references to secondary topics; and finally the id, so
     * the order stays stable between pages.
     */
    String SEARCH_ORDER = """
            order by case when s.topic.id = :topicId then 3
                          when s.id in (select ranked.id from Statement ranked
                                        join ranked.referencedTopics linked where linked.id = :topicId) then 2
                          else 1 end desc,
                     case when s.status = :verifiedStatus then 0 else 1 end,
                     (select count(c) from StatementCondition c
                       where c.statement = s and c.kind in (:matchedKinds)) desc,
                     (select count(m) from Statement m join m.referencedTopics t
                       where m.id = s.id and t.id in (:secondaryTopics)) desc,
                     s.id asc
            """;

    /**
     * Identifiers of the requested page, filtered and ranked by the database, together with the
     * total number of matching claims.
     */
    @Query(value = "select s.id from Statement s where " + SEARCH_PREDICATES + " " + SEARCH_ORDER,
            countQuery = "select count(s) from Statement s where " + SEARCH_PREDICATES)
    Page<String> findSearchIds(
            @Param("primaryTopicIds") Collection<String> primaryTopicIds,
            @Param("referenceTopicIds") Collection<String> referenceTopicIds,
            @Param("statuses") Collection<StatementStatus> statuses,
            @Param("categories") Collection<StatementCategory> categories,
            @Param("topicId") String topicId,
            @Param("sportType") TopicType sportType,
            @Param("restrictToSportTopic") boolean restrictToSportTopic,
            @Param("excludedKinds") Collection<ConditionKind> excludedKinds,
            @Param("conflictingKinds") Collection<ConditionKind> conflictingKinds,
            @Param("windSpeedKind") ConditionKind windSpeedKind,
            @Param("windSpeedValue") Double windSpeedValue,
            @Param("waveHeightKind") ConditionKind waveHeightKind,
            @Param("waveHeightValue") Double waveHeightValue,
            @Param("waterTemperatureKind") ConditionKind waterTemperatureKind,
            @Param("waterTemperatureValue") Double waterTemperatureValue,
            @Param("airTemperatureKind") ConditionKind airTemperatureKind,
            @Param("airTemperatureValue") Double airTemperatureValue,
            @Param("matchedKinds") Collection<ConditionKind> matchedKinds,
            @Param("secondaryTopics") Collection<String> secondaryTopics,
            @Param("verifiedStatus") StatementStatus verifiedStatus,
            Pageable pageable);

    /** Loads complete objects for a single result page only. */
    @Query("""
            select distinct s from Statement s
            left join fetch s.topic
            left join fetch s.conditions
            left join fetch s.referencedTopics
            where s.id in :ids
            """)
    List<Statement> findPageWithDetails(@Param("ids") Collection<String> ids);

    @Query("select s.status, count(s) from Statement s group by s.status")
    List<Object[]> countGroupedByStatus();
}
package com.polytech.knowledgebase.structure.repository;

import com.polytech.knowledgebase.structure.domain.Statement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StatementRepository extends JpaRepository<Statement, String> {

    // ЭТАП 1: Быстрый поиск только ID по теме
    @Query("SELECT s.id FROM Statement s WHERE s.topic.id = :topicId")
    List<String> findIdsByTopic(@Param("topicId") String topicId);

    // ЭТАП 2: Жадная загрузка коллекций (условий и подтверждений) по выбранным ID
    @Query("SELECT DISTINCT s FROM Statement s " +
           "LEFT JOIN FETCH s.conditions " +
           "LEFT JOIN FETCH s.evidences " +
           "WHERE s.id IN :ids")
    List<Statement> findAllByIdInWithCollections(@Param("ids") List<String> ids);
}

package com.polytech.knowledgebase.structure.schema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adds the index the database-side search depends on.
 * <p>
 * The statement to topic links live in a join table that Hibernate creates and keeps up to date,
 * but it cannot declare a secondary index on it. The search resolves the topic of a claim with
 * {@code statement_references.topic_id}, so without that index every search reads the whole link
 * table.
 */
@Component
@Order(-5)
@ConditionalOnProperty(name = "spring.jpa.hibernate.ddl-auto", havingValue = "update")
public class SearchIndexInitializer implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(SearchIndexInitializer.class);
    private static final String INDEX_NAME = "idx_statement_references_topic";

    private final JdbcTemplate jdbc;

    public SearchIndexInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        Boolean postgres = jdbc.execute((ConnectionCallback<Boolean>) connection ->
                "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName()));
        if (!Boolean.TRUE.equals(postgres)) {
            return;
        }

        jdbc.execute("create index if not exists " + INDEX_NAME + " on statement_references (topic_id)");
        LOG.info("Ensured the search index {} on statement_references(topic_id).", INDEX_NAME);
    }
}

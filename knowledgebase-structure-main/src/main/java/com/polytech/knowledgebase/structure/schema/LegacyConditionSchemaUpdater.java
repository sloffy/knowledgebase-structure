package com.polytech.knowledgebase.structure.schema;

import com.polytech.knowledgebase.structure.domain.ConditionKind;
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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Updates only the enum check left behind by Hibernate's existing-schema update. */
@Component
@Order(-10)
@ConditionalOnProperty(name = "spring.jpa.hibernate.ddl-auto", havingValue = "update")
public class LegacyConditionSchemaUpdater implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(LegacyConditionSchemaUpdater.class);
    private static final Set<String> LEGACY_KINDS = Set.of(
            "BEGINNER", "EXPERIENCED", "CALM_WATER", "WAVES", "LIGHT_WIND", "STRONG_WIND");
    private static final Pattern ENUM_LITERAL = Pattern.compile("'([A-Z_]+)'");
    private static final String CONSTRAINT_NAME = "ck_statement_conditions_kind_supported";

    private final JdbcTemplate jdbc;

    public LegacyConditionSchemaUpdater(JdbcTemplate jdbc) {
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

        List<Constraint> legacy = jdbc.query("""
                select namespace.nspname, relation.relname, constraint_row.conname,
                       pg_catalog.pg_get_constraintdef(constraint_row.oid) as definition
                from pg_catalog.pg_constraint constraint_row
                join pg_catalog.pg_class relation on relation.oid = constraint_row.conrelid
                join pg_catalog.pg_namespace namespace on namespace.oid = relation.relnamespace
                join pg_catalog.pg_attribute attribute on attribute.attrelid = relation.oid
                    and attribute.attname = 'kind'
                where constraint_row.contype = 'c'
                    and namespace.nspname = current_schema()
                    and relation.relname = 'statement_conditions'
                    and constraint_row.conkey = array[attribute.attnum]::smallint[]
                """, (result, row) -> new Constraint(result.getString("nspname"), result.getString("relname"),
                result.getString("conname"), result.getString("definition")))
                .stream().filter(constraint -> isLegacyEnumCheck(constraint.definition())).toList();

        if (legacy.isEmpty()) {
            return;
        }

        Constraint first = legacy.getFirst();
        String table = identifier(first.schema()) + "." + identifier(first.table());
        String allowedKinds = Arrays.stream(ConditionKind.values()).map(kind -> "'" + kind.name() + "'")
                .collect(Collectors.joining(", "));

        // Adding the replacement first preserves an active check throughout the transaction.
        Boolean replacementExists = jdbc.queryForObject("""
                select exists (
                    select 1 from pg_catalog.pg_constraint constraint_row
                    join pg_catalog.pg_class relation on relation.oid = constraint_row.conrelid
                    join pg_catalog.pg_namespace namespace on namespace.oid = relation.relnamespace
                    where namespace.nspname = ? and relation.relname = ? and constraint_row.conname = ?
                )
                """, Boolean.class, first.schema(), first.table(), CONSTRAINT_NAME);

        if (Boolean.TRUE.equals(replacementExists)) {
            throw new IllegalStateException("Cannot replace the legacy enum check: the target constraint name already exists.");
        }

        jdbc.execute("alter table " + table + " add constraint " + identifier(CONSTRAINT_NAME)
                + " check (kind in (" + allowedKinds + "))");
        for (Constraint constraint : legacy) {
            jdbc.execute("alter table " + table + " drop constraint " + identifier(constraint.name()));
        }

        LOG.info("Expanded the legacy condition kind check for {}.{}", first.schema(), first.table());
    }

    static boolean isLegacyEnumCheck(String definition) {
        if (definition == null || definition.contains("'WIND_SPEED'")) {
            return false;
        }

        Set<String> literals = new HashSet<>();
        int literalCount = 0;
        Matcher matcher = ENUM_LITERAL.matcher(definition);
        while (matcher.find()) {
            literals.add(matcher.group(1));
            literalCount++;
        }
        if (!literals.equals(LEGACY_KINDS) || literalCount != LEGACY_KINDS.size()) {
            return false;
        }

        String expression = definition.replaceAll("::(?:character varying|varchar|text)(?:\\[\\])?", "")
                .replace("\"kind\"", "kind");
        if (Pattern.compile("\\bkind\\b").matcher(expression).results().count() != 1
                || !expression.matches("(?is).*(?:=\\s*ANY\\s*\\(|\\bIN\\s*\\().*")) {
            return false;
        }

        String remainder = ENUM_LITERAL.matcher(expression).replaceAll("")
                .replaceAll("(?i)\\b(?:CHECK|kind|ANY|ARRAY|IN)\\b", "");
        return remainder.matches("[\\s()\\[\\]=,]*");
    }

    private static String identifier(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    private record Constraint(String schema, String table, String name, String definition) {}
}

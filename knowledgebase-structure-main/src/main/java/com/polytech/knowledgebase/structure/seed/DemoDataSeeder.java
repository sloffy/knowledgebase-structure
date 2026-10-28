package com.polytech.knowledgebase.structure.seed;

import com.polytech.knowledgebase.structure.domain.*;
import com.polytech.knowledgebase.structure.repository.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class DemoDataSeeder {
    private final TopicRepository topics;
    private final StatementRepository statements;
    private final SourceRepository sources;
    private final TopicRelationRepository relations;
    private static final Map<ConditionKind, String> CONDITION_TEXT = Map.of(
            ConditionKind.BEGINNER, "Для начинающих",
            ConditionKind.EXPERIENCED, "Для опытных занимающихся",
            ConditionKind.CALM_WATER, "На спокойной воде",
            ConditionKind.WAVES, "На волнах",
            ConditionKind.LIGHT_WIND, "При слабом ветре",
            ConditionKind.STRONG_WIND, "При сильном ветре");

    public DemoDataSeeder(TopicRepository topics, StatementRepository statements,
                          SourceRepository sources, TopicRelationRepository relations) {
        this.topics = topics;
        this.statements = statements;
        this.sources = sources;
        this.relations = relations;
    }

    @Transactional(rollbackFor = IOException.class)
    public void seed() throws IOException {
        Map<String, Topic> catalog = new LinkedHashMap<>();
        catalog.put("sup", topic("sup", "Сапбординг", TopicType.SPORT,
                "сапбординга", "сапбординге", "сапбордингом", "сап", "сапа", "сапе", "sup", "sup boarding", "сап бординг"));
        catalog.put("kayak", topic("kayak", "Каякинг", TopicType.SPORT,
                "каякинга", "каякинге", "каякингом", "каяк", "каяке", "каяка", "kayaking"));
        catalog.put("windsurf", topic("windsurf", "Виндсёрфинг", TopicType.SPORT,
                "виндсерфинг", "виндсерфинга", "виндсерфинге", "виндсерфингом", "windsurfing"));
        catalog.put("paddle", topic("paddle", "Весло", TopicType.EQUIPMENT, "весла", "веслом"));
        catalog.put("board", topic("board", "Доска", TopicType.EQUIPMENT, "доску", "доске"));
        catalog.put("sail", topic("sail", "Парус", TopicType.EQUIPMENT, "паруса", "парусом"));
        catalog.put("wind", topic("wind", "Ветер", TopicType.ENVIRONMENT, "ветра", "ветре"));
        catalog.put("water", topic("water", "Вода", TopicType.ENVIRONMENT, "воде", "воду"));
        relation("sup-paddle", catalog.get("sup"), catalog.get("paddle"), RelationType.USES);
        relation("sup-board", catalog.get("sup"), catalog.get("board"), RelationType.USES);
        relation("kayak-paddle", catalog.get("kayak"), catalog.get("paddle"), RelationType.USES);
        relation("windsurf-sail", catalog.get("windsurf"), catalog.get("sail"), RelationType.USES);
        relation("windsurf-wind", catalog.get("windsurf"), catalog.get("wind"), RelationType.DEPENDS_ON);
        relation("sup-water", catalog.get("sup"), catalog.get("water"), RelationType.PRACTICED_ON);

        Source source = sources.findById("demo-source-v1").orElseGet(() -> sources.save(new Source(
                "demo-source-v1", "Учебные примеры MVP — не внешняя проверка",
                "classpath:demo-statements.tsv", true)));
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("demo-statements.tsv").getInputStream(), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }

                String[] row = line.split("\\t", -1);
                if (row.length != 6) {
                    throw new IllegalStateException("Invalid demo row: " + line);
                }

                String id = "demo-" + row[0];
                if (statements.existsById(id)) {
                    continue;
                }

                Statement statement = new Statement(id, Objects.requireNonNull(catalog.get(row[1])),
                        row[3], StatementCategory.valueOf(row[2]), StatementStatus.DEMO);
                if (!row[4].isBlank()) {
                    for (String name : row[4].split(",")) {
                        ConditionKind kind = ConditionKind.valueOf(name);
                        statement.addCondition(id + "-" + kind.name().toLowerCase(Locale.ROOT), kind, CONDITION_TEXT.get(kind));
                    }
                }
                if (!row[5].isBlank()) {
                    for (String reference : row[5].split(",")) {
                        statement.addReference(Objects.requireNonNull(catalog.get(reference)));
                    }
                }

                statement.addEvidence(id + "-evidence", source, row[3], "Запись " + row[0]);
                statements.save(statement);
            }
        }
    }

    private Topic topic(String key, String name, TopicType type, String... synonyms) {
        return topics.findById("demo-topic-" + key).orElseGet(() -> {
            Topic topic = new Topic("demo-topic-" + key, name, type);
            for (int i = 0; i < synonyms.length; i++) {
                topic.addSynonym("demo-synonym-" + key + "-" + i, synonyms[i]);
            }
            return topics.save(topic);
        });
    }
    private void relation(String key, Topic from, Topic to, RelationType type) {
        String id = "demo-relation-" + key;
        if (!relations.existsById(id)) {
            relations.save(new TopicRelation(id, from, to, type));
        }
    }
}

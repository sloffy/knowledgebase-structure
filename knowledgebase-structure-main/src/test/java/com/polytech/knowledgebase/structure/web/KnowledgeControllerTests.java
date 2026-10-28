package com.polytech.knowledgebase.structure.web;

import com.polytech.knowledgebase.structure.seed.DemoDataSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.main.web-application-type=servlet",
        "spring.datasource.url=jdbc:h2:mem:web-knowledge;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.demo.seed=false"
})
@ActiveProfiles("test")
class KnowledgeControllerTests {
    @LocalServerPort int port;
    @Autowired DemoDataSeeder seeder;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void prepareCatalog() throws IOException {
        seeder.seed();
    }

    @Test
    void servesDashboardAndCatalogWithNumericConditionMetadata() throws Exception {
        HttpResponse<String> page = send("GET", "/", null);
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("Поиск знаний", "Источники", "/app.js");

        JsonNode catalog = successful("GET", "/api/catalog", null, 200);
        assertThat(catalog.path("topics").valueStream().map(topic -> topic.path("id").asString()))
                .contains("demo-topic-sup", "demo-topic-paddle");
        JsonNode wind = catalog.path("conditionKinds").valueStream()
                .filter(kind -> kind.path("kind").asString().equals("WIND_SPEED")).findFirst().orElseThrow();
        assertThat(wind.path("numeric").booleanValue()).isTrue();
        assertThat(wind.path("unit").asString()).isEqualTo("м/с");
    }

    @Test
    void rejectsMalformedJsonInvalidRangesContradictoryConditionsAndStatuses() throws Exception {
        HttpResponse<String> malformed = send("POST", "/api/search", "{");
        assertThat(malformed.statusCode()).isEqualTo(400);
        assertThat(json.readTree(malformed.body()).has("message")).isTrue();

        Map<String, Object> invalidRange = statementInput("Тест диапазона", List.of(Map.of(
                "kind", "WIND_SPEED", "text", "Некорректный диапазон", "minValue", 8, "maxValue", 2)), List.of());
        assertThat(sendJson("POST", "/api/statements", invalidRange).statusCode()).isEqualTo(400);

        Map<String, Object> contradictory = statementInput("Тест опыта", List.of(
                Map.of("kind", "BEGINNER", "text", "Для начинающих"),
                Map.of("kind", "EXPERIENCED", "text", "Для опытных")), List.of());
        HttpResponse<String> conflict = sendJson("POST", "/api/statements", contradictory);
        assertThat(conflict.statusCode()).isEqualTo(400);
        assertThat(json.readTree(conflict.body()).path("fieldErrors").has("conditions")).isTrue();

        JsonNode candidate = successful("POST", "/api/statements",
                statementInput("Без подтверждающего источника", List.of(), List.of()), 201);
        String reviewPath = "/api/statements/" + candidate.path("id").asString() + "/review";
        assertThat(sendJson("POST", reviewPath, Map.of("status", "VERIFIED", "reviewer", "Редактор",
                "note", "Проверка без источника", "version", candidate.path("version").longValue())).statusCode())
                .isEqualTo(400);
        assertThat(sendJson("POST", reviewPath, Map.of("status", "APPROVED", "reviewer", "Редактор",
                "note", "Неизвестный статус", "version", candidate.path("version").longValue())).statusCode())
                .isEqualTo(400);
        assertThat(sendJson("POST", "/api/search", Map.of("question", "сапбординг", "page", 0, "size", 51))
                .statusCode()).isEqualTo(400);
    }

    @Test
    void createsVerifiesSearchesRevisesAndRejectsStaleWritesThroughHttp() throws Exception {
        JsonNode source = successful("POST", "/api/sources", Map.of(
                "title", "Источник для интеграционной проверки", "location", "https://example.org/" + UUID.randomUUID(),
                "demonstration", false, "author", "Тестовая организация", "publishedOn", "2026-01-15"), 201);
        List<Map<String, Object>> conditions = List.of(
                Map.of("kind", "CALM_WATER", "text", "На спокойной воде"),
                Map.of("kind", "WIND_SPEED", "text", "Ветер до 5 м/с", "minValue", 0, "maxValue", 5));
        List<Map<String, Object>> evidence = List.of(Map.of("sourceId", source.path("id").asString(),
                "fragment", "Фрагмент тестового документа", "locator", "Раздел 2"));
        Map<String, Object> input = statementInput("Утверждение для проверки HTTP", conditions, evidence);
        JsonNode created = successful("POST", "/api/statements", input, 201);
        String statementId = created.path("id").asString();
        String path = "/api/statements/" + statementId;
        assertThat(created.path("status").asString()).isEqualTo("CANDIDATE");

        JsonNode verified = successful("POST", path + "/review", Map.of("status", "VERIFIED",
                "reviewer", "Иван Редактор", "note", "Сверено с разделом 2", "version", created.path("version").longValue()), 200);
        assertThat(verified.path("status").asString()).isEqualTo("VERIFIED");
        assertThat(verified.path("reviewedBy").asString()).isEqualTo("Иван Редактор");
        assertThat(verified.path("reviewedAt").isString()).isTrue();
        assertThat(verified.path("reviews").size()).isEqualTo(1);

        Map<String, Object> query = Map.of("topicId", "demo-topic-sup", "categories", List.of("SAFETY"),
                "conditions", List.of("CALM_WATER"), "numericConditions", Map.of("WIND_SPEED", 5),
                "includeDemo", false, "page", 0, "size", 10);
        JsonNode found = successful("POST", "/api/search", query, 200);
        assertThat(found.path("statements").valueStream().map(item -> item.path("id").asString())).contains(statementId);
        JsonNode hit = found.path("statements").valueStream()
                .filter(item -> item.path("id").asString().equals(statementId)).findFirst().orElseThrow();
        assertThat(hit.path("applicability").asString()).isEqualTo("MATCHED");
        assertThat(hit.path("review").path("reviewer").asString()).isEqualTo("Иван Редактор");
        assertThat(hit.path("referencedTopics").get(0).path("id").asString()).isEqualTo("demo-topic-paddle");

        input.put("text", "Уточнённое утверждение после проверки");
        input.put("version", verified.path("version").longValue());
        JsonNode revised = successful("PUT", path, input, 200);
        assertThat(revised.path("status").asString()).isEqualTo("CANDIDATE");
        assertThat(revised.path("reviewedBy").isNull()).isTrue();
        assertThat(revised.path("reviews").size()).isEqualTo(1);
        assertThat(revised.path("version").longValue()).isGreaterThan(verified.path("version").longValue());

        JsonNode hidden = successful("POST", "/api/search", query, 200);
        assertThat(hidden.path("statements").valueStream().map(item -> item.path("id").asString())).doesNotContain(statementId);
        assertThat(sendJson("PUT", path, input).statusCode()).isEqualTo(409);
        assertThat(sendJson("POST", path + "/review", Map.of("status", "VERIFIED", "reviewer", "Другой редактор",
                "note", "Устаревшая версия", "version", verified.path("version").longValue())).statusCode()).isEqualTo(409);

        JsonNode latest = successful("GET", path, null, 200);
        assertThat(latest.path("text").asString()).isEqualTo("Уточнённое утверждение после проверки");
        JsonNode reverified = successful("POST", path + "/review", Map.of("status", "VERIFIED",
                "reviewer", "Иван Редактор", "note", "Повторная проверка", "version", latest.path("version").longValue()), 200);
        assertThat(reverified.path("reviews").size()).isEqualTo(2);
    }

    private Map<String, Object> statementInput(String text, List<Map<String, Object>> conditions,
                                               List<Map<String, Object>> evidence) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("topicId", "demo-topic-sup");
        input.put("text", text);
        input.put("category", "SAFETY");
        input.put("referencedTopicIds", List.of("demo-topic-paddle"));
        input.put("conditions", conditions);
        input.put("evidence", evidence);
        input.put("demonstration", false);
        return input;
    }

    private JsonNode successful(String method, String path, Object body, int status) throws Exception {
        HttpResponse<String> response = body == null ? send(method, path, null) : sendJson(method, path, body);
        assertThat(response.statusCode()).as("%s %s: %s", method, path, response.body()).isEqualTo(status);
        return json.readTree(response.body());
    }

    private HttpResponse<String> sendJson(String method, String path, Object body) throws Exception {
        return send(method, path, json.writeValueAsString(body));
    }

    private HttpResponse<String> send(String method, String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}

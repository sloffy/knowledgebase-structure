package com.polytech.knowledgebase.structure.console;

import com.polytech.knowledgebase.structure.domain.StatementStatus;
import com.polytech.knowledgebase.structure.search.SearchResult;
import org.springframework.stereotype.Component;
import java.io.PrintWriter;
import java.util.stream.Collectors;

@Component
public class ConsoleRenderer {
    public void render(SearchResult result, PrintWriter out) {
        out.println("Тема: " + (result.topics().isEmpty() ? "не определена" : result.topics().stream()
                .map(SearchResult.TopicView::name).collect(Collectors.joining(", "))));
        out.println("Категория: " + (result.category() == null ? "не определена" : result.category()));
        if (!result.message().isEmpty()) {
            out.println(result.message());
        }

        for (var statement : result.statements()) {
            out.printf("%n[%s] %s (%s)%n", statement.id(), statement.text(), statement.category());
            out.println("Статус: " + (statement.status() == StatementStatus.DEMO
                    ? "DEMO — демонстрационные данные" : statement.status().name()));
            out.println("Условия: " + (statement.conditions().isEmpty()
                    ? "не заданы в базе" : String.join("; ", statement.conditions())));
            if (statement.review() != null) {
                out.printf("Проверка: %s, %s — %s%n", statement.review().reviewer(),
                        statement.review().reviewedAt(), statement.review().note());
            }
            if (statement.sources().isEmpty()) {
                out.println("Источник и подтверждающий фрагмент отсутствуют.");
            }

            for (var source : statement.sources()) {
                out.printf("Источник: %s [%s]%s%n", source.title(), source.location(),
                        source.demonstration() ? " — учебный источник" : "");
                out.printf("Фрагмент (%s): %s%n", source.locator(), source.fragment());
            }
        }
        out.flush();
    }
}

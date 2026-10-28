package com.polytech.knowledgebase.structure.console;

import com.polytech.knowledgebase.structure.search.KnowledgeSearchService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import java.io.*;

@Component
@Order(10)
@ConditionalOnProperty(name = "app.console.enabled", havingValue = "true")
public class KnowledgeConsole implements CommandLineRunner {
    private final KnowledgeSearchService search;
    private final ConsoleRenderer renderer;
    private final boolean includeDemo;
    public KnowledgeConsole(KnowledgeSearchService search, ConsoleRenderer renderer,
                            @Value("${app.demo.include:true}") boolean includeDemo) {
        this.search = search;
        this.renderer = renderer;
        this.includeDemo = includeDemo;
    }
    @Override
    public void run(String... args) throws IOException {
        var streams = ConsoleStreams.open(System.console(), System.in, System.out);
        var out = streams.writer();
        var reader = streams.reader();
        out.println("База знаний по водным видам спорта. Введите вопрос; exit / выход — завершение.");
        if (includeDemo) out.println("Включены демонстрационные данные: учебные примеры, не проверенные рекомендации.");
        while (true) {
            out.print("> ");
            out.flush();
            String question = reader.readLine();
            if (question == null || question.strip().equalsIgnoreCase("exit") || question.strip().equalsIgnoreCase("выход")) break;
            renderer.render(search.search(question, includeDemo), out);
        }
        out.println("Работа завершена.");
        out.flush();
    }
}

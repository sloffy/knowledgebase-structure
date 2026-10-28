package com.polytech.knowledgebase.structure.search;

import com.polytech.knowledgebase.structure.domain.Statement;
import com.polytech.knowledgebase.structure.repository.StatementRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StatementSearchConcurrencyTest {

    @Autowired
    private StatementSearchService searchService;

    @Autowired
    private StatementRepository statementRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        searchService.setSearchBreakpoint(null);
        statementRepository.deleteAll();
    }

    @Test
    void testConcurrentSearchSnapshotConsistency() throws InterruptedException {
        String statementId = UUID.randomUUID().toString();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        
        tx.executeWithoutResult(status -> {
            Statement s = new Statement();
            s.setId(statementId);
            s.setStatus("CANDIDATE");
            s.setText("Первоначальный текст (версия 1)");
            statementRepository.save(s);
        });

        CountDownLatch searchStepOneDone = new CountDownLatch(1);
        CountDownLatch editTxCommitted = new CountDownLatch(1);

        searchService.setSearchBreakpoint(() -> {
            try {
                searchStepOneDone.countDown(); 
                editTxCommitted.await(5, TimeUnit.SECONDS); 
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        final List<Statement>[] container = new List[1];

        executor.submit(() -> {
            container[0] = searchService.searchTwoStep("some-topic-id");
        });

        executor.submit(() -> {
            try {
                searchStepOneDone.await(5, TimeUnit.SECONDS);
                tx.executeWithoutResult(status -> {
                    Statement s = statementRepository.findById(statementId).orElseThrow();
                    s.setStatus("VERIFIED");
                    s.setText("Текст изменен параллельно! (версия 2)");
                    statementRepository.saveAndFlush(s);
                });
                editTxCommitted.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        List<Statement> result = container[0];
        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getStatus()).isEqualTo("CANDIDATE");
        assertThat(result.get(0).getText()).isEqualTo("Первоначальный текст (версия 1)");
    }
}

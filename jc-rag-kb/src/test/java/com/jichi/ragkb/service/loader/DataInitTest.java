package com.jichi.ragkb.service.loader;

import com.jichi.ragkb.entity.KbDocument;
import com.jichi.ragkb.repository.DocChunkRepository;
import com.jichi.ragkb.repository.KbDocumentRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
@ActiveProfiles("dev")     // ★ 坑 1：必须激活 dev 才会跑 DataInitializer
class DataInitTest {
    @Autowired
    private KbDocumentRepository kbDocumentRepository;
    @Autowired
    private DocChunkRepository docChunkRepository;

    @Test
    void verifyTestDataLoaded() {
        // ★ 坑 2：异步任务可能没跑完——用 Awaitility 轮询等待
        //   最多等 60s（要给 Embedding API + DB 写入留出时间）；
        //   每 2s 查一次，命中目标就立即返回。
        Awaitility.await()
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    long docCount = kbDocumentRepository.countByStatus(KbDocument.DocumentStatus.DONE);
                    assertThat(docCount).isGreaterThanOrEqualTo(3);
                });

        long docCount = kbDocumentRepository.countByStatus(KbDocument.DocumentStatus.DONE);
        long chunkCount = docChunkRepository.count();

        assertThat(chunkCount).isGreaterThan(10);

        System.out.printf("已索引文档数：%d，分块总数：%d%n", docCount, chunkCount);
    }
}
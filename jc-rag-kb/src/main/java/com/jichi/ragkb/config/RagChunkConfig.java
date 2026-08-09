package com.jichi.ragkb.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RagChunkConfig {
    private final RagChunkProperties ragChunkProperties;

    @Bean
    public ChunkConfig chunkConfig() {
        log.info("RagChunkConfig.chunkConfig size={}, overlap={}", ragChunkProperties.getSize(), ragChunkProperties.getOverlap());
        return new ChunkConfig()
                .setChunkSize(ragChunkProperties.getSize())
                .setChunkOverlap(ragChunkProperties.getOverlap());
    }
}
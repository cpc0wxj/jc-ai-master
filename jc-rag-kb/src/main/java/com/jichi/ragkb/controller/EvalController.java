package com.jichi.ragkb.controller;

import com.jichi.ragkb.dto.ApiResponse;
import com.jichi.ragkb.dto.EvalReport;
import com.jichi.ragkb.entity.DocChunk;
import com.jichi.ragkb.entity.EvalDataset;
import com.jichi.ragkb.repository.DocChunkRepository;
import com.jichi.ragkb.repository.EvalDatasetRepository;
import com.jichi.ragkb.security.UserContext;
import com.jichi.ragkb.service.EvalService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * 评估接口
 * 提供评估触发、历史对比、评估数据集管理和 Chunk 查询功能
 */
@RestController
@RequestMapping("/api/v1/eval")
@RequiredArgsConstructor
public class EvalController {

    private final EvalService evalService;
    private final EvalDatasetRepository datasetRepository;
    private final DocChunkRepository chunkRepository;

    /**
     * 触发评估（管理员专用）
     */
    @PostMapping("/{kbId}/run")
    public ApiResponse<EvalReport> runEval(
            @PathVariable Long kbId,
            @RequestParam(defaultValue = "latest") String version) {
        EvalReport report = evalService.runEvaluation(kbId, version);
        return ApiResponse.ok(report);
    }

    /**
     * 查看历史评估对比
     */
    @GetMapping("/{kbId}/history")
    public ApiResponse<List<EvalReport>> getHistory(@PathVariable Long kbId) {
        return ApiResponse.ok(evalService.compareVersions(kbId));
    }

    // ==================== 评估数据集管理 ====================

    /**
     * 查询知识库的评估数据集
     */
    @GetMapping("/{kbId}/dataset")
    public ApiResponse<List<EvalDataset>> listDataset(@PathVariable Long kbId) {
        return ApiResponse.ok(datasetRepository.findByKbId(kbId));
    }

    /**
     * 新增评估问题
     */
    @PostMapping("/{kbId}/dataset")
    public ApiResponse<EvalDataset> addQuestion(
            @PathVariable Long kbId,
            @RequestBody EvalDatasetRequest req) {
        EvalDataset item = new EvalDataset()
                .setKbId(kbId)
                .setQuestion(req.getQuestion())
                .setExpectedAnswer(req.getExpectedAnswer())
                .setExpectedChunkIds(req.getExpectedChunkIds())
                .setCreatedBy(UserContext.getUserId());
        datasetRepository.save(item);
        return ApiResponse.ok(item);
    }

    /**
     * 更新评估问题（含回填 expectedChunkIds）
     */
    @PutMapping("/{kbId}/dataset/{id}")
    public ApiResponse<EvalDataset> updateQuestion(
            @PathVariable Long kbId,
            @PathVariable Long id,
            @RequestBody EvalDatasetRequest req) {
        EvalDataset item = datasetRepository.findById(id);
        if (Objects.isNull(item)) {
            throw new RuntimeException("评估数据不存在");
        }
        if (StringUtils.isNotBlank(req.getQuestion())) {
            item.setQuestion(req.getQuestion());
        }
        if (Objects.nonNull(req.getExpectedAnswer())) {
            item.setExpectedAnswer(req.getExpectedAnswer());
        }
        if (Objects.nonNull(req.getExpectedChunkIds())) {
            item.setExpectedChunkIds(req.getExpectedChunkIds());
        }
        datasetRepository.updateById(item);
        return ApiResponse.ok(item);
    }

    /**
     * 删除评估问题
     */
    @DeleteMapping("/{kbId}/dataset/{id}")
    public ApiResponse<Void> deleteQuestion(
            @PathVariable Long kbId,
            @PathVariable Long id) {
        datasetRepository.deleteById(id);
        return ApiResponse.ok(null);
    }

    // ==================== Chunk 查询（用于标注 expectedChunkIds） ====================

    /**
     * 查询知识库下的所有 Chunk（只返回 id、docId、chunkIndex 和内容摘要）
     */
    @GetMapping("/{kbId}/chunks")
    public ApiResponse<List<ChunkSummary>> listChunks(@PathVariable Long kbId) {
        List<DocChunk> chunks = chunkRepository.findByKbId(kbId);
        List<ChunkSummary> summaries = chunks.stream().map(c -> {
            String content = c.getContent().length() > 200
                    ? c.getContent().substring(0, 200) + "..."
                    : c.getContent();
            return new ChunkSummary()
                    .setId(c.getId())
                    .setDocId(c.getDocId())
                    .setChunkIndex(c.getChunkIndex())
                    .setContent(content)
                    .setTokenCount(c.getTokenCount());
        }).toList();
        return ApiResponse.ok(summaries);
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class EvalDatasetRequest {
        private String question;
        private String expectedAnswer;
        private Long[] expectedChunkIds;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class ChunkSummary {
        private Long id;
        private Long docId;
        private Integer chunkIndex;
        private String content;
        private Integer tokenCount;
    }
}

package com.jichi.ragkb.controller;

import com.jichi.ragkb.dto.ApiResponse;
import com.jichi.ragkb.dto.RagQueryRequest;
import com.jichi.ragkb.service.FullRagPipeline;
import com.jichi.ragkb.service.RagQueryService;
import com.jichi.ragkb.service.RagQueryServiceV2;
import com.jichi.ragkb.service.RagQueryServiceV3;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG 查询接口
 * 提供多版本查询入口，当前默认使用 FullRagPipeline（完整管线）
 */
@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
public class RagQueryController {
    private final RagQueryService ragQueryService;

    private final RagQueryServiceV2 ragQueryServiceV2;

    private final RagQueryServiceV3 ragQueryServiceV3;

    private final FullRagPipeline fullRagPipeline;

//    @PostMapping("/query")
//    public ApiResponse<String> query(@RequestBody RagQueryRequest ragQueryRequest) {
//        String result = ragQueryService.query(ragQueryRequest.getQuestion(), ragQueryRequest.getKbIdList());
//        return ApiResponse.ok(result);
//    }

        @PostMapping("/query")
        public ApiResponse<String> query(@RequestBody RagQueryRequest req) {
            return ApiResponse.ok(ragQueryServiceV2.query(req.getQuestion(), req.getKbIdList()));
        }

    //    @PostMapping("/query")
    //    public ApiResponse<String> query(@RequestBody RagQueryRequest req) {
    //        return ApiResponse.ok(ragQueryServiceV3.query(req.getQuestion(), req.getKbIds()));
    //    }

    // @PostMapping("/query")
    // public ApiResponse<RagResponse> query(@RequestBody RagQueryRequest req) {
    //     RagResponse result = fullRagPipeline.query(req.getQuestion(), req.getKbIds());
    //     return ApiResponse.ok(result);
    // }
}
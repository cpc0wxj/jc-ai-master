package com.jichi.ragkb.controller;

import com.jichi.ragkb.dto.ApiResponse;
import com.jichi.ragkb.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户反馈接口
 * 接收用户对 AI 回答的点赞/点踩
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/feedback")
public class FeedbackController {
    private final FeedbackService feedbackService;

    @PostMapping("/{messageId}")
    public ApiResponse<Void> submitFeedback(
            @PathVariable Long messageId,
            @RequestParam int feedback,
            @RequestParam(required = false) String comment) {
        feedbackService.submitFeedback(messageId, feedback, comment);
        return ApiResponse.ok(null);
    }
}
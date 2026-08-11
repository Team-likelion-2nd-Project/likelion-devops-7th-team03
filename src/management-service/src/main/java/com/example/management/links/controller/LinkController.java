package com.example.management.links.controller;

import com.example.management.auth.controller.argument.CurrentUser;
import com.example.management.common.api.ApiResponse;
import com.example.management.links.controller.dto.CreateLinkRequest;
import com.example.management.links.controller.dto.LinkResponse;
import com.example.management.links.service.LinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 링크 관리 API. @CurrentUser가 JWT subject를 내부 users.id로 변환해 주입한다. */
@RestController
@RequestMapping("/api/v1/links")
@RequiredArgsConstructor
public class LinkController {

    private final LinkService linkService;

    @PostMapping
    public ResponseEntity<ApiResponse<LinkResponse>> create(
            @CurrentUser Long userId,
            @Valid @RequestBody CreateLinkRequest request
    ) {
        LinkResponse response = linkService.create(userId, request);
        return ResponseEntity.ok(ApiResponse.success(200, response, "링크가 생성되었습니다."));
    }
}

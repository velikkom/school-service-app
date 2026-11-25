package com.okulservisi.backend.parent.controller;

import com.okulservisi.backend.parent.api.ParentApiPaths;
import com.okulservisi.backend.parent.dto.request.ParentStudentLinkCreateRequest;
import com.okulservisi.backend.parent.dto.response.ParentStudentLinkResponse;
import com.okulservisi.backend.parent.mapper.ParentStudentLinkMapper;
import com.okulservisi.backend.parent.service.ParentStudentLinkService;
import com.okulservisi.backend.security.annotation.AdminOnly;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** docs/API.md "4. Veli" — öğrenci bağlantıları, ADMIN only. */
@RestController
@RequestMapping(ParentApiPaths.PARENT_STUDENT_LINK_BASE)
@RequiredArgsConstructor
@AdminOnly
public class ParentStudentLinkController {

    private final ParentStudentLinkService parentStudentLinkService;
    private final ParentStudentLinkMapper parentStudentLinkMapper;

    @GetMapping
    public ResponseEntity<List<ParentStudentLinkResponse>> list(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long parentId) {
        return ResponseEntity.ok(parentStudentLinkService.list(studentId, parentId).stream()
                .map(parentStudentLinkMapper::toResponse)
                .toList());
    }

    @PostMapping
    public ResponseEntity<ParentStudentLinkResponse> create(@Valid @RequestBody ParentStudentLinkCreateRequest request) {
        return ResponseEntity.ok(parentStudentLinkMapper.toResponse(parentStudentLinkService.create(request)));
    }

    @PostMapping(ParentApiPaths.REVOKE)
    public ResponseEntity<ParentStudentLinkResponse> revoke(@PathVariable Long id) {
        return ResponseEntity.ok(parentStudentLinkMapper.toResponse(parentStudentLinkService.revoke(id)));
    }
}

package com.okulservisi.backend.payment.controller;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.payment.service.FinanceDocumentService;
import com.okulservisi.backend.security.annotation.AdminOrParent;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/API.md §27 — makbuz ve ekstre indirme. ADMIN tümü; PARENT sadece aktif bağlı çocuğu (mevcut
 * servislerin kapsam kontrolü, kapsam dışı 404). STUDENT/DRIVER/HOSTESS 403 (finans uç noktaları).
 */
@RestController
@RequiredArgsConstructor
public class FinanceDocumentController {

    private static final MediaType HTML_UTF8 = new MediaType("text", "html", StandardCharsets.UTF_8);

    private final FinanceDocumentService documentService;

    @GetMapping("/api/payments/{id}/receipt")
    @AdminOrParent
    public ResponseEntity<String> receipt(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return document(documentService.receiptHtml(id, principal), "makbuz-" + id + ".html");
    }

    @GetMapping("/api/students/{studentId}/statement/document")
    @AdminOrParent
    public ResponseEntity<String> statement(@PathVariable Long studentId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return document(documentService.statementHtml(studentId, principal), "ekstre-" + studentId + ".html");
    }

    private ResponseEntity<String> document(String html, String fileName) {
        return ResponseEntity.ok()
                .contentType(HTML_UTF8)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(html);
    }
}

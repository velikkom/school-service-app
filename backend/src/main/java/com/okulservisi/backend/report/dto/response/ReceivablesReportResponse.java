package com.okulservisi.backend.report.dto.response;

import java.math.BigDecimal;

/**
 * docs/API.md "14. Dashboard / Rapor" — {@code GET /api/reports/finance/receivables}.
 *
 * <p><b>Tasarım kararı — kapsam:</b> docs net değil ("TÜM öğrenciler için toplam tahakkuk - toplam
 * tamamlanmış ödeme ... öğrenci bazında kırılım da ekleyebilirsin, basit bir toplam da yeterli").
 * Öğrenci bazında kırılım EKLENMEDİ — tek bir genel toplam yeterli bulundu, çünkü (a) docs bunu
 * açıkça yeterli sayıyor, (b) öğrenci bazında kırılım zaten {@code GET /api/students/{id}/statement}
 * (docs/API.md "13. Ödeme") üzerinden tek tek erişilebilir durumda, tekrar burada N+1 sorgu ile
 * listelemek bu rapor endpoint'inin kapsamını gereksiz büyütürdü.
 */
public record ReceivablesReportResponse(BigDecimal totalCharged, BigDecimal totalCollected, BigDecimal totalReceivable) {
}

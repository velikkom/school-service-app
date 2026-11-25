package com.okulservisi.backend.payment.service;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.payment.dto.response.StatementEntryResponse;
import com.okulservisi.backend.payment.dto.response.StudentStatementResponse;
import com.okulservisi.backend.payment.entity.Payment;
import com.okulservisi.backend.payment.entity.PaymentMethod;
import com.okulservisi.backend.payment.entity.PaymentStatus;
import com.okulservisi.backend.settings.entity.InstitutionSettings;
import com.okulservisi.backend.settings.service.InstitutionSettingsService;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.student.repository.StudentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * docs/API.md §27 — ödeme makbuzu ve öğrenci ekstresi belgeleri. Projede hazır bir PDF/çıktı üretimi
 * olmadığından (ve Türkçe karakterli PDF için gömülü font lisansı gerektirdiğinden) belge, tarayıcıda
 * açılıp yazdırılabilen/PDF'e kaydedilebilen bağımsız bir UTF-8 HTML dosyası olarak üretilir. Veri
 * kaynakları ve kapsam kontrolleri MEVCUT servislerdir ({@link PaymentService#getByIdForPrincipal},
 * {@link StudentStatementService#getStatement}) — ekranda görünen ile belge birebir aynıdır.
 * Biçim: para {@code 1.250,00 TL} (TRY, tr-TR), tarih {@code dd.MM.yyyy HH:mm} (Europe/Istanbul).
 */
@Service
@RequiredArgsConstructor
public class FinanceDocumentService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZONE);
    private static final Locale TR = Locale.forLanguageTag("tr-TR");

    private final PaymentService paymentService;
    private final StudentStatementService studentStatementService;
    private final StudentRepository studentRepository;
    private final InstitutionSettingsService institutionSettingsService;

    @Transactional(readOnly = true)
    public String receiptHtml(Long paymentId, AuthenticatedUser principal) {
        Payment payment = paymentService.getByIdForPrincipal(paymentId, principal);
        Student student = studentRepository.findById(payment.getStudent().getId()).orElseThrow();
        InstitutionSettings settings = institutionSettingsService.get();
        StringBuilder body = new StringBuilder();
        body.append("<h1>Ödeme Makbuzu</h1>");
        body.append("<table class=\"kv\">");
        row(body, "Makbuz No", "#" + payment.getId());
        row(body, "Öğrenci", student.getFullName());
        row(body, "Tutar", money(payment.getAmount()));
        row(body, "Ödeme Yöntemi", method(payment.getMethod()));
        row(body, "Ödeme Tarihi", dateTime(payment.getCreatedAt()));
        row(body, "Durum", payment.getStatus() == PaymentStatus.COMPLETED ? "Tamamlandı"
                : "İptal edildi (" + dateTime(payment.getCancelledAt()) + ")");
        if (payment.getNote() != null && !payment.getNote().isBlank()) {
            row(body, "Açıklama", payment.getNote());
        }
        body.append("</table>");
        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            body.append("<p class=\"warn\">Bu ödeme iptal edilmiştir; makbuz geçerli bir tahsilatı göstermez.</p>");
        }
        return page("Ödeme Makbuzu #" + payment.getId(), settings, body.toString());
    }

    @Transactional(readOnly = true)
    public String statementHtml(Long studentId, AuthenticatedUser principal) {
        StudentStatementResponse statement = studentStatementService.getStatement(studentId, principal);
        Student student = studentRepository.findById(studentId).orElseThrow();
        InstitutionSettings settings = institutionSettingsService.get();
        StringBuilder body = new StringBuilder();
        body.append("<h1>Hesap Ekstresi</h1>");
        body.append("<table class=\"kv\">");
        row(body, "Öğrenci", student.getFullName());
        row(body, "Düzenlenme", dateTime(Instant.now()));
        row(body, "Güncel Bakiye", money(statement.currentBalance()));
        body.append("</table>");
        body.append("<table class=\"grid\"><thead><tr><th>Tarih</th><th>İşlem</th><th>Açıklama</th>"
                + "<th class=\"num\">Tutar</th><th class=\"num\">Bakiye</th></tr></thead><tbody>");
        if (statement.entries().isEmpty()) {
            body.append("<tr><td colspan=\"5\">Kayıt bulunmuyor.</td></tr>");
        }
        for (StatementEntryResponse entry : statement.entries()) {
            boolean charge = "CHARGE".equals(entry.type());
            String description = charge ? "Dönem " + escape(entry.period())
                    : method(entry.method()) + (entry.paymentStatus() == PaymentStatus.CANCELLED ? " (iptal)" : "");
            body.append("<tr><td>").append(dateTime(entry.occurredAt())).append("</td><td>")
                    .append(charge ? "Tahakkuk" : "Ödeme").append("</td><td>").append(description)
                    .append("</td><td class=\"num\">").append(charge ? "" : "-").append(money(entry.amount()))
                    .append("</td><td class=\"num\">").append(money(entry.runningBalance())).append("</td></tr>");
        }
        body.append("</tbody></table>");
        return page("Hesap Ekstresi — " + student.getFullName(), settings, body.toString());
    }

    private String page(String title, InstitutionSettings settings, String content) {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang=\"tr\"><head><meta charset=\"utf-8\"><title>")
                .append(escape(title)).append("</title><style>")
                .append("body{font-family:Arial,Helvetica,sans-serif;color:#1f2933;max-width:820px;margin:24px auto;padding:0 16px}")
                .append("header{border-bottom:2px solid #1f2933;margin-bottom:16px;padding-bottom:8px}")
                .append("h1{font-size:20px}table{border-collapse:collapse;width:100%;margin-bottom:16px}")
                .append(".kv td{padding:4px 8px;border-bottom:1px solid #e4e7eb}.kv td:first-child{width:180px;color:#52606d}")
                .append(".grid th,.grid td{border:1px solid #cbd2d9;padding:6px;font-size:13px;text-align:left}")
                .append(".num{text-align:right!important;white-space:nowrap}.warn{color:#b42318;font-weight:bold}")
                .append("footer{font-size:12px;color:#52606d;margin-top:24px}@media print{body{margin:0}}")
                .append("</style></head><body><header><strong>").append(escape(settings.getName())).append("</strong>");
        if (settings.getAddress() != null) {
            html.append("<br>").append(escape(settings.getAddress()));
        }
        if (settings.getPhone() != null) {
            html.append("<br>Tel: ").append(escape(settings.getPhone()));
        }
        html.append("</header>").append(content).append("<footer>");
        if (settings.getReportHeaderNote() != null) {
            html.append(escape(settings.getReportHeaderNote())).append("<br>");
        }
        html.append("Tutarlar Türk Lirası (TRY) cinsindendir. Saatler Europe/Istanbul saatidir.</footer></body></html>");
        return html.toString();
    }

    private static void row(StringBuilder sb, String label, String value) {
        sb.append("<tr><td>").append(escape(label)).append("</td><td>").append(escape(value)).append("</td></tr>");
    }

    static String money(BigDecimal amount) {
        if (amount == null) {
            return "—";
        }
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(TR));
        return format.format(amount.setScale(2, RoundingMode.HALF_UP)) + " TL";
    }

    private static String dateTime(Instant instant) {
        return instant == null ? "—" : DATE_TIME.format(instant);
    }

    private static String method(PaymentMethod method) {
        if (method == null) {
            return "—";
        }
        return switch (method) {
            case CASH -> "Nakit";
            case BANK_TRANSFER -> "Havale/EFT";
            case CARD -> "Kart";
            case OTHER -> "Diğer";
        };
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '&' -> sb.append("&amp;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}

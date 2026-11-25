package com.okulservisi.backend.report.service.impl;

import com.okulservisi.backend.attendance.entity.AttendanceEventType;
import com.okulservisi.backend.attendance.repository.AttendanceEventRepository;
import com.okulservisi.backend.billing.repository.MonthlyChargeRepository;
import com.okulservisi.backend.common.exception.BadRequestException;
import com.okulservisi.backend.payment.repository.PaymentRepository;
import com.okulservisi.backend.report.dto.response.CollectionsReportResponse;
import com.okulservisi.backend.report.dto.response.DailySummaryResponse;
import com.okulservisi.backend.report.dto.response.ReceivablesReportResponse;
import com.okulservisi.backend.report.service.ReportService;
import com.okulservisi.backend.trip.entity.TripStatus;
import com.okulservisi.backend.trip.repository.TripRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * docs/API.md "14. Dashboard / Rapor" (bkz. docs/RBAC_MATRIX.md: {@code /api/reports/**} SADECE
 * ADMIN). Entity/migration GEREKTİRMEZ — sadece Trip/AttendanceEvent/MonthlyCharge/Payment
 * repository'lerine eklenen (additive) agregasyon sorgularını okur (bkz. CLAUDE.md görev tanımı).
 */
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private final TripRepository tripRepository;
    private final AttendanceEventRepository attendanceEventRepository;
    private final PaymentRepository paymentRepository;
    private final MonthlyChargeRepository monthlyChargeRepository;

    @Override
    public DailySummaryResponse dailySummary(LocalDate businessDate) {
        if (businessDate == null) {
            throw new BadRequestException("date parametresi zorunludur (örn. '2026-10-20').");
        }
        long total = tripRepository.countByBusinessDate(businessDate);
        long planned = tripRepository.countByBusinessDateAndStatus(businessDate, TripStatus.PLANNED);
        long inProgress = tripRepository.countByBusinessDateAndStatus(businessDate, TripStatus.IN_PROGRESS);
        long completed = tripRepository.countByBusinessDateAndStatus(businessDate, TripStatus.COMPLETED);
        long cancelled = tripRepository.countByBusinessDateAndStatus(businessDate, TripStatus.CANCELLED);

        long boarding = attendanceEventRepository.countByTripBusinessDateAndEventType(
                businessDate, AttendanceEventType.BOARDING);
        long dropoff = attendanceEventRepository.countByTripBusinessDateAndEventType(
                businessDate, AttendanceEventType.DROPOFF);
        long absence = attendanceEventRepository.countByTripBusinessDateAndEventType(
                businessDate, AttendanceEventType.ABSENCE);

        return new DailySummaryResponse(businessDate, total, planned, inProgress, completed, cancelled,
                boarding, dropoff, absence);
    }

    @Override
    public CollectionsReportResponse collections(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BadRequestException("from ve to parametreleri zorunludur (örn. '2026-10-01').");
        }
        if (from.isAfter(to)) {
            throw new BadRequestException("from, to tarihinden sonra olamaz.");
        }
        Instant fromInstant = from.atStartOfDay(ISTANBUL).toInstant();
        Instant toInstantExclusive = to.plusDays(1).atStartOfDay(ISTANBUL).toInstant();

        BigDecimal total = paymentRepository.sumCompletedAmountBetween(fromInstant, toInstantExclusive);
        List<CollectionsReportResponse.MethodAmount> byMethod = paymentRepository
                .totalsByMethodBetween(fromInstant, toInstantExclusive).stream()
                .map(row -> new CollectionsReportResponse.MethodAmount(row.getMethod(), row.getTotal()))
                .toList();

        return new CollectionsReportResponse(from, to, total, byMethod);
    }

    @Override
    public ReceivablesReportResponse receivables() {
        BigDecimal totalCharged = monthlyChargeRepository.sumAllAmounts();
        BigDecimal totalCollected = paymentRepository.sumAllCompletedAmounts();
        return new ReceivablesReportResponse(totalCharged, totalCollected, totalCharged.subtract(totalCollected));
    }
}

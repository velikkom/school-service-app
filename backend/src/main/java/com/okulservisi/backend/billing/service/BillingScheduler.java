package com.okulservisi.backend.billing.service;
import com.okulservisi.backend.billing.service.BillingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * docs/API.md "12. Aylık ücret / tahakkuk": "Ayrıca scheduler ile otomatik (Europe/Istanbul, ayın
 * 1'i 03:00) tetiklenir — aynı idempotency kuralına uyar." {@link BillingService#run} ile TAM OLARAK
 * AYNI metot/kural kullanılır (bkz. BillingService javadoc'u); bu sınıf sadece "ne zaman" sorusunu
 * cevaplar, "nasıl"ı tekrar yazmaz. {@code @EnableScheduling} {@code BackendApplication}'da tanımlıdır.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingScheduler {

    private final BillingService billingService;

    @Scheduled(cron = "0 0 3 1 * *", zone = "Europe/Istanbul")
    public void runMonthlyBilling() {
        String period = billingService.currentPeriod();
        BillingRunResult result = billingService.run(period);
        log.info("Otomatik aylık tahakkuk çalıştı: period={} created={} skipped={}",
                result.period(), result.created(), result.skipped());
    }
}

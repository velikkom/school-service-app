package com.okulservisi.backend.common.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.okulservisi.backend.common.entity.AuditLog;
import com.okulservisi.backend.common.entity.AuditLogRepository;
import com.okulservisi.backend.user.repository.UserRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2026-11-18 — {@code audit_log} yazıcısı (docs/DATABASE_DESIGN.md "kritik yoklama düzeltmeleri").
 * Çağıranın transaction'ına KATILIR: iş kaydı commit edilmezse denetim kaydı da oluşmaz (ve tersi).
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditLog record(Long actorUserId, String action, String entityName, Long entityId, Map<String, ?> detail) {
        AuditLog log = new AuditLog();
        log.setActor(actorUserId == null ? null : userRepository.getReferenceById(actorUserId));
        log.setAction(action);
        log.setEntityName(entityName);
        log.setEntityId(entityId);
        try {
            log.setDetail(objectMapper.writeValueAsString(detail));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Denetim kaydı serileştirilemedi.", ex);
        }
        return auditLogRepository.save(log);
    }
}

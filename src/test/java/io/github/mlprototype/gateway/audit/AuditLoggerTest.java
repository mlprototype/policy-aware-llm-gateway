package io.github.mlprototype.gateway.audit;

import io.github.mlprototype.gateway.observability.GatewayMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLoggerTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private GatewayMetrics gatewayMetrics;

    @InjectMocks
    private AuditLogger auditLogger;

    @Test
    void logPersistsInjectionScoreAndCategories() {
        AuditEvent event = AuditEvent.builder()
                .traceId("trace-1")
                .tenantId("tenant-1")
                .clientId("client-1")
                .model("gpt-4o-mini")
                .latencyMs(12)
                .statusCode(403)
                .status("blocked")
                .injectionDetected(true)
                .injectionAction("BLOCK")
                .injectionRules("[IGNORE_INSTRUCTIONS, REVEAL_SYSTEM_PROMPT]")
                .injectionScore(85)
                .injectionCategories("INSTRUCTION_OVERRIDE,SYSTEM_PROMPT_EXTRACTION")
                .build();

        auditLogger.log(event);

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLogEntity entity = captor.getValue();
        assertThat(entity.getInjectionDetected()).isTrue();
        assertThat(entity.getInjectionAction()).isEqualTo("BLOCK");
        assertThat(entity.getInjectionRules()).isEqualTo("[IGNORE_INSTRUCTIONS, REVEAL_SYSTEM_PROMPT]");
        assertThat(entity.getInjectionScore()).isEqualTo(85);
        assertThat(entity.getInjectionCategories())
                .isEqualTo("INSTRUCTION_OVERRIDE,SYSTEM_PROMPT_EXTRACTION");
    }
}

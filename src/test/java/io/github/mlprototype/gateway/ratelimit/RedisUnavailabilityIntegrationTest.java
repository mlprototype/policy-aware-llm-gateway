package io.github.mlprototype.gateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        // DBは起動に失敗するため除外する
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
        // Redisのホスト/ポートを確実に存在しない接続先に設定
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=9999",
        "spring.data.redis.timeout=1000" // 接続タイムアウトを1秒に設定してテスト時間を短縮
})
@ActiveProfiles("test")
class RedisUnavailabilityIntegrationTest {

    @Autowired
    private RateLimiter rateLimiter;

    // DBを必要とするサービスをモック化して起動エラーを回避
    @MockitoBean
    private io.github.mlprototype.gateway.security.AuthenticationService authenticationService;

    @MockitoBean
    private io.github.mlprototype.gateway.audit.AuditLogger auditLogger;

    @MockitoBean
    private io.github.mlprototype.gateway.router.ProviderRoutingService providerRoutingService;

    @MockitoBean
    private io.github.mlprototype.gateway.content.ContentSecurityService contentSecurityService;

    @MockitoBean
    private io.github.mlprototype.gateway.observability.GatewayMetrics gatewayMetrics;

    @Test
    void testAppStartsAndRateLimiterFailsOpenWhenRedisIsUnavailable() {
        // 1. Redisが存在しない（接続失敗する）状態でも、Spring BootのApplicationContextが正常に起動できていることを確認
        assertThat(rateLimiter).isNotNull();

        // 2. Redis接続失敗時に、例外を投げずにFail-Open（UNAVAILABLE）が返ることを確認
        RateLimiter.RateLimitResult result = rateLimiter.check("test-tenant", 10);

        assertThat(result.status()).isEqualTo(RateLimiter.RateLimitResult.Status.UNAVAILABLE);
        assertThat(result.isRejected()).isFalse();
        assertThat(result.isAvailable()).isFalse();
    }
}

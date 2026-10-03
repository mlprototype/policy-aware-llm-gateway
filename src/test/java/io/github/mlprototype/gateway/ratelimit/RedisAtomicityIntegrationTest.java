package io.github.mlprototype.gateway.ratelimit;

import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisAtomicityIntegrationTest {
    private LettuceConnectionFactory connection;
    private StringRedisTemplate redis;
    private RateLimiter limiter;
    private String tenant;

    @BeforeAll
    void connect() {
        int port = Integer.parseInt(System.getenv().getOrDefault("GATEWAY_TEST_REDIS_PORT", "6379"));
        connection = new LettuceConnectionFactory("127.0.0.1", port);
        connection.afterPropertiesSet();
        redis = new StringRedisTemplate(connection);
        limiter = new RateLimiter(redis);
    }

    @BeforeEach
    void uniqueTenant() { tenant = "hardening-" + UUID.randomUUID(); }

    @AfterEach
    void removeOnlyOwnCounters() {
        var keys = redis.keys("rate_limit:" + tenant + ":*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
    }

    @AfterAll
    void disconnect() { connection.destroy(); }

    @Test
    void firstIncrementSetsTtlAndLaterIncrementDoesNotExtendIt() {
        assertThat(limiter.check(tenant, 10).remaining()).isEqualTo(9);
        String key = counterKey();
        assertThat(redis.getExpire(key)).isBetween(1L, 120L);
        redis.expire(key, Duration.ofSeconds(60));
        var result = limiter.check(tenant, 10);
        // A minute boundary can create a fresh window while the original key keeps its TTL.
        assertThat(result.remaining()).isIn(8, 9);
        assertThat(redis.getExpire(key)).isBetween(1L, 60L);
    }

    @Test
    void existingCounterWithoutTtlIsRepairedAndStillEnforcesLimit() {
        String key = currentKey(Instant.now());
        String nextKey = currentKey(Instant.now().plusSeconds(60));
        redis.opsForValue().set(key, "4");
        redis.opsForValue().set(nextKey, "4");
        assertThat(redis.getExpire(key)).isEqualTo(-1L);
        assertThat(limiter.check(tenant, 4).isRejected()).isTrue();
        var repairedKeys = redis.keys("rate_limit:" + tenant + ":*").stream()
                .filter(candidate -> "5".equals(redis.opsForValue().get(candidate))).toList();
        assertThat(repairedKeys).hasSize(1);
        assertThat(redis.getExpire(repairedKeys.getFirst())).isBetween(1L, 120L);
    }

    @Test
    void concurrentCallsCountExactlyOnceAndEnforceTheLimit() throws Exception {
        try (var executor = Executors.newFixedThreadPool(16)) {
            var calls = new ArrayList<Callable<RateLimiter.RateLimitResult>>();
            for (int i = 0; i < 100; i++) calls.add(() -> limiter.check(tenant, 50));
            var results = executor.invokeAll(calls);
            long allowed = 0;
            long rejected = 0;
            for (var future : results) {
                var result = future.get();
                assertThat(result.isAvailable()).isTrue();
                if (result.isRejected()) rejected++; else allowed++;
            }
            long total = 0;
            long expectedAllowed = 0;
            for (String key : redis.keys("rate_limit:" + tenant + ":*")) {
                long count = Long.parseLong(redis.opsForValue().get(key));
                total += count;
                expectedAllowed += Math.min(count, 50);
                assertThat(redis.getExpire(key)).isBetween(1L, 120L);
            }
            assertThat(total).isEqualTo(100);
            assertThat(allowed).isEqualTo(expectedAllowed);
            assertThat(rejected).isEqualTo(100 - expectedAllowed);
        }
    }

    @Test
    void scriptFailureRemainsUnavailableAndFailOpen() {
        String key = currentKey(Instant.now());
        String nextKey = currentKey(Instant.now().plusSeconds(60));
        redis.opsForHash().put(key, "fixture", "wrong-type");
        redis.opsForHash().put(nextKey, "fixture", "wrong-type");
        var result = limiter.check(tenant, 10);
        assertThat(result.status()).isEqualTo(RateLimiter.RateLimitResult.Status.UNAVAILABLE);
        assertThat(result.isRejected()).isFalse();
    }

    private String currentKey(Instant instant) {
        return "rate_limit:" + tenant + ":" + instant.atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
    }

    private String counterKey() {
        var keys = redis.keys("rate_limit:" + tenant + ":*");
        assertThat(keys).hasSize(1);
        return keys.iterator().next();
    }
}

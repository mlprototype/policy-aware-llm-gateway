package io.github.mlprototype.gateway.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Fixed-window rate limiter using Redis.
 * Key format: rate_limit:{tenantId}:{yyyyMMddHHmm}
 * TTL: 120 seconds (auto-cleanup after window expires).
 *
 * Fail-open: if Redis is unavailable, requests are allowed through
 * with a warning log. No X-RateLimit-* headers are set in that case.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiter {

    private final StringRedisTemplate redisTemplate;

    private static final DateTimeFormatter MINUTE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    /**
     * Checks if the request should be allowed under the rate limit.
     *
     * @param tenantId tenant identifier
     * @param limitPerMinute maximum requests per minute for this tenant
     * @return RateLimitResult with allowed/rejected/unavailable status
     */
    public RateLimitResult check(String tenantId, int limitPerMinute) {
        try {
            String minute = Instant.now().atOffset(ZoneOffset.UTC).format(MINUTE_FORMAT);
            String key = "rate_limit:" + tenantId + ":" + minute;

            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                log.warn("Redis INCR returned null for key={}", key);
                return RateLimitResult.unavailable();
            }

            // Set TTL on first increment
            if (count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(120));
            }

            int remaining = Math.max(0, limitPerMinute - count.intValue());

            if (count > limitPerMinute) {
                return RateLimitResult.rejected(limitPerMinute, 0);
            }

            return RateLimitResult.allowed(limitPerMinute, remaining);

        } catch (Exception e) {
            log.warn("Redis unavailable, fail-open: {}", e.getMessage());
            return RateLimitResult.unavailable();
        }
    }

    /**
     * Result of a rate limit check.
     */
    public record RateLimitResult(
            Status status,
            int limit,
            int remaining
    ) {
        public enum Status { ALLOWED, REJECTED, UNAVAILABLE }

        public boolean isRejected() {
            return status == Status.REJECTED;
        }

        public boolean isAvailable() {
            return status != Status.UNAVAILABLE;
        }

        public String toAuditString() {
            return switch (status) {
                case ALLOWED -> "allowed";
                case REJECTED -> "rejected";
                case UNAVAILABLE -> "redis_unavailable_fail_open";
            };
        }

        static RateLimitResult allowed(int limit, int remaining) {
            return new RateLimitResult(Status.ALLOWED, limit, remaining);
        }

        static RateLimitResult rejected(int limit, int remaining) {
            return new RateLimitResult(Status.REJECTED, limit, remaining);
        }

        static RateLimitResult unavailable() {
            return new RateLimitResult(Status.UNAVAILABLE, 0, 0);
        }
    }
}

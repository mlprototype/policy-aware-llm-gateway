package io.github.mlprototype.gateway.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mlprototype.gateway.security.RequestContext;
import io.github.mlprototype.gateway.security.RequestContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    private RateLimitFilter filter;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(rateLimiter, objectMapper);
        RequestContextHolder.set(new RequestContext("tenant-1", "client-1", 60, io.github.mlprototype.gateway.content.PiiAction.MASK, io.github.mlprototype.gateway.content.InjectionAction.WARN));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void doFilter_whenAllowed_passesWithHeaders() throws Exception {
        when(rateLimiter.check(anyString(), anyInt()))
                .thenReturn(RateLimiter.RateLimitResult.allowed(60, 59));

        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("60");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("59");
    }

    @Test
    void doFilter_whenRejected_returns429() throws Exception {
        when(rateLimiter.check(anyString(), anyInt()))
                .thenReturn(RateLimiter.RateLimitResult.rejected(60, 0));

        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("60");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(response.getContentAsString()).contains("Too Many Requests");
    }

    @Test
    void doFilter_whenRedisUnavailable_failsOpenWithoutHeaders() throws Exception {
        when(rateLimiter.check(anyString(), anyInt()))
                .thenReturn(RateLimiter.RateLimitResult.unavailable());

        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        // Should pass through successfully (Fail-open)
        assertThat(response.getStatus()).isEqualTo(200);
        // BUT headers should NOT be present
        assertThat(response.getHeader("X-RateLimit-Limit")).isNull();
        assertThat(response.getHeader("X-RateLimit-Remaining")).isNull();
    }
}

package io.github.mlprototype.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Measures request processing time and sets X-Gateway-Latency-Ms response header.
 * Runs after TraceIdFilter to ensure trace context is available.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class LatencyFilter extends OncePerRequestFilter {

    public static final String LATENCY_HEADER = "X-Gateway-Latency-Ms";
    public static final String START_TIME_ATTR = "gateway.startTime";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        request.setAttribute(START_TIME_ATTR, startTime);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long latency = System.currentTimeMillis() - startTime;
            response.setHeader(LATENCY_HEADER, String.valueOf(latency));
        }
    }
}

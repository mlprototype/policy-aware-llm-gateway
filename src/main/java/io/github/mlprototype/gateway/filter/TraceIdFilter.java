package io.github.mlprototype.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Assigns a unique trace ID to each request.
 * If the client sends X-Request-Id, that value is used; otherwise a UUID is generated.
 * The trace ID is placed in MDC for log correlation and added to the response header.
 *
 * Sprint 2 extension: integrate with Micrometer traceId.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Gateway-Trace-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_TRACE_ID = "traceId";

    /**
     * リクエストにTrace IDを割り当て、ログや後続処理で利用可能な状態にするフィルター処理関数です。
     * ヘッダーからTrace IDの候補を受け取り、必要に応じてIDを生成してフィルターチェーンを進めます。
     * 状態更新として、MDC、Servletのリクエスト属性、およびクライアントへのレスポンスヘッダーにTrace IDを格納し、
     * リクエスト終了時にはMDCから確実にクリアします。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(REQUEST_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_TRACE_ID, traceId);
        // Store in request attribute for downstream access
        request.setAttribute(MDC_TRACE_ID, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TRACE_ID);
        }
    }
}

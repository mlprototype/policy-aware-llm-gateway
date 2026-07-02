package io.github.mlprototype.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingResponseWrapper;
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

    /**
     * リクエストの処理時間を計測し、レスポンスに記録するフィルター処理関数です。
     * HTTPリクエストとレスポンスを受け取り、フィルターチェーンの前と後で時刻を取得して差分を算出します。
     * 状態更新として、後続処理で開始時間を参照できるようにリクエスト属性に設定し、
     * 処理完了後（正常・異常を問わず）にレスポンスヘッダーへレイテンシ（ミリ秒）を追加します。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        request.setAttribute(START_TIME_ATTR, startTime);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(request, responseWrapper);
        } finally {
            long latency = System.currentTimeMillis() - startTime;
            responseWrapper.setHeader(LATENCY_HEADER, String.valueOf(latency));
            responseWrapper.copyBodyToResponse();
        }
    }
}

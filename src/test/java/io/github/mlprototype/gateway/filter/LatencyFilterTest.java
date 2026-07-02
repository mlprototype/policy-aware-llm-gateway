package io.github.mlprototype.gateway.filter;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LatencyFilterTest {

    private final LatencyFilter filter = new LatencyFilter();

    @Test
    void doFilter_setsLatencyHeaderBeforeResponseIsCommitted() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        HttpServletResponse commitAwareResponse = new CommitAwareResponse(response);

        filter.doFilterInternal(request, commitAwareResponse, (servletRequest, servletResponse) -> {
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
            httpResponse.setStatus(HttpServletResponse.SC_OK);
            httpResponse.setContentType("application/json");
            httpResponse.getOutputStream().write("{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8));
            httpResponse.flushBuffer();
        });

        assertThat(response.getHeader(LatencyFilter.LATENCY_HEADER)).isNotNull();
        assertThat(Long.parseLong(response.getHeader(LatencyFilter.LATENCY_HEADER))).isGreaterThanOrEqualTo(0);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).isEqualTo("{\"status\":\"ok\"}");
    }

    /** Simulates a servlet container that ignores header mutations after commit. */
    private static final class CommitAwareResponse extends HttpServletResponseWrapper {

        private CommitAwareResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setHeader(String name, String value) {
            if (!isCommitted()) {
                super.setHeader(name, value);
            }
        }
    }
}

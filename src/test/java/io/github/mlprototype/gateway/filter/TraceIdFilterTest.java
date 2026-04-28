package io.github.mlprototype.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFilterTest {

    private TraceIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TraceIdFilter();
    }

    @Test
    void doFilter_withoutRequestId_generatesUuid() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        String traceId = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        assertThat(traceId).isNotNull().isNotBlank();
        // UUID format check
        assertThat(traceId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void doFilter_withRequestId_usesProvidedId() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.REQUEST_ID_HEADER, "custom-trace-123");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo("custom-trace-123");
    }

    @Test
    void doFilter_cleansMdcAfterRequest() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(MDC.get(TraceIdFilter.MDC_TRACE_ID)).isNull();
    }
}

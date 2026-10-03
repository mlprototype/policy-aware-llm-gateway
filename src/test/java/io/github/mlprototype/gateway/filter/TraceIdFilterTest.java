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
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "a", "request-ID_1.2:part", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    })
    void safeIdsArePreservedAcrossAllCorrelationSurfaces(String supplied) throws Exception {
        assertCorrelation(supplied, true);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            " ", "   ", "unsafe id", "日本語", "id/part", "id\npart",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    })
    void invalidIdsAreReplacedWithoutRejectingRequest(String supplied) throws Exception {
        assertCorrelation(supplied, false);
    }

    private void assertCorrelation(String supplied, boolean preserve) throws Exception {
        var request = new MockHttpServletRequest();
        if (supplied != null) request.addHeader(TraceIdFilter.REQUEST_ID_HEADER, supplied);
        var response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, (req, res) -> {
            String trace = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
            assertThat(req.getAttribute(TraceIdFilter.MDC_TRACE_ID)).isEqualTo(trace);
            assertThat(MDC.get(TraceIdFilter.MDC_TRACE_ID)).isEqualTo(trace);
            assertThat(trace).hasSizeLessThanOrEqualTo(64);
            if (preserve) assertThat(trace).isEqualTo(supplied);
            else assertThat(trace).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        });
        assertThat(MDC.get(TraceIdFilter.MDC_TRACE_ID)).isNull();
    }

}

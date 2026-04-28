package io.github.mlprototype.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyFilterTest {

    private ApiKeyFilter filter;
    private ObjectMapper objectMapper;
    private static final String VALID_KEY = "test-api-key-123";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        filter = new ApiKeyFilter(objectMapper);
        ReflectionTestUtils.setField(filter, "expectedApiKey", VALID_KEY);
    }

    @Test
    void doFilter_withValidApiKey_passes() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(ApiKeyFilter.API_KEY_HEADER, VALID_KEY);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull(); // chain was invoked
    }

    @Test
    void doFilter_withInvalidApiKey_returns401() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(ApiKeyFilter.API_KEY_HEADER, "wrong-key");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Unauthorized");
    }

    @Test
    void doFilter_withoutApiKey_returns401() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void shouldNotFilter_actuatorEndpoints_returnsTrue() {
        var request = new MockHttpServletRequest();
        request.setRequestURI("/actuator/health");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_apiEndpoints_returnsFalse() {
        var request = new MockHttpServletRequest();
        request.setRequestURI("/v1/chat/completions");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }
}

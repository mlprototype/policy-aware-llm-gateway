package io.github.mlprototype.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.github.mlprototype.gateway.exception.GatewayException;
import io.github.mlprototype.gateway.security.AuthenticationService;
import io.github.mlprototype.gateway.security.RequestContext;
import io.github.mlprototype.gateway.security.RequestContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyFilterTest {

    private ApiKeyFilter filter;
    private ObjectMapper objectMapper;

    @Mock
    private AuthenticationService authenticationService;

    private static final String VALID_KEY = "test-api-key-123";
    private static final RequestContext TEST_CONTEXT =
            new RequestContext("tenant-1", "client-1", 60);

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        filter = new ApiKeyFilter(authenticationService, objectMapper);
    }

    @Test
    void doFilter_withValidApiKey_passes() throws Exception {
        when(authenticationService.authenticate(VALID_KEY)).thenReturn(TEST_CONTEXT);

        var request = new MockHttpServletRequest();
        request.addHeader(ApiKeyFilter.API_KEY_HEADER, VALID_KEY);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void doFilter_withValidApiKey_clearsContextAfterChain() throws Exception {
        when(authenticationService.authenticate(VALID_KEY)).thenReturn(TEST_CONTEXT);

        var request = new MockHttpServletRequest();
        request.addHeader(ApiKeyFilter.API_KEY_HEADER, VALID_KEY);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        // ThreadLocal must be cleared after filter completes
        assertThat(catchIllegalState()).isTrue();
    }

    @Test
    void doFilter_withInvalidApiKey_returns401() throws Exception {
        when(authenticationService.authenticate("wrong-key"))
                .thenThrow(new GatewayException("Invalid or missing API key", 401));

        var request = new MockHttpServletRequest();
        request.addHeader(ApiKeyFilter.API_KEY_HEADER, "wrong-key");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Unauthorized");
    }

    @Test
    void doFilter_withSuspendedTenant_returns403() throws Exception {
        when(authenticationService.authenticate(anyString()))
                .thenThrow(new GatewayException("Tenant is suspended", 403));

        var request = new MockHttpServletRequest();
        request.addHeader(ApiKeyFilter.API_KEY_HEADER, "suspended-key");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("Forbidden");
    }

    @Test
    void doFilter_withoutApiKey_returns401() throws Exception {
        when(authenticationService.authenticate(null))
                .thenThrow(new GatewayException("Invalid or missing API key", 401));

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

    private boolean catchIllegalState() {
        try {
            RequestContextHolder.getRequired();
            return false;
        } catch (IllegalStateException e) {
            return true;
        }
    }
}

package cl.reservakids.infrastructure;

import cl.reservakids.infrastructure.security.RateLimitFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setup() {
        filter = new RateLimitFilter();
        ReflectionTestUtils.setField(filter, "trustProxy", true);
        ReflectionTestUtils.setField(filter, "publicMaxPorMinuto", 30);
        ReflectionTestUtils.setField(filter, "authMaxPorMinuto", 10);
        ReflectionTestUtils.setField(filter, "panelMaxPorMinuto", 60);
    }

    @Test
    void ipFalsificadaEnXForwardedForNoEvadeElRateLimit() throws Exception {
        int status = 200;
        for (int i = 1; i <= 31; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/fiestas");
            request.setRequestURI("/api/public/fiestas");
            request.addHeader("X-Forwarded-For", "1.2.3." + i + ", 9.9.9.9");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());
            status = response.getStatus();
        }
        assertEquals(429, status);
    }

    @Test
    void ipsRealesDistintasUsanBucketsDistintos() throws Exception {
        for (int i = 1; i <= 31; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/fiestas");
            request.setRequestURI("/api/public/fiestas");
            request.addHeader("X-Forwarded-For", "10.0.0." + i);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    void incluyeRetryAfterEnRespuesta429() throws Exception {
        // agota el bucket rápido
        for (int i = 0; i < 31; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/fiestas");
            request.setRequestURI("/api/public/fiestas");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
        }

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/fiestas");
        request.setRequestURI("/api/public/fiestas");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(429, response.getStatus());
        String retryAfter = response.getHeader("Retry-After");
        assertNotNull(retryAfter);
        int segundos = Integer.parseInt(retryAfter);
        assertTrue(segundos >= 1 && segundos <= 60, "Retry-After debe estar entre 1 y 60: " + segundos);
    }

    @Test
    void authEndpointUsaLimiteMasEstricto() throws Exception {
        ReflectionTestUtils.setField(filter, "authMaxPorMinuto", 5);
        int status = 200;
        for (int i = 1; i <= 6; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRequestURI("/api/auth/login");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            status = response.getStatus();
        }
        assertEquals(429, status);
    }

    @Test
    void panelEndpointUsaLimitePropio() throws Exception {
        ReflectionTestUtils.setField(filter, "panelMaxPorMinuto", 5);
        int status = 200;
        for (int i = 1; i <= 6; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/reservas");
            request.setRequestURI("/api/reservas");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            status = response.getStatus();
        }
        assertEquals(429, status);
    }

    @Test
    void webhookEndpointUsaLimitePropio() throws Exception {
        ReflectionTestUtils.setField(filter, "webhookMaxPorMinuto", 5);
        int status = 200;
        for (int i = 1; i <= 6; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/public/webhooks/mercadopago/1");
            request.setRequestURI("/api/public/webhooks/mercadopago/1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            status = response.getStatus();
        }
        assertEquals(429, status);
    }

    @Test
    void actuatorNoEsFiltrado() throws Exception {
        for (int i = 1; i <= 50; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
            request.setRequestURI("/actuator/health");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    void sinProxyConfiaEnRemoteAddrIgnoraXForwardedFor() throws Exception {
        ReflectionTestUtils.setField(filter, "trustProxy", false);
        for (int i = 1; i <= 30; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/fiestas");
            request.setRequestURI("/api/public/fiestas");
            request.addHeader("X-Forwarded-For", "1.2.3." + i);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertEquals(200, response.getStatus(), "Request " + i + " should pass");
        }
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/fiestas");
        request.setRequestURI("/api/public/fiestas");
        request.addHeader("X-Forwarded-For", "9.9.9.9");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(429, response.getStatus(), "31st request from same IP should be rate limited");
    }
}

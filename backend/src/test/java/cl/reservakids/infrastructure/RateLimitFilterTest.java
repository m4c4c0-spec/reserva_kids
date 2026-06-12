package cl.reservakids.infrastructure;

import cl.reservakids.infrastructure.security.RateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fix #1 (revisión de código): detrás del proxy, la PRIMERA IP de X-Forwarded-For la
 * controla el cliente (el proxy solo appendea la real al final). Tomarla permitía un
 * bypass total del rate limit estrenando un bucket por request.
 */
class RateLimitFilterTest {

    @Test
    void ipFalsificadaEnXForwardedForNoEvadeElRateLimit() throws Exception {
        RateLimitFilter filter = new RateLimitFilter();
        ReflectionTestUtils.setField(filter, "trustProxy", true);

        int status = 200;
        for (int i = 1; i <= 31; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/fiestas");
            request.setRequestURI("/api/public/fiestas");
            // el atacante varía la primera IP en cada request; el proxy appendea la real (9.9.9.9)
            request.addHeader("X-Forwarded-For", "1.2.3." + i + ", 9.9.9.9");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());
            status = response.getStatus();
        }
        assertEquals(429, status); // la 31ª cae en el MISMO bucket (la IP real)
    }

    @Test
    void ipsRealesDistintasUsanBucketsDistintos() throws Exception {
        RateLimitFilter filter = new RateLimitFilter();
        ReflectionTestUtils.setField(filter, "trustProxy", true);

        for (int i = 1; i <= 31; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/fiestas");
            request.setRequestURI("/api/public/fiestas");
            request.addHeader("X-Forwarded-For", "10.0.0." + i); // clientes reales distintos
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());
            assertEquals(200, response.getStatus());
        }
    }
}

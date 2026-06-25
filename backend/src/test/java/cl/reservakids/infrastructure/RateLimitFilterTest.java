package cl.reservakids.infrastructure;

import cl.reservakids.domain.repository.RateLimitBucketRepository;
import cl.reservakids.infrastructure.security.RateLimitFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setup() {
        // H2: el conteo es atómico en BD. El mock simula ese upsert: incrementa por
        // (ip|rutaTipo|minuto) y devuelve el nuevo valor, igual que el INSERT … RETURNING.
        RateLimitBucketRepository repo = mock(RateLimitBucketRepository.class);
        Map<String, Integer> contadores = new ConcurrentHashMap<>();
        when(repo.incrementarYContar(anyString(), anyString(), anyLong(), anyInt()))
                .thenAnswer(inv -> {
                    String clave = inv.getArgument(0) + "|" + inv.getArgument(1) + "|" + inv.getArgument(2);
                    return contadores.merge(clave, 1, Integer::sum);
                });
        filter = new RateLimitFilter(repo);
        ReflectionTestUtils.setField(filter, "trustProxy", true);
        ReflectionTestUtils.setField(filter, "publicMaxPorMinuto", 30);
        ReflectionTestUtils.setField(filter, "authMaxPorMinuto", 10);
        ReflectionTestUtils.setField(filter, "panelMaxPorMinuto", 60);
        ReflectionTestUtils.setField(filter, "authRegisterMaxPorMinuto", 3);
        ReflectionTestUtils.setField(filter, "clienteAuthRegisterMaxPorMinuto", 3);
        ReflectionTestUtils.setField(filter, "publicReservasMaxPorMinuto", 5);
        ReflectionTestUtils.setField(filter, "authRegisterGlobalMaxPorMinuto", 60);
        ReflectionTestUtils.setField(filter, "clienteAuthRegisterGlobalMaxPorMinuto", 60);
        ReflectionTestUtils.setField(filter, "publicReservasGlobalMaxPorMinuto", 100);
        ReflectionTestUtils.setField(filter, "authGlobalMaxPorMinuto", 200);
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

    @Test
    void registroDuenoUsaLimiteMasEstrictoQueLogin() throws Exception {
        int status = 200;
        for (int i = 1; i <= 4; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
            request.setRequestURI("/api/auth/register");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            status = response.getStatus();
        }
        assertEquals(429, status, "Registro dueño debe limitarse a 3/min");
    }

    @Test
    void loginDuenoSigueUsandoBucketAuthGeneral() throws Exception {
        int status = 200;
        for (int i = 1; i <= 11; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRequestURI("/api/auth/login");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            status = response.getStatus();
        }
        assertEquals(429, status, "Login dueño debe limitarse a 10/min");
    }

    @Test
    void registroClienteUsaLimiteEstricto() throws Exception {
        int status = 200;
        for (int i = 1; i <= 4; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/cliente-auth/register");
            request.setRequestURI("/api/cliente-auth/register");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            status = response.getStatus();
        }
        assertEquals(429, status, "Registro cliente debe limitarse a 3/min");
    }

    @Test
    void solicitudPublicaReservasUsaLimiteEstricto() throws Exception {
        int status = 200;
        for (int i = 1; i <= 6; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/public/fiestas-pepito/reservas");
            request.setRequestURI("/api/public/fiestas-pepito/reservas");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            status = response.getStatus();
        }
        assertEquals(429, status, "POST /reservas debe limitarse a 5/min");
    }

    @Test
    void catalogoPublicoSigueUsandoBucketPublicoGeneral() throws Exception {
        int status = 200;
        for (int i = 1; i <= 31; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/fiestas-pepito");
            request.setRequestURI("/api/public/fiestas-pepito");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            status = response.getStatus();
        }
        assertEquals(429, status, "GET público debe limitarse a 30/min");
    }

    @Test
    void registerYBucketsAuthSonIndependientes() throws Exception {
        for (int i = 1; i <= 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
            request.setRequestURI("/api/auth/register");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filter.doFilter(request, resp, new MockFilterChain());
        }
        MockHttpServletRequest loginReq = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse loginResp = new MockHttpServletResponse();
        loginReq.setRequestURI("/api/auth/login");
        filter.doFilter(loginReq, loginResp, new MockFilterChain());
        assertEquals(200, loginResp.getStatus(), "El bucket de register no debe consumir el de login");
    }

    @Test
    void limiteGlobalRegistroDuenoFrenaAtaqueDistribuido() throws Exception {
        ReflectionTestUtils.setField(filter, "authRegisterGlobalMaxPorMinuto", 5);
        int status = 200;
        for (int i = 1; i <= 6; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
            request.setRequestURI("/api/auth/register");
            request.addHeader("X-Forwarded-For", "10.0.0." + i);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            status = response.getStatus();
        }
        assertEquals(429, status, "Límite global de 5 debe frenar aunque cada IP haga solo 1 request");
    }

    @Test
    void limiteGlobalRegistroClienteFrenaAtaqueDistribuido() throws Exception {
        ReflectionTestUtils.setField(filter, "clienteAuthRegisterGlobalMaxPorMinuto", 5);
        int status = 200;
        for (int i = 1; i <= 6; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/cliente-auth/register");
            request.setRequestURI("/api/cliente-auth/register");
            request.addHeader("X-Forwarded-For", "172.16.0." + i);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            status = response.getStatus();
        }
        assertEquals(429, status, "Límite global de 5 debe frenar registro cliente con múltiples IPs");
    }

    @Test
    void limiteGlobalReservasPublicasFrenaAtaqueDistribuido() throws Exception {
        ReflectionTestUtils.setField(filter, "publicReservasGlobalMaxPorMinuto", 5);
        int status = 200;
        for (int i = 1; i <= 6; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/public/negocio-" + i + "/reservas");
            request.setRequestURI("/api/public/negocio-" + i + "/reservas");
            request.addHeader("X-Forwarded-For", "192.168.0." + i);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            status = response.getStatus();
        }
        assertEquals(429, status, "Límite global de 5 debe frenar solicitudes públicas con múltiples IPs");
    }

    @Test
    void limiteGlobalNoAfectaEndpointsSinConfiguracionGlobal() throws Exception {
        for (int i = 1; i <= 70; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/reservas");
            request.setRequestURI("/api/reservas");
            request.addHeader("X-Forwarded-For", "10.10.0." + i);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertEquals(200, response.getStatus(), "Panel no tiene límite global, request " + i + " debe pasar");
        }
    }

    @Test
    void limiteGlobalLoginFrenaFuerzaBrutaDistribuida() throws Exception {
        ReflectionTestUtils.setField(filter, "authGlobalMaxPorMinuto", 5);
        int status = 200;
        for (int i = 1; i <= 6; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRequestURI("/api/auth/login");
            request.addHeader("X-Forwarded-For", "1.1.1." + i);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            status = response.getStatus();
        }
        assertEquals(429, status, "Límite global de login debe frenar con múltiples IPs");
    }
}

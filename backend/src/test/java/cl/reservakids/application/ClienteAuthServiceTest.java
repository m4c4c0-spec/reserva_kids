package cl.reservakids.application;

import cl.reservakids.application.dto.ClienteDtos;
import cl.reservakids.application.usecase.AuthEventPort;
import cl.reservakids.application.usecase.ClienteAuthService;
import cl.reservakids.application.usecase.TokenPort;
import cl.reservakids.domain.model.CuentaCliente;
import cl.reservakids.domain.model.RefreshTokenCliente;
import cl.reservakids.domain.repository.CuentaClienteRepository;
import cl.reservakids.domain.repository.RefreshTokenClienteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClienteAuthServiceTest {

    @Mock CuentaClienteRepository cuentaClienteRepository;
    @Mock RefreshTokenClienteRepository refreshTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock TokenPort tokenPort;
    @Mock AuthEventPort authEvent;

    @InjectMocks ClienteAuthService service;

    @Test
    void loginEmiteAccessYRefreshToken() {
        ReflectionTestUtils.setField(service, "refreshDays", 7L);
        CuentaCliente cuenta = cuentaConId(1L);
        when(cuentaClienteRepository.findByEmail("ana@test.cl")).thenReturn(Optional.of(cuenta));
        when(passwordEncoder.matches("password123", cuenta.getPasswordHash())).thenReturn(true);
        when(tokenPort.emitirAccessTokenCliente(1L, "ana@test.cl")).thenReturn("access-token");

        ClienteDtos.ClienteTokenResponse resp = service.login(
                new ClienteDtos.LoginClienteRequest("ana@test.cl", "password123"));

        assertEquals("access-token", resp.accessToken());
        assertNotNull(resp.refreshToken());
        assertEquals("Ana", resp.nombre());

        ArgumentCaptor<RefreshTokenCliente> captor = ArgumentCaptor.forClass(RefreshTokenCliente.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertEquals(1L, captor.getValue().getCuentaClienteId());
        assertFalse(captor.getValue().isRevocado());
    }

    @Test
    void refreshRotadoRevocaFamiliaCompleta() {
        ReflectionTestUtils.setField(service, "refreshDays", 7L);
        RefreshTokenCliente token = new RefreshTokenCliente();
        token.setId(java.util.UUID.randomUUID());
        token.setCuentaClienteId(1L);
        token.setRevocado(true);
        token.setExpiraEn(OffsetDateTime.now().plusDays(7));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThrows(BadCredentialsException.class, () -> service.refresh("token-robadox"));
        verify(refreshTokenRepository).revocarTodosDeCuenta(1L);
    }

    private CuentaCliente cuentaConId(Long id) {
        CuentaCliente c = new CuentaCliente();
        ReflectionTestUtils.setField(c, "id", id);
        c.setEmail("ana@test.cl");
        c.setNombre("Ana");
        c.setTelefono("56911111111");
        c.setPasswordHash("hash");
        return c;
    }
}

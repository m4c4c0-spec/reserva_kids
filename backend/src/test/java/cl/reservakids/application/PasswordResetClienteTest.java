package cl.reservakids.application;

import cl.reservakids.application.usecase.AuthEventPort;
import cl.reservakids.application.usecase.ClienteAuthService;
import cl.reservakids.application.usecase.NotificacionPort;
import cl.reservakids.application.usecase.TokenPort;
import cl.reservakids.domain.model.CuentaCliente;
import cl.reservakids.domain.model.PasswordResetToken;
import cl.reservakids.domain.repository.CuentaClienteRepository;
import cl.reservakids.domain.repository.PasswordResetTokenRepository;
import cl.reservakids.domain.repository.RefreshTokenClienteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Sprint 2 §2.3: recuperación de contraseña para cuentas de cliente (apoderados).
 * Verifica el mismo patrón anti-enumeración que {@link PasswordResetServiceTest}:
 * solicitud silenciosa ante email desconocido, token de un solo uso, y revocación
 * de todas las sesiones al cambiar la contraseña.
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetClienteTest {

    @Mock CuentaClienteRepository cuentaClienteRepository;
    @Mock RefreshTokenClienteRepository refreshTokenRepository;
    @Mock PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock TokenPort tokenPort;
    @Mock NotificacionPort notificacion;
    @Mock AuthEventPort authEvent;

    @InjectMocks ClienteAuthService service;

    @Test
    void solicitarResetGeneraTokenInvalidaPreviosYNotifica() {
        ReflectionTestUtils.setField(service, "resetMinutos", 30L);
        CuentaCliente cuenta = new CuentaCliente();
        ReflectionTestUtils.setField(cuenta, "id", 7L);
        cuenta.setEmail("ana@test.cl");
        when(cuentaClienteRepository.findByEmail("ana@test.cl")).thenReturn(Optional.of(cuenta));

        service.solicitarResetPassword("ana@test.cl");

        verify(passwordResetTokenRepository).invalidarVigentesDeCuentaCliente(7L);
        verify(passwordResetTokenRepository).save(argThat(t ->
                t.getCuentaClienteId().equals(7L) && !t.isUsado()
                        && t.getExpiraEn().isAfter(OffsetDateTime.now().plusMinutes(25))));
        verify(notificacion).resetPasswordCliente(eq(cuenta), anyString());
    }

    /** Anti-enumeración: un email desconocido no lanza, no guarda ni envía nada. */
    @Test
    void solicitarResetConEmailDesconocidoEsSilencioso() {
        when(cuentaClienteRepository.findByEmail("nadie@test.cl")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.solicitarResetPassword("nadie@test.cl"));

        verifyNoInteractions(passwordResetTokenRepository, notificacion);
    }

    @Test
    void confirmarResetCambiaPasswordQuemaTokenYRevocaSesiones() {
        PasswordResetToken token = resetToken(false, OffsetDateTime.now().plusMinutes(10));
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        CuentaCliente cuenta = new CuentaCliente();
        ReflectionTestUtils.setField(cuenta, "id", 7L);
        cuenta.setPasswordHash("hash-viejo");
        when(cuentaClienteRepository.findById(7L)).thenReturn(Optional.of(cuenta));
        when(passwordEncoder.encode("clave-nueva-segura")).thenReturn("hash-nuevo");

        service.confirmarResetPassword("token-plano", "clave-nueva-segura");

        assertTrue(token.isUsado());
        assertEquals("hash-nuevo", cuenta.getPasswordHash());
        verify(refreshTokenRepository).revocarTodosDeCuenta(7L);
    }

    @Test
    void confirmarResetRechazaTokenUsadoOExpirado() {
        PasswordResetToken usado = resetToken(true, OffsetDateTime.now().plusMinutes(10));
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(usado));

        assertThrows(BadCredentialsException.class,
                () -> service.confirmarResetPassword("token-ya-usado", "clave-nueva-segura"));

        verify(refreshTokenRepository, never()).revocarTodosDeCuenta(any());
        verifyNoInteractions(passwordEncoder);
    }

    private PasswordResetToken resetToken(boolean usado, OffsetDateTime expiraEn) {
        PasswordResetToken token = new PasswordResetToken();
        token.setId(UUID.randomUUID());
        token.setCuentaClienteId(7L);
        token.setTokenHash("hash");
        token.setUsado(usado);
        token.setExpiraEn(expiraEn);
        return token;
    }
}

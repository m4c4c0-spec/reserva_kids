package cl.reservakids.application;

import cl.reservakids.application.dto.AuthDtos.ResetConfirmacionRequest;
import cl.reservakids.application.usecase.NotificacionPort;
import cl.reservakids.application.usecase.PasswordResetService;
import cl.reservakids.domain.model.PasswordResetToken;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.model.Usuario;
import cl.reservakids.domain.repository.PasswordResetTokenRepository;
import cl.reservakids.domain.repository.RefreshTokenRepository;
import cl.reservakids.domain.repository.TenantRepository;
import cl.reservakids.domain.repository.UsuarioRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Falla 1.3 (revisión 5 años): recuperación de contraseña del dueño. */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock UsuarioRepository usuarioRepository;
    @Mock TenantRepository tenantRepository;
    @Mock PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock NotificacionPort notificacion;

    @InjectMocks PasswordResetService service;

    @Test
    void solicitarResetGeneraTokenInvalidaPreviosYNotifica() {
        ReflectionTestUtils.setField(service, "resetMinutos", 30L);
        Usuario usuario = new Usuario();
        usuario.setId(5L);
        usuario.setTenantId(1L);
        usuario.setEmail("dueno@mail.cl");
        when(usuarioRepository.findByEmail("dueno@mail.cl")).thenReturn(Optional.of(usuario));
        Tenant activo = new Tenant();
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(activo));

        service.solicitarResetPassword("dueno@mail.cl");

        verify(passwordResetTokenRepository).invalidarVigentesDeUsuario(5L); // solo el último enlace sirve
        verify(passwordResetTokenRepository).save(argThat(t ->
                t.getUsuarioId().equals(5L) && !t.isUsado()
                        && t.getExpiraEn().isAfter(OffsetDateTime.now().plusMinutes(25))));
        verify(notificacion).resetPassword(eq(usuario), anyString());
    }

    /** Anti-enumeración: un email desconocido no lanza, no guarda y no envía nada. */
    @Test
    void solicitarResetConEmailDesconocidoEsSilencioso() {
        when(usuarioRepository.findByEmail("nadie@mail.cl")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.solicitarResetPassword("nadie@mail.cl"));

        verifyNoInteractions(passwordResetTokenRepository, notificacion);
    }

    /** Falla 3.3: un tenant suspendido/cerrado tampoco recibe resets (y sin revelar nada). */
    @Test
    void solicitarResetDeTenantNoActivoEsSilencioso() {
        Usuario usuario = new Usuario();
        usuario.setId(5L);
        usuario.setTenantId(1L);
        when(usuarioRepository.findByEmail("dueno@mail.cl")).thenReturn(Optional.of(usuario));
        Tenant cerrado = new Tenant();
        cerrado.setEstado(Tenant.ESTADO_CERRADO);
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(cerrado));

        assertDoesNotThrow(() -> service.solicitarResetPassword("dueno@mail.cl"));

        verifyNoInteractions(passwordResetTokenRepository, notificacion);
    }

    @Test
    void confirmarResetCambiaPasswordQuemaTokenYRevocaSesiones() {
        PasswordResetToken token = resetToken(false, OffsetDateTime.now().plusMinutes(10));
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        Usuario usuario = new Usuario();
        usuario.setId(5L);
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.encode("clave-nueva-segura")).thenReturn("hash-nuevo");

        service.confirmarResetPassword(new ResetConfirmacionRequest("token-plano", "clave-nueva-segura"));

        assertTrue(token.isUsado()); // un solo uso
        assertEquals("hash-nuevo", usuario.getPasswordHash());
        verify(refreshTokenRepository).revocarTodosDeUsuario(5L); // todas las sesiones mueren
    }

    @Test
    void confirmarResetRechazaTokenUsadoOExpirado() {
        PasswordResetToken usado = resetToken(true, OffsetDateTime.now().plusMinutes(10));
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(usado));

        assertThrows(BadCredentialsException.class, () -> service.confirmarResetPassword(
                new ResetConfirmacionRequest("token-ya-usado", "clave-nueva-segura")));

        verify(refreshTokenRepository, never()).revocarTodosDeUsuario(any());
        verifyNoInteractions(passwordEncoder);
    }

    private PasswordResetToken resetToken(boolean usado, OffsetDateTime expiraEn) {
        PasswordResetToken token = new PasswordResetToken();
        token.setId(UUID.randomUUID());
        token.setUsuarioId(5L);
        token.setTokenHash("hash");
        token.setUsado(usado);
        token.setExpiraEn(expiraEn);
        return token;
    }
}

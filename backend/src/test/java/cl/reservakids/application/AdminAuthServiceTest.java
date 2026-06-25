package cl.reservakids.application;

import cl.reservakids.application.dto.AdminDtos;
import cl.reservakids.application.usecase.AdminAuthService;
import cl.reservakids.application.usecase.AuthCrypto;
import cl.reservakids.application.usecase.AuthEventPort;
import cl.reservakids.application.usecase.TokenPort;
import cl.reservakids.domain.model.Administrador;
import cl.reservakids.domain.model.RefreshTokenAdmin;
import cl.reservakids.domain.repository.AdministradorRepository;
import cl.reservakids.domain.repository.RefreshTokenAdminRepository;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    @Mock AdministradorRepository administradorRepository;
    @Mock RefreshTokenAdminRepository refreshTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock TokenPort tokenPort;
    @Mock AuthEventPort authEvent;
    @Mock AuthCrypto authCrypto;

    @InjectMocks AdminAuthService service;

    @Test
    void loginEmiteAccessYRefreshToken() {
        ReflectionTestUtils.setField(service, "refreshDays", 7L);
        Administrador admin = adminConId(1L);
        when(administradorRepository.findByEmail("ops@reservakids.cl")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("password123", admin.getPasswordHash())).thenReturn(true);
        when(tokenPort.emitirAccessTokenAdmin(1L, "ops@reservakids.cl")).thenReturn("access-token");

        AdminDtos.AdminTokenResponse resp = service.login(
                new AdminDtos.LoginAdminRequest("ops@reservakids.cl", "password123"));

        assertEquals("access-token", resp.accessToken());
        assertNotNull(resp.refreshToken());
        assertEquals("Operador", resp.nombre());

        ArgumentCaptor<RefreshTokenAdmin> captor = ArgumentCaptor.forClass(RefreshTokenAdmin.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertEquals(1L, captor.getValue().getAdministradorId());
        assertFalse(captor.getValue().isRevocado());
    }

    @Test
    void loginConPasswordIncorrectoFalla() {
        Administrador admin = adminConId(1L);
        when(administradorRepository.findByEmail("ops@reservakids.cl")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("mala", admin.getPasswordHash())).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> service.login(
                new AdminDtos.LoginAdminRequest("ops@reservakids.cl", "mala")));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void loginEmailInexistenteFalla() {
        when(administradorRepository.findByEmail("nadie@reservakids.cl")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> service.login(
                new AdminDtos.LoginAdminRequest("nadie@reservakids.cl", "password123")));
    }

    @Test
    void refreshRotadoRevocaFamiliaCompleta() {
        ReflectionTestUtils.setField(service, "refreshDays", 7L);
        RefreshTokenAdmin token = new RefreshTokenAdmin();
        token.setId(UUID.randomUUID());
        token.setAdministradorId(1L);
        token.setRevocado(true);
        token.setExpiraEn(OffsetDateTime.now().plusDays(7));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThrows(BadCredentialsException.class, () -> service.refresh("token-robadox"));
        verify(refreshTokenRepository).revocarTodosDeAdmin(1L);
    }

    @Test
    void logoutRevocaTodasLasSesiones() {
        service.logout(1L, null);
        verify(refreshTokenRepository).revocarTodosDeAdmin(1L);
    }

    private Administrador adminConId(Long id) {
        Administrador a = new Administrador();
        ReflectionTestUtils.setField(a, "id", id);
        a.setEmail("ops@reservakids.cl");
        a.setNombre("Operador");
        a.setPasswordHash("hash");
        return a;
    }
}

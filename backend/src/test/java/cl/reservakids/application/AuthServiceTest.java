package cl.reservakids.application;

import cl.reservakids.application.dto.AuthDtos.RefreshRequest;
import cl.reservakids.application.usecase.AuthService;
import cl.reservakids.application.usecase.HorarioAtencionService;
import cl.reservakids.application.usecase.TokenPort;
import cl.reservakids.domain.model.RefreshToken;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.model.Usuario;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock TenantRepository tenantRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock HorarioAtencionService horarioAtencionService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock TokenPort tokenPort;

    @InjectMocks AuthService service;

    /** Fix #2 (revisión de código): reusar un refresh rotado = robo → muere toda la familia. */
    @Test
    void reusoDeRefreshRevocadoRevocaTodaLaFamilia() {
        RefreshToken robado = token(5L, true, OffsetDateTime.now().plusDays(3));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(robado));

        assertThrows(BadCredentialsException.class,
                () -> service.refresh(new RefreshRequest("token-robado-ya-rotado")));

        verify(refreshTokenRepository).revocarTodosDeUsuario(5L);
        verify(tokenPort, never()).emitirAccessToken(any());
    }

    @Test
    void refreshExpiradoNoEmiteTokensNiRevocaFamilia() {
        RefreshToken vencido = token(5L, false, OffsetDateTime.now().minusMinutes(1));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(vencido));

        assertThrows(BadCredentialsException.class,
                () -> service.refresh(new RefreshRequest("token-vencido")));

        verify(refreshTokenRepository, never()).revocarTodosDeUsuario(any());
        verify(tokenPort, never()).emitirAccessToken(any());
    }

    @Test
    void refreshValidoRotaElTokenYEmiteUnoNuevo() {
        ReflectionTestUtils.setField(service, "refreshDays", 7L);
        RefreshToken vigente = token(5L, false, OffsetDateTime.now().plusDays(3));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(vigente));

        Usuario usuario = new Usuario();
        usuario.setId(5L);
        usuario.setTenantId(1L);
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuario));
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setSlug("fiestas-pepito");
        tenant.setNombre("Fiestas Pepito");
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(tokenPort.emitirAccessToken(usuario)).thenReturn("jwt-nuevo");

        var respuesta = service.refresh(new RefreshRequest("token-vigente"));

        assertTrue(vigente.isRevocado()); // rotación: el usado queda revocado
        assertEquals("jwt-nuevo", respuesta.accessToken());
        assertNotNull(respuesta.refreshToken());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    /** Fix #6 (revisión de código): logout sin principal (access vencido) revoca por hash. */
    @Test
    void logoutSinPrincipalRevocaPorHashDelRefresh() {
        RefreshToken vigente = token(5L, false, OffsetDateTime.now().plusDays(3));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(vigente));

        service.logout(null, "refresh-en-claro");

        verify(refreshTokenRepository).revocarTodosDeUsuario(5L);
    }

    @Test
    void logoutConPrincipalRevocaTodosSinNecesitarBody() {
        service.logout(5L, null);

        verify(refreshTokenRepository).revocarTodosDeUsuario(5L);
        verify(refreshTokenRepository, never()).findByTokenHash(any());
    }

    /** Falla 3.3 (revisión 5 años): un tenant suspendido/cerrado no puede iniciar sesión. */
    @Test
    void loginRechazadoSiElTenantNoEstaActivo() {
        Usuario usuario = new Usuario();
        usuario.setId(5L);
        usuario.setTenantId(1L);
        usuario.setPasswordHash("hash");
        when(usuarioRepository.findByEmail("dueno@mail.cl")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("clave-correcta", "hash")).thenReturn(true);
        Tenant suspendido = new Tenant();
        suspendido.setEstado(Tenant.ESTADO_SUSPENDIDO);
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(suspendido));

        assertThrows(BadCredentialsException.class, () -> service.login(
                new cl.reservakids.application.dto.AuthDtos.LoginRequest("dueno@mail.cl", "clave-correcta")));
        verify(tokenPort, never()).emitirAccessToken(any());
    }

    /** Falla 3.3: el refresh de un tenant cerrado se quema (rotación commiteada) y no emite tokens. */
    @Test
    void refreshDeTenantCerradoQuemaElTokenSinEmitirNuevos() {
        RefreshToken vigente = token(5L, false, OffsetDateTime.now().plusDays(3));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(vigente));
        Usuario usuario = new Usuario();
        usuario.setId(5L);
        usuario.setTenantId(1L);
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuario));
        Tenant cerrado = new Tenant();
        cerrado.setEstado(Tenant.ESTADO_CERRADO);
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(cerrado));

        assertThrows(BadCredentialsException.class,
                () -> service.refresh(new RefreshRequest("token-de-tenant-cerrado")));

        assertTrue(vigente.isRevocado()); // el noRollbackFor commitea la rotación: token quemado
        verify(tokenPort, never()).emitirAccessToken(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    private RefreshToken token(Long usuarioId, boolean revocado, OffsetDateTime expiraEn) {
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setUsuarioId(usuarioId);
        token.setTokenHash("hash");
        token.setRevocado(revocado);
        token.setExpiraEn(expiraEn);
        return token;
    }
}

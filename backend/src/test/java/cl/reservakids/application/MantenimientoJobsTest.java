package cl.reservakids.application;

import cl.reservakids.application.usecase.BloqueMantenimientoJobs;
import cl.reservakids.application.usecase.ClienteRetencionJobs;
import cl.reservakids.application.usecase.DistributedLockPort;
import cl.reservakids.application.usecase.TenantPurgaJobs;
import cl.reservakids.application.usecase.TokenMantenimientoJobs;
import cl.reservakids.domain.model.Cliente;
import cl.reservakids.domain.model.EstadoBloque;
import cl.reservakids.domain.model.EstadoReserva;
import cl.reservakids.domain.model.Reserva;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.BloqueDisponibleRepository;
import cl.reservakids.domain.repository.ClienteRepository;
import cl.reservakids.domain.repository.PagoRepository;
import cl.reservakids.domain.repository.PasswordResetTokenRepository;
import cl.reservakids.domain.repository.RefreshTokenAdminRepository;
import cl.reservakids.domain.repository.RefreshTokenClienteRepository;
import cl.reservakids.domain.repository.RefreshTokenRepository;
import cl.reservakids.domain.repository.ReservaRepository;
import cl.reservakids.domain.repository.ServicioRepository;
import cl.reservakids.domain.repository.TenantRepository;
import cl.reservakids.domain.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Jobs de mantenimiento, ya descompuestos por concern: tokens, retención de clientes,
 * bloques y purga de tenants. Un único test de Mockito inyecta los mocks compartidos en
 * cada job y verifica su comportamiento de forma independiente.
 */
@ExtendWith(MockitoExtension.class)
class MantenimientoJobsTest {

    @Mock ReservaRepository reservaRepository;
    @Mock BloqueDisponibleRepository bloqueRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock RefreshTokenClienteRepository refreshTokenClienteRepository;
    @Mock RefreshTokenAdminRepository refreshTokenAdminRepository;
    @Mock ClienteRepository clienteRepository;
    @Mock TenantRepository tenantRepository;
    @Mock PagoRepository pagoRepository;
    @Mock ServicioRepository servicioRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock DistributedLockPort distributedLock;
    @Spy Clock clock = Clock.fixed(Instant.parse("2026-06-10T12:00:00Z"), ZoneOffset.UTC);

    @InjectMocks TokenMantenimientoJobs tokenJobs;
    @InjectMocks ClienteRetencionJobs clienteJobs;
    @InjectMocks BloqueMantenimientoJobs bloqueJobs;
    @InjectMocks TenantPurgaJobs tenantPurgaJobs;

    @BeforeEach
    void lockSiempreDisponible() {
        // tokenJobs, clienteJobs y tenantPurgaJobs toman el lock distribuido antes de operar;
        // bloqueJobs no lo usa, por eso el stub es lenient.
        lenient().when(distributedLock.tryAcquire(any())).thenReturn(true);
    }

    @Test
    void purgaRefreshTokensInvalidos() {
        when(refreshTokenRepository.purgarInvalidos(any())).thenReturn(12);
        when(refreshTokenClienteRepository.purgarInvalidos(any())).thenReturn(5);
        when(refreshTokenAdminRepository.purgarInvalidos(any())).thenReturn(1);

        tokenJobs.purgarRefreshTokens();

        verify(refreshTokenRepository).purgarInvalidos(any());
        verify(refreshTokenClienteRepository).purgarInvalidos(any());
        verify(refreshTokenAdminRepository).purgarInvalidos(any());
        // Falla 1.3 (5 años): el mismo job purga los tokens de reset usados/vencidos
        verify(passwordResetTokenRepository).purgarInvalidos(any());
    }

    /** Falla #4 (revisión 2 años, Ley 21.719): anonimiza inactivos, respeta a quien tenga reservas activas. */
    @Test
    void anonimizaInactivosSinReservasActivas() {
        ReflectionTestUtils.setField(clienteJobs, "retencionClienteMeses", 24L);

        Cliente inactivo = clienteConId(30L);
        Cliente conReservaActiva = clienteConId(31L);
        when(clienteRepository.findByAnonimizadoEnIsNullAndUltimaActividadEnBefore(any()))
                .thenReturn(List.of(inactivo, conReservaActiva));
        when(reservaRepository.existsByClienteIdAndEstadoIn(30L, EstadoReserva.ACTIVOS)).thenReturn(false);
        when(reservaRepository.existsByClienteIdAndEstadoIn(31L, EstadoReserva.ACTIVOS)).thenReturn(true);

        clienteJobs.anonimizarInactivos();

        assertTrue(inactivo.isAnonimizado());
        assertEquals(Cliente.NOMBRE_ANONIMO, inactivo.getNombre());
        assertNull(inactivo.getEmail());
        assertFalse(conReservaActiva.isAnonimizado());
        assertEquals("Ana", conReservaActiva.getNombre());
        // Falla 3.2 (5 años): limpia los comentarios del anonimizado, no del que sigue activo
        verify(reservaRepository).anonimizarComentariosDeCliente(30L, Reserva.COMENTARIOS_ANONIMIZADOS);
        verify(reservaRepository, never()).anonimizarComentariosDeCliente(eq(31L), any());
    }

    /** Falla #11 (revisión 2 años): el barrido elimina bloques pasados DISPONIBLE sin reservas. */
    @Test
    void limpiaBloquesPasadosSinReservas() {
        when(bloqueRepository.eliminarPasadosSinReserva(LocalDate.parse("2026-06-10"), EstadoBloque.DISPONIBLE))
                .thenReturn(3);

        bloqueJobs.limpiarBloquesPasados();

        verify(bloqueRepository).eliminarPasadosSinReserva(LocalDate.parse("2026-06-10"), EstadoBloque.DISPONIBLE);
    }

    /** Falla 3.3 (revisión 5 años): un tenant cerrado hace > N días se purga FÍSICAMENTE entero. */
    @Test
    void purgaTenantsCerradosVencidaLaVentanaDeGracia() {
        ReflectionTestUtils.setField(tenantPurgaJobs, "purgaDiasTrasCierre", 90L);

        Tenant cerrado = new Tenant();
        ReflectionTestUtils.setField(cerrado, "id", 7L);
        cerrado.setSlug("fiestas-pepito");
        cerrado.cerrar(java.time.OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        when(tenantRepository.findByEstadoAndCerradoEnBefore(eq(Tenant.ESTADO_CERRADO), any()))
                .thenReturn(List.of(cerrado));

        tenantPurgaJobs.purgarTenantsCerrados();

        // Orden FK: pago → reserva → bloque/cliente/servicio → tokens → usuario → tenant
        var orden = inOrder(pagoRepository, reservaRepository, bloqueRepository,
                clienteRepository, servicioRepository, refreshTokenRepository,
                passwordResetTokenRepository, usuarioRepository, tenantRepository);
        orden.verify(pagoRepository).eliminarDeTenant(7L);
        orden.verify(reservaRepository).eliminarDeTenant(7L);
        orden.verify(bloqueRepository).eliminarDeTenant(7L);
        orden.verify(clienteRepository).eliminarDeTenant(7L);
        orden.verify(servicioRepository).eliminarDeTenant(7L);
        orden.verify(refreshTokenRepository).eliminarDeTenant(7L);
        orden.verify(passwordResetTokenRepository).eliminarDeTenant(7L); // falla 1.3, antes que usuario
        orden.verify(usuarioRepository).eliminarDeTenant(7L);
        orden.verify(tenantRepository).delete(cerrado);
    }

    /** Idempotencia (falla 4.4): sin tenants vencidos, la pasada no borra nada. */
    @Test
    void purgaNoTocaNadaSinTenantsVencidos() {
        ReflectionTestUtils.setField(tenantPurgaJobs, "purgaDiasTrasCierre", 90L);
        when(tenantRepository.findByEstadoAndCerradoEnBefore(any(), any())).thenReturn(List.of());

        tenantPurgaJobs.purgarTenantsCerrados();

        verifyNoInteractions(pagoRepository, servicioRepository, usuarioRepository);
        verify(tenantRepository, never()).delete(any());
    }

    private Cliente clienteConId(Long id) {
        Cliente cliente = new Cliente();
        ReflectionTestUtils.setField(cliente, "id", id);
        cliente.setNombre("Ana");
        cliente.setTelefono("56911111111");
        cliente.setEmail("ana@mail.cl");
        return cliente;
    }
}

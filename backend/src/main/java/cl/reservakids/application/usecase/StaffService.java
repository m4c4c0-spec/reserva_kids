package cl.reservakids.application.usecase;

import cl.reservakids.application.dto.StaffDtos.*;
import cl.reservakids.domain.model.*;
import cl.reservakids.domain.repository.RolPersonalRepository;
import cl.reservakids.domain.repository.StaffRepository;
import cl.reservakids.domain.repository.ReservaRepository;
import cl.reservakids.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final StaffRepository staffRepository;
    private final ReservaRepository reservaRepository;
    private final RolPersonalRepository rolPersonalRepository;
    private final TenantRepository tenantRepository;
    private final NotificacionPort notificacion;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Staff login(String email, String password) {
        Staff s = staffRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Credenciales inválidas"));
        if (!s.isActivo()) throw new IllegalArgumentException("Cuenta desactivada");
        if (!passwordEncoder.matches(password, s.getPasswordHash()))
            throw new IllegalArgumentException("Credenciales inválidas");
        return s;
    }

    @Transactional(readOnly = true)
    public List<StaffResponse> listar(Long tenantId) {
        return staffRepository.findByTenantIdOrderByNombre(tenantId).stream()
                .map(this::aResponse).toList();
    }

    @Transactional
    public StaffResponse crear(Long tenantId, StaffCrearRequest req) {
        if (staffRepository.findByEmail(req.email()).isPresent())
            throw new IllegalArgumentException("El email ya está registrado");
        Staff s = new Staff();
        s.setTenantId(tenantId);
        s.setNombre(req.nombre().trim());
        s.setEmail(req.email().trim().toLowerCase());
        s.setTelefono(req.telefono() != null ? req.telefono().trim() : null);
        s.setPasswordHash(passwordEncoder.encode(req.password()));

        // RBAC: si se especifica rolPersonalId y pertenece al tenant, se asigna
        if (req.rolPersonalId() != null) {
            RolPersonal rol = rolPersonalRepository.findById(req.rolPersonalId())
                    .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado"));
            if (!rol.getTenantId().equals(tenantId)) {
                throw new IllegalArgumentException("El rol no pertenece a este negocio");
            }
            s.setRolPersonalId(rol.getId());
            s.setRol(rol.getNombre());
        } else {
            // Compatibilidad legacy: si no se especifica rol, usar el string libre
            String rolStr = req.rol() != null && !req.rol().isBlank() ? req.rol().trim() : "ANIMADOR";
            s.setRol(rolStr);
        }
        Staff guardado = staffRepository.save(s);

        // Email de bienvenida con credenciales
        String negocioNombre = tenantRepository.findById(tenantId)
                .map(Tenant::getNombre).orElse("ReservaKids");
        notificacion.staffBienvenida(guardado, req.password(), negocioNombre);

        return aResponse(guardado);
    }

    @Transactional
    public StaffResponse actualizar(Long tenantId, Long staffId, StaffActualizarRequest req) {
        Staff s = staffRepository.findByIdAndTenantId(staffId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Staff no encontrado"));
        if (req.nombre() != null && !req.nombre().isBlank()) {
            s.setNombre(req.nombre().trim());
        }
        if (req.rolPersonalId() != null) {
            RolPersonal rol = rolPersonalRepository.findById(req.rolPersonalId())
                    .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado"));
            if (!rol.getTenantId().equals(tenantId)) {
                throw new IllegalArgumentException("El rol no pertenece a este negocio");
            }
            s.setRolPersonalId(rol.getId());
            s.setRol(rol.getNombre());
        }
        return aResponse(staffRepository.save(s));
    }

    @Transactional
    public void eliminar(Long tenantId, Long staffId) {
        Staff s = staffRepository.findByIdAndTenantId(staffId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Staff no encontrado"));
        s.setActivo(false);
    }

    @Transactional(readOnly = true)
    public List<Reserva> eventosDelDia(Long tenantId, String fechaStr) {
        LocalDate fecha = LocalDate.parse(fechaStr);
        OffsetDateTime inicio = fecha.atStartOfDay(ZoneId.of("America/Santiago")).toOffsetDateTime();
        OffsetDateTime fin = fecha.plusDays(1).atStartOfDay(ZoneId.of("America/Santiago")).toOffsetDateTime();
        return reservaRepository.findByTenantIdOrderByCreadaEnDesc(tenantId).stream()
                .filter(r -> r.getInicio() != null && !r.getInicio().isBefore(inicio) && r.getInicio().isBefore(fin))
                .filter(r -> "CONFIRMADA".equals(r.getEstado().name()) || "REALIZADA".equals(r.getEstado().name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Staff> staffConWhatsapp(Long tenantId) {
        return staffRepository.findByTenantIdAndActivoTrueAndWhatsappRecordatorioTrue(tenantId);
    }

    private StaffResponse aResponse(Staff s) {
        String rolNombre = s.getRol();
        if (s.getRolPersonalId() != null) {
            rolNombre = rolPersonalRepository.findById(s.getRolPersonalId())
                    .map(RolPersonal::getNombre)
                    .orElse(s.getRol());
        }
        return new StaffResponse(s.getId(), s.getNombre(), s.getEmail(),
                s.getTelefono(), s.getRol(), s.getRolPersonalId(), rolNombre,
                s.isActivo(), s.isWhatsappRecordatorio());
    }
}

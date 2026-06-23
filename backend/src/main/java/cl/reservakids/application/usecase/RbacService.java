package cl.reservakids.application.usecase;

import cl.reservakids.domain.model.Permiso;
import cl.reservakids.domain.model.RolPersonal;
import cl.reservakids.domain.model.Staff;
import cl.reservakids.domain.repository.PermisoRepository;
import cl.reservakids.domain.repository.RolPersonalRepository;
import cl.reservakids.domain.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RbacService {

    private final RolPersonalRepository rolPersonalRepository;
    private final PermisoRepository permisoRepository;
    private final StaffRepository staffRepository;

    /**
     * Verifica si un usuario (dueño o staff) tiene un permiso específico.
     * El dueño siempre tiene todos los permisos.
     */
    @Transactional(readOnly = true)
    public boolean tienePermiso(Long usuarioId, Long tenantId, String rol, String permiso) {
        if ("DUENO".equals(rol) || "ADMIN".equals(rol)) {
            return true;
        }
        if (!"STAFF".equals(rol)) {
            return false;
        }
        Set<String> permisos = permisosEfectivos(usuarioId, tenantId);
        return permisos.contains(permiso);
    }

    /**
     * Resuelve los permisos efectivos de un staff:
     * si tiene rol asignado → permisos del rol
     * si no tiene rol (legacy) → permisos mínimos (eventos:hoy)
     */
    private Set<String> permisosEfectivos(Long staffId, Long tenantId) {
        Staff staff = staffRepository.findById(staffId).orElse(null);
        if (staff == null) return Set.of();

        if (staff.getRolPersonalId() != null) {
            List<String> codigos = rolPersonalRepository.findPermisosByRolId(staff.getRolPersonalId());
            return new HashSet<>(codigos);
        }

        // Staff legacy sin rol asignado: permisos mínimos
        return Set.of("eventos:hoy");
    }

    /** Lista todos los roles del tenant. Si no hay ninguno, crea los defaults automáticamente. */
    @Transactional
    public List<RolPersonal> listarRoles(Long tenantId) {
        var roles = rolPersonalRepository.findByTenantIdOrderByNombre(tenantId);
        if (roles.isEmpty()) {
            crearRolesPorDefecto(tenantId);
            return rolPersonalRepository.findByTenantIdOrderByNombre(tenantId);
        }
        return roles;
    }

    /** Catálogo completo de permisos disponibles. */
    @Transactional(readOnly = true)
    public List<Permiso> listarPermisos() {
        return permisoRepository.findAllByOrderByCategoriaAscCodigoAsc();
    }

    /** Crea un rol con los permisos indicados. */
    @Transactional
    public RolPersonal crearRol(Long tenantId, String nombre, String descripcion, List<String> permisos) {
        if (rolPersonalRepository.existsByTenantIdAndNombre(tenantId, nombre)) {
            throw new IllegalArgumentException("Ya existe un rol con ese nombre");
        }
        RolPersonal rol = new RolPersonal(tenantId, nombre.trim());
        rol.setDescripcion(descripcion != null ? descripcion.trim() : null);
        rol.setPermisos(resolverPermisos(permisos));
        return rolPersonalRepository.save(rol);
    }

    @Transactional
    public RolPersonal actualizarRol(Long tenantId, Long rolId, String nombre, String descripcion, List<String> permisos) {
        RolPersonal rol = rolPersonalRepository.findById(rolId)
                .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado"));
        if (!rol.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Rol no pertenece a este negocio");
        }
        if (nombre != null && !nombre.isBlank() && !nombre.equals(rol.getNombre())) {
            if (rolPersonalRepository.existsByTenantIdAndNombre(tenantId, nombre.trim())) {
                throw new IllegalArgumentException("Ya existe un rol con ese nombre");
            }
            rol.setNombre(nombre.trim());
        }
        if (descripcion != null) rol.setDescripcion(descripcion.trim());
        if (permisos != null) rol.setPermisos(resolverPermisos(permisos));
        return rolPersonalRepository.save(rol);
    }

    @Transactional
    public void eliminarRol(Long tenantId, Long rolId) {
        RolPersonal rol = rolPersonalRepository.findById(rolId)
                .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado"));
        if (!rol.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Rol no pertenece a este negocio");
        }
        long staffConRol = staffRepository.countByRolPersonalId(rolId);
        if (staffConRol > 0) {
            throw new IllegalArgumentException(
                    "No se puede eliminar: hay " + staffConRol + " personas con este rol. Reasígnalos primero.");
        }
        rolPersonalRepository.delete(rol);
    }

    /** Crea los roles por defecto para un tenant nuevo. */
    @Transactional
    public void crearRolesPorDefecto(Long tenantId) {
        Map<String, List<String>> defaults = Map.of(
            "Limpieza",     List.of("eventos:hoy"),
            "Animador",     List.of("eventos:hoy", "eventos:detalle"),
            "Recepcionista",List.of("eventos:hoy", "eventos:detalle", "reservas:leer", "calendario:leer"),
            "Gerente",      List.of(
                "eventos:hoy", "eventos:detalle",
                "reservas:leer", "reservas:cotizar", "reservas:confirmar",
                "reservas:realizar", "reservas:cancelar", "reservas:pagos",
                "servicios:leer", "servicios:crear", "servicios:editar", "servicios:eliminar",
                "calendario:leer", "calendario:crear", "calendario:borrar",
                "personal:leer",
                "finanzas:leer",
                "configuracion:leer"
            )
        );
        for (var entry : defaults.entrySet()) {
            if (!rolPersonalRepository.existsByTenantIdAndNombre(tenantId, entry.getKey())) {
                RolPersonal rol = new RolPersonal(tenantId, entry.getKey());
                rol.setPermisos(resolverPermisos(entry.getValue()));
                rolPersonalRepository.save(rol);
            }
        }
    }

    private Set<Permiso> resolverPermisos(List<String> codigos) {
        if (codigos == null || codigos.isEmpty()) return Set.of();
        return codigos.stream()
                .map(c -> permisoRepository.findByCodigo(c)
                        .orElseThrow(() -> new IllegalArgumentException("Permiso no encontrado: " + c)))
                .collect(Collectors.toSet());
    }
}

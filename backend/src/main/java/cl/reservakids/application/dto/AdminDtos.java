package cl.reservakids.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/** Administrador de plataforma: login, sesión y gobierno de negocios (F2). */
public final class AdminDtos {

    private AdminDtos() {}

    public record LoginAdminRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {}

    /** El refresh token viaja en cookie HttpOnly; el body solo expone el access. */
    public record AdminTokenResponse(String accessToken, String refreshToken, String email, String nombre) {}

    /** Fila del listado de negocios de la consola de admin (F2). */
    public record NegocioAdminResumen(
            Long id, String slug, String nombre, String plan, String estado,
            OffsetDateTime creadoEn, long reservas) {}

    /** KPIs globales de plataforma (F4): visión de operador, cross-tenant. */
    public record MetricasGlobales(
            long negociosTotal, long negociosActivos, long negociosSuspendidos, long negociosCerrados,
            long reservasTotal, long reservasActivas,
            long apoderados, long recaudadoSenasClp) {}

    /** Entrada de la bitácora de acciones (F5). */
    public record AuditoriaItem(OffsetDateTime creadoEn, String adminEmail, String accion, String detalle) {}

    /** Administrador en el listado de gestión multi-admin (F5). Nunca expone el hash. */
    public record AdminResumen(Long id, String email, String nombre, OffsetDateTime creadoEn) {}

    /** Alta de un nuevo administrador por otro admin (F5). */
    public record CrearAdminRequest(
            @NotBlank @Size(max = 120) String nombre,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {}
}

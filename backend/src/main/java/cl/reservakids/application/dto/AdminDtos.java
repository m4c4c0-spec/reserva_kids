package cl.reservakids.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

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
}

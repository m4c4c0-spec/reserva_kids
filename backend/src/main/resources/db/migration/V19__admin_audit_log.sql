-- V19: bitácora de acciones del administrador de plataforma (F5).
--
-- Cumple la promesa que la consola muestra al operador ("Las acciones quedan registradas"):
-- toda acción con impacto sobre un negocio (suspender/reactivar) o sobre la plataforma
-- (alta de otro admin) deja traza inmutable de QUIÉN, QUÉ y CUÁNDO. Append-only.

CREATE TABLE admin_audit_log (
    id               BIGSERIAL    PRIMARY KEY,
    administrador_id BIGINT       NOT NULL REFERENCES administrador(id),
    accion           VARCHAR(40)  NOT NULL,   -- SUSPENDER_NEGOCIO, REACTIVAR_NEGOCIO, CREAR_ADMIN
    tenant_id        BIGINT,                  -- negocio afectado (nullable: acciones de plataforma)
    detalle          VARCHAR(500),
    creado_en        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_admin_audit_creado_en ON admin_audit_log(creado_en DESC);

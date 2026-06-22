-- V20: trazabilidad completa de acciones de DUENO y CLIENTE (RNF-07).
-- Complementa el admin_audit_log (V19) que solo cubre acciones del ADMIN.
--
-- Principio: toda acción que modifica estado de negocio (reservas, servicios, calendario,
-- configuración, pagos) o que crea entidades (solicitud pública, cita) deja una traza
-- inmutable de QUIÉN, QUÉ, SOBRE QUÉ, DÓNDE (IP) y CUÁNDO.
-- Append-only: solo INSERT; nunca UPDATE ni DELETE.

CREATE TABLE audit_event (
    id            BIGSERIAL     PRIMARY KEY,
    tenant_id     BIGINT        NOT NULL,
    actor_id      BIGINT        NOT NULL,     -- usuarioId (DUENO) o cuentaClienteId (CLIENTE)
    actor_type    VARCHAR(10)   NOT NULL,     -- 'DUENO' o 'CLIENTE'
    accion        VARCHAR(40)   NOT NULL,     -- ver constantes en AuditEvent.java
    recurso_tipo  VARCHAR(30),                -- 'RESERVA', 'SERVICIO', 'BLOQUE', 'CONFIGURACION', 'NEGOCIO', 'PAGO'
    recurso_id    BIGINT,
    detalle       VARCHAR(1000),
    ip_address    VARCHAR(45),                -- IPv4 o IPv6 del actor
    creado_en     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX ix_audit_event_tenant_accion ON audit_event(tenant_id, accion);
CREATE INDEX ix_audit_event_creado_en ON audit_event(creado_en DESC);

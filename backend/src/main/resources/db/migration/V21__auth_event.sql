-- V21: trazabilidad de eventos de autenticación (RNF-07).
-- Registra toda acción de seguridad relacionada con login, refresh, logout,
-- reset de contraseña y detección de robo de token para todos los actores
-- (DUENO, CLIENTE, ADMIN). Append-only: solo INSERT, nunca UPDATE ni DELETE.

CREATE TABLE auth_event (
    id            BIGSERIAL     PRIMARY KEY,
    actor_type    VARCHAR(10)   NOT NULL,    -- 'DUENO', 'CLIENTE', 'ADMIN'
    actor_id      BIGINT,                    -- usuarioId, cuentaClienteId o administradorId
    email_hash    VARCHAR(64)   NOT NULL,    -- SHA-256 del email normalizado (búsqueda sin exponer email)
    accion        VARCHAR(30)   NOT NULL,    -- LOGIN, LOGIN_FAIL, REFRESH, THEFT_DETECTED, LOGOUT, RESET_REQUEST, RESET_COMPLETE
    resultado     VARCHAR(10)   NOT NULL,    -- SUCCESS, FAILURE
    ip_address    VARCHAR(45),               -- IPv4 o IPv6 del actor
    user_agent    VARCHAR(500),              -- User-Agent del navegador
    detalle       VARCHAR(500),
    creado_en     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX ix_auth_event_creado_en ON auth_event(creado_en DESC);
CREATE INDEX ix_auth_event_email_hash ON auth_event(email_hash);

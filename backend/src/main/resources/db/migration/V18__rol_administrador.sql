-- V18: rol administrador de plataforma (gobierno de negocios cross-tenant).
--
-- Tercer actor del sistema, separado de:
--   * usuario        (dueño    — rol DUENO   — ligado a un tenant)
--   * cuenta_cliente (apoderado — rol CLIENTE — global, sin tenant)
-- El administrador NO tiene tenant_id: gobierna TODOS los negocios. Auth propia
-- (espejo de cuenta_cliente) con refresh por rotación (modelo de V16).
--
-- El primer admin NO se hornea aquí (un hash en el repo es deuda de seguridad y queda
-- en el historial git): se crea al arranque desde variables de entorno (AdminBootstrap).

CREATE TABLE administrador (
    id            BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(160) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    nombre        VARCHAR(120) NOT NULL,
    creado_en     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE refresh_token_admin (
    id               UUID         PRIMARY KEY,
    administrador_id BIGINT       NOT NULL REFERENCES administrador(id),
    token_hash       VARCHAR(64)  NOT NULL,
    expira_en        TIMESTAMPTZ  NOT NULL,
    revocado         BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX ix_refresh_admin_admin ON refresh_token_admin(administrador_id);

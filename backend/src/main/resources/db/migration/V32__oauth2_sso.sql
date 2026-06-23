-- V32: SSO (Google / Microsoft / Apple) — columnas OAuth2 y password opcional
ALTER TABLE usuario
    ADD COLUMN IF NOT EXISTS oauth_provider    VARCHAR(20),
    ADD COLUMN IF NOT EXISTS oauth_provider_id VARCHAR(255),
    ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE cuenta_cliente
    ADD COLUMN IF NOT EXISTS oauth_provider    VARCHAR(20),
    ADD COLUMN IF NOT EXISTS oauth_provider_id VARCHAR(255),
    ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE administrador
    ADD COLUMN IF NOT EXISTS oauth_provider    VARCHAR(20),
    ADD COLUMN IF NOT EXISTS oauth_provider_id VARCHAR(255),
    ALTER COLUMN password_hash DROP NOT NULL;

-- Índices para búsqueda por (provider, provider_id)
CREATE UNIQUE INDEX IF NOT EXISTS idx_usuario_oauth       ON usuario (oauth_provider, oauth_provider_id) WHERE oauth_provider IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_cuenta_cliente_oauth ON cuenta_cliente (oauth_provider, oauth_provider_id) WHERE oauth_provider IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_administrador_oauth  ON administrador (oauth_provider, oauth_provider_id) WHERE oauth_provider IS NOT NULL;

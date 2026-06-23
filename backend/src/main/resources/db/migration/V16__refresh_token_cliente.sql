-- V16: refresh tokens para cuentas de cliente (apoderados).
--
-- El área de cliente tenía solo access token (15 min): se expulsaba al login cada
-- 15 min. Esta tabla replica el modelo del dueño (SHA-256, rotación, revocación
-- masiva ante robo, purga diaria) para darle la misma experiencia de sesión.

CREATE TABLE refresh_token_cliente (
    id               UUID PRIMARY KEY,
    cuenta_cliente_id BIGINT      NOT NULL REFERENCES cuenta_cliente(id),
    token_hash       VARCHAR(64) NOT NULL,
    expira_en        TIMESTAMPTZ NOT NULL,
    revocado         BOOLEAN     NOT NULL DEFAULT FALSE
);
CREATE INDEX ix_refresh_cliente_cuenta ON refresh_token_cliente(cuenta_cliente_id);

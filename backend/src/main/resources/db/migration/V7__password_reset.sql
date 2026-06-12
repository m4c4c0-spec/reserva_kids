-- ReservaKids V7 — Falla 1.3 (revisión a 5 años): recuperación de contraseña.
--
-- Sin esto, el primer dueño que olvidara su clave perdía el acceso a su negocio y el
-- "soporte" era un UPDATE manual por SSH contra producción. Misma maquinaria probada
-- del refresh token: el token viaja en claro por email UNA vez y en BD solo vive su
-- SHA-256; un solo uso y vencimiento corto (RESET_PASSWORD_MINUTOS, 30).
CREATE TABLE password_reset_token (
    id         UUID PRIMARY KEY,
    usuario_id BIGINT      NOT NULL REFERENCES usuario(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expira_en  TIMESTAMPTZ NOT NULL,
    usado      BOOLEAN     NOT NULL DEFAULT FALSE,
    creado_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_prt_usuario ON password_reset_token(usuario_id);

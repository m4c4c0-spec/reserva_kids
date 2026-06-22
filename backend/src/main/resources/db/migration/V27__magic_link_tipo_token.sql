-- V27: Magic links (login sin contraseña) para dueños adultos mayores.
-- Reusamos password_reset_token (misma estructura: hash SHA-256, un solo uso, vencimiento
-- corto) añadiéndole un discriminador de tipo, así un token de magic link no se acepta
-- como reset de contraseña y viceversa.

ALTER TABLE password_reset_token ADD COLUMN tipo VARCHAR(16) NOT NULL DEFAULT 'PASSWORD';

ALTER TABLE password_reset_token
    ADD CONSTRAINT ck_password_reset_token_tipo CHECK (tipo IN ('PASSWORD', 'MAGIC'));

-- Vencimiento del reset de contraseña seguía siendo 30 min (app.password-reset.minutos);
-- el magic link usa su propio plazo (app.magic-link.minutos, default 10) que se setea al
-- crear el token, así que no hay que tocar el esquema.
-- V31: Tabla de claves de idempotencia para prevenir operaciones duplicadas.
-- El cliente envía X-Idempotency-Key (UUID v4) en mutaciones (POST/PUT/PATCH);
-- la API registra la clave y rechaza reenvíos con 409 Conflict.
-- Las claves expiran tras 24 h; un job programado las purga.

CREATE TABLE idempotency_key (
    key          VARCHAR(64)  NOT NULL,
    endpoint     VARCHAR(255) NOT NULL,
    creado_en    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (key)
);

CREATE INDEX idx_idempotency_key_creado_en ON idempotency_key (creado_en);

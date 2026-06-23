-- V29: Saldos Pendientes, Escudo Legal, RSVP, Upsells, Cierre de Caja

-- Escudo Legal: politicas de cancelacion por tenant (texto enriquecido libre)
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS politicas_cancelacion TEXT;

-- Upsells: flag de servicio adicional (pinta-caritas, pizzas extra, etc.)
ALTER TABLE servicio ADD COLUMN IF NOT EXISTS es_adicional BOOLEAN NOT NULL DEFAULT false;

-- Escudo Legal: momento en que el cliente acepto las politicas al reservar
ALTER TABLE reserva ADD COLUMN IF NOT EXISTS politicas_aceptadas_en TIMESTAMPTZ;

-- RSVP: invitados a la fiesta con link unico de confirmacion
CREATE TABLE IF NOT EXISTS invitado (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL REFERENCES tenant(id),
  reserva_id BIGINT NOT NULL REFERENCES reserva(id),
  nombre VARCHAR(120) NOT NULL,
  email VARCHAR(160),
  telefono VARCHAR(20),
  estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
  token VARCHAR(64) NOT NULL UNIQUE,
  comentarios VARCHAR(500),
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indice para busqueda por token (pagina publica de confirmacion)
CREATE INDEX IF NOT EXISTS idx_invitado_token ON invitado(token);
-- Indice para listar invitados de una reserva
CREATE INDEX IF NOT EXISTS idx_invitado_reserva ON invitado(reserva_id);

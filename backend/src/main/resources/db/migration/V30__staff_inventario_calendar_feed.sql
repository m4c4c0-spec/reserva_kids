-- V30: Staff, Inventario, Google Calendar Feed

-- Staff: miembro del equipo del negocio (animador, limpieza, etc.)
CREATE TABLE IF NOT EXISTS staff (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL REFERENCES tenant(id),
  nombre VARCHAR(120) NOT NULL,
  email VARCHAR(160) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  telefono VARCHAR(20),
  rol VARCHAR(30) NOT NULL DEFAULT 'ANIMADOR',
  activo BOOLEAN NOT NULL DEFAULT true,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_staff_tenant ON staff(tenant_id);

-- Inventario: stock fisico para servicios adicionales
ALTER TABLE servicio ADD COLUMN IF NOT EXISTS stock INTEGER;

-- Canal de notificaciones: flag si un staff recibe recordatorios por WhatsApp
ALTER TABLE staff ADD COLUMN IF NOT EXISTS whatsapp_recordatorio BOOLEAN NOT NULL DEFAULT true;

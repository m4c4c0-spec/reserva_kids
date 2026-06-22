-- V26: Teléfono de contacto del salón (WhatsApp) expuesto en el mini-sitio público.
-- Sirve para el botón flotante "¿Dudas? Habla con el dueño": si un apoderado/abuelo se
-- traba en el flujo (sobre todo al pagar la seña), puede escribirle directo al dueño.
-- Se normaliza a E.164 sin '+' (569XXXXXXXX) — el catálogo público lo devuelve tal cual
-- para armar wa.me/<telefono>; nunca se expone otro dato personal del dueño.

ALTER TABLE tenant ADD COLUMN telefono_contacto VARCHAR(20);

-- No es obligatorio: el botón solo aparece si el dueño lo configura.
ALTER TABLE tenant ADD CONSTRAINT ck_tenant_telefono_contacto
    CHECK (telefono_contacto IS NULL OR telefono_contacto ~ '^[0-9]{8,15}$');
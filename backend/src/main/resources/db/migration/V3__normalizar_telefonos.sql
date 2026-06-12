-- ReservaKids V3 — Falla #9 (revisión a 2 años): teléfonos normalizados
-- (ver docs/REVISION_FALLAS_2_ANOS.md)
--
-- El cliente se deduplica por (tenant, telefono) y el link wa.me requiere código de país:
-- '+56 9 1234 5678', '56912345678' y '912345678' creaban TRES clientes distintos y links
-- de WhatsApp rotos. Desde ahora la aplicación normaliza al guardar (E.164 chileno sin '+');
-- esta migración limpia los datos existentes. Se excluyen los clientes anonimizados:
-- su placeholder ('anon-<id>') no es un teléfono.

-- 1. Solo dígitos
UPDATE cliente
SET telefono = regexp_replace(telefono, '[^0-9]', '', 'g')
WHERE anonimizado_en IS NULL
  AND telefono ~ '[^0-9]';

-- 2. Celular escrito sin código de país (9XXXXXXXX → 569XXXXXXXX)
UPDATE cliente
SET telefono = '56' || telefono
WHERE anonimizado_en IS NULL
  AND telefono ~ '^9[0-9]{8}$';

-- Nota: si dos filas de un mismo tenant colapsan al mismo número, quedan como duplicados
-- (no hay restricción única sobre (tenant_id, telefono)); la deduplicación dura se decidirá
-- con la ficha de cliente (RF-12, v1.1).

-- ReservaKids V6 — Falla 3.2 (revisión a 5 años): la anonimización era incompleta.
--
-- Cliente.anonimizar() limpiaba nombre/teléfono/email del cliente, pero los comentarios
-- de sus reservas — texto libre con datos personales: "[Contacto: <nombre>]", motivos de
-- cancelación, lo que el apoderado escribió en el formulario — sobrevivían a la supresión
-- (Ley 21.719, vigencia plena dic-2026). Desde ahora el código los reemplaza al anonimizar;
-- esta migración corrige el residuo de los clientes YA anonimizados.
--
-- version + 1: los UPDATE masivos no pasan por @Version; se incrementa a mano para que
-- cualquier lectura optimista concurrente pierda la carrera (en vez de pisar este borrado).
UPDATE reserva r
SET comentarios = '[anonimizado]',
    version     = r.version + 1
FROM cliente c
WHERE c.id = r.cliente_id
  AND c.anonimizado_en IS NOT NULL
  AND r.comentarios IS NOT NULL
  AND r.comentarios <> '[anonimizado]';

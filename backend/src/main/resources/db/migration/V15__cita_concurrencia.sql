-- V15: concurrencia en citas por hora — evitar doble reserva de la misma franja.
--
-- El agendamiento por hora no usaba ninguna barrera atómica: dos clientes podían
-- leer la misma hora libre y crear ambos la cita, solapándose. El código ahora:
--   1. Toma un advisory lock por (tenant_id, fecha) para serializar agendamientos
--      de un mismo negocio en un mismo día (pg_advisory_xact_lock, libera al COMMIT).
--   2. Re-verifica que la franja [inicio, fin) no se solape con citas tomadas.
--   3. Inserta la cita PENDIENTE_PAGO.
--
-- Este mecanismo es portable (no requiere la extensión btree_gist de un EXCLUDE)
-- y suficiente para la escala del proyecto (un solo recurso por negocio).

-- Reemplazamos el índice anterior por uno que incluya inicio y fin para acelerar
-- la consulta de solapamiento (la antigua solo tenía inicio).
DROP INDEX IF EXISTS idx_reserva_tenant_inicio;
CREATE INDEX idx_reserva_tenant_inicio_fin ON reserva(tenant_id, inicio, fin) WHERE inicio IS NOT NULL;

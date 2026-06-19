-- V14: cita agendada por hora (multi-servicio).
--
-- La reserva pasa de "un servicio + un bloque pre-creado" a "una franja [inicio, fin) con
-- varios servicios". Los campos viejos (servicio_id, bloque_id, num_ninos) se vuelven
-- NULLABLE: el flujo de cumpleaños los sigue usando hasta su retiro (Fase 7), las citas no.

ALTER TABLE reserva ALTER COLUMN servicio_id DROP NOT NULL;
ALTER TABLE reserva ALTER COLUMN bloque_id   DROP NOT NULL;

ALTER TABLE reserva ADD COLUMN inicio            TIMESTAMPTZ;
ALTER TABLE reserva ADD COLUMN fin               TIMESTAMPTZ;
-- Cuenta de cliente (apoderado) que agendó — distinto del Cliente tenant-scoped (contacto).
ALTER TABLE reserva ADD COLUMN cuenta_cliente_id BIGINT REFERENCES cuenta_cliente(id);

-- Ocupación: citas por hora de un negocio en un rango de tiempo (DisponibilidadService).
CREATE INDEX idx_reserva_tenant_inicio ON reserva(tenant_id, inicio) WHERE inicio IS NOT NULL;

-- Servicios de la cita, con snapshot de nombre/precio/duración al momento de agendar:
-- editar el catálogo después no altera el detalle ni el total de citas ya tomadas.
CREATE TABLE reserva_servicio (
    id           BIGSERIAL PRIMARY KEY,
    reserva_id   BIGINT       NOT NULL REFERENCES reserva(id) ON DELETE CASCADE,
    servicio_id  BIGINT       NOT NULL,
    nombre       VARCHAR(120) NOT NULL,
    precio_clp   INTEGER      NOT NULL,
    duracion_min INTEGER      NOT NULL
);
CREATE INDEX idx_reserva_servicio_reserva ON reserva_servicio(reserva_id);

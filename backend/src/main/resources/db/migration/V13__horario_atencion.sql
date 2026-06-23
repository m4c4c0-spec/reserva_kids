-- V13: agendamiento por hora — horario de atención del negocio + granularidad de slots.
--
-- Reemplaza el modelo de "bloques pre-creados por el dueño" (bloque_disponible) por uno
-- calculado: el dueño define su horario de atención por día y un intervalo, y el sistema
-- genera las horas libres según la duración total de los servicios elegidos (DisponibilidadService).

ALTER TABLE tenant ADD COLUMN intervalo_min INTEGER NOT NULL DEFAULT 30;

CREATE TABLE horario_atencion (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT   NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    dia_semana    SMALLINT NOT NULL CHECK (dia_semana BETWEEN 1 AND 7), -- ISO-8601: 1=Lunes .. 7=Domingo
    hora_apertura TIME     NOT NULL,
    hora_cierre   TIME     NOT NULL,
    CHECK (hora_cierre > hora_apertura)
);
CREATE INDEX idx_horario_tenant_dia ON horario_atencion(tenant_id, dia_semana);

-- Seed: horario por defecto para los negocios ACTIVOS (L-V 09:00–18:00, Sáb 10:00–14:00) para
-- que el agendamiento por hora funcione de inmediato sin esperar a la config del dueño (panel).
INSERT INTO horario_atencion (tenant_id, dia_semana, hora_apertura, hora_cierre)
SELECT t.id, d.dia, TIME '09:00', TIME '18:00'
FROM tenant t CROSS JOIN (VALUES (1), (2), (3), (4), (5)) AS d(dia)
WHERE t.estado = 'ACTIVO';

INSERT INTO horario_atencion (tenant_id, dia_semana, hora_apertura, hora_cierre)
SELECT t.id, 6, TIME '10:00', TIME '14:00'
FROM tenant t
WHERE t.estado = 'ACTIVO';

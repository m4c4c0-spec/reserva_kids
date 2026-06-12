-- ReservaKids V2 — Revisión de fallas a 2 años + cumplimiento Ley 21.719
-- (ver docs/REVISION_FALLAS_2_ANOS.md)

-- ── Falla #5: pago como libro contable, listo para Mercado Pago (P1) ──
-- tipo: ABONO suma, DEVOLUCION resta (el monto siempre es positivo).
ALTER TABLE pago ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'ABONO';
ALTER TABLE pago ADD CONSTRAINT ck_pago_tipo CHECK (tipo IN ('ABONO', 'DEVOLUCION'));

-- estado: los pagos manuales nacen CONFIRMADO; la pasarela creará PENDIENTE → CONFIRMADO/RECHAZADO.
ALTER TABLE pago ADD COLUMN estado VARCHAR(20) NOT NULL DEFAULT 'CONFIRMADO';
ALTER TABLE pago ADD CONSTRAINT ck_pago_estado CHECK (estado IN ('PENDIENTE', 'CONFIRMADO', 'RECHAZADO'));

-- referencia_externa: id de la pasarela (payment_id de MP). Único cuando existe →
-- idempotencia de webhooks (MP los reenvía duplicados y desordenados).
ALTER TABLE pago ADD COLUMN referencia_externa VARCHAR(120);
CREATE UNIQUE INDEX ux_pago_ref_externa ON pago(referencia_externa) WHERE referencia_externa IS NOT NULL;

-- registrado_por: auditoría mínima — quién anotó el pago (NULL = sistema/pasarela).
ALTER TABLE pago ADD COLUMN registrado_por BIGINT REFERENCES usuario(id);

-- ── Falla #1/#2: expiración de cotizaciones sin respuesta ──
ALTER TABLE reserva ADD COLUMN cotizada_en TIMESTAMPTZ;
-- backfill: cotizadas históricas usan su fecha de creación como base
UPDATE reserva SET cotizada_en = creada_en WHERE estado = 'COTIZADA' AND cotizada_en IS NULL;

-- ── Falla #4: Ley 21.719 (protección de datos personales, vigencia plena dic-2026) ──
-- consentimiento_en: prueba de cuándo el titular aceptó el tratamiento (art. consentimiento).
ALTER TABLE cliente ADD COLUMN consentimiento_en TIMESTAMPTZ;
-- ultima_actividad_en: base del plazo de retención (minimización de datos).
ALTER TABLE cliente ADD COLUMN ultima_actividad_en TIMESTAMPTZ NOT NULL DEFAULT now();
-- anonimizado_en: marca de ejercicio del derecho de supresión (los datos ya no son personales).
ALTER TABLE cliente ADD COLUMN anonimizado_en TIMESTAMPTZ;

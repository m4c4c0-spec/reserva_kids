-- V23: facturación de la suscripción SaaS (los salones le pagan a ReservaKids).
-- Cada tenant tiene un plan (GRATIS/BASICO/PRO) y un estado de suscripción.
-- La integración de pago recurrente (Mercado Pago Subscriptions / Stripe) se
-- configura por tenant cuando elige un plan pago.

ALTER TABLE tenant ADD COLUMN plan_suscripcion VARCHAR(20) NOT NULL DEFAULT 'GRATIS';
ALTER TABLE tenant ADD COLUMN suscripcion_estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO';
ALTER TABLE tenant ADD COLUMN suscripcion_inicio TIMESTAMPTZ;
ALTER TABLE tenant ADD COLUMN suscripcion_renovacion TIMESTAMPTZ;
ALTER TABLE tenant ADD COLUMN suscripcion_referencia_externa VARCHAR(120); -- ID en MercadoPago/Stripe

CREATE TABLE suscripcion_pago (
    id              BIGSERIAL    PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL REFERENCES tenant(id),
    plan             VARCHAR(20)  NOT NULL,    -- GRATIS, BASICO, PRO
    monto_clp        INTEGER      NOT NULL,
    periodo          VARCHAR(10)  NOT NULL,    -- MENSUAL, ANUAL
    estado           VARCHAR(20)  NOT NULL,    -- PENDIENTE, APROBADO, RECHAZADO
    referencia_externa VARCHAR(120),
    fecha            TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_suscripcion_pago_tenant ON suscripcion_pago(tenant_id, fecha DESC);

-- V25: Integración con Khipu (transferencias bancarias directas) — multi-pasarela.
-- Cada dueño elige su pasarela de pago en línea. Mercado Pago sigue siendo el default
-- para no romper a los tenants ya configurados; Khipu es la alternativa de comisión baja
-- para transferencias/CuentaRUT, típica de regiones.

-- 1. Pasarela activa del tenant y credenciales de Khipu (cifrado en reposo, ver S1).
ALTER TABLE tenant
ADD COLUMN pasarela_pago VARCHAR(20) NOT NULL DEFAULT 'MERCADOPAGO',
ADD COLUMN khipu_api_key VARCHAR(512),
ADD COLUMN khipu_receiver_id BIGINT;

ALTER TABLE tenant
ADD CONSTRAINT ck_tenant_pasarela_pago CHECK (pasarela_pago IN ('MERCADOPAGO', 'KHIPU'));

-- 2. El link de pago se guarda en reserva.mp_preference_id / mp_init_point ya existentes:
-- son agnósticos del proveedor (Khipu payment_id / payment_url caben ahí igual que los de MP),
-- así que no se toca el esquema de reserva y el frontend/payload no cambian.
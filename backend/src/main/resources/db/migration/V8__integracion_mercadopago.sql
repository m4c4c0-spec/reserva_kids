-- V8: Integración con Mercado Pago Checkout Pro (Multi-tenant)
-- Permite a cada negocio configurar su propio Access Token.

-- 1. Añadir credenciales de MP a la tabla tenant
ALTER TABLE tenant
ADD COLUMN mp_access_token VARCHAR(255);

-- 2. Añadir datos de la preferencia a la reserva
ALTER TABLE reserva
ADD COLUMN mp_preference_id VARCHAR(255),
ADD COLUMN mp_init_point VARCHAR(500);

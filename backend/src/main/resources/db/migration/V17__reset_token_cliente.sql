-- V17: password_reset_token soporta cuentas de cliente (apoderados)
-- cuenta_cliente_id es nullable; usuario_id ya lo es (solo uno de los dos se usa).
ALTER TABLE password_reset_token ADD COLUMN cuenta_cliente_id BIGINT REFERENCES cuenta_cliente(id);
ALTER TABLE password_reset_token ALTER COLUMN usuario_id DROP NOT NULL;
CREATE INDEX idx_prt_cuenta_cliente ON password_reset_token(cuenta_cliente_id) WHERE cuenta_cliente_id IS NOT NULL;

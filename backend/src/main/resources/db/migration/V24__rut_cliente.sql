-- V24: RUT del cliente (identidad chilena). Almacenado normalizado (sin puntos, con guión):
-- "12345678-5". Se valida con algoritmo Módulo 11 antes de persistir.
-- El RUT se anonimiza junto con los demás datos personales (Ley 21.719).

ALTER TABLE cliente ADD COLUMN rut VARCHAR(12);
CREATE INDEX ix_cliente_rut ON cliente(rut);

ALTER TABLE cuenta_cliente ADD COLUMN rut VARCHAR(12);
CREATE UNIQUE INDEX uq_cuenta_cliente_rut ON cuenta_cliente(rut) WHERE rut IS NOT NULL;

-- V11: cuenta de cliente (apoderado) — login propio para navegar el directorio de negocios.
--
-- A diferencia de `usuario` (dueño, ligado a un tenant), la cuenta de cliente es GLOBAL:
-- no pertenece a ningún negocio; inicia sesión para elegir entre las empresas registradas
-- con horarios disponibles. La reserva en sí sigue siendo anónima por teléfono en /{slug}.

CREATE TABLE cuenta_cliente (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(160) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    nombre        VARCHAR(120),
    creado_en     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- V9: el email es identidad de login — debe ser case-insensitive.
-- AuthService ahora normaliza (trim + lower) al registrar y al buscar; esta
-- migración alinea los datos históricos. Si dos cuentas colisionaran al bajar
-- a minúsculas (no debería existir: mismo dueño con doble registro), la única
-- afectada conservaría su forma original y el UNIQUE detendría la migración
-- para resolverlo a mano — preferible a fusionar cuentas en silencio.
UPDATE usuario SET email = lower(trim(email)) WHERE email <> lower(trim(email));

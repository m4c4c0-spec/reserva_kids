-- ReservaKids V4 — Fix #3 (revisión de código): optimistic locking en reserva
-- (ver docs/BITACORA.md, Sesión 5)
--
-- El job de expiración y el panel del dueño pueden tocar la misma reserva en paralelo
-- (job lee COTIZADA vencida → el dueño la confirma → el job pisa la confirmación con
-- CANCELADA). @Version hace que el perdedor de la carrera falle en vez de sobrescribir.
ALTER TABLE reserva ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

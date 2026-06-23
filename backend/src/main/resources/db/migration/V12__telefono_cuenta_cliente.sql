-- V12: teléfono de la cuenta de cliente.
--
-- El agendamiento de citas confirma y recuerda por WhatsApp (y correo), así que la cuenta
-- de cliente necesita un teléfono de contacto. Se guarda normalizado a E.164 chileno sin '+'
-- (56XXXXXXXXX) — misma convención que `cliente.telefono` (Cliente.normalizarTelefono), para
-- que el link wa.me / el envío por WhatsApp funcionen sin reprocesar el número.
--
-- Nullable: las cuentas creadas antes de esta versión no tienen teléfono; se pedirá al
-- registrar de aquí en adelante y se puede completar al agendar la primera cita.

ALTER TABLE cuenta_cliente ADD COLUMN telefono VARCHAR(20);

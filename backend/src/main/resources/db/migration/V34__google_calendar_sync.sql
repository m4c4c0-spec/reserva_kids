-- V34: Sincronización bidireccional con Google Calendar.
-- Permite que el dueño conecte su Google Calendar y que los eventos se sincronicen
-- automáticamente: al confirmar una reserva se crea un evento en Google Calendar,
-- y si el dueño borra el evento desde su iPhone/Google Calendar, el webhook de
-- Google notifica a ReservaKids para liberar el bloque automáticamente.

-- Columna en reserva para rastrear el evento de Google Calendar asociado
ALTER TABLE reserva ADD COLUMN google_event_id VARCHAR(255);
COMMENT ON COLUMN reserva.google_event_id IS 'ID del evento en Google Calendar (null = no sincronizado)';

-- Columnas en tenant para la conexión con Google Calendar
ALTER TABLE tenant ADD COLUMN google_calendar_sync_enabled BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE tenant ADD COLUMN google_calendar_id VARCHAR(255);
ALTER TABLE tenant ADD COLUMN google_calendar_channel_id VARCHAR(255);
ALTER TABLE tenant ADD COLUMN google_calendar_resource_id VARCHAR(255);
ALTER TABLE tenant ADD COLUMN google_calendar_channel_expiration TIMESTAMPTZ;

COMMENT ON COLUMN tenant.google_calendar_sync_enabled IS 'Sincronización automática con Google Calendar activa';
COMMENT ON COLUMN tenant.google_calendar_id IS 'ID del calendario de Google vinculado (primary o especifico)';
COMMENT ON COLUMN tenant.google_calendar_channel_id IS 'ID del canal de notificaciones push de Google (watch)';
COMMENT ON COLUMN tenant.google_calendar_resource_id IS 'Resource ID del canal de watch de Google Calendar';

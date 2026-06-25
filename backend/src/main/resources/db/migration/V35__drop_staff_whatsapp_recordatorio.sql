-- V35: elimina la columna staff.whatsapp_recordatorio.
-- El recordatorio por WhatsApp al staff (job semanal recordatorioStaffViernes,
-- query findByTenantIdAndActivoTrueAndWhatsappRecordatorioTrue y el flag del DTO)
-- se removió del código; la columna quedó huérfana desde V30.
ALTER TABLE staff DROP COLUMN IF EXISTS whatsapp_recordatorio;

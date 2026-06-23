-- V10 (revisión de seguridad S1 + S3): credenciales de Mercado Pago cifradas en reposo.
--
-- S1: el Access Token deja de guardarse en texto plano (lo cifra CredentialCipher con
--     AES-256-GCM). El texto cifrado en base64 con prefijo es más largo que el token plano,
--     así que ampliamos la columna.
-- S3: nuevo secreto de firma del webhook (x-signature), también cifrado.
--
-- Los tokens ya guardados quedan en texto plano: CredentialCipher.decrypt los tolera
-- (compatibilidad) y se re-cifran solos la próxima vez que el dueño guarde su configuración.

ALTER TABLE tenant ALTER COLUMN mp_access_token TYPE VARCHAR(512);
ALTER TABLE tenant ADD COLUMN mp_webhook_secret VARCHAR(512);

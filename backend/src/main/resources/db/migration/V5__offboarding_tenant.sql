-- ReservaKids V5 — Offboarding de tenant (falla 3.3, revisión a 5 años)
-- Un negocio que cierra deja de existir de verdad: CERRADO oculta su página y bloquea
-- sus accesos; tras la ventana de gracia (TENANT_PURGA_DIAS, 90) un job purga FÍSICAMENTE
-- todos sus datos — incluidos los de sus clientes apoderados (Ley 21.719, supresión real).

-- Marca de cierre: base del plazo de purga (y de la reapertura por arrepentimiento).
ALTER TABLE tenant ADD COLUMN cerrado_en TIMESTAMPTZ;

-- SUSPENDIDO: acceso bloqueado y página oculta, datos intactos (morosidad — falla #10/2.1).
ALTER TABLE tenant ADD CONSTRAINT ck_tenant_estado
    CHECK (estado IN ('ACTIVO', 'SUSPENDIDO', 'CERRADO'));

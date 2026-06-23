-- V33: RBAC granular para el personal del negocio.
-- Reemplaza el campo staff.rol (VARCHAR libre) por un sistema de roles predefinidos
-- con permisos atómicos que el dueño configura desde su panel.

-- Catálogo de permisos del sistema (el dueño no puede crear/borrar permisos, solo asignarlos)
CREATE TABLE permiso (
    id         BIGSERIAL    PRIMARY KEY,
    codigo     VARCHAR(40)  NOT NULL UNIQUE,
    nombre     VARCHAR(80)  NOT NULL,
    categoria  VARCHAR(30)  NOT NULL
);

-- Roles de personal definidos por el dueño del negocio
CREATE TABLE rol_personal (
    id          BIGSERIAL    PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL REFERENCES tenant(id),
    nombre      VARCHAR(60)  NOT NULL,
    descripcion VARCHAR(200),
    creado_en   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, nombre)
);

-- Asignación de permisos a roles (N:M)
CREATE TABLE rol_personal_permiso (
    rol_personal_id BIGINT NOT NULL REFERENCES rol_personal(id) ON DELETE CASCADE,
    permiso_id      BIGINT NOT NULL REFERENCES permiso(id) ON DELETE RESTRICT,
    PRIMARY KEY (rol_personal_id, permiso_id)
);

-- ── Catálogo inicial de permisos ──

INSERT INTO permiso (codigo, nombre, categoria) VALUES
-- Eventos del día (lo mínimo que todo staff necesita)
('eventos:hoy',       'Ver eventos del día',            'eventos'),
('eventos:detalle',   'Ver detalle de eventos',         'eventos'),

-- Reservas
('reservas:leer',     'Ver bandeja de solicitudes',     'reservas'),
('reservas:cotizar',  'Cotizar reservas',               'reservas'),
('reservas:confirmar','Confirmar reservas',              'reservas'),
('reservas:realizar', 'Marcar reserva como realizada',  'reservas'),
('reservas:cancelar', 'Cancelar reservas',              'reservas'),
('reservas:pagos',    'Registrar pagos y señas',        'reservas'),

-- Servicios
('servicios:leer',    'Ver lista de servicios',         'servicios'),
('servicios:crear',   'Crear servicios',                'servicios'),
('servicios:editar',  'Editar servicios',               'servicios'),
('servicios:eliminar','Desactivar servicios',           'servicios'),

-- Calendario
('calendario:leer',   'Ver bloques del calendario',     'calendario'),
('calendario:crear',  'Crear bloques disponibles',      'calendario'),
('calendario:borrar', 'Eliminar bloques',               'calendario'),

-- Gestión de personal
('personal:leer',     'Ver lista del personal',         'personal'),
('personal:gestionar','Crear y eliminar personal',      'personal'),

-- Finanzas
('finanzas:leer',     'Ver caja y dashboard',           'finanzas'),
('finanzas:exportar', 'Exportar datos del negocio',     'finanzas'),

-- Configuración del negocio
('configuracion:leer', 'Ver configuración',             'configuracion'),
('configuracion:editar','Editar configuración',         'configuracion');

-- ── Ajuste de la tabla staff: foreign key a rol_personal ──

-- Creamos la columna nueva (nullable durante la migración)
ALTER TABLE staff ADD COLUMN rol_personal_id BIGINT REFERENCES rol_personal(id);

-- El campo rol antiguo queda como respaldo; lo marcaremos como deprecado.
-- En una V34 futura se podrá eliminar.
COMMENT ON COLUMN staff.rol IS 'DEPRECADO — usar rol_personal_id. Se mantiene por compatibilidad.';

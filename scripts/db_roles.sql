-- Segregación de permisos en PostgreSQL (principio de mínimo privilegio).
-- Ejecutar UNA vez por un superusuario (postgres) para crear los roles separados.
--
-- reservakids_admin  → solo para Flyway (migraciones DDL: CREATE/ALTER/DROP)
-- reservakids_app    → solo para la aplicación (DML: SELECT/INSERT/UPDATE/DELETE)
--
-- La aplicación NUNCA debe correr con permisos DDL. Si un atacante logra inyección
-- SQL (hipotético, el código usa queries parametrizadas), el daño se acota a los datos.
--
-- Uso:
--   1. psql -U postgres -d reservakids -f db_roles.sql
--   2. En .env: DB_USER=reservakids_app DB_PASSWORD=<password_app>
--   3. Para migraciones: DB_USER=reservakids_admin DB_PASSWORD=<password_admin>

-- Rol de migraciones (DDL): Flyway necesita CREATE, ALTER, DROP en el schema public
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'reservakids_admin') THEN
        CREATE ROLE reservakids_admin WITH LOGIN PASSWORD 'cambiar_por_password_seguro_admin';
    END IF;
END
$$;

-- Rol de aplicación (DML): solo lectura/escritura de datos
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'reservakids_app') THEN
        CREATE ROLE reservakids_app WITH LOGIN PASSWORD 'cambiar_por_password_seguro_app';
    END IF;
END
$$;

-- Admin: dueño del schema público (puede crear/modificar/borrar estructuras)
GRANT ALL PRIVILEGES ON SCHEMA public TO reservakids_admin;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO reservakids_admin;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO reservakids_admin;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO reservakids_admin;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO reservakids_admin;

-- App: solo DML (sin DDL, sin crear/borrar tablas)
GRANT USAGE ON SCHEMA public TO reservakids_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO reservakids_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO reservakids_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO reservakids_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO reservakids_app;

-- La app NO necesita:
--   CREATE / ALTER / DROP / TRUNCATE / REFERENCES / TRIGGER sobre tablas
--   CREATE sobre schema
--   Acceso a pg_catalog más allá del SELECT público por defecto

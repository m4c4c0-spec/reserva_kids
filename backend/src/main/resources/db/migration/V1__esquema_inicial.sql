-- ReservaKids — esquema inicial (modelo ER §4.2 del SDLC)

CREATE TABLE tenant (
    id          BIGSERIAL PRIMARY KEY,
    nombre      VARCHAR(120) NOT NULL,
    slug        VARCHAR(60)  NOT NULL UNIQUE,
    plan        VARCHAR(20)  NOT NULL DEFAULT 'BASICO',
    estado      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVO',
    creado_en   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE usuario (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL REFERENCES tenant(id),
    email         VARCHAR(160) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    rol           VARCHAR(20)  NOT NULL DEFAULT 'DUENO',
    creado_en     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE refresh_token (
    id         UUID PRIMARY KEY,
    usuario_id BIGINT      NOT NULL REFERENCES usuario(id),
    token_hash VARCHAR(64) NOT NULL,
    expira_en  TIMESTAMPTZ NOT NULL,
    revocado   BOOLEAN     NOT NULL DEFAULT FALSE
);
CREATE INDEX ix_refresh_usuario ON refresh_token(usuario_id);

CREATE TABLE servicio (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL REFERENCES tenant(id),
    nombre       VARCHAR(120) NOT NULL,
    descripcion  TEXT,
    precio_clp   INTEGER      NOT NULL CHECK (precio_clp >= 0),
    duracion_min INTEGER,
    capacidad    INTEGER,
    activo       BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_servicio_tenant ON servicio(tenant_id);

CREATE TABLE cliente (
    id        BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT       NOT NULL REFERENCES tenant(id),
    nombre    VARCHAR(120) NOT NULL,
    telefono  VARCHAR(30)  NOT NULL,
    email     VARCHAR(160)
);
CREATE INDEX ix_cliente_tenant ON cliente(tenant_id);

CREATE TABLE bloque_disponible (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT      NOT NULL REFERENCES tenant(id),
    fecha       DATE        NOT NULL,
    hora_inicio TIME        NOT NULL,
    hora_fin    TIME        NOT NULL,
    estado      VARCHAR(20) NOT NULL DEFAULT 'DISPONIBLE',
    CONSTRAINT ux_bloque_tenant_fecha UNIQUE (tenant_id, fecha, hora_inicio),
    CONSTRAINT ck_bloque_horas CHECK (hora_fin > hora_inicio)
);
CREATE INDEX ix_bloque_tenant_fecha ON bloque_disponible(tenant_id, fecha);

CREATE TABLE reserva (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT      NOT NULL REFERENCES tenant(id),
    cliente_id  BIGINT      NOT NULL REFERENCES cliente(id),
    servicio_id BIGINT      NOT NULL REFERENCES servicio(id),
    bloque_id   BIGINT      NOT NULL REFERENCES bloque_disponible(id),
    estado      VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    num_ninos   INTEGER,
    comuna      VARCHAR(80),
    comentarios TEXT,
    total_clp   INTEGER,
    senia_clp   INTEGER,
    creada_en   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_reserva_tenant_estado ON reserva(tenant_id, estado);

-- RNF-05: imposible doble-reservar un bloque — índice único parcial sobre estados activos
CREATE UNIQUE INDEX ux_reserva_bloque_activa ON reserva(bloque_id)
    WHERE estado IN ('PENDIENTE', 'COTIZADA', 'CONFIRMADA');

CREATE TABLE pago (
    id              BIGSERIAL PRIMARY KEY,
    reserva_id      BIGINT      NOT NULL REFERENCES reserva(id),
    monto_clp       INTEGER     NOT NULL CHECK (monto_clp > 0),
    medio           VARCHAR(30) NOT NULL,
    comprobante_url VARCHAR(300),
    fecha           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_pago_reserva ON pago(reserva_id);

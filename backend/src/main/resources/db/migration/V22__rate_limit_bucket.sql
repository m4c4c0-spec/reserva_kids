-- V22: bucket persistente de rate limiting (RNF-07 disponibilidad).
-- Complementa el ConcurrentHashMap en memoria: si la app se reinicia, los contadores
-- sobreviven. La entrada expira tras 2 minutos sin actividad (limpiada por RateLimitFilter).
-- También permite compartir el límite entre múltiples instancias si se despliega en cluster.

CREATE TABLE rate_limit_bucket (
    ip            VARCHAR(45)   NOT NULL,
    ruta_tipo     VARCHAR(50)   NOT NULL,  -- 'public', 'auth', 'panel', etc.
    epoch_minuto  BIGINT        NOT NULL,
    contador      INTEGER       NOT NULL DEFAULT 0,
    max_permitido INTEGER       NOT NULL,
    creado_en     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (ip, ruta_tipo)
);

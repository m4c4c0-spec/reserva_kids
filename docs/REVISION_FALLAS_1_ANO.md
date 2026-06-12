# Revisión de fallas a 1 año — ReservaKids

**Fecha:** 2026-06-10 · **Pregunta guía:** *¿qué puede fallar en este proyecto de aquí a un año de operación real?*

Cada falla incluye: cómo se manifestaría, cuándo, y la corrección aplicada. Todas las correcciones de la columna ✅ están implementadas y testeadas.

---

## Fallas encontradas y corregidas

### 1. ✅ La tabla `refresh_token` crece sin límite
- **Síntoma a futuro:** cada login y cada rotación de refresh insertan una fila que nunca se borra. Con 5 tenants activos usando el panel a diario: miles de filas muertas en meses; consultas de auth degradándose y backups engordando.
- **Cuándo:** degradación gradual, notorio a los 6–12 meses.
- **Corrección:** `RefreshTokenRepository.purgarInvalidos()` (DELETE de revocados/expirados) ejecutado por job diario a las 04:30 en `ExpiracionService.purgarRefreshTokens()`.

### 2. ✅ Zona horaria: el servidor corre en UTC, el negocio en Chile
- **Síntoma a futuro:** entre las 20:00 y medianoche (UTC−4) el "hoy" del servidor ya es mañana: el dueño no puede crear bloques para el día siguiente ("no se pueden crear bloques en el pasado") y la disponibilidad pública muestra/oculta días corridos. Reportes confusos de usuarios nocturnos — exactamente cuando los apoderados cotizan cumpleaños.
- **Cuándo:** desde el primer deploy en VPS/Docker (que corren en UTC).
- **Corrección:** bean `Clock` con zona configurable (`APP_TIMEZONE`, default `America/Santiago`) inyectado en `CalendarioService`; toda comparación con "hoy" usa `LocalDate.now(clock)`.

### 3. ✅ Rate limiting roto detrás del reverse proxy
- **Síntoma a futuro:** el plan de hosting es VPS + Caddy (SDLC §5). Detrás del proxy, `getRemoteAddr()` devuelve siempre la IP de Caddy → **todos los visitantes comparten un único bucket de 30 req/min**. Con tráfico modesto, el sitio público entero responde 429 — los clientes no pueden cotizar y el negocio pierde reservas sin saber por qué.
- **Cuándo:** primer día con tráfico real en producción.
- **Corrección:** `RateLimitFilter.ipCliente()` lee la primera IP de `X-Forwarded-For` cuando `TRUST_PROXY=true` (false por defecto: en local nadie puede falsificar el header).

### 4. ✅ Slugs reservados: un negocio podía registrarse como "panel" o "login"
- **Síntoma a futuro:** el frontend sirve la página pública en `/{slug}`. Un negocio registrado como `login`, `panel`, `api`… tendría su página inaccesible para siempre (las rutas estáticas ganan), con su slug único quemado en BD.
- **Cuándo:** en cuanto un usuario real elija un nombre desafortunado — cuestión de meses.
- **Corrección:** lista `SLUGS_RESERVADOS` validada en `AuthService.registrar()` → 400 "slug reservado".

### 5. ✅ Parámetros malformados producían 500 con stacktrace
- **Síntoma a futuro:** `?mes=2026-13`, `?estado=FOO` o `/reservas/abc` lanzaban `DateTimeParseException`/`TypeMismatch` → 500 "error interno" + stacktrace en el log. En el endpoint público, cualquier bot/scanner los gatilla a diario: logs llenos de falsas alarmas que entierran los errores reales (y el dueño del proyecto estudia — riesgo #5 del SDLC, su tiempo de triage es escaso).
- **Cuándo:** desde el primer scanner automático que toque el dominio (días después del deploy).
- **Corrección:** `GlobalExceptionHandler` mapea `MethodArgumentTypeMismatchException`, `DateTimeParseException` y `HttpMessageNotReadableException` → 400 uniforme sin stacktrace.

### 6. ✅ ClassCastException latente en la validación del JWT
- **Síntoma a futuro:** Jackson deserializa números pequeños del JWT como `Integer`; `claims.get("tenantId", Long.class)` lanza `ClassCastException` → **ningún usuario puede autenticarse**. No se ve en tests unitarios del dominio; explota en el primer request real autenticado.
- **Cuándo:** inmediato en producción (bug bomba).
- **Corrección:** lectura tolerante `((Number) claims.get("tenantId")).longValue()` en `JwtService.validar()`.

### 7. ✅ Email fantasma: notificación despachada antes del commit
- **Síntoma a futuro:** el email se enviaba `@Async` *dentro* de la transacción. Si el commit falla (p. ej. el índice único anti doble-reserva en una carrera), el dueño recibe un correo de una solicitud **que no existe en su panel** → confianza destruida en el feature central del producto (riesgo #4 del SDLC).
- **Cuándo:** baja probabilidad por evento, casi seguro en un año con varios tenants.
- **Corrección:** `NotificacionAdapter` registra el envío como `TransactionSynchronization.afterCommit()` (en hilo aparte vía `CompletableFuture`); sin transacción activa envía directo. Se quitó `@EnableAsync` (ya innecesario).

### 8. ✅ Pudrición de dependencias (proyecto de una sola persona)
- **Síntoma a futuro:** CVEs en Spring Boot, jjwt, axios o las imágenes Docker sin que nadie mire; en 12 meses el `pom.xml` queda 2 versiones menores atrás y la migración se vuelve un proyecto en sí misma.
- **Cuándo:** acumulativo; crítico al primer CVE explotable público.
- **Corrección:** `.github/dependabot.yml` — Maven y npm semanal, GitHub Actions y Docker mensual, con límite de PRs abiertas.

---

## Riesgos identificados, NO corregidos (decisión consciente)

| Riesgo | Por qué no ahora | Cuándo actuar |
|---|---|---|
| Rate limit en memoria no sobrevive reinicios ni multi-instancia | El MVP corre en 1 VPS / 1 instancia; bucket4j+Redis es complejidad prematura | Al escalar a 2+ instancias |
| `@Scheduled` duplicaría jobs con 2+ instancias (expiración correría dos veces — es idempotente, pero…) | Mismo motivo; ShedLock cuando aplique | Al escalar a 2+ instancias |
| Tokens en `sessionStorage` (XSS los expone) | Mitigado: access de 15 min + rotación de refresh; cookies HttpOnly requieren CSRF y dominio común | Al tener dominio propio en producción |
| Reservas `COTIZADA` no expiran (bloque `EN_ESPERA` indefinido si el cliente desaparece) | El dueño ve la solicitud en su panel y puede cancelarla; auto-expirar cotizaciones es decisión de negocio, no técnica | Decidir con los negocios piloto |
| Sin captcha en el endpoint público (§6 lo menciona) | Rate limiting cubre el MVP; captcha agrega fricción a la conversión | Si aparece spam real |
| Backups quedan en el mismo VPS | `backup_db.sh` deja el dump local; si el disco muere, mueren los backups | Antes del piloto: rclone a B2/S3 (1 línea extra en el cron) |
| `barrido de bloques pasados` (bloques viejos quedan DISPONIBLE en BD) | Invisible para usuarios (la consulta pública filtra desde hoy); solo es ruido histórico | Limpieza opcional v1.1 |

---

## Cobertura de la corrección

- Tests actualizados: `ExpiracionServiceTest` ahora cubre la purga de tokens (9 tests de aplicación + 5 de dominio en total).
- Variables nuevas documentadas en `.env.example` y `docker-compose.yml`: `APP_TIMEZONE`, `TRUST_PROXY`.
- Detalle de implementación paso a paso: ver `BITACORA.md` (Sesión 3).

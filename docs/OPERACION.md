# Operación — runbooks de ReservaKids

Procedimientos operativos que no viven en el código. Nacen de la revisión de fallas a 2 años
([`REVISION_FALLAS_2_ANOS.md`](REVISION_FALLAS_2_ANOS.md)): los modos de muerte más probables del
proyecto no son bugs sino **procesos que nadie ejecuta**. Este documento los convierte en rutina.

---

## 1. Ventana de mantenimiento semestral (falla #6 — el stack expira)

**Cuándo:** enero y julio (agendar en el calendario personal, ahora).

Checklist (≈ medio día):

1. **Dependabot**: mergear las PRs verdes pendientes (Maven, npm, Actions, Docker).
2. **Spring Boot**: subir a la última *minor* de la línea actual (`mvn versions:display-parent-updates`).
   Leer las release notes de la próxima *major* — si la línea actual está a < 6 meses del fin de
   soporte OSS, agendar la migración para la siguiente ventana, no "para después".
3. **PostgreSQL**: verificar fecha de EOL de la versión en uso (la 16 muere en noviembre 2028).
   Migrar de major = dump + restore con la imagen nueva (ensayado en el paso 4).
4. **Ensayo de restore** (falla #7): levantar un Postgres limpio y restaurar el último backup:
   ```bash
   docker run --rm -d --name pg-restore -e POSTGRES_PASSWORD=test postgres:16-alpine
   zcat backups/reservakids_FECHA.sql.gz | docker exec -i pg-restore psql -U postgres
   docker exec pg-restore psql -U postgres -c "SELECT count(*) FROM reserva;"  # ¿números plausibles?
   docker rm -f pg-restore
   ```
   Anotar el resultado en la bitácora. **Un backup no ensayado es una esperanza, no un backup.**
5. **Frontend**: `npm outdated`; majors de Vite/Tailwind solo si hay tiempo de probar el build.
6. **CI**: ¿sigue verde? ¿`mvn test` y `vite build` pasan localmente?

## 2. Rotación de `JWT_SECRET` (falla #12)

Cuándo rotarlo: sospecha de filtración (`.env` copiado, VPS comprometido, secreto pegado en un chat),
o preventivamente en cada ventana semestral.

1. Generar el nuevo: `openssl rand -base64 64`.
2. Reemplazar `JWT_SECRET` en el `.env` del VPS.
3. `docker compose up -d backend` (reinicio).
4. Efecto: **todas las sesiones activas mueren a la vez** — los access tokens (15 min) y los refresh
   dejan de validar; los usuarios simplemente vuelven a iniciar sesión. Con ~decenas de tenants esto
   es aceptable; no hace falta doble secreto (`kid`) salvo que algún día haya SSO o terceros.
5. Avisar a los tenants solo si la rotación fue por incidente.

## 3. Backups offsite (falla #7)

- `scripts/backup_db.sh` sube el dump a un remoto rclone si `RCLONE_REMOTE` está definido.
- Configuración inicial (una vez): `rclone config` → Backblaze B2 (capa gratis 10 GB) o S3;
  luego `RCLONE_REMOTE=b2:reservakids-backups` en el entorno del cron:
  ```cron
  0 3 * * * RCLONE_REMOTE=b2:reservakids-backups /ruta/reserva_kids/scripts/backup_db.sh /var/backups/reservakids
  ```
- El script **falla con ruido** si rclone no puede subir — un cron en rojo hoy es mejor que
  descubrir en el desastre que nunca se subió nada. Revisar el mail del cron.
- Ensayo de restore: paso 4 de la ventana semestral.

## 4. Monitoreo (fallas #3 y #8)

- **Uptime**: crear un monitor gratuito (UptimeRobot u otro) apuntando a
  `https://<dominio>/actuator/health` cada 5 min, con alerta al email personal. El endpoint es
  público y sin detalles; refleja BD y app vivas.
- **Docker**: el servicio `backend` tiene healthcheck — `docker ps` muestra `(healthy)`;
  con `restart: unless-stopped`, una JVM muerta se reinicia sola.
- **Email**: si el SMTP acumula fallos, el panel muestra un aviso ámbar (datos de
  `GET /api/sistema/notificaciones`). Si aparece: revisar credenciales Brevo/Resend, cuota,
  y que el dominio tenga SPF/DKIM alineados.

## 5. Planes y cobro — decisión pendiente (falla #10)

La columna `tenant.plan` existe (`BASICO`) pero **no se lee en ninguna parte**: no hay límites ni
cobro (RF-13, v1.2). Riesgo de negocio, no técnico: cada mes que pasa con tenants gratis hace más
difícil introducir límites (quedarán "grandfathered" por encima de cualquier tope).

**Decidir con los negocios piloto antes de v1.2:** precio, qué limita el plan básico
(¿nº de reservas/mes?, ¿bloques?), y si los pilotos quedan exentos de por vida (decisión explícita,
no por omisión). Hasta entonces: no aceptar más tenants de los que se pueden migrar a mano.

## 6. Derechos Ley 21.719 — guía rápida para el dueño del negocio

- **"Bórrenme de su base"** (supresión): panel → (API) `POST /api/clientes/{id}/anonimizar`.
  Si el cliente tiene reservas activas, primero cancelarlas o completarlas. La operación conserva
  la fila (números históricos intactos) pero borra nombre/teléfono/email para siempre, y desde la
  falla 3.2 (revisión 5 años) **también reemplaza los comentarios de sus reservas** por
  `[anonimizado]` — eran texto libre con datos personales ("[Contacto: …]", motivos de cancelación).
  Regla de diseño derivada: ningún dato personal nuevo en campos de texto libre.
- **Automático**: clientes sin actividad por `RETENCION_CLIENTE_MESES` (24 por defecto) se
  anonimizan solos el día 1 de cada mes.
- **Consentimiento**: el formulario público exige el checkbox (queda `consentimiento_en` como prueba).
- Pendiente al tener dominio propio: página de política de privacidad enlazada desde el checkbox.

## 7. Offboarding de tenant (falla 3.3 — revisión a 5 años)

El ciclo de vida completo de un negocio que se va. Implementado en `TenantService` +
`ExpiracionService.purgarTenantsCerrados()`; estados en `tenant.estado` (`ck_tenant_estado`).

### Cierre self-service (lo hace el dueño, sin intervención del operador)
1. Panel → pie de página → **"Cerrar negocio definitivamente"** (o `POST /api/tenant/cerrar`).
2. El backend exige el slug exacto como confirmación y **rechaza si hay reservas CONFIRMADAS**
   (hay señas: el dueño debe cancelarlas devolviendo el dinero, o marcarlas realizadas).
3. Efectos inmediatos: PENDIENTE/COTIZADA canceladas con nota `[Cierre del negocio]`,
   página pública oculta, sesiones revocadas, login bloqueado. La respuesta incluye el
   **export JSON completo** (se descarga automáticamente — última copia garantizada).

### Ventana de gracia y purga física
- Durante `TENANT_PURGA_DIAS` (90) los datos siguen en BD pero inaccesibles.
- **Reapertura por arrepentimiento** (solo dentro de la ventana):
  `UPDATE tenant SET estado='ACTIVO', cerrado_en=NULL WHERE slug='<slug>';`
- Vencida la ventana, el job mensual (día 2, 05:30) borra FÍSICAMENTE todo el rastro:
  pagos → reservas → bloques → clientes → servicios → tokens → usuarios → tenant.
  Es supresión real Ley 21.719 (incluye los datos de los apoderados del negocio) y
  mantiene la BD y los backups sin datos muertos (falla 4.2).
- ⚠️ La purga NO toca los **backups offsite** anteriores: expiran solos con la retención
  de 14 días. Tras una purga, no restaurar dumps previos sin re-purgar.

### Suspensión (morosidad — preparado para el cobro, falla #10/§5)
- No hay panel de admin todavía: se opera por SQL.
  `UPDATE tenant SET estado='SUSPENDIDO' WHERE slug='<slug>';`  (reversible: `'ACTIVO'`)
- Efectos: login y refresh bloqueados ("negocio suspendido"), página pública oculta,
  **datos intactos** — pensado para impago: se reactiva al pagar, sin pérdida.
- Los access tokens vigentes duran hasta 15 min más (TTL del JWT) — aceptable.

### Cierre del servicio completo (falla 5.1 — escribir en frío, ejecutar en caliente)
Si algún día ReservaKids entero se apaga:
1. Avisar a todos los tenants con **90 días** de anticipación (email + aviso en panel).
2. Indicarles el botón "Exportar mis datos" (autoservicio) y ofrecer el export por SQL
   a quien no entre: el mismo JSON de `GET /api/tenant/export`.
3. Al día 90: detener el servicio, `pg_dump` final cifrado (retener 6 meses por disputas),
   **borrar BD, volumen `pgdata` y backups offsite** (rclone delete + verificación),
   liberar dominio y VPS. Documentar la fecha de borrado (prueba de cumplimiento).

# Manual de despliegue — ReservaKids

Guía paso a paso para llevar ReservaKids a producción en un **VPS con Docker Compose + Caddy**
(la opción del SDLC §5). Al final hay una alternativa sin VPS (Vercel + Railway) y el checklist
de verificación post-despliegue. Los procedimientos recurrentes (backups, rotación de secretos,
ventana semestral) viven en [`OPERACION.md`](OPERACION.md).

---

## 0. Requisitos

| Qué | Detalle |
|---|---|
| VPS | 1 vCPU / 2 GB RAM alcanzan para el MVP (JVM + Postgres). Ubuntu 24.04 LTS o Debian 12 |
| Dominio | p. ej. `reservakids.cl`, con acceso al panel DNS |
| Cuentas | SMTP transaccional (Brevo capa gratis o Resend) · Backblaze B2/S3 para backups offsite |
| En el VPS | `docker` + `docker compose`, `git`, `rclone`, `ufw` |

```bash
# en el VPS (una vez)
curl -fsSL https://get.docker.com | sh
apt install -y rclone ufw git
ufw allow 22/tcp && ufw allow 80/tcp && ufw allow 443/tcp && ufw enable
```

## 1. DNS

Crear dos registros A apuntando a la IP del VPS:

```
reservakids.cl        A   <IP_VPS>
www.reservakids.cl    A   <IP_VPS>
```

(El frontend y la API se sirven por el mismo dominio vía Caddy — así no hay CORS entre orígenes
distintos y las cookies futuras serían first-party.)

## 2. Código y configuración

```bash
git clone <repo> /opt/reservakids && cd /opt/reservakids
cp .env.example .env
```

Editar `.env` — **todas** estas son obligatorias en producción:

```bash
POSTGRES_PASSWORD=$(openssl rand -base64 24)   # generar, no inventar
JWT_SECRET=$(openssl rand -base64 64)          # mínimo 64 bytes
CORS_ALLOWED_ORIGINS=https://reservakids.cl
APP_TIMEZONE=America/Santiago
TRUST_PROXY=true                               # ¡SOLO porque Caddy está delante!
MAIL_HOST=smtp-relay.brevo.com                 # o smtp.resend.com
MAIL_PORT=587
MAIL_USERNAME=<usuario brevo>
MAIL_PASSWORD=<api key brevo>
MAIL_FROM=no-reply@reservakids.cl
```

> ⚠️ `TRUST_PROXY=true` hace que el rate limit confíe en `X-Forwarded-For`. Solo es seguro
> detrás de un proxy que controle ese header (Caddy lo hace). Jamás con el puerto 8080
> expuesto a internet — por eso el paso 4 lo cierra.

## 3. Frontend (build estático)

```bash
cd frontend
echo "VITE_API_URL=https://reservakids.cl" > .env.production
npm ci && npm run build        # genera dist/ (~150 kB)
```

`dist/` lo sirve Caddy directamente (paso 4) — no hace falta Node en producción.

## 4. Caddy (TLS automático + reverse proxy + SPA)

`/etc/caddy/Caddyfile` (instalar Caddy: `apt install caddy`):

```caddyfile
reservakids.cl {
    encode gzip

    # API y health check → backend en localhost
    handle /api/* {
        reverse_proxy localhost:8080
    }
    handle /actuator/health {
        reverse_proxy localhost:8080
    }

    # SPA Vue: archivos estáticos con fallback a index.html (rutas /panel, /{slug})
    handle {
        root * /opt/reservakids/frontend/dist
        try_files {path} /index.html
        file_server
    }
}

www.reservakids.cl {
    redir https://reservakids.cl{uri} permanent
}
```

```bash
systemctl reload caddy
```

Caddy obtiene y renueva los certificados TLS solo (Let's Encrypt) y setea `X-Forwarded-For`
correctamente (appendea — por eso el backend toma la **última** IP, fix #1 de la Sesión 5).

**Cerrar el puerto interno:** en `docker-compose.yml`, cambiar el mapeo del backend a
`"127.0.0.1:8080:8080"` (y el de la BD a `"127.0.0.1:5432:5432"` o eliminarlo) para que solo
Caddy/localhost lleguen a ellos. Con `ufw` ya bloqueando todo salvo 22/80/443, es doble candado.

## 5. Levantar

```bash
cd /opt/reservakids
docker compose up -d --build
docker compose ps          # backend debe quedar (healthy) — tarda ~60 s (start_period)
docker compose logs backend | grep Flyway   # migraciones V1..V4 aplicadas
```

Flyway crea/migra el esquema automáticamente en el arranque (`ddl-auto: validate`: Hibernate
solo verifica, nunca toca el esquema).

## 6. Email (SPF/DKIM — sin esto, spam)

En Brevo/Resend: agregar el dominio, y crear los registros DNS que indiquen (SPF, DKIM, DMARC).
Verificar con el chequeo integrado del proveedor. Sin esto los avisos de solicitudes llegarán
a spam y el negocio "no se enterará" de las reservas (falla #3 de la revisión a 2 años — el
panel avisa si los envíos fallan, pero el spam no es un fallo detectable).

## 7. Backups (diario + offsite)

```bash
rclone config        # crear remoto "b2" → bucket reservakids-backups
crontab -e
```

```cron
0 3 * * * RCLONE_REMOTE=b2:reservakids-backups /opt/reservakids/scripts/backup_db.sh /var/backups/reservakids >> /var/log/reservakids-backup.log 2>&1
```

El script verifica el dump, mantiene 14 días locales y sube la copia offsite (falla con ruido
si no puede). **Ensayar el restore ahora** — instrucciones en el propio script — y repetirlo
cada ventana semestral (`OPERACION.md` §1).

## 8. Monitoreo

1. UptimeRobot (gratis): monitor HTTP a `https://reservakids.cl/actuator/health` cada 5 min,
   alerta al email personal.
2. `docker ps` debe mostrar el backend `(healthy)`; con `restart: unless-stopped` una JVM
   caída se reinicia sola.
3. El panel muestra un aviso ámbar si el SMTP acumula fallos (`/api/sistema/notificaciones`).

## 9. Checklist post-despliegue

- [ ] `https://reservakids.cl` carga el frontend con candado TLS
- [ ] `https://reservakids.cl/actuator/health` → `{"status":"UP"}`
- [ ] Registro de un negocio de prueba → llega al panel
- [ ] Crear servicio + bloque → visibles en `https://reservakids.cl/<slug>`
- [ ] Solicitud pública de prueba (con el checkbox de datos) → email recibido **en inbox, no spam** + visible en panel
- [ ] Flujo completo: cotizar → confirmar → registrar seña → marcar realizada
- [ ] `curl` 31 veces seguidas a `/api/public/<slug>` → la 31ª responde 429
- [ ] Puerto 8080 y 5432 NO accesibles desde fuera (`nmap <IP>` desde otra máquina)
- [ ] Backup manual: `./scripts/backup_db.sh` → dump local + copia en B2
- [ ] Restore de prueba del dump (OPERACION.md §1 paso 4)
- [ ] UptimeRobot en verde; apagar el backend 1 min → llega la alerta
- [ ] Borrar el negocio de prueba (o dejarlo como demo)

## 10. Actualizar la aplicación (deploy de una versión nueva)

```bash
cd /opt/reservakids
git pull
cd frontend && npm ci && npm run build && cd ..   # frontend nuevo (Caddy lo sirve al instante)
docker compose up -d --build backend              # backend nuevo; Flyway migra solo
docker compose ps                                 # esperar (healthy)
```

Ventana de corte: ~30–60 s mientras la JVM arranca. Para el MVP es aceptable;
zero-downtime (2 réplicas + ShedLock para los jobs) queda para cuando haya tráfico que lo pague.

---

## Alternativa sin VPS: Vercel + Railway/Render

Para evitar administrar un servidor (a costa de ~USD 5–10/mes extra y menos control):

1. **BD + API en Railway**: crear proyecto con plugin PostgreSQL; deploy del directorio
   `backend/` (detecta el Dockerfile). Variables: las mismas del paso 2, con `DB_URL` del
   plugin y `TRUST_PROXY=true` (Railway pone proxy delante).
2. **Frontend en Vercel**: importar el repo, root `frontend/`, build `npm run build`,
   output `dist/`. Variable `VITE_API_URL=https://<api>.railway.app`.
3. **CORS**: `CORS_ALLOWED_ORIGINS=https://<app>.vercel.app` (aquí sí hay orígenes distintos).
4. Backups: Railway hace snapshots, pero seguir corriendo `backup_db.sh` desde cualquier
   máquina con el `DATABASE_URL` externo + rclone (no depender solo del proveedor).

Limitaciones: los jobs `@Scheduled` requieren que la instancia no "duerma" (no usar planes
serverless que escalan a cero), y el rate limit en memoria supone 1 sola instancia.

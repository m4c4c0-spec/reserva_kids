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
CRED_ENC_KEY=$(openssl rand -base64 32)        # AES-256: cifra el token de Mercado Pago en reposo (S1)
CORS_ALLOWED_ORIGINS=https://reservakids.cl
FRONTEND_URL=https://reservakids.cl            # base de los enlaces de reset de contraseña
API_URL=https://reservakids.cl                 # URL pública base: webhook de Mercado Pago + enlaces email
APP_TIMEZONE=America/Santiago
TRUST_PROXY=true                               # ¡SOLO porque Caddy está delante!
MAIL_HOST=smtp-relay.brevo.com                 # o smtp.resend.com
MAIL_PORT=587
MAIL_USERNAME=<usuario brevo>
MAIL_PASSWORD=<api key brevo>
MAIL_SMTP_AUTH=true                            # proveedores reales: auth + STARTTLS
MAIL_SMTP_STARTTLS=true
MAIL_FROM=no-reply@reservakids.cl
```

> ⚠️ `TRUST_PROXY=true` hace que el rate limit confíe en `X-Forwarded-For`. Solo es seguro
> detrás de un proxy que controle ese header (Caddy lo hace). Jamás con el puerto 8080
> expuesto a internet — por eso el paso 4 lo cierra.

> ⚠️ `API_URL` **debe ser la URL pública de la API** (la que Mercado Pago usa como
> `notificationUrl` del webhook). Si queda en `localhost`, MP no alcanza el webhook y los
> pagos online nunca se auto-confirman. Caddy rutea `/api/*` en el mismo dominio principal;
> `API_URL` debe ser `https://reservakids.cl` (no un subdominio aparte). Asegúrate de que
> `https://reservakids.cl/api/public/webhooks/mercadopago/` sea accesible desde internet
> (sin auth — el endpoint valida la firma del propio MP).

> 🔐 `CRED_ENC_KEY` cifra el Access Token de Mercado Pago y el secreto del webhook en la BD.
> **Guárdala fuera de la BD y no la rotes a la ligera:** al cambiarla, las credenciales MP ya
> cifradas dejan de poder descifrarse y cada negocio deberá volver a pegar su token. Si se
> omite, se deriva de `JWT_SECRET` (aceptable solo en desarrollo).

### Mercado Pago (por negocio, en el panel del dueño)

Cada negocio configura sus credenciales en **Configuración** del panel; no van en `.env`:

- **Access Token** (Credenciales de Producción de su cuenta MP) — obligatorio para cobrar señas online.
- **Secreto de firma del webhook** (MP → Tus Integraciones → Webhooks → Firma secreta) — opcional
  pero recomendado: si se configura, la API valida el header `x-signature` y rechaza con `401`
  cualquier notificación que no venga firmada por MP (S3). Sin él, la autenticidad se apoya solo
  en re-consultar el pago a la API de MP.

Ambos valores se guardan **cifrados en reposo** (AES-256-GCM, ver `CRED_ENC_KEY`).

## 3. Frontend (Nuxt 3 SSR — lo construye Docker)

> **El frontend ya NO es un build estático.** Migró de Vite SPA (`dist/`) a **Nuxt 3 SSR**:
> dos procesos Node (Nitro) servidos por Caddy según el path. No se corre `npm run build`
> en el host; las imágenes las construye `docker compose` desde `frontend/Dockerfile` y el
> deploy lo orquesta `scripts/deploy-prod.sh` (paso 5).

Dos unidades de deploy independientes (misma imagen, dos contenedores):

| Contenedor | Rutas | Puerto interno |
|---|---|---|
| `frontend-public` | `/`, `/{slug}`, `/invitacion`, `/negocios`, `/privacidad`, assets | `127.0.0.1:3000` |
| `frontend-panel`  | `/panel`, `/admin`, `/clientes`, `/staff`, `/login`, `/reset`, `/oauth2` | `127.0.0.1:3001` |

Así, si un deploy rompe el Panel, la app pública de reservas sigue operando. La URL pública
de la API la toman por entorno (`FRONTEND_URL` / `NUXT_PUBLIC_API_URL`); en modo single-tenant,
`RESERVAKIDS_SINGLE_TENANT_SLUG` se inyecta al arrancar (no se hornea en el build).

## 4. Caddy (TLS automático + reverse proxy + SPA)

`/etc/caddy/Caddyfile` (instalar Caddy: `apt install caddy`). El repo trae un `Caddyfile`
listo con placeholder `TU_DOMINIO`. Sustituye por tu dominio y cópialo:

```bash
sed "s/TU_DOMINIO/reservakids.cl/g" Caddyfile | sudo tee /etc/caddy/Caddyfile
```

El contenido resultante (para referencia):

```caddyfile
# (TU_DOMINIO ya reemplazado por tu dominio real)
reservakids.cl {
    encode gzip

    header {
        Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' https://fonts.googleapis.com 'unsafe-inline'; font-src https://fonts.gstatic.com; img-src 'self' data:; connect-src 'self' https://*.mercadopago.cl https://*.mercadopago.com; frame-ancestors 'none'; base-uri 'self'; form-action 'self' https://*.mercadopago.com"
        X-Content-Type-Options "nosniff"
        Referrer-Policy "no-referrer"
        X-Frame-Options "DENY"
        Permissions-Policy "geolocation=(), microphone=(), camera=()"
        # HSTS: 30 días iniciales. Tras validar, subir a 1 año.
        Strict-Transport-Security "max-age=2592000"
    }

    handle /api/* {
        reverse_proxy localhost:8080
    }
    handle /actuator/health {
        reverse_proxy localhost:8080
    }

    @panel path /panel /panel/* /admin /admin/* /clientes /clientes/* /staff /staff/* /login /login/* /reset /reset/* /magic /magic/* /oauth2 /oauth2/*
    handle @panel {
        reverse_proxy localhost:3001
    }

    handle {
        reverse_proxy localhost:3000
    }
}

www.reservakids.cl {
    redir https://reservakids.cl{uri} permanent
}
```

> La API y el frontend comparten el mismo dominio (sin subdominio `api.`). Caddy rutea
> `/api/*` al backend y el resto a los frontends Nuxt SSR. Así no hay CORS entre orígenes
> distintos y las cookies HttpOnly son first-party. El `Caddyfile.docker` es la variante
> con hostnames internos de compose para el modo all-in-docker.

```bash
sudo systemctl reload caddy
```

Caddy obtiene y renueva los certificados TLS solo (Let's Encrypt) y setea `X-Forwarded-For`
correctamente (appendea — por eso el backend toma la **última** IP, fix #1 de la Sesión 5).

**Cerrar los puertos internos (ya automatizado):** `compose.prod.yml` bindea backend, BD y los
dos front SSR a `127.0.0.1` (vía la directiva `!override` — Compose **fusiona** las listas de
`ports`, así que sin `!override` los binds a loopback NO quitan los `0.0.0.0` del base y todo
quedaría expuesto). Con `ufw` bloqueando todo salvo 22/80/443, es doble candado. El stack de
observabilidad (Grafana/Prometheus/Tempo) también queda en loopback y no lo arranca el deploy
de prod; accédelo por túnel SSH (`ssh -L 3002:127.0.0.1:3002 vps`).

## 5. Levantar

Usar el script de despliegue, que valida las variables obligatorias, autogenera secretos que
falten (incluida `GRAFANA_ADMIN_PASSWORD`), construye las imágenes (backend + Nuxt SSR) y levanta
**solo** los servicios de producción con el overlay `compose.prod.yml`:

```bash
cd /opt/reservakids
./scripts/deploy-prod.sh
```

Equivale a (si prefieres a mano):

```bash
docker compose -f docker-compose.yml -f compose.prod.yml --env-file .env \
    up -d --build db backend frontend-public frontend-panel backup
docker compose ... ps          # backend (healthy) — tarda ~60 s (start_period)
docker compose ... logs backend | grep Flyway   # migraciones V1..V34 aplicadas
```

> Se levantan servicios **explícitos** a propósito: así NO arrancan ni `mailpit` (perfil dev)
> ni el stack de observabilidad ni el `caddy` dockerizado (en prod, Caddy va en el host).

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
- [ ] Headers de seguridad presentes: `curl -I https://reservakids.cl | grep -iE "content-security-policy|x-content-type-options|referrer-policy|strict-transport-security"` (revisión §5.12)
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
./scripts/deploy-prod.sh        # reconstruye imágenes (backend + Nuxt SSR) y relevanta; Flyway migra solo
```

`deploy-prod.sh` reconstruye y relevanta backend + ambos front SSR; Flyway aplica las migraciones
nuevas al arrancar. Ventana de corte: ~30–60 s mientras la JVM arranca. Para el MVP es aceptable;
zero-downtime (2 réplicas + ShedLock para los jobs) queda para cuando haya tráfico que lo pague.

---

## Alternativa sin VPS: Vercel + Railway/Render

Para evitar administrar un servidor (a costa de ~USD 5–10/mes extra y menos control):

1. **BD + API en Railway**: crear proyecto con plugin PostgreSQL; deploy del directorio
   `backend/` (detecta el Dockerfile). Variables: las mismas del paso 2, con `DB_URL` del
   plugin y `TRUST_PROXY=true` (Railway pone proxy delante).
2. **Frontend en Vercel**: importar el repo, root `frontend/`. Al ser **Nuxt 3 SSR**, Vercel
   detecta el preset Nitro automáticamente (no es un `dist/` estático). Variable
   `NUXT_PUBLIC_API_URL=https://<api>.railway.app` (y `RESERVAKIDS_SINGLE_TENANT_SLUG` si aplica).
3. **CORS**: `CORS_ALLOWED_ORIGINS=https://<app>.vercel.app` (aquí sí hay orígenes distintos).
4. Backups: Railway hace snapshots, pero seguir corriendo `backup_db.sh` desde cualquier
   máquina con el `DATABASE_URL` externo + rclone (no depender solo del proveedor).

Limitaciones: los jobs `@Scheduled` requieren que la instancia no "duerma" (no usar planes
serverless que escalan a cero), y el rate limit en memoria supone 1 sola instancia.

---

## Instalación como app móvil (PWA)

ReservaKids es una **PWA** (Progressive Web App): el panel del negocio se instala en el móvil
o escritorio del dueño como si fuera una app nativa, sin pasar por tiendas de apps. Tras
instalarla, abre en pantalla completa (sin barra del navegador), tiene su propio icono en el
home screen y arranca sola. El service worker precachea el shell (HTML/CSS/JS/iconos) para
que la app cargue instantáneo incluso con mala conexión — las respuestas de la API (reservas,
calendario) siempre son frescas, no se cachean.

> La PWA solo cubre el **panel del negocio** (`/panel/**`): las páginas públicas (`/{slug}`,
> landing) se sirven como web normal para que clientes ocasionales no instalen nada.

### Requisitos servidos

- HTTPS (Caddy lo da automáticamente — la PWA no funciona sobre HTTP salvo en `localhost`).
- `manifest.webmanifest`, `sw.js` (service worker) e iconos 192/512 px (any + maskable) +
  `apple-touch-icon` 180px. Los genera `@vite-pwa/nuxt` durante `nuxt build` (en `.output/`)
  y los sirve el servidor SSR de Nitro del contenedor del Panel.

El build de la imagen Docker (`nuxt build`) genera todo esto. No hay que hacer nada extra en el servidor.

### Android (Chrome / Edge)

1. Abrir `https://reservakids.cl/panel` en Chrome.
2. El panel muestra un botón **"Instalar app"** (icono `install_mobile`) en la barra
   superior (móvil) o en el sidebar (escritorio) cuando el navegador confirma que se puede
   instalar. Tocarlo → confirmar.
   - Alternativa sin botón: menú ⋮ → **"Añadir a pantalla de inicio"** / **"Instalar
     aplicación"**.
3. El icono de ReservaKids aparece en el home screen. Al abrirlo, va pantalla completa.

> El botón "Instalar app" usa el evento `beforeinstallprompt` (Chrome/Edge/Android). Si el
> usuario ya instaló la app, el botón desaparece automáticamente.

### iOS (Safari)

> iOS **no dispara** `beforeinstallprompt`, así que el botón "Instalar app" no aparece en
> Safari. La instalación es manual pero igual de funcional.

1. Abrir `https://reservakids.cl/panel` en Safari.
2. Tocar el botón **Compartir** (cuadrado con flecha hacia arriba).
3. Elegir **"Añadir a pantalla de inicio"**.
4. Confirmar el título (por defecto "ReservaKids"). El icono aparece en el home screen.

Tras instalar, abre en standalone (sin barra de Safari) con el `apple-touch-icon` y respeta
el notch / home indicator gracias a `viewport-fit=cover` + `env(safe-area-inset-*)`.

### Escritorio (Chrome / Edge)

1. Abrir `https://reservakids.cl/panel` en Chrome/Edge.
2. Icono **⊕ Instalar** en la barra de direcciones (a la derecha), o menú → **"Instalar
   ReservaKids"**.
3. Se abre en su propia ventana (sin pestañas) y se añade al dock/taskbar.

### Actualizaciones

`registerType: 'autoUpdate'` en la config de `@vite-pwa/nuxt` (en `nuxt.config.ts`) hace que el service worker se
actualice automáticamente cuando se publica una versión nueva. El usuario no necesita hacer
nada: la próxima vez que abre la app, el SW descarga los assets nuevos y los activa al
cerrar todas las pestañas. Para forzar la actualización inmediata, el usuario puede cerrar
todas las instancias de la app y reabrirla.

> Al publicar una versión nueva (paso 10 del manual), el nuevo `sw.js` reemplaza al viejo.
> No hace falta que el usuario "desinstale y reinstale" — la PWA se actualiza sola.

### Qué NO es la PWA

- No hay notificaciones push nativas (requieren un service + API de push, fuera del MVP).
  Los avisos llegan por email + WhatsApp.
- No hay offline mode completo: el shell carga offline, pero las operaciones (crear
  reserva, cotizar, confirmar) necesitan conexión a la API.
- No está publicada en Play Store / App Store. Para eso se necesitaría un TWA (Trusted Web
  Activity) o Capacitor — no justificado para bus factor 1.

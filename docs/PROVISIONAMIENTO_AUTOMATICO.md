# Provisión Automática (Bootstrapper Single-Tenant + Despliegue Docker)

Al migrar hacia el modelo de **Licencias Exclusivas (High-Ticket)**, la página de registro pública ("Hágalo usted mismo") fue eliminada para proteger la marca blanca del software.

Esto genera un problema operativo: ¿cómo creas la cuenta del dueño del salón en la base de datos cuando instalas el software en un nuevo servidor?

Para evitar la mala práctica de ejecutar scripts de SQL manuales en la base de datos de producción (`INSERT INTO tenant...`), el sistema cuenta con dos mecanismos complementarios:

1. **Backend `SingleTenantBootstrapper`** — provisiona Tenant + Usuario + Horario al iniciar
2. **Frontend Docker + entrypoint** — inyecta `VITE_SINGLE_TENANT_SLUG` en runtime sin recompilar

---

## ¿Cómo funciona la inyección runtime del slug en el Frontend?

Vite incrusta las variables `VITE_*` en el bundle JS durante `vite build`. No se pueden cambiar después sin recompilar. Para resolver esto sin crear una imagen Docker por cliente, usamos el patrón `window.__SINGLE_TENANT_SLUG__`:

```
Build time:   index.html → <script>window.__SINGLE_TENANT_SLUG__ = "";</script>
Runtime:      entrypoint.sh → sed replace "" → valor de $VITE_SINGLE_TENANT_SLUG
JS code:      window.__SINGLE_TENANT_SLUG__ || fallback import.meta.env (dev mode)
```

Si `VITE_SINGLE_TENANT_SLUG` está vacía, el frontend opera en modo multi-tenant normal (marketplace habilitado, landing page visible).

---

## ¿Cómo desplegar un cliente nuevo? (Un comando de Docker)

Crea un archivo `.env` en la raíz del proyecto junto al `docker-compose.yml`:

```env
# Base de datos (genera valores reales; NO copies estos ejemplos)
POSTGRES_PASSWORD=<openssl rand -base64 24>
JWT_SECRET=<openssl rand -base64 64>

# Activación del Auto-Instalador (Backend)
RESERVAKIDS_SINGLE_TENANT_ENABLED=true
RESERVAKIDS_SINGLE_TENANT_SLUG=salon-fantasia
RESERVAKIDS_SINGLE_TENANT_NOMBRE="Salón Fantasía"
RESERVAKIDS_SINGLE_TENANT_ADMIN_EMAIL=contacto@salonfantasia.cl
RESERVAKIDS_SINGLE_TENANT_ADMIN_PASSWORD=<contraseña-temporal-de-un-solo-uso>

# En modo full-Docker (sin Vite dev), el frontend está en el mismo dominio
FRONTEND_URL=http://localhost
```

Luego:

```bash
docker compose up -d
```

El backend:
1. Detecta `RESERVAKIDS_SINGLE_TENANT_ENABLED=true`
2. Verifica que la tabla `tenant` está vacía
3. Crea Tenant, Usuario (dueño) y Horario por Defecto vía `AuthService.registrar()`
4. Loggea confirmación

El frontend:
1. `entrypoint.sh` inyecta `RESERVAKIDS_SINGLE_TENANT_SLUG` en `index.html`
2. El router detecta el slug → elimina marketplace (`/negocios` → 404)
3. La ruta `/` carga directamente el catálogo del negocio (PublicSiteView)

El reverse proxy (Caddy):
1. Expone puerto 80 → rutea a frontend (nginx) y backend (Java)
2. Aplica CSP, HSTS, y headers de seguridad

### Desarrollo híbrido (Vite dev + Docker backend)

Si preferís desarrollar con Vite dev server (hot reload) y solo Docker para backend/db:

```env
# No setear RESERVAKIDS_SINGLE_TENANT_* (o dejarlas en false/vacío)
FRONTEND_URL=http://localhost:5173
```

```bash
docker compose up db mailpit backend -d   # solo backend
cd frontend && npm run dev                # Vite en :5173
```

El slug se lee de `frontend/.env` (`VITE_SINGLE_TENANT_SLUG`).

---

## Configurar dominio del cliente

Para exponer el sistema con el dominio del cliente (ej. `reservas.salonfantasia.cl`):

1. Apuntá el DNS del dominio a la IP del VPS
2. Modificá `Caddyfile.docker` para usar el dominio real:
   ```
   reservas.salonfantasia.cl {
       ...
   }
   ```
3. Caddy obtiene automáticamente certificado SSL con Let's Encrypt

---

## Estructura de contenedores

```
                   ┌─────────────────┐
                   │  Caddy (:80)    │  ← reverse proxy + TLS
                   └───────┬─────────┘
                           │
              ┌────────────┼────────────┐
              ▼            │            ▼
     ┌────────────┐        │   ┌──────────────┐
     │  Frontend   │        │   │   Backend     │
     │ nginx (:80) │        │   │ Java (:8080)  │
     └────────────┘        │   └──────┬─────────┘
                           │          │
                           │   ┌──────▼─────────┐
                           │   │  Postgres (:5432)│
                           │   └────────────────┘
                           │
                    ┌──────▼──────┐
                    │  Mailpit     │
                    └─────────────┘
```

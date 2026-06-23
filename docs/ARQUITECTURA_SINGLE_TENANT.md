# Arquitectura "Licencia Exclusiva" (Single-Tenant High-Ticket)

## 1. Contexto Comercial y Estratégico

ReservaKids fue concebido originalmente como una plataforma SaaS Multi-Tenant y un Directorio/Marketplace (modelo Silicon Valley / Product-Led Growth). Sin embargo, tras un análisis del mercado geográfico objetivo (Victoria, Región de la Araucanía), se determinó que el mercado local es finito y requiere un enfoque de ventas distinto.

En lugar de cobrar una mensualidad baja ($30.000) esperando cientos de clientes (modelo de volumen), la plataforma pivotó hacia un **Modelo de Licencias Propietarias de Alto Valor (High-Ticket)**. 
Bajo este modelo, se vende el software como una solución "Llave en Mano" o "White-Glove" por un valor de Setup elevado (ej. $250.000), seguido de una mensualidad de mantenimiento (ej. $39.990).

El cliente local percibe mayor valor al tener su "propio sistema exclusivo" bajo su propio dominio (ej. `reservas-salonfantasia.cl`), sin compartir un portal público con sus competidores.

## 2. Implementación Técnica (El "Interruptor Mágico")

Para lograr este modelo de exclusividad sin destruir la robusta arquitectura Multi-Tenant del Backend (Spring Boot + PostgreSQL), se implementó un mecanismo de aislamiento a nivel de Frontend (**Nuxt 3 SSR**).

> **Nota de arquitectura (2026-06):** el frontend migró de Vue 3 SPA (Vite + vue-router, estáticos en `dist/`) a **Nuxt 3 con SSR** (file-based routing en `src/pages`, middleware global). El interruptor single-tenant ya no vive en `router/index.js` sino en un middleware de Nuxt, y la variable se inyecta por entorno al contenedor SSR en runtime (no se hornea en un build estático).

### Variable de Entorno: `NUXT_PUBLIC_SINGLE_TENANT_SLUG`
Su presencia actúa como interruptor arquitectónico. Se inyecta al contenedor SSR en runtime (no requiere recompilar la imagen). En la práctica se define `RESERVAKIDS_SINGLE_TENANT_SLUG` en `.env`; el `docker-compose.yml` la propaga como `NUXT_PUBLIC_SINGLE_TENANT_SLUG` (y queda disponible en el cliente vía `window.__SINGLE_TENANT_SLUG__`, inyectado en `nuxt.config.ts`):

```env
# Ejemplo de configuración para un despliegue de licencia exclusiva (en .env)
RESERVAKIDS_SINGLE_TENANT_SLUG=salon-fantasia
```

### Comportamiento (middleware `src/middleware/single-tenant.global.ts`)
Si la variable está definida, la app muta su comportamiento automáticamente:
1.  **Bloqueo de Marketplace:** la ruta del directorio público (`/negocios`) redirige a `/404`, evitando fugas de tráfico hacia la competencia.
2.  **Home de cliente directo:** `/clientes` redirige a `/clientes/reservas` (no al directorio: solo existe un negocio).
3.  **Apropiación de la Raíz (`/`):** la página `src/pages/index.vue` renderiza el catálogo y calendario del negocio configurado en lugar de la landing de ventas del SaaS.
4.  **URL Limpia (Marca Blanca):** el cliente final nunca ve el "slug" en la URL; el sistema completo aparenta ser propietario y exclusivo.

## 3. Guía de Despliegue y Operación

Para vender e instalar el software a un nuevo cliente bajo este modelo, los pasos operativos son:

1.  **Clonación de Repositorio:** Descargar el código fuente en un servidor VPS dedicado o en un contenedor aislado.
2.  **Base de Datos Dedicada:** Iniciar una instancia de PostgreSQL limpia. El Backend correrá sus migraciones (Flyway) normalmente.
3.  **Setup de Datos (Manual o Súper-Admin):** Crear el usuario dueño (`Tenant`) en la base de datos y asignarle su "slug" único.
4.  **Inyección del Frontend (por entorno, no por build):** definir en el `.env` del despliegue:
    ```env
    RESERVAKIDS_SINGLE_TENANT_SLUG=el-slug-del-cliente
    FRONTEND_URL=https://reservas.tusalon.cl
    ```
    Estas variables las consume el contenedor SSR de Nuxt al arrancar (vía `docker-compose.yml` → `NUXT_PUBLIC_*`); no hay que recompilar la imagen por cliente.
5.  **Despliegue:** `./scripts/deploy-prod.sh` construye las imágenes (backend + Nuxt SSR) y levanta el stack; Caddy expone el dominio comprado para el cliente (ej. `reservas.tusalon.cl`). Ver [`MANUAL_DESPLIEGUE.md`](MANUAL_DESPLIEGUE.md).

## 4. Visión a Futuro

Al mantener la estructura Multi-Tenant bajo el capó (tablas con `tenant_id`), **se conserva la capacidad de actualizar el código de forma unificada**. 
Si en el futuro se descubre un bug o se desarrolla una nueva función, basta con hacer un `git pull` en los servidores de todos los clientes. Ningún esquema de base de datos se rompe. 

Si el negocio decide expandirse nacionalmente con un modelo SaaS automatizado, basta con no definir la variable `RESERVAKIDS_SINGLE_TENANT_SLUG` y la plataforma revertirá automáticamente a su estado original de Marketplace masivo.

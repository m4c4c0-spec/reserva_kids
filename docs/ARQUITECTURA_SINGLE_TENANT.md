# Arquitectura "Licencia Exclusiva" (Single-Tenant High-Ticket)

## 1. Contexto Comercial y Estratégico

ReservaKids fue concebido originalmente como una plataforma SaaS Multi-Tenant y un Directorio/Marketplace (modelo Silicon Valley / Product-Led Growth). Sin embargo, tras un análisis del mercado geográfico objetivo (Victoria, Región de la Araucanía), se determinó que el mercado local es finito y requiere un enfoque de ventas distinto.

En lugar de cobrar una mensualidad baja ($30.000) esperando cientos de clientes (modelo de volumen), la plataforma pivotó hacia un **Modelo de Licencias Propietarias de Alto Valor (High-Ticket)**. 
Bajo este modelo, se vende el software como una solución "Llave en Mano" o "White-Glove" por un valor de Setup elevado (ej. $250.000), seguido de una mensualidad de mantenimiento (ej. $39.990).

El cliente local percibe mayor valor al tener su "propio sistema exclusivo" bajo su propio dominio (ej. `reservas-salonfantasia.cl`), sin compartir un portal público con sus competidores.

## 2. Implementación Técnica (El "Interruptor Mágico")

Para lograr este modelo de exclusividad sin destruir la robusta arquitectura Multi-Tenant del Backend (Spring Boot + PostgreSQL), se implementó un mecanismo de aislamiento a nivel de Frontend (Vue 3).

### Variable de Entorno: `VITE_SINGLE_TENANT_SLUG`
Se agregó soporte para esta variable en el archivo `.env` del Frontend. Su presencia actúa como un interruptor arquitectónico:

```env
# Ejemplo de configuración para un despliegue de licencia exclusiva
VITE_SINGLE_TENANT_SLUG=salon-fantasia
```

### Comportamiento del Enrutador (`router/index.js`)
Si la variable está definida, la aplicación muta sus rutas automáticamente:
1.  **Bloqueo de Marketplace:** Las vistas `DirectorioPublicoView.vue` y `DirectorioView.vue` se deshabilitan, retornando un error 404 para evitar fugas de tráfico hacia la competencia.
2.  **Apropiación de la Raíz (`/`):** La Landing Page de ventas del SaaS (`LandingPageView.vue`) se oculta. En su lugar, al acceder a la raíz del dominio, se renderiza instantáneamente el catálogo y calendario del negocio definido en la variable de entorno (`PublicSiteView.vue`).
3.  **URL Limpia (Marca Blanca):** El cliente final (los padres) nunca ve el "slug" en la URL como si fuera un subdirectorio. El sistema completo aparenta ser propietario y exclusivo.

## 3. Guía de Despliegue y Operación

Para vender e instalar el software a un nuevo cliente bajo este modelo, los pasos operativos son:

1.  **Clonación de Repositorio:** Descargar el código fuente en un servidor VPS dedicado o en un contenedor aislado.
2.  **Base de Datos Dedicada:** Iniciar una instancia de PostgreSQL limpia. El Backend correrá sus migraciones (Flyway) normalmente.
3.  **Setup de Datos (Manual o Súper-Admin):** Crear el usuario dueño (`Tenant`) en la base de datos y asignarle su "slug" único.
4.  **Inyección del Frontend:** Crear el archivo `frontend/.env` definiendo:
    ```env
    VITE_API_URL=https://api.tusalon.cl
    VITE_SINGLE_TENANT_SLUG=el-slug-del-cliente
    ```
5.  **Build y Exposición:** Compilar el frontend (`npm run build`) y exponerlo en el dominio web comprado para el cliente (ej. `reservas.tusalon.cl`).

## 4. Visión a Futuro

Al mantener la estructura Multi-Tenant bajo el capó (tablas con `tenant_id`), **se conserva la capacidad de actualizar el código de forma unificada**. 
Si en el futuro se descubre un bug o se desarrolla una nueva función, basta con hacer un `git pull` en los servidores de todos los clientes. Ningún esquema de base de datos se rompe. 

Si el negocio decide expandirse nacionalmente con un modelo SaaS automatizado, basta con no definir la variable `VITE_SINGLE_TENANT_SLUG` y la plataforma revertirá automáticamente a su estado original de Marketplace masivo.

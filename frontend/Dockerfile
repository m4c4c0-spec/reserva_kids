# ── Stage 1: build ──
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm install --legacy-peer-deps
COPY . .
ENV NUXT_TELEMETRY_DISABLED=1
RUN npm run build

# ── Stage 2: run (Node SSR server via Nitro) ──
FROM node:22-alpine
WORKDIR /app
RUN apk add --no-cache wget

COPY --from=build /app/.output /app/.output
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENV NITRO_PORT=3000
ENV NITRO_HOST=0.0.0.0
EXPOSE 3000

# Healthcheck: Nitro expone /.health o / en producción
HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=10s \
  CMD wget -qO- http://localhost:3000/ || exit 1

ENTRYPOINT ["/entrypoint.sh"]

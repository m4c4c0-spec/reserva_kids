#!/usr/bin/env bash
set -euo pipefail

ROJO='\033[0;31m'; VERDE='\033[0;32m'; AMAR='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
ok()    { printf "${VERDE}✔${NC} %s\n" "$1"; }
warn()  { printf "${AMAR}⚠${NC} %s\n" "$1"; }
err()   { printf "${ROJO}✘${NC} %s\n" "$1" >&2; }
info()  { printf "${CYAN}ℹ${NC} %s\n" "$1"; }

echo ""
echo -e "${CYAN}═══════════════════════════════════════════════${NC}"
echo -e "${CYAN}  ReservaKids — Setup VPS (Ubuntu/Debian)${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════${NC}"
echo ""

if [ "$(id -u)" -ne 0 ]; then
  err "Ejecuta como root: sudo bash scripts/setup-vps.sh"
  exit 1
fi

info "Actualizando paquetes del sistema..."
apt-get update -qq && apt-get upgrade -y -qq
ok "Sistema actualizado"

if ! command -v docker &>/dev/null; then
  info "Instalando Docker..."
  curl -fsSL https://get.docker.com | sh
  systemctl enable --now docker
  ok "Docker instalado"
else
  ok "Docker ya instalado: $(docker --version)"
fi

if ! docker compose version &>/dev/null; then
  info "Instalando Docker Compose v2..."
  apt-get install -y -qq docker-compose-plugin
  ok "Docker Compose v2 instalado"
else
  ok "Docker Compose v2 ya instalado: $(docker compose version --short)"
fi

if ! command -v caddy &>/dev/null; then
  info "Instalando Caddy..."
  apt-get install -y -qq debian-keyring debian-archive-keyring apt-transport-https curl
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | tee /etc/apt/sources.list.d/caddy-stable.list
  apt-get update -qq
  apt-get install -y -qq caddy
  systemctl enable caddy
  ok "Caddy instalado"
else
  ok "Caddy ya instalado: $(caddy version)"
fi

if ! command -v git &>/dev/null; then
  info "Instalando git..."
  apt-get install -y -qq git
  ok "Git instalado"
else
  ok "Git ya instalado: $(git --version)"
fi

info "Configurando firewall (ufw)..."
if command -v ufw &>/dev/null; then
  ufw allow 22/tcp   >/dev/null 2>&1
  ufw allow 80/tcp   >/dev/null 2>&1
  ufw allow 443/tcp  >/dev/null 2>&1
  ufw --force enable >/dev/null 2>&1
  ok "Firewall configurado (22, 80, 443)"
else
  warn "ufw no disponible — configura el firewall manualmente"
fi

echo ""
echo -e "${VERDE}═══════════════════════════════════════════════${NC}"
echo -e "${VERDE}  VPS listo para ReservaKids${NC}"
echo -e "${VERDE}═══════════════════════════════════════════════${NC}"
echo ""
echo "  Proximos pasos:"
echo "    1. Clonar el repo:"
echo "       git clone https://github.com/m4c4c0-spec/reserva_kids.git"
echo "       cd reserva_kids"
echo ""
echo "    2. Configurar .env:"
echo "       cp .env.example .env"
echo "       nano .env   # editar DOMAIN, secrets, SMTP, WhatsApp"
echo ""
echo "    3. Desplegar:"
echo "       ./deploy-prod.sh"
echo ""
echo "    4. Para updates futuros:"
echo "       git pull && ./deploy-prod.sh"
echo ""

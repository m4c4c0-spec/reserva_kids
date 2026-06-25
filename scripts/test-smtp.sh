#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  echo "ERROR: No existe .env en la raíz del proyecto"
  exit 1
fi

set -a; . ./.env; set +a

export _SMTP_HOST="${MAIL_HOST:-}"
export _SMTP_PORT="${MAIL_PORT:-587}"
export _SMTP_USER="${MAIL_USERNAME:-}"
export _SMTP_PASS="${MAIL_PASSWORD:-}"
export _SMTP_FROM="${MAIL_FROM:-no-reply@reservakids.cl}"
export _SMTP_TO="${1:-$MAIL_FROM}"
export _SMTP_STARTTLS="${MAIL_SMTP_STARTTLS:-true}"

echo "═══════════════════════════════════════════════"
echo "  Prueba SMTP — ReservaKids"
echo "═══════════════════════════════════════════════"
echo ""
echo "  Host:     ${_SMTP_HOST:-<vacío>}"
echo "  Puerto:   $_SMTP_PORT"
echo "  Usuario:  ${_SMTP_USER:-<vacío>}"
echo "  From:     $_SMTP_FROM"
echo "  To:       $_SMTP_TO"
echo ""

if [ -z "$_SMTP_HOST" ]; then
  echo "MAIL_HOST está vacío. En Docker se usa Mailpit (localhost:1025)."
  echo "Para probar con Mailpit local:"
  echo "  MAIL_HOST=localhost MAIL_PORT=1025 $0"
  echo ""
  echo "Para producción, configura MAIL_HOST en .env (ej: smtp.resend.com)"
  exit 1
fi

echo "Enviando email de prueba a $_SMTP_TO ..."
echo ""

python3 << 'PYEOF'
import os, smtplib, ssl, sys
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart

host = os.environ["_SMTP_HOST"]
port = int(os.environ["_SMTP_PORT"])
user = os.environ["_SMTP_USER"]
password = os.environ["_SMTP_PASS"]
sender = os.environ["_SMTP_FROM"]
to = os.environ["_SMTP_TO"]
starttls = os.environ["_SMTP_STARTTLS"].lower() == "true"

msg = MIMEMultipart("alternative")
msg["Subject"] = "ReservaKids - Email de prueba"
msg["From"] = sender
msg["To"] = to

html = f"""
<html><body style="font-family: sans-serif; padding: 20px;">
<h2 style="color: #b5007d;">ReservaKids - Prueba de SMTP</h2>
<p>Si recibes este email, la configuracion SMTP es correcta.</p>
<table style="border-collapse: collapse; margin-top: 16px;">
  <tr><td style="padding: 4px 12px; border: 1px solid #ddd;"><b>Host</b></td><td style="padding: 4px 12px; border: 1px solid #ddd;">{host}</td></tr>
  <tr><td style="padding: 4px 12px; border: 1px solid #ddd;"><b>Puerto</b></td><td style="padding: 4px 12px; border: 1px solid #ddd;">{port}</td></tr>
  <tr><td style="padding: 4px 12px; border: 1px solid #ddd;"><b>Usuario</b></td><td style="padding: 4px 12px; border: 1px solid #ddd;">{user}</td></tr>
  <tr><td style="padding: 4px 12px; border: 1px solid #ddd;"><b>STARTTLS</b></td><td style="padding: 4px 12px; border: 1px solid #ddd;">{starttls}</td></tr>
</table>
<p style="color: #888; margin-top: 24px; font-size: 12px;">Enviado desde scripts/test-smtp.sh</p>
</body></html>
"""

msg.attach(MIMEText(html, "html"))

try:
    if port == 465:
        ctx = ssl.create_default_context()
        server = smtplib.SMTP_SSL(host, port, context=ctx, timeout=15)
    else:
        server = smtplib.SMTP(host, port, timeout=15)
        server.ehlo()
        if starttls:
            server.starttls(context=ssl.create_default_context())
            server.ehlo()

    if user and password:
        server.login(user, password)

    server.sendmail(sender, [to], msg.as_string())
    server.quit()
    print("EMAIL ENVIADO correctamente a " + to)
    print("Revisa tu bandeja de entrada (y spam).")
except Exception as e:
    print("FALLO al enviar email: " + str(e), file=sys.stderr)
    sys.exit(1)
PYEOF

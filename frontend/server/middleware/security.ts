export default defineEventHandler((event) => {
  setResponseHeaders(event, {
    'Content-Security-Policy':
      "default-src 'self'; script-src 'self' 'unsafe-inline' https://connect.facebook.net; style-src 'self' https://fonts.googleapis.com 'unsafe-inline'; font-src 'self' https://fonts.gstatic.com; img-src 'self' data: https://www.facebook.com; connect-src 'self' https://*.mercadopago.cl https://*.mercadopago.com https://www.facebook.com; frame-ancestors 'none'; base-uri 'self'; form-action 'self' https://*.mercadopago.com",
    'X-Content-Type-Options': 'nosniff',
    'X-Frame-Options': 'DENY',
    'Referrer-Policy': 'no-referrer',
    'Permissions-Policy': 'geolocation=(), microphone=(), camera=()',
  })
})

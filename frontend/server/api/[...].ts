export default defineEventHandler(async (event) => {
  const apiTarget = process.env.VITE_API_TARGET || process.env.NUXT_API_TARGET || 'http://localhost:8080'
  const path = event.path.replace(/^\/api/, '')

  const target = `${apiTarget}/api${path}`

  const body = ['GET', 'HEAD'].includes(event.method) ? undefined : await readBody(event).catch(() => undefined)

  try {
    const response = await $fetch(target, {
      method: event.method as any,
      headers: {
        ...Object.fromEntries(
          Object.entries(event.headers).filter(([k]) =>
            ['cookie', 'authorization', 'content-type', 'x-requested-with', 'accept'].includes(k.toLowerCase()),
          ),
        ),
        host: new URL(apiTarget).host,
      },
      body,
    })
    return response
  } catch (err: any) {
    setResponseStatus(event, err.statusCode || 502)
    return err.data || { message: 'Error de proxy' }
  }
})

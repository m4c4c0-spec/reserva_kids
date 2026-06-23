import { ref, onBeforeUnmount } from 'vue'

// Confetti de celebración con Canvas 2D. Aislado del componente para que
// LoginView.vue se ocupe solo del formulario de auth. El cleanup del
// animationId se hace solo: cuando no quedan partículas el loop termina,
// y onBeforeUnmount cancela por si desmontan antes.
const COLORES = ['#ff4db8', '#ff9cdb', '#fff8f8', '#ffe170', '#4b92fd']

export function useConfetti() {
  const canvasRef = ref(null)
  let animationId = null

  function lanzar() {
    const canvas = canvasRef.value
    if (!canvas) return
    canvas.width = window.innerWidth
    canvas.height = window.innerHeight
    const ctx = canvas.getContext('2d')

    const particulas = Array.from({ length: 150 }, () => ({
      x: canvas.width / 2,
      y: canvas.height / 2,
      size: Math.random() * 10 + 6,
      color: COLORES[Math.floor(Math.random() * COLORES.length)],
      vx: Math.random() * 20 - 10,
      vy: Math.random() * -20 - 5,
      rot: Math.random() * 360,
      vrot: Math.random() * 10 - 5,
    }))

    function animar() {
      ctx.clearRect(0, 0, canvas.width, canvas.height)
      for (let i = particulas.length - 1; i >= 0; i--) {
        const p = particulas[i]
        p.x += p.vx
        p.y += p.vy
        p.vy += 0.5
        p.rot += p.vrot
        ctx.save()
        ctx.translate(p.x, p.y)
        ctx.rotate((p.rot * Math.PI) / 180)
        ctx.fillStyle = p.color
        ctx.fillRect(-p.size / 2, -p.size / 2, p.size, p.size)
        ctx.restore()
        if (p.y > canvas.height + 50) particulas.splice(i, 1)
      }
      if (particulas.length > 0) {
        animationId = requestAnimationFrame(animar)
      } else {
        animationId = null
      }
    }
    animar()
  }

  onBeforeUnmount(() => {
    if (animationId) cancelAnimationFrame(animationId)
  })

  return { canvasRef, lanzar }
}

<script setup>
defineProps({
  mensaje: { type: String, default: 'Cargando...' },
})
</script>

<template>
  <div
    class="fixed inset-0 z-[100] bg-surface/90 backdrop-blur-sm flex flex-col items-center justify-center"
    role="status"
    aria-live="polite"
  >
    <!-- Algodón de azúcar animado -->
    <div class="cotton-candy">
      <div class="cotton-body">
        <div class="cotton-fluff f1" />
        <div class="cotton-fluff f2" />
        <div class="cotton-fluff f3" />
        <div class="cotton-fluff f4" />
        <div class="cotton-fluff f5" />
        <div class="cotton-fluff f6" />
        <div class="cotton-fluff f7" />
        <div class="cotton-fluff f8" />
      </div>
      <div class="cotton-stick" />
    </div>

    <p class="font-display font-bold text-lg text-on-surface mt-6 animate-pulse">{{ mensaje }}</p>
  </div>
</template>

<style scoped>
.cotton-candy {
  position: relative;
  width: 120px;
  height: 180px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.cotton-body {
  position: relative;
  width: 100px;
  height: 100px;
}

.cotton-fluff {
  position: absolute;
  border-radius: 50%;
  filter: blur(3px);
}

.f1 {
  width: 60px;
  height: 60px;
  background: radial-gradient(circle at 30% 30%, #ffb3d9, #ff4db8);
  top: 0;
  left: 20px;
  -webkit-animation: cotton-bounce 2s ease-in-out infinite;
  animation: cotton-bounce 2s ease-in-out infinite;
}

.f2 {
  width: 55px;
  height: 55px;
  background: radial-gradient(circle at 40% 40%, #ffe0f0, #ff80ce);
  top: 5px;
  left: 0;
  -webkit-animation: cotton-bounce 2s ease-in-out 0.25s infinite;
  animation: cotton-bounce 2s ease-in-out 0.25s infinite;
}

.f3 {
  width: 50px;
  height: 50px;
  background: radial-gradient(circle at 50% 30%, #b3e0ff, #66b3ff);
  top: 0;
  left: 50px;
  -webkit-animation: cotton-bounce 2s ease-in-out 0.5s infinite;
  animation: cotton-bounce 2s ease-in-out 0.5s infinite;
}

.f4 {
  width: 48px;
  height: 48px;
  background: radial-gradient(circle at 60% 50%, #ffd4ec, #ff6bc4);
  top: 25px;
  left: 55px;
  -webkit-animation: cotton-bounce 2s ease-in-out 0.75s infinite;
  animation: cotton-bounce 2s ease-in-out 0.75s infinite;
}

.f5 {
  width: 52px;
  height: 52px;
  background: radial-gradient(circle at 35% 35%, #d4e8ff, #80bfff);
  top: 30px;
  left: -5px;
  -webkit-animation: cotton-bounce 2s ease-in-out 1s infinite;
  animation: cotton-bounce 2s ease-in-out 1s infinite;
}

.f6 {
  width: 44px;
  height: 44px;
  background: radial-gradient(circle at 45% 55%, #ffcce0, #ff99d0);
  top: 45px;
  left: 30px;
  -webkit-animation: cotton-bounce 2s ease-in-out 1.25s infinite;
  animation: cotton-bounce 2s ease-in-out 1.25s infinite;
}

.f7 {
  width: 42px;
  height: 42px;
  background: radial-gradient(circle at 30% 60%, #ffe6f2, #ffb3d9);
  top: 50px;
  left: 10px;
  -webkit-animation: cotton-bounce 2s ease-in-out 1.5s infinite;
  animation: cotton-bounce 2s ease-in-out 1.5s infinite;
}

.f8 {
  width: 40px;
  height: 40px;
  background: radial-gradient(circle at 40% 40%, #cce5ff, #99ccff);
  top: 55px;
  left: 50px;
  -webkit-animation: cotton-bounce 2s ease-in-out 1.75s infinite;
  animation: cotton-bounce 2s ease-in-out 1.75s infinite;
}

.cotton-stick {
  width: 6px;
  height: 70px;
  background: linear-gradient(to bottom, #f0d4b0, #c4945b);
  border-radius: 0 0 3px 3px;
  margin-top: -2px;
  position: relative;
  z-index: -1;
}

@-webkit-keyframes cotton-bounce {
  0%,
  100% {
    -webkit-transform: translateY(0) scale(1);
    opacity: 0.85;
  }
  50% {
    -webkit-transform: translateY(-6px) scale(1.08);
    opacity: 1;
  }
}

@keyframes cotton-bounce {
  0%,
  100% {
    transform: translateY(0) scale(1);
    opacity: 0.85;
  }
  50% {
    transform: translateY(-6px) scale(1.08);
    opacity: 1;
  }
}
</style>

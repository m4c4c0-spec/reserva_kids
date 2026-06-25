<script setup>
definePageMeta({ layout: 'default' })

useSeoMeta({
  title: 'Política de privacidad',
  description:
    'Cómo tratamos y protegemos tus datos personales en cumplimiento de la Ley 21.719 de protección de datos.',
})

const contacto = 'hola@dulcevida.cl'
</script>

<template>
  <main class="min-h-screen bg-surface py-10 px-4 sm:px-6 lg:px-8">
    <article class="mx-auto max-w-3xl rounded-3xl bg-white p-8 sm:p-12 shadow-soft">
      <h1 class="font-display text-3xl sm:text-4xl font-bold text-primary mb-6">Política de privacidad</h1>
      <p class="text-on-surface-variant mb-8">Última actualización: 18 de junio de 2026.</p>

      <section class="space-y-6 text-on-surface leading-relaxed">
        <div>
          <h2 class="font-display text-xl font-semibold text-on-surface mb-2">1. Responsable del tratamiento</h2>
          <p>
            DulceVida SpA, RUT 77.123.456-7, con domicilio en Santiago de Chile, es responsable de los datos personales
            que se tratan a través de esta plataforma. Puedes contactarnos en
            <a :href="'mailto:' + contacto" class="text-secondary hover:underline">{{ contacto }}</a
            >.
          </p>
        </div>

        <div>
          <h2 class="font-display text-xl font-semibold text-on-surface mb-2">2. Datos que recolectamos</h2>
          <ul class="list-disc pl-5 space-y-1">
            <li>Datos de identidad: nombre, RUT, teléfono y correo electrónico del apoderado.</li>
            <li>
              Datos de la reserva: número de niños asistentes (solo cantidad, sin nombres ni edades), comuna y
              comentarios.
            </li>
            <li>
              No recolectamos datos identificativos de menores: por política de privacidad, no almacenamos nombres ni
              fechas de nacimiento de niños.
            </li>
            <li>Datos de uso: navegación, preferencias de servicio e historial de reservas.</li>
            <li>Datos de pago: procesados por Mercado Pago; no almacenamos tarjetas ni tokens bancarios.</li>
          </ul>
        </div>

        <div>
          <h2 class="font-display text-xl font-semibold text-on-surface mb-2">3. Finalidad del tratamiento</h2>
          <p>
            Utilizamos tus datos para gestionar reservas, enviar confirmaciones y recordatorios, permitir la
            comunicación con el negocio seleccionado, mejorar nuestros servicios y cumplir con obligaciones legales
            (incluida la Ley N° 19.628 y la Ley N° 21.719).
          </p>
        </div>

        <div>
          <h2 class="font-display text-xl font-semibold text-on-surface mb-2">4. Base legal</h2>
          <p>
            El tratamiento se fundamenta en la ejecución del contrato de servicios, el consentimiento del titular cuando
            corresponda y el cumplimiento de obligaciones legales.
          </p>
        </div>

        <div>
          <h2 class="font-display text-xl font-semibold text-on-surface mb-2">5. Conservación</h2>
          <p>
            Conservamos los datos solo durante el tiempo necesario para las finalidades indicadas y los plazos legales
            aplicables. Los clientes inactivos se anonimizan automáticamente tras el período de retención definido en
            nuestras condiciones de servicio.
          </p>
        </div>

        <div>
          <h2 class="font-display text-xl font-semibold text-on-surface mb-2">6. Tus derechos</h2>
          <p>
            Tienes derecho a acceder, rectificar, cancelar y oponerte al tratamiento de tus datos personales (derechos
            ARCO). Para ejercerlos, escríbenos a
            <a :href="'mailto:' + contacto" class="text-secondary hover:underline">{{ contacto }}</a>
            indicando tu nombre, correo asociado y el derecho que deseas ejercer.
          </p>
        </div>

        <div>
          <h2 class="font-display text-xl font-semibold text-on-surface mb-2">7. Seguridad</h2>
          <p>
            Aplicamos medidas técnicas y organizativas para proteger tus datos: conexiones cifradas (TLS), control de
            acceso, tokens de sesión con vencimiento y auditoría periódica.
          </p>
        </div>

        <div>
          <h2 class="font-display text-xl font-semibold text-on-surface mb-2">8. Cookies y tecnologías similares</h2>
          <p>
            Usamos cookies estrictamente necesarias para mantener tu sesión y recordar tus preferencias. No utilizamos
            cookies de terceros para publicidad comportamental.
          </p>
        </div>

        <div>
          <h2 class="font-display text-xl font-semibold text-on-surface mb-2">9. Transferencias internacionales</h2>
          <p>
            Tus datos se almacenan en servidores ubicados en Chile. En caso de requerirse alguna transferencia
            internacional, se realizará solo a países con niveles adecuados de protección o con las salvaguardas
            contractuales correspondientes.
          </p>
        </div>

        <div>
          <h2 class="font-display text-xl font-semibold text-on-surface mb-2">10. Cambios a esta política</h2>
          <p>
            Podemos actualizar esta política ocasionalmente. Te notificaremos cambios importantes a través de la
            plataforma o al correo asociado a tu cuenta. El uso continuado del servicio implica la aceptación de la
            versión vigente.
          </p>
        </div>
      </section>

      <div class="mt-10 pt-6 border-t border-outline-variant text-center">
        <NuxtLink to="/" class="inline-flex items-center gap-2 text-secondary hover:underline font-medium">
          <span class="material-symbols-outlined">arrow_back</span>
          Volver al inicio
        </NuxtLink>
      </div>
    </article>
  </main>
</template>

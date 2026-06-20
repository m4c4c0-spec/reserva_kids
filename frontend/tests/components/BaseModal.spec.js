import { mount, flushPromises } from '@vue/test-utils'
import { describe, it, expect, afterEach } from 'vitest'
import BaseModal from '../../src/components/BaseModal.vue'

// Helper: monta cerrado y lo abre, esperando a que el watch async (nextTick +
// enfoque inicial) corra. Así cubrimos el path real de apertura del modal.
async function montarYAbrir(props = {}, slots = {}) {
  const wrapper = mount(BaseModal, {
    props: { visible: false, titulo: '', ...props },
    slots,
    attachTo: document.body,
  })
  await wrapper.setProps({ visible: true })
  await flushPromises()
  return wrapper
}

describe('BaseModal', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('no renderiza el diálogo cuando visible=false', async () => {
    const wrapper = await montarYAbrir()
    // Volver a cerrar para este test: lo dejamos en false desde el inicio.
    await wrapper.setProps({ visible: false })
    await flushPromises()
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('renderiza el título y el contenido del slot cuando visible=true', async () => {
    await montarYAbrir({ titulo: 'Cancelar' }, { default: '<p>¿Seguro?</p>' })
    const dialog = document.querySelector('[role="dialog"]')
    expect(dialog).not.toBeNull()
    expect(dialog.textContent).toContain('Cancelar')
    expect(dialog.textContent).toContain('¿Seguro?')
  })

  it('el slot "acciones" reemplaza a los botones por defecto', async () => {
    await montarYAbrir({}, { acciones: '<button data-custom>Hecho</button>' })
    const dialog = document.querySelector('[role="dialog"]')
    expect(dialog.querySelector('button[data-custom]')).not.toBeNull()
    expect(dialog.textContent).not.toContain('Cancelar')
    expect(dialog.textContent).not.toContain('Confirmar')
  })

  it('el botón Cancelar por defecto emite "cancelar"', async () => {
    const wrapper = await montarYAbrir()
    const botones = document.querySelectorAll('[role="dialog"] button')
    // El primer botón del slot por defecto es "Cancelar".
    expect(botones[0].textContent.trim()).toBe('Cancelar')
    botones[0].click()
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('cancelar')).toBeTruthy()
  })

  it('el botón Confirmar por defecto emite "confirmar"', async () => {
    const wrapper = await montarYAbrir()
    const botones = document.querySelectorAll('[role="dialog"] button')
    botones[1].click()
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('confirmar')).toBeTruthy()
  })

  it('presionar Escape emite "cerrar"', async () => {
    const wrapper = await montarYAbrir()
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('cerrar')).toBeTruthy()
  })

  it('tiene role="dialog", aria-modal="true" y aria-label = título', async () => {
    await montarYAbrir({ titulo: 'Cerrar negocio' })
    const dialog = document.querySelector('[role="dialog"]')
    expect(dialog.getAttribute('aria-modal')).toBe('true')
    expect(dialog.getAttribute('aria-label')).toBe('Cerrar negocio')
  })

  it('al abrirse, enfoca el contenedor del diálogo (focus dentro del modal)', async () => {
    // jsdom no computa offsetParent (sin layout), así que focusables() filtra
    // todo y el foco cae en el dialogRef (tabindex="-1"). En navegador real con
    // layout, enfocaría el primer input/botón del slot. Validamos que el foco
    // queda dentro del diálogo (no se escapa afuera): ese es el contrato del trap.
    await montarYAbrir({}, { default: '<input data-test="campo" />' })
    const dialog = document.querySelector('[role="dialog"]')
    expect(dialog.contains(document.activeElement)).toBe(true)
  })

  it('al cerrarse, restaura el foco al elemento que lo tenía antes', async () => {
    // Simulamos un botón externo que abre el modal.
    const boton = document.createElement('button')
    boton.textContent = 'abrir'
    document.body.appendChild(boton)
    boton.focus()

    const wrapper = await montarYAbrir()
    // Al abrir, el foco se mueve al diálogo (no al botón externo).
    expect(document.activeElement).not.toBe(boton)

    // Al cerrar (visible=false), restaura el foco al botón externo.
    await wrapper.setProps({ visible: false })
    await flushPromises()
    expect(document.activeElement).toBe(boton)

    document.body.removeChild(boton)
  })
})

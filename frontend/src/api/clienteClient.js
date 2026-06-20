import { createAuthClient } from './createAuthClient'
import { useClienteAuthStore } from '../stores/clienteAuth'

const api = createAuthClient(useClienteAuthStore, '/clientes/entrar')

export default api

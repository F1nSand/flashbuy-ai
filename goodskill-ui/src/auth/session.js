import {reactive} from 'vue'
import {api} from '../api/client.js'

export const session = reactive({user: null, loaded: false})
let pendingLoad = null

export async function loadSession(force = false) {
  if (!force && session.loaded) return session.user
  if (pendingLoad) return pendingLoad

  pendingLoad = api.get('/api/auth/user/info').then((response) => {
    const result = response.data
    if (result?.code !== 0 || !result?.data) {
      throw new Error('SESSION_UNAVAILABLE')
    }
    const value = result.data
    const user = value.user || {}
    session.user = {
      id: user.id,
      username: user.username || user.account || '用户',
      roles: Array.isArray(value.roles) ? value.roles : []
    }
    session.loaded = true
    return session.user
  }).catch(() => {
    session.user = null
    session.loaded = true
    return null
  }).finally(() => {
    pendingLoad = null
  })

  return pendingLoad
}

export function resetSession() {
  session.user = null
  session.loaded = false
  pendingLoad = null
}

export function hasAnyRole(roles = []) {
  return roles.some(role => session.user?.roles?.includes(role))
}

export async function logout() {
  try {
    await api.post('/api/auth/logout')
  } finally {
    resetSession()
  }
}

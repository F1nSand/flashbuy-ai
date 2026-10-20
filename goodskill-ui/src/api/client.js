import axios from 'axios'

export const productionMode = import.meta.env.VITE_APP_MODE === 'production'

let serverClockOffsetMs = 0

export const api = axios.create({
  baseURL: '',
  timeout: 15000,
  withCredentials: true,
  ...(productionMode ? {
    xsrfCookieName: '__Host-XSRF-TOKEN',
    xsrfHeaderName: 'X-CSRF-TOKEN',
    withXSRFToken: true
  } : {})
})

api.interceptors.response.use((response) => {
  const serverDate = response.headers?.date
  if (serverDate) {
    const parsed = Date.parse(serverDate)
    if (Number.isFinite(parsed)) serverClockOffsetMs = parsed - Date.now()
  }
  return response
}, (error) => {
  if (productionMode && (error?.response?.status === 401 || error?.response?.data?.code === 401)) {
    window.dispatchEvent(new CustomEvent('goodskill:session-expired'))
  }
  return Promise.reject(error)
})

export const serverNow = () => Date.now() + serverClockOffsetMs

export const formatApiError = (error) => {
  const code = error?.response?.data?.data?.errorCode || error?.response?.data?.msg
    || error?.response?.data?.message
  if (code === 'ACTIVITY_NOT_OPEN') return '活动尚未开始或已经结束。'
  if (code === 'SOLD_OUT') return '商品已售罄。'
  if (code === 'PURCHASE_LIMIT_REACHED') return '已达到本活动该商品的购买上限。'
  if (code === 'ADMISSION_PAUSED') return '当前暂不接受新订单，请稍后再试。'
  if (code === 'PAYMENT_DISABLED') return '支付服务尚未启用。'
  if (code === 'TEMPORARY_UNAVAILABLE') return '服务繁忙，请稍后使用同一操作重试。'
  if (code === 'FORBIDDEN') return '当前账号没有执行此操作的权限。'
  if (error?.response?.status === 401 || error?.response?.data?.code === 401) return '登录状态已失效，请重新登录。'
  if (!error?.response) return '网络暂时不可用，请检查连接后重试。'
  return typeof code === 'string' ? code : '请求未能完成，请稍后重试。'
}

export const newIdempotencyKey = () => globalThis.crypto?.randomUUID?.()
  || `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`

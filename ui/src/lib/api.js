const BASE = '/api'

function getToken() {
  return localStorage.getItem('skybook_token')
}

export async function apiFetch(url, options = {}) {
  const token = getToken()
  const headers = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(options.headers || {}),
  }
  const res = await fetch(BASE + url, { ...options, headers })
  const json = await res.json()
  if (!res.ok) throw new Error(json.message || json.data || 'Request failed')
  return json.data !== undefined ? json.data : json
}

// ─── Auth ──────────────────────────────────────────────────────────
export const authAPI = {
  login:       (body)      => apiFetch('/auth/login',        { method: 'POST', body: JSON.stringify(body) }),
  register:    (body)      => apiFetch('/auth/register',     { method: 'POST', body: JSON.stringify(body) }),
  me:          ()          => apiFetch('/auth/me'),
  addUpi:      (body)      => apiFetch('/auth/upi',          { method: 'POST', body: JSON.stringify(body) }),
  deleteUpi:   (id)        => apiFetch(`/auth/upi/${id}`,    { method: 'DELETE' }),
  deleteCard:  (id)        => apiFetch(`/auth/card/${id}`,   { method: 'DELETE' }),
  setupIntent: ()          => apiFetch('/auth/setup-intent', { method: 'POST' }),
  saveCard:    (body)      => apiFetch('/auth/save-card',    { method: 'POST', body: JSON.stringify(body) }),
}

// ─── Flights ───────────────────────────────────────────────────────
export const flightAPI = {
  airports: (keyword) => apiFetch(`/flights/airports?keyword=${encodeURIComponent(keyword)}`),
  search:   (params)  => apiFetch(`/flights/search?${new URLSearchParams(params)}`),
  seatmap:  (id)      => apiFetch(`/flights/seatmap/${id}`),
  lockSeat:   (body)  => apiFetch('/flights/seats/lock',   { method: 'POST', body: JSON.stringify(body) }),
  unlockSeat: (body)  => apiFetch('/flights/seats/unlock', { method: 'POST', body: JSON.stringify(body) }),
  config:     ()      => apiFetch('/flights/config'),
}

// ─── Payment ───────────────────────────────────────────────────────
export const paymentAPI = {
  config:        ()              => apiFetch('/payment/config'),
  payCard:       (body)          => apiFetch('/payment/card',             { method: 'POST', body: JSON.stringify(body) }),
  payUpi:        (body)          => apiFetch('/payment/upi',              { method: 'POST', body: JSON.stringify(body) }),
  pollIntent:    (id)            => apiFetch(`/payment/intent/${id}`),
  confirmBooking:(body)          => apiFetch('/payment/confirm-booking',  { method: 'POST', body: JSON.stringify(body) }),
  myBookings:    ()              => apiFetch('/payment/bookings'),
  cancelSeat:    (bookingId, seat) => apiFetch(`/payment/bookings/${bookingId}/cancel-seat`, { method: 'POST', body: JSON.stringify({ seat }) }),
}

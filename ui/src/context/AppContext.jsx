import React, { createContext, useContext, useReducer, useCallback } from 'react'

const AppContext = createContext(null)

const initialState = {
  // Auth
  user: JSON.parse(localStorage.getItem('skybook_user') || 'null'),
  token: localStorage.getItem('skybook_token') || null,

  // Search
  origin: '', originCity: '',
  destination: '', destinationCity: '',
  dateStart: '', dateEnd: '',
  adults: 1, maxDays: 0, sortBy: 'price',

  // Results
  flights: [],

  // Booking
  selectedFlight: null,
  selectedSeats: [],
  lockExpiry: null,

  // Payment
  stripePublishableKey: '',
  stripeConfigured: false,

  // Toasts
  toasts: [],
}

function reducer(state, action) {
  switch (action.type) {
    case 'SET_AUTH':
      localStorage.setItem('skybook_token', action.token)
      localStorage.setItem('skybook_user', JSON.stringify(action.user))
      return { ...state, user: action.user, token: action.token }

    case 'LOGOUT':
      localStorage.removeItem('skybook_token')
      localStorage.removeItem('skybook_user')
      return { ...state, user: null, token: null, selectedFlight: null, selectedSeats: [], lockExpiry: null }

    case 'UPDATE_USER':
      localStorage.setItem('skybook_user', JSON.stringify(action.user))
      return { ...state, user: action.user }

    case 'SET_SEARCH':
      return { ...state, ...action.payload }

    case 'SET_FLIGHTS':
      return { ...state, flights: action.flights }

    case 'SELECT_FLIGHT':
      return { ...state, selectedFlight: action.flight, selectedSeats: [], lockExpiry: null }

    case 'SET_SEATS':
      return { ...state, selectedSeats: action.seats }

    case 'SET_LOCK_EXPIRY':
      return { ...state, lockExpiry: action.expiry }

    case 'SET_STRIPE':
      return { ...state, stripePublishableKey: action.key, stripeConfigured: action.configured }

    case 'ADD_TOAST':
      return { ...state, toasts: [...state.toasts, { id: Date.now(), ...action.toast }] }

    case 'REMOVE_TOAST':
      return { ...state, toasts: state.toasts.filter(t => t.id !== action.id) }

    default:
      return state
  }
}

export function AppProvider({ children }) {
  const [state, dispatch] = useReducer(reducer, initialState)

  const toast = useCallback((message, type = 'info', ms = 3500) => {
    const id = Date.now()
    dispatch({ type: 'ADD_TOAST', toast: { id, message, type } })
    setTimeout(() => dispatch({ type: 'REMOVE_TOAST', id }), ms)
  }, [])

  return (
    <AppContext.Provider value={{ state, dispatch, toast }}>
      {children}
    </AppContext.Provider>
  )
}

export function useApp() {
  const ctx = useContext(AppContext)
  if (!ctx) throw new Error('useApp must be used within AppProvider')
  return ctx
}

import React from 'react'
import { useApp } from '../context/AppContext'

export default function Navbar({ onNavigate }) {
  const { state, dispatch } = useApp()

  const handleLogout = () => {
    dispatch({ type: 'LOGOUT' })
    onNavigate('search')
  }

  return (
    <nav className="navbar">
      <div className="nav-brand" onClick={() => onNavigate('search')}>
        <span className="nav-logo">✈️</span>
        <span className="nav-title">SkyBook</span>
      </div>
      <div className="nav-actions">
        {state.user ? (
          <>
            <button className="nav-btn" onClick={() => onNavigate('profile')}>
              👤 {state.user.name}
            </button>
            <button className="nav-btn" onClick={handleLogout}>
              Logout
            </button>
          </>
        ) : (
          <button className="nav-btn primary" onClick={() => onNavigate('auth')}>
            Login / Register
          </button>
        )}
      </div>
    </nav>
  )
}

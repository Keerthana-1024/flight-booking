import React from 'react'
import { useApp } from '../context/AppContext'

export default function ToastContainer() {
  const { state } = useApp()

  return (
    <div className="toast-wrap">
      {state.toasts.map(t => (
        <div key={t.id} className={`toast toast-${t.type}`}>
          {t.type === 'success' && <span>✅</span>}
          {t.type === 'error' && <span>❌</span>}
          {t.type === 'info' && <span>ℹ️</span>}
          <div>{t.message}</div>
        </div>
      ))}
    </div>
  )
}

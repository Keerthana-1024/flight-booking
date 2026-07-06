import React, { useState } from 'react'
import { useApp } from '../context/AppContext'
import { authAPI } from '../lib/api'

export default function AuthPage({ onNavigate }) {
  const { dispatch, toast } = useApp()
  const [isLogin, setIsLogin] = useState(true)
  const [loading, setLoading] = useState(false)
  const [form, setForm] = useState({ name: '', email: '', password: '', phone: '' })

  const handleSubmit = async (e) => {
    e.preventDefault()
    setLoading(true)
    try {
      let data
      if (isLogin) {
        data = await authAPI.login({ email: form.email, password: form.password })
      } else {
        data = await authAPI.register(form)
      }
      dispatch({ type: 'SET_AUTH', token: data.token, user: data.user })
      toast(isLogin ? 'Welcome back!' : 'Account registered successfully!', 'success')
      onNavigate('search')
    } catch (err) {
      toast(err.message, 'error')
    } finally {
      setLoading(false)
    }
  }

  const handleChange = (e) => {
    setForm(prev => ({ ...prev, [e.target.name]: e.target.value }))
  }

  return (
    <div className="auth-wrap anim-fadeUp">
      <div className="card auth-card">
        <div className="text-center mb-24">
          <span className="auth-logo-icon">✈️</span>
          <h2 className="mt-8 fw-800">{isLogin ? 'Welcome back to SkyBook' : 'Create an Account'}</h2>
          <p className="text-muted text-sm mt-4">
            {isLogin ? 'Access saved cards, UPI IDs, and bookings' : 'Register to start saving payment options'}
          </p>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-16">
          {!isLogin && (
            <div className="form-group">
              <label className="form-label">Full Name</label>
              <input
                required
                type="text"
                name="name"
                className="form-input"
                placeholder="John Doe"
                value={form.name}
                onChange={handleChange}
              />
            </div>
          )}

          <div className="form-group">
            <label className="form-label">Email Address</label>
            <input
              required
              type="email"
              name="email"
              className="form-input"
              placeholder="name@example.com"
              value={form.email}
              onChange={handleChange}
            />
          </div>

          <div className="form-group">
            <label className="form-label">Password</label>
            <input
              required
              type="password"
              name="password"
              className="form-input"
              placeholder="••••••••"
              value={form.password}
              onChange={handleChange}
            />
          </div>

          {!isLogin && (
            <div className="form-group">
              <label className="form-label">Phone Number (optional)</label>
              <input
                type="tel"
                name="phone"
                className="form-input"
                placeholder="+91 99999 99999"
                value={form.phone}
                onChange={handleChange}
              />
            </div>
          )}

          <button disabled={loading} type="submit" className="btn btn-primary btn-full mt-8">
            {loading ? <span className="spinner" /> : (isLogin ? 'Sign In' : 'Sign Up')}
          </button>
        </form>

        <div className="divider" />

        <div className="text-center text-sm">
          <span className="text-muted">
            {isLogin ? "Don't have an account? " : 'Already have an account? '}
          </span>
          <button
            onClick={() => setIsLogin(!isLogin)}
            className="btn-ghost fw-600"
            style={{ color: 'var(--accent)', padding: '2px 6px', borderRadius: '4px' }}
          >
            {isLogin ? 'Create one' : 'Sign in'}
          </button>
        </div>
      </div>
    </div>
  )
}

import React, { useState, useEffect } from 'react'
import { useApp } from '../context/AppContext'
import { authAPI, paymentAPI } from '../lib/api'

export default function ProfilePage({ onNavigate }) {
  const { state, dispatch, toast } = useApp()
  const [bookings, setBookings] = useState([])
  const [upiVal, setUpiVal] = useState('')
  const [upiNick, setUpiNick] = useState('')
  const [loadingBookings, setLoadingBookings] = useState(true)
  const [activeTab, setActiveTab] = useState('bookings') // 'bookings' | 'cards' | 'upi'

  useEffect(() => {
    if (!state.user) {
      onNavigate('auth')
      return
    }
    fetchBookings()
  }, [state.user])

  const fetchBookings = async () => {
    try {
      const res = await paymentAPI.myBookings()
      setBookings(res || [])
    } catch (err) {
      toast(err.message, 'error')
    } finally {
      setLoadingBookings(false)
    }
  }

  const handleAddUpi = async (e) => {
    e.preventDefault()
    if (!upiVal.includes('@')) {
      toast('Please enter a valid VPA address format', 'error')
      return
    }
    try {
      const updatedProfile = await authAPI.addUpi({ vpa: upiVal, nickname: upiNick })
      dispatch({ type: 'UPDATE_USER', user: updatedProfile })
      setUpiVal('')
      setUpiNick('')
      toast('UPI Address registered successfully', 'success')
    } catch (err) {
      toast(err.message, 'error')
    }
  }

  const handleDeleteUpi = async (id) => {
    try {
      const updatedProfile = await authAPI.deleteUpi(id)
      dispatch({ type: 'UPDATE_USER', user: updatedProfile })
      toast('UPI deleted', 'info')
    } catch (err) {
      toast(err.message, 'error')
    }
  }

  const handleDeleteCard = async (id) => {
    try {
      const updatedProfile = await authAPI.deleteCard(id)
      dispatch({ type: 'UPDATE_USER', user: updatedProfile })
      toast('Card deleted', 'info')
    } catch (err) {
      toast(err.message, 'error')
    }
  }

  const formatDate = (dt) => {
    if (!dt) return '—'
    return new Date(dt).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })
  }

  const getActiveSeats = (b) => {
    if (!b.seats) return []
    const cancelled = b.cancelledSeats ? b.cancelledSeats.split(',').map(s => s.trim().toUpperCase()) : []
    return b.seats.split(',').map(s => s.trim()).filter(s => !cancelled.includes(s.toUpperCase()))
  }

  if (!state.user) return null

  return (
    <div className="page-wrap page-wide anim-fadeUp">
      {/* Profile header */}
      <div className="card mb-20 flex items-center gap-20" style={{ flexDirection: 'row', padding: '20px 24px' }}>
        <div className="profile-avatar" style={{ flexShrink: 0 }}>
          {state.user.name.charAt(0).toUpperCase()}
        </div>
        <div style={{ flex: 1 }}>
          <h3 className="fw-800">{state.user.name}</h3>
          <p className="text-muted text-sm">{state.user.email}</p>
          {state.user.phone && <p className="text-muted text-xs mt-4">📞 {state.user.phone}</p>}
        </div>
        <div className="badge badge-purple">{bookings.length} booking{bookings.length !== 1 ? 's' : ''}</div>
      </div>

      {/* Tab navigation */}
      <div className="payment-tabs mb-20">
        <button className={`payment-tab ${activeTab === 'bookings' ? 'active' : ''}`} onClick={() => setActiveTab('bookings')}>
          🎫 My Bookings
        </button>
        <button className={`payment-tab ${activeTab === 'cards' ? 'active' : ''}`} onClick={() => setActiveTab('cards')}>
          💳 Saved Cards
        </button>
        <button className={`payment-tab ${activeTab === 'upi' ? 'active' : ''}`} onClick={() => setActiveTab('upi')}>
          📱 Saved UPI
        </button>
      </div>

      {/* BOOKINGS HISTORY TAB */}
      {activeTab === 'bookings' && (
        <div>
          {loadingBookings ? (
            <div className="text-center py-32"><span className="spinner spinner-dark" /></div>
          ) : bookings.length === 0 ? (
            <div className="card text-center py-40 text-muted">
              <span style={{ fontSize: '3rem' }}>✈️</span>
              <p className="mt-16 fw-600">No flights booked yet.</p>
              <p className="text-xs mt-8">Search for flights to purchase your first ticket!</p>
              <button className="btn btn-primary mt-20" onClick={() => onNavigate('search')}>Find Flights</button>
            </div>
          ) : (
            <div className="flex flex-col gap-16">
              {bookings.map(b => {
                const activeSeats = getActiveSeats(b)
                const cancelledSeats = b.cancelledSeats ? b.cancelledSeats.split(',').map(s => s.trim()) : []
                const isFullyCancelled = b.status === 'CANCELLED'
                return (
                  <div key={b.id} className={`card booking-card ${isFullyCancelled ? 'booking-cancelled' : ''}`}>
                    {/* Header row */}
                    <div className="flex justify-between items-center mb-12">
                      <div className="flex items-center gap-12">
                        <span className="booking-ref-badge">{b.bookingReference}</span>
                        <span className={`badge ${isFullyCancelled ? 'badge-red' : 'badge-green'}`}>
                          {isFullyCancelled ? '❌ Cancelled' : '✅ Confirmed'}
                        </span>
                      </div>
                      <div className="text-xs text-muted">Booked {formatDate(b.bookedAt)}</div>
                    </div>

                    {/* Route row */}
                    <div className="booking-route">
                      <div className="booking-airport">
                        <div className="fw-800" style={{ fontSize: '1.4rem' }}>{b.departureCode}</div>
                        <div className="text-xs text-muted">Departure</div>
                      </div>
                      <div className="booking-route-line">
                        <span className="text-muted text-xs">{b.flightNumber}</span>
                        <div className="route-dash" />
                        <span style={{ fontSize: '1.2rem' }}>✈️</span>
                      </div>
                      <div className="booking-airport" style={{ textAlign: 'right' }}>
                        <div className="fw-800" style={{ fontSize: '1.4rem' }}>{b.arrivalCode}</div>
                        <div className="text-xs text-muted">Arrival</div>
                      </div>
                    </div>

                    {/* Details grid */}
                    <div className="booking-details-grid">
                      <div className="booking-detail">
                        <div className="booking-detail-label">Passengers</div>
                        <div className="booking-detail-val">{b.adults} Adult{b.adults > 1 ? 's' : ''}</div>
                      </div>
                      <div className="booking-detail">
                        <div className="booking-detail-label">Active Seats</div>
                        <div className="booking-detail-val">
                          {activeSeats.length > 0
                            ? activeSeats.map(s => <span key={s} className="seat-chip" style={{ fontSize: '0.7rem' }}>{s}</span>)
                            : <span className="text-muted">None</span>}
                        </div>
                      </div>
                      {cancelledSeats.length > 0 && (
                        <div className="booking-detail">
                          <div className="booking-detail-label">Cancelled Seats</div>
                          <div className="booking-detail-val">
                            {cancelledSeats.map(s => (
                              <span key={s} className="seat-chip" style={{ fontSize: '0.7rem', opacity: 0.5, textDecoration: 'line-through' }}>{s}</span>
                            ))}
                          </div>
                        </div>
                      )}
                      <div className="booking-detail">
                        <div className="booking-detail-label">Payment Method</div>
                        <div className="booking-detail-val">{b.paymentMethod || '—'}</div>
                      </div>
                      <div className="booking-detail">
                        <div className="booking-detail-label">Amount Paid</div>
                        <div className="booking-detail-val fw-800" style={{ color: 'var(--green)', fontSize: '1.1rem' }}>
                          ₹{parseFloat(b.totalAmount || 0).toLocaleString('en-IN')}
                        </div>
                      </div>
                      {b.departureTime && (
                        <div className="booking-detail">
                          <div className="booking-detail-label">Flight Departs</div>
                          <div className="booking-detail-val">{formatDate(b.departureTime)}</div>
                        </div>
                      )}
                      {b.arrivalTime && (
                        <div className="booking-detail">
                          <div className="booking-detail-label">Flight Arrives</div>
                          <div className="booking-detail-val">{formatDate(b.arrivalTime)}</div>
                        </div>
                      )}
                    </div>

                    {/* Footer */}
                    {!isFullyCancelled && (
                      <div className="mt-12 text-xs text-muted">
                        ℹ️ To cancel individual seats, go to the seat map for this flight and click your booked seats.
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          )}
        </div>
      )}

      {/* SAVED CARDS TAB */}
      {activeTab === 'cards' && (
        <div className="card">
          <h4 className="fw-800 mb-12">My Saved Cards</h4>
          {state.user.savedCards?.length === 0 ? (
            <p className="text-muted text-xs text-center py-12">No credit/debit cards saved yet</p>
          ) : (
            state.user.savedCards?.map(c => (
              <div key={c.id} className="saved-item">
                <div className="method-info">
                  <div className="fw-600 text-sm">{c.brand?.toUpperCase()} ending in {c.last4}</div>
                  <div className="text-muted text-xs">Expires {c.expMonth}/{c.expYear} {c.nickname ? `• "${c.nickname}"` : ''}</div>
                </div>
                <button className="saved-item-del" onClick={() => handleDeleteCard(c.id)}>🗑️</button>
              </div>
            ))
          )}
        </div>
      )}

      {/* SAVED UPI TAB */}
      {activeTab === 'upi' && (
        <div className="card">
          <h4 className="fw-800 mb-12">My Saved UPI IDs</h4>
          {state.user.savedUpis?.map(u => (
            <div key={u.id} className="saved-item">
              <div className="method-info">
                <div className="fw-600 text-sm">{u.vpa}</div>
                <div className="text-muted text-xs">{u.nickname || 'UPI Account'}</div>
              </div>
              <button className="saved-item-del" onClick={() => handleDeleteUpi(u.id)}>🗑️</button>
            </div>
          ))}

          <form onSubmit={handleAddUpi} className="flex flex-col gap-8 mt-16">
            <input
              required
              type="text"
              placeholder="upi-address@bank"
              className="form-input text-xs"
              value={upiVal}
              onChange={(e) => setUpiVal(e.target.value)}
            />
            <input
              type="text"
              placeholder="Alias (e.g. GPay Phone)"
              className="form-input text-xs"
              value={upiNick}
              onChange={(e) => setUpiNick(e.target.value)}
            />
            <button type="submit" className="btn btn-secondary btn-sm btn-full">
              Add UPI VPA
            </button>
          </form>
        </div>
      )}
    </div>
  )
}

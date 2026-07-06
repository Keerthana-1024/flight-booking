import React, { useState, useEffect } from 'react'
import { loadStripe } from '@stripe/stripe-js'
import { Elements, CardElement, useStripe, useElements } from '@stripe/react-stripe-js'
import { useApp } from '../context/AppContext'
import { paymentAPI, flightAPI } from '../lib/api'

// Initialize Stripe outside components to avoid reloading
let stripePromise = null

function CheckoutForm({ onNavigate, totalCost }) {
  const stripe = useStripe()
  const elements = useElements()
  const { state, dispatch, toast } = useApp()
  const { selectedFlight, selectedSeats } = state

  const [paymentMethod, setPaymentMethod] = useState('saved-card') // saved-card | card | saved-upi | upi
  const [loading, setLoading] = useState(false)
  const [successRef, setSuccessRef] = useState(null)

  // Card details parameters
  const [saveCard, setSaveCard] = useState(false)
  const [cardNickname, setCardNickname] = useState('')
  const [selectedSavedCard, setSelectedSavedCard] = useState('')

  // UPI details parameters
  const [upiVpa, setUpiVpa] = useState('')
  const [saveUpi, setSaveUpi] = useState(false)
  const [selectedSavedUpi, setSelectedSavedUpi] = useState('')

  // Setup saved methods default selection
  useEffect(() => {
    if (state.user) {
      if (state.user.savedCards?.length > 0) {
        setSelectedSavedCard(state.user.savedCards[0].paymentMethodId)
      } else {
        setPaymentMethod('card')
      }
      if (state.user.savedUpis?.length > 0) {
        setSelectedSavedUpi(state.user.savedUpis[0].vpa)
      }
    }
  }, [state.user])

  const handleCardPayment = async (e) => {
    e.preventDefault()
    if (!stripe || !elements) return
    setLoading(true)

    try {
      let pmId = selectedSavedCard

      // Create new payment method if selected
      if (paymentMethod === 'card') {
        const cardElement = elements.getElement(CardElement)
        const { error, paymentMethod: pm } = await stripe.createPaymentMethod({
          type: 'card',
          card: cardElement,
        })
        if (error) throw new Error(error.message)
        pmId = pm.id
      }

      if (!pmId) throw new Error('Please select or input a card')

      // Initiate charge
      const res = await paymentAPI.payCard({
        paymentMethodId: pmId,
        flightId: selectedFlight.id,
        flightRef: selectedFlight.flightNumber,
        seats: selectedSeats.join(','),
        adults: state.adults,
        totalAmount: totalCost,
        saveCard: paymentMethod === 'card' && saveCard,
        cardType: 'CREDIT',
        cardNickname,
        flightNumber: selectedFlight.flightNumber,
        airline: selectedFlight.airline,
        departure: selectedFlight.departure,
        arrival: selectedFlight.arrival
      })

      // Check next action (e.g. 3D secure)
      if (res.status === 'requires_action' || res.status === 'requires_source_action') {
        toast('Redirecting for card verification (3D Secure)...', 'info')
        const { error, paymentIntent } = await stripe.confirmCardPayment(res.clientSecret)
        if (error) throw new Error(error.message)

        if (paymentIntent.status === 'succeeded') {
          // Confirm booking via server
          const confirmRes = await paymentAPI.confirmBooking({
            paymentIntentId: paymentIntent.id,
            flightId: selectedFlight.id,
            seats: selectedSeats.join(','),
            totalAmount: totalCost,
            flightNumber: selectedFlight.flightNumber,
            airline: selectedFlight.airline,
            departure: selectedFlight.departure,
            arrival: selectedFlight.arrival,
            adults: state.adults
          })
          setSuccessRef(confirmRes.bookingReference)
          dispatch({ type: 'SET_SEATS', seats: [] })
          toast('Flight booked successfully!', 'success')
        }
      } else if (res.status === 'succeeded') {
        // Find booking reference in list or generated
        setSuccessRef('FLT-' + Math.random().toString(36).substring(3, 9).toUpperCase())
        dispatch({ type: 'SET_SEATS', seats: [] })
        toast('Flight booked successfully!', 'success')
      } else {
        throw new Error('Payment status: ' + res.status)
      }
    } catch (err) {
      toast(err.message, 'error')
    } finally {
      setLoading(false)
    }
  }

  const handleUpiPayment = async (e) => {
    e.preventDefault()
    setLoading(true)

    try {
      const vpa = paymentMethod === 'saved-upi' ? selectedSavedUpi : upiVpa
      if (!vpa) throw new Error('Please select or input a valid UPI VPA')

      const res = await paymentAPI.payUpi({
        flightId: selectedFlight.id,
        flightRef: selectedFlight.flightNumber,
        seats: selectedSeats.join(','),
        upiVpa: vpa,
        totalAmount: totalCost,
        saveUpi: paymentMethod === 'upi' && saveUpi,
        upiNickname: 'UPI-' + vpa.split('@')[0],
        flightNumber: selectedFlight.flightNumber,
        airline: selectedFlight.airline,
        departure: selectedFlight.departure,
        arrival: selectedFlight.arrival,
        adults: state.adults
      })

      // Simulate a quick confirmation delay for mock/test UPI
      toast('Processing UPI payment transaction...', 'info')
      setTimeout(async () => {
        try {
          const confirmRes = await paymentAPI.confirmBooking({
            paymentIntentId: res.paymentIntentId,
            flightId: selectedFlight.id,
            seats: selectedSeats.join(','),
            totalAmount: totalCost,
            flightNumber: selectedFlight.flightNumber,
            airline: selectedFlight.airline,
            departure: selectedFlight.departure,
            arrival: selectedFlight.arrival,
            adults: state.adults
          })
          setSuccessRef(confirmRes.bookingReference)
          dispatch({ type: 'SET_SEATS', seats: [] })
          toast('UPI payment confirmed! Flight booked.', 'success')
        } catch (err) {
          toast(err.message, 'error')
        } finally {
          setLoading(false)
        }
      }, 2500)

    } catch (err) {
      toast(err.message, 'error')
      setLoading(false)
    }
  }

  if (successRef) {
    return (
      <div className="card text-center anim-scaleIn">
        <span style={{ fontSize: '4rem' }}>🎉</span>
        <h2 className="fw-800 mt-12">Booking Confirmed!</h2>
        <p className="text-muted mt-8">Your ticket reference has been generated. An email confirmation has been sent.</p>
        <div className="modal-ref">{successRef}</div>
        <div className="flex gap-12 mt-24">
          <button className="btn btn-secondary btn-full" onClick={() => onNavigate('search')}>
            Book Another Flight
          </button>
          <button className="btn btn-primary btn-full" onClick={() => onNavigate('profile')}>
            View My Bookings
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="card">
      <h3 className="fw-800 mb-16">Select Payment Method</h3>

      <div className="payment-tabs">
        {state.user?.savedCards?.length > 0 && (
          <button
            type="button"
            className={`payment-tab ${paymentMethod === 'saved-card' ? 'active' : ''}`}
            onClick={() => setPaymentMethod('saved-card')}
          >
            💳 Saved Cards
          </button>
        )}
        <button
          type="button"
          className={`payment-tab ${paymentMethod === 'card' ? 'active' : ''}`}
          onClick={() => setPaymentMethod('card')}
        >
          💳 New Credit / Debit Card
        </button>
        {state.user?.savedUpis?.length > 0 && (
          <button
            type="button"
            className={`payment-tab ${paymentMethod === 'saved-upi' ? 'active' : ''}`}
            onClick={() => setPaymentMethod('saved-upi')}
          >
            📱 Saved UPI IDs
          </button>
        )}
        <button
          type="button"
          className={`payment-tab ${paymentMethod === 'upi' ? 'active' : ''}`}
          onClick={() => setPaymentMethod('upi')}
        >
          📱 UPI NetBanking
        </button>
      </div>

      {/* Forms based on tab selection */}
      {paymentMethod === 'saved-card' && (
        <form onSubmit={handleCardPayment}>
          <div className="mb-16">
            {state.user?.savedCards.map(c => (
              <div
                key={c.id}
                className={`saved-method ${selectedSavedCard === c.paymentMethodId ? 'active' : ''}`}
                onClick={() => setSelectedSavedCard(c.paymentMethodId)}
              >
                <div className={`method-radio ${selectedSavedCard === c.paymentMethodId ? 'checked' : ''}`} />
                <div className="method-info">
                  <div className="method-title">{c.brand.toUpperCase()} ending in {c.last4}</div>
                  <div className="method-sub">Expires {c.expMonth}/{c.expYear} {c.nickname ? `• "${c.nickname}"` : ''}</div>
                </div>
              </div>
            ))}
          </div>
          <button disabled={loading} type="submit" className="btn btn-success btn-full mt-16">
            {loading ? <span className="spinner" /> : `Pay ₹${totalCost.toLocaleString('en-IN')}`}
          </button>
        </form>
      )}

      {paymentMethod === 'card' && (
        <form onSubmit={handleCardPayment}>
          <div className="form-group mb-16">
            <label className="form-label">Card Credentials</label>
            <div className="stripe-field">
              <CardElement
                options={{
                  style: {
                    base: {
                      color: '#ffffff',
                      fontFamily: 'Inter, sans-serif',
                      fontSmoothing: 'antialiased',
                      fontSize: '15px',
                      '::placeholder': { color: '#68728a' },
                    },
                    invalid: { color: '#ff4d4d', iconColor: '#ff4d4d' },
                  },
                }}
              />
            </div>
          </div>

          <div className="form-group mb-12">
            <label className="form-label">Save Card Label (optional)</label>
            <input
              type="text"
              placeholder="e.g. My Personal Card"
              className="form-input"
              value={cardNickname}
              onChange={(e) => setCardNickname(e.target.value)}
            />
          </div>

          <label className="check-row mb-16">
            <input
              type="checkbox"
              checked={saveCard}
              onChange={(e) => setSaveCard(e.target.checked)}
            />
            <span className="text-sm text-muted">Save card payment information for future use</span>
          </label>

          <button disabled={loading || !stripe} type="submit" className="btn btn-success btn-full">
            {loading ? <span className="spinner" /> : `Pay ₹${totalCost.toLocaleString('en-IN')}`}
          </button>
        </form>
      )}

      {paymentMethod === 'saved-upi' && (
        <form onSubmit={handleUpiPayment}>
          <div className="mb-16">
            {state.user?.savedUpis.map(u => (
              <div
                key={u.id}
                className={`saved-method ${selectedSavedUpi === u.vpa ? 'active' : ''}`}
                onClick={() => setSelectedSavedUpi(u.vpa)}
              >
                <div className={`method-radio ${selectedSavedUpi === u.vpa ? 'checked' : ''}`} />
                <div className="method-info">
                  <div className="method-title">{u.vpa}</div>
                  <div className="method-sub">{u.nickname || 'Personal UPI'}</div>
                </div>
              </div>
            ))}
          </div>
          <button disabled={loading} type="submit" className="btn btn-success btn-full mt-16">
            {loading ? <span className="spinner" /> : `Pay ₹${totalCost.toLocaleString('en-IN')}`}
          </button>
        </form>
      )}

      {paymentMethod === 'upi' && (
        <form onSubmit={handleUpiPayment}>
          <div className="form-group mb-16">
            <label className="form-label">UPI ID / VPA</label>
            <input
              type="text"
              placeholder="username@bank"
              required
              className="form-input"
              value={upiVpa}
              onChange={(e) => setUpiVpa(e.target.value)}
            />
          </div>

          <label className="check-row mb-16">
            <input
              type="checkbox"
              checked={saveUpi}
              onChange={(e) => setSaveUpi(e.target.checked)}
            />
            <span className="text-sm text-muted">Save UPI Address for future checkout</span>
          </label>

          <button disabled={loading} type="submit" className="btn btn-success btn-full">
            {loading ? <span className="spinner" /> : `Pay ₹${totalCost.toLocaleString('en-IN')}`}
          </button>
        </form>
      )}
    </div>
  )
}

export default function PaymentPage({ onNavigate }) {
  const { state, dispatch, toast } = useApp()
  const { selectedFlight, selectedSeats } = state
  const [stripeConfig, setStripeConfig] = useState(null)

  useEffect(() => {
    if (!selectedFlight || selectedSeats.length === 0) {
      onNavigate('search')
    }
  }, [selectedFlight, selectedSeats])

  useEffect(() => {
    async function loadConfig() {
      try {
        const conf = await paymentAPI.config()
        setStripeConfig(conf)
        if (conf.stripeConfigured && conf.stripePublishableKey) {
          stripePromise = loadStripe(conf.stripePublishableKey)
        }
      } catch (err) {
        toast('Failed to load payment gateways config', 'error')
      }
    }
    loadConfig()
  }, [])

  const calculateTotalCost = () => {
    if (!selectedFlight) return 0
    return selectedFlight.price * state.adults // simplified sum here
  }

  if (!selectedFlight) return null

  return (
    <div className="page-wrap anim-fadeUp">
      <div className="flex justify-between items-center mb-16">
        <button className="btn btn-secondary btn-sm" onClick={() => onNavigate('seats')}>
          ← Back to Seat Selection
        </button>
        <h4 className="fw-700">Payment Checkout</h4>
      </div>

      <div className="grid-3">
        {/* Left Col: Forms wrapper */}
        <div style={{ gridColumn: 'span 2' }}>
          {stripeConfig?.stripeConfigured ? (
            <Elements stripe={stripePromise}>
              <CheckoutForm onNavigate={onNavigate} totalCost={calculateTotalCost()} />
            </Elements>
          ) : (
            <div className="card text-center py-24">
              <p className="text-muted">Stripe keys are missing from `.env` configuration file.</p>
              <p className="text-xs text-muted mt-8">Please copy actual keys or update backend `stripe.secret.key` to mock transaction systems.</p>
            </div>
          )}
        </div>

        {/* Right Col: Side Detail Panel */}
        <div className="flex flex-col gap-16">
          <div className="card card-glass">
            <h4 className="fw-800 mb-12">Flight Details</h4>
            <div className="flex flex-col gap-8 text-sm">
              <div className="flex justify-between">
                <span className="text-muted">Airline</span>
                <span className="fw-600">{selectedFlight.airlineName}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted">From</span>
                <span className="fw-600">{selectedFlight.departureCity} ({selectedFlight.departure})</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted">To</span>
                <span className="fw-600">{selectedFlight.arrivalCity} ({selectedFlight.arrival})</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted">Seats Selected</span>
                <span className="fw-600">{selectedSeats.join(', ')}</span>
              </div>
            </div>
            <div className="divider" style={{ margin: '12px 0' }} />
            <div className="flex justify-between fw-800">
              <span>Amount due</span>
              <span className="price-amount" style={{ fontSize: '1.2rem' }}>₹{calculateTotalCost().toLocaleString('en-IN')}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

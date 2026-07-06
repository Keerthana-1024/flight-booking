import React, { useState, useEffect } from 'react'
import { useApp } from '../context/AppContext'
import { flightAPI, paymentAPI } from '../lib/api'

export default function SeatSelectionPage({ onNavigate }) {
  const { state, dispatch, toast } = useApp()
  const { selectedFlight, selectedSeats } = state
  const [rows, setRows] = useState({})
  const [loading, setLoading] = useState(true)
  const [countdown, setCountdown] = useState(null)
  const [cancelModal, setCancelModal] = useState(null) // { seat, bookingId, bookingRef }
  const [cancelling, setCancelling] = useState(false)

  // Redirect if no flight selected
  useEffect(() => {
    if (!selectedFlight) {
      onNavigate('search')
    }
  }, [selectedFlight])

  const fetchSeatMap = async () => {
    if (!selectedFlight) return
    try {
      const res = await flightAPI.seatmap(selectedFlight.id)
      setRows(res.rows || {})
    } catch (err) {
      toast(err.message, 'error')
    } finally {
      setLoading(false)
    }
  }

  // Poll for seat lock status updates every 5 seconds
  useEffect(() => {
    fetchSeatMap()
    const interval = setInterval(fetchSeatMap, 5000)
    return () => clearInterval(interval)
  }, [selectedFlight])

  // Countdown timer for locks
  useEffect(() => {
    if (selectedSeats.length === 0) {
      setCountdown(null)
      dispatch({ type: 'SET_LOCK_EXPIRY', expiry: null })
      return
    }

    // Default 3 min lock (180s) when seat is selected
    const end = Date.now() + 180 * 1000
    dispatch({ type: 'SET_LOCK_EXPIRY', expiry: end })

    const timer = setInterval(() => {
      const remaining = Math.max(0, Math.round((end - Date.now()) / 1000))
      setCountdown(remaining)
      if (remaining <= 0) {
        clearInterval(timer)
        toast('Seat lock session expired. Please select seats again.', 'error')
        dispatch({ type: 'SET_SEATS', seats: [] })
      }
    }, 1000)

    return () => clearInterval(timer)
  }, [selectedSeats.length])

  const handleSeatClick = async (seat) => {
    // Handle clicking own booked seat — show cancel modal
    if (seat.status === 'BOOKED_BY_YOU') {
      setCancelModal({
        seat: seat.seatNumber,
        bookingRef: seat.bookingReference,
        // We need bookingId — fetch from bookings list
        bookingId: null,
        bookingRef: seat.bookingReference,
      })
      // Fetch bookingId from history
      try {
        const bookings = await paymentAPI.myBookings()
        const match = bookings.find(b => b.bookingReference === seat.bookingReference)
        if (match) {
          setCancelModal({ seat: seat.seatNumber, bookingId: match.id, bookingRef: seat.bookingReference })
        }
      } catch (e) {}
      return
    }

    const isSelected = selectedSeats.includes(seat.seatNumber)

    if (isSelected) {
      // Unlock seat
      try {
        await flightAPI.unlockSeat({ flightId: selectedFlight.id, seatNumber: seat.seatNumber })
        dispatch({ type: 'SET_SEATS', seats: selectedSeats.filter(s => s !== seat.seatNumber) })
        fetchSeatMap()
      } catch (err) {
        toast(err.message, 'error')
      }
    } else {
      // Verify passenger count
      if (selectedSeats.length >= state.adults) {
        toast(`You can only select up to ${state.adults} seat(s) for this booking.`, 'info')
        return
      }
      // Lock seat
      try {
        await flightAPI.lockSeat({ flightId: selectedFlight.id, seatNumber: seat.seatNumber })
        dispatch({ type: 'SET_SEATS', seats: [...selectedSeats, seat.seatNumber] })
        fetchSeatMap()
      } catch (err) {
        toast(err.message, 'error')
      }
    }
  }

  const handleCancelSeat = async () => {
    if (!cancelModal?.bookingId) {
      toast('Could not find booking details.', 'error')
      return
    }
    setCancelling(true)
    try {
      const res = await paymentAPI.cancelSeat(cancelModal.bookingId, cancelModal.seat)
      toast(`Seat ${cancelModal.seat} cancelled. Booking: ${res.bookingStatus === 'CANCELLED' ? 'Fully Cancelled' : 'Partially Active'}`, 'success')
      setCancelModal(null)
      fetchSeatMap()
    } catch (err) {
      toast(err.message, 'error')
    } finally {
      setCancelling(false)
    }
  }

  const formatTime = (secs) => {
    if (secs === null || secs <= 0) return '00:00'
    const m = Math.floor(secs / 60).toString().padStart(2, '0')
    const s = (secs % 60).toString().padStart(2, '0')
    return `${m}:${s}`
  }

  const calculateTotalCost = () => {
    if (!selectedFlight) return 0
    let seatCharge = 0
    selectedSeats.forEach(s => {
      Object.values(rows).flat().forEach(seat => {
        if (seat && seat.seatNumber === s && seat.extraCost) {
          seatCharge += parseInt(seat.extraCost.replace(/[^\d]/g, ''))
        }
      })
    })
    return (selectedFlight.price * state.adults) + seatCharge
  }

  if (loading || !selectedFlight) {
    return (
      <div className="page-wrap text-center py-32">
        <span className="spinner spinner-dark" />
        <p className="text-muted mt-8">Loading aircraft seat arrangement...</p>
      </div>
    )
  }

  return (
    <div className="page-wrap anim-fadeUp">
      <div className="flex justify-between items-center mb-16">
        <button className="btn btn-secondary btn-sm" onClick={() => onNavigate('search')}>
          ← Back to Results
        </button>
        <h4 className="fw-700">Flight: {selectedFlight.flightNumber}</h4>
      </div>

      {/* Cancel seat modal */}
      {cancelModal && (
        <div className="modal-overlay" onClick={() => setCancelModal(null)}>
          <div className="modal-box" onClick={e => e.stopPropagation()}>
            <h3 className="fw-800 mb-12">✈️ Your Booked Seat</h3>
            <p className="text-muted mb-8">
              You already hold <strong>Seat {cancelModal.seat}</strong> on this flight.
            </p>
            <p className="text-muted mb-16">
              Booking Ref: <code className="modal-ref" style={{ fontSize: '0.85rem' }}>{cancelModal.bookingRef}</code>
            </p>
            <p className="text-sm text-muted mb-20">
              Do you want to cancel this individual seat? The seat will become available for others after cancellation.
            </p>
            <div className="flex gap-12">
              <button className="btn btn-secondary btn-full" onClick={() => setCancelModal(null)}>
                Keep My Seat
              </button>
              <button
                className="btn btn-danger btn-full"
                onClick={handleCancelSeat}
                disabled={cancelling}
              >
                {cancelling ? <span className="spinner" /> : '🗑 Cancel This Seat'}
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="grid-3">
        {/* Left Side: Seat Map Grid */}
        <div className="card grid-2-col-span-2" style={{ gridColumn: 'span 2' }}>
          <h3 className="fw-800 mb-16">Select Seat Map</h3>

          {/* Seat Map Legend */}
          <div className="seat-legend">
            <div className="legend-item"><div className="legend-dot seat-AVAILABLE" /> Available</div>
            <div className="legend-item"><div className="legend-dot seat-OCCUPIED" /> Booked</div>
            <div className="legend-item"><div className="legend-dot seat-BLOCKED" /> Blocked</div>
            <div className="legend-item"><div className="legend-dot seat-LOCKED" /> Locked (Other User)</div>
            <div className="legend-item"><div className="legend-dot seat-SELECTED" /> Selected</div>
            <div className="legend-item"><div className="legend-dot seat-BOOKED_BY_YOU" /> Your Booking</div>
          </div>

          <div className="seat-map-wrap">
            <div className="text-center text-xs text-muted mb-12">FRONT OF AIRCRAFT</div>
            {Object.entries(rows).map(([rowNum, seatsInRow]) => {
              const isBusiness = seatsInRow[0]?.cabin === 'BUSINESS'
              return (
                <React.Fragment key={rowNum}>
                  {rowNum === '1' && <div className="cabin-label">Business Class (₹1,500 extra)</div>}
                  {rowNum === '5' && <div className="divider" />}
                  {rowNum === '5' && <div className="cabin-label">Economy Class</div>}

                  <div className="seat-row">
                    <span className="seat-row-num">{rowNum}</span>
                    {seatsInRow.map((seat, idx) => (
                      <React.Fragment key={seat.seatNumber}>
                        {/* Empty spacer for aisle */}
                        {((isBusiness && idx === 2) || (!isBusiness && idx === 3)) && (
                          <div className="seat-aisle" />
                        )}

                        <button
                          disabled={seat.status === 'OCCUPIED' || seat.status === 'BLOCKED' || seat.status === 'LOCKED' || seat.status === 'BOOKED'}
                          className={`seat-btn seat-${seat.status}`}
                          title={
                            seat.status === 'BOOKED_BY_YOU'
                              ? `${seat.seatNumber} — Your booking (${seat.bookingReference}). Click to cancel.`
                              : `${seat.seatNumber} - ${seat.cabin} ${seat.extraCost ? `(${seat.extraCost})` : ''}`
                          }
                          onClick={() => handleSeatClick(seat)}
                        >
                          {seat.seatNumber}
                        </button>
                      </React.Fragment>
                    ))}
                  </div>
                </React.Fragment>
              )
            })}
            <div className="text-center text-xs text-muted mt-12">REAR OF AIRCRAFT</div>
          </div>
        </div>

        {/* Right Side: Booking Summary */}
        <div className="flex flex-col gap-16">
          <div className="card">
            <h3 className="fw-800 mb-16">Booking Summary</h3>
            <div className="summary-table">
              <div className="summary-row">
                <div className="summary-key">Flight</div>
                <div className="summary-val">{selectedFlight.departure} → {selectedFlight.arrival}</div>
              </div>
              <div className="summary-row">
                <div className="summary-key">Date</div>
                <div className="summary-val">{selectedFlight.flightDate}</div>
              </div>
              <div className="summary-row">
                <div className="summary-key">Passengers</div>
                <div className="summary-val">{state.adults} Adult(s)</div>
              </div>
              <div className="summary-row">
                <div className="summary-key">Base Ticket Price</div>
                <div className="summary-val">₹{(selectedFlight.price * state.adults).toLocaleString('en-IN')}</div>
              </div>

              <div className="summary-row">
                <div className="summary-key">Selected Seat(s)</div>
                <div className="summary-val">
                  {selectedSeats.length === 0 ? (
                    <span className="text-muted">None Selected</span>
                  ) : (
                    selectedSeats.map(s => <span key={s} className="seat-chip">{s}</span>)
                  )}
                </div>
              </div>

              <div className="summary-row total">
                <div className="summary-key">Grand Total</div>
                <div className="summary-val">₹{calculateTotalCost().toLocaleString('en-IN')}</div>
              </div>
            </div>

            {/* Lock countdown timer */}
            {countdown !== null && (
              <div className="lock-timer">
                <span>⏱️ Seat Lock Timer:</span>
                <span className={`lock-time-val ${countdown < 30 ? 'urgent' : ''}`}>
                  {formatTime(countdown)}
                </span>
              </div>
            )}

            <button
              disabled={selectedSeats.length < state.adults}
              className="btn btn-primary btn-full mt-16"
              onClick={() => onNavigate('payment')}
            >
              Proceed to Payment
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

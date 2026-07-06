import React, { useState, useEffect, useRef } from 'react'
import { useApp } from '../context/AppContext'
import { flightAPI } from '../lib/api'

export default function SearchPage({ onNavigate }) {
  const { state, dispatch, toast } = useApp()
  const [loading, setLoading] = useState(false)
  const [searched, setSearched] = useState(false)

  // Form states initialized from context to retain inputs
  const [form, setForm] = useState({
    origin: state.origin || 'DEL',
    originCity: state.originCity || 'Delhi',
    destination: state.destination || 'BOM',
    destinationCity: state.destinationCity || 'Mumbai',
    dateStart: state.dateStart || new Date().toISOString().split('T')[0],
    dateEnd: state.dateEnd || '',
    adults: state.adults || 1,
    maxDays: state.maxDays || 0,
    sortBy: state.sortBy || 'price'
  })

  // Autocomplete UI states
  const [originKeyword, setOriginKeyword] = useState('')
  const [destKeyword, setDestKeyword] = useState('')
  const [originSuggestions, setOriginSuggestions] = useState([])
  const [destSuggestions, setDestSuggestions] = useState([])
  const [showOriginAuto, setShowOriginAuto] = useState(false)
  const [showDestAuto, setShowDestAuto] = useState(false)

  const origRef = useRef()
  const destRef = useRef()

  useEffect(() => {
    function handleClickOutside(e) {
      if (origRef.current && !origRef.current.contains(e.target)) setShowOriginAuto(false)
      if (destRef.current && !destRef.current.contains(e.target)) setShowDestAuto(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  // Fetch origin suggestions
  useEffect(() => {
    if (!originKeyword.trim()) {
      setOriginSuggestions([])
      return
    }
    const delayDebounce = setTimeout(async () => {
      try {
        const res = await flightAPI.airports(originKeyword)
        setOriginSuggestions(res.airports || [])
      } catch (err) {}
    }, 200)
    return () => clearTimeout(delayDebounce)
  }, [originKeyword])

  // Fetch dest suggestions
  useEffect(() => {
    if (!destKeyword.trim()) {
      setDestSuggestions([])
      return
    }
    const delayDebounce = setTimeout(async () => {
      try {
        const res = await flightAPI.airports(destKeyword)
        setDestSuggestions(res.airports || [])
      } catch (err) {}
    }, 200)
    return () => clearTimeout(delayDebounce)
  }, [destKeyword])

  const handleSearch = async (e) => {
    if (e) e.preventDefault()
    setLoading(true)
    setSearched(true)

    // Save search inputs to context
    dispatch({ type: 'SET_SEARCH', payload: form })

    try {
      const results = await flightAPI.search({
        origin: form.origin,
        destination: form.destination,
        dateStart: form.dateStart,
        dateEnd: form.dateEnd || form.dateStart,
        adults: form.adults,
        maxDays: form.maxDays,
        sortBy: form.sortBy
      })
      dispatch({ type: 'SET_FLIGHTS', flights: results.flights || [] })
    } catch (err) {
      toast(err.message, 'error')
    } finally {
      setLoading(false)
    }
  }

  // Auto-search if results exist already or on sort change
  const handleSortChange = (newSort) => {
    setForm(prev => {
      const next = { ...prev, sortBy: newSort }
      // Trigger search with updated state
      dispatch({ type: 'SET_SEARCH', payload: next })
      return next
    })
  }

  useEffect(() => {
    if (searched) {
      handleSearch()
    }
  }, [form.sortBy])

  const handleBook = (flight) => {
    dispatch({ type: 'SELECT_FLIGHT', flight })
    if (!state.user) {
      toast('Please login to continue with booking.', 'info')
      onNavigate('auth')
    } else {
      onNavigate('seats')
    }
  }

  const formatDuration = (mins) => {
    const hrs = Math.floor(mins / 60)
    const m = mins % 60
    return hrs > 0 ? `${hrs}h ${m}m` : `${m}m`
  }

  const formatTime = (isoStr) => {
    return new Date(isoStr).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }

  return (
    <div className="page-wrap page-wide anim-fadeUp">
      {/* Search Bar Panel */}
      <div className="card mb-24">
        <h3 className="fw-800 mb-16 text-center" style={{ fontSize: '1.4rem' }}>Find Flights Around the World</h3>
        <form onSubmit={handleSearch} className="flex flex-col gap-16">
          <div className="grid-4">
            {/* Origin Autocomplete */}
            <div className="form-group autocomplete-wrap" ref={origRef}>
              <label className="form-label">From</label>
              <input
                type="text"
                className="form-input"
                placeholder="Search Airport/City"
                value={showOriginAuto ? originKeyword : `${form.originCity} (${form.origin})`}
                onFocus={() => {
                  setOriginKeyword('')
                  setShowOriginAuto(true)
                }}
                onChange={(e) => setOriginKeyword(e.target.value)}
              />
              {showOriginAuto && originSuggestions.length > 0 && (
                <div className="autocomplete-list">
                  {originSuggestions.map(a => (
                    <div
                      key={a.iata}
                      className="autocomplete-item"
                      onClick={() => {
                        setForm(p => ({ ...p, origin: a.iata, originCity: a.city }))
                        setShowOriginAuto(false)
                      }}
                    >
                      <div className="autocomplete-iata">{a.iata} - {a.city}</div>
                      <div className="autocomplete-name">{a.name}</div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Destination Autocomplete */}
            <div className="form-group autocomplete-wrap" ref={destRef}>
              <label className="form-label">To</label>
              <input
                type="text"
                className="form-input"
                placeholder="Search Airport/City"
                value={showDestAuto ? destKeyword : `${form.destinationCity} (${form.destination})`}
                onFocus={() => {
                  setDestKeyword('')
                  setShowDestAuto(true)
                }}
                onChange={(e) => setDestKeyword(e.target.value)}
              />
              {showDestAuto && destSuggestions.length > 0 && (
                <div className="autocomplete-list">
                  {destSuggestions.map(a => (
                    <div
                      key={a.iata}
                      className="autocomplete-item"
                      onClick={() => {
                        setForm(p => ({ ...p, destination: a.iata, destinationCity: a.city }))
                        setShowDestAuto(false)
                      }}
                    >
                      <div className="autocomplete-iata">{a.iata} - {a.city}</div>
                      <div className="autocomplete-name">{a.name}</div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Start Date */}
            <div className="form-group">
              <label className="form-label">Departure Date</label>
              <input
                type="date"
                required
                className="form-input"
                value={form.dateStart}
                onChange={(e) => setForm(p => ({ ...p, dateStart: e.target.value }))}
              />
            </div>

            {/* End Date (Whole Week Range) */}
            <div className="form-group">
              <label className="form-label">Return / End Date (Optional Range)</label>
              <input
                type="date"
                className="form-input"
                value={form.dateEnd}
                min={form.dateStart}
                onChange={(e) => setForm(p => ({ ...p, dateEnd: e.target.value }))}
              />
            </div>
          </div>

          <div className="grid-3 items-center">
            {/* Passengers */}
            <div className="form-group">
              <label className="form-label">Adult Passengers</label>
              <select
                className="form-input"
                value={form.adults}
                onChange={(e) => setForm(p => ({ ...p, adults: parseInt(e.target.value) }))}
              >
                {[1, 2, 3, 4, 5].map(n => <option key={n} value={n}>{n}</option>)}
              </select>
            </div>

            {/* Max Duration Days */}
            <div className="form-group">
              <label className="form-label">Max Flight Duration (Days)</label>
              <select
                className="form-input"
                value={form.maxDays}
                onChange={(e) => setForm(p => ({ ...p, maxDays: parseInt(e.target.value) }))}
              >
                <option value={0}>Any Duration</option>
                <option value={1}>Up to 1 Day (24 hrs)</option>
                <option value={2}>Up to 2 Days (48 hrs)</option>
                <option value={3}>Up to 3 Days (72 hrs)</option>
                <option value={5}>Up to 5 Days</option>
                <option value={7}>Up to 7 Days</option>
              </select>
            </div>

            {/* Submit */}
            <div className="form-group" style={{ justifyContent: 'flex-end', height: '100%' }}>
              <button disabled={loading} type="submit" className="btn btn-primary btn-full" style={{ height: '45px' }}>
                {loading ? <span className="spinner" /> : 'Search Flights ✈️'}
              </button>
            </div>
          </div>
        </form>
      </div>

      {/* Sorting & Filter controls */}
      {searched && (
        <div className="flex items-center justify-between mb-16 flex-wrap gap-12">
          <div>
            <h4 className="fw-700">{state.flights.length} flights found</h4>
            <p className="text-muted text-xs">For {form.adults} adult(s) • Showing direct & transit results</p>
          </div>
          <div className="sort-pills">
            <span className="form-label text-muted" style={{ alignSelf: 'center', marginRight: '4px' }}>Sort By</span>
            <button
              className={`sort-pill ${form.sortBy === 'price' ? 'active' : ''}`}
              onClick={() => handleSortChange('price')}
            >
              💵 Price
            </button>
            <button
              className={`sort-pill ${form.sortBy === 'duration' ? 'active' : ''}`}
              onClick={() => handleSortChange('duration')}
            >
              ⏱️ Duration
            </button>
            <button
              className={`sort-pill ${form.sortBy === 'layover' ? 'active' : ''}`}
              onClick={() => handleSortChange('layover')}
            >
              🛑 Transit/Layover
            </button>
          </div>
        </div>
      )}

      {/* Flight Cards List */}
      <div className="flex flex-col gap-12">
        {loading ? (
          [1, 2, 3].map(n => (
            <div key={n} className="skeleton" style={{ height: '100px', width: '100%', borderRadius: '14px' }} />
          ))
        ) : searched && state.flights.length === 0 ? (
          <div className="card text-center text-muted py-32">
            No flights match your search query. Try broadening your dates or duration limits.
          </div>
        ) : (
          state.flights.map(f => (
            <div key={f.id} className="flight-card anim-fadeUp" onClick={() => handleBook(f)}>
              <div className="flex items-center gap-16" style={{ flex: '1.2' }}>
                <span className="airline-badge">{f.airline}</span>
                <div>
                  <h4 className="fw-700">{f.airlineName}</h4>
                  <span className="text-muted text-xs">{f.flightNumber} • {f.flightDate}</span>
                </div>
              </div>

              {/* Route */}
              <div className="flex items-center gap-16" style={{ flex: '3' }}>
                <div className="text-right">
                  <div className="route-iata">{formatTime(f.departureTime)}</div>
                  <div className="route-city">{f.departureCity} ({f.departure})</div>
                </div>

                <div className="flex-col items-center justify-between" style={{ flex: 1, display: 'flex' }}>
                  <span className="text-muted text-xs">{formatDuration(f.durationMinutes)}</span>
                  <div className="route-line">
                    <div className="route-dot" />
                    <div className="route-dash" />
                    <div className="route-dot" />
                  </div>
                  <span className="badge badge-purple" style={{ fontSize: '0.62rem', padding: '1px 6px' }}>
                    {f.stops === 0 ? 'Non-stop' : `${f.stops} stop(s) ${f.layoverMinutes > 0 ? `(${formatDuration(f.layoverMinutes)} layover)` : ''}`}
                  </span>
                </div>

                <div>
                  <div className="route-iata">{formatTime(f.arrivalTime)}</div>
                  <div className="route-city">{f.arrivalCity} ({f.arrival})</div>
                </div>
              </div>

              {/* Price & Action */}
              <div className="text-right" style={{ flex: '1' }}>
                <div className="price-amount">₹{f.price.toLocaleString('en-IN')}</div>
                <div className="text-muted text-xs mb-8">total price</div>
                <button className="btn btn-primary btn-sm">Book Now</button>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  )
}

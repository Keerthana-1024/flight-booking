import React, { useState } from 'react'
import { AppProvider } from './context/AppContext'
import Navbar from './components/Navbar'
import ToastContainer from './components/ToastContainer'

// Pages
import SearchPage from './pages/SearchPage'
import SeatSelectionPage from './pages/SeatSelectionPage'
import PaymentPage from './pages/PaymentPage'
import ProfilePage from './pages/ProfilePage'
import AuthPage from './pages/AuthPage'

function MainLayout() {
  const [currentPage, setCurrentPage] = useState('search') // search | seats | payment | profile | auth

  const navigate = (page) => {
    setCurrentPage(page)
  }

  return (
    <>
      <Navbar onNavigate={navigate} />

      {/* Decorative Orbs */}
      <div className="hero-bg">
        <div className="orb orb-1" />
        <div className="orb orb-2" />
        <div className="orb orb-3" />
      </div>

      <main style={{ position: 'relative', zIndex: 1 }}>
        {currentPage === 'search' && <SearchPage onNavigate={navigate} />}
        {currentPage === 'seats' && <SeatSelectionPage onNavigate={navigate} />}
        {currentPage === 'payment' && <PaymentPage onNavigate={navigate} />}
        {currentPage === 'profile' && <ProfilePage onNavigate={navigate} />}
        {currentPage === 'auth' && <AuthPage onNavigate={navigate} />}
      </main>

      <ToastContainer />
    </>
  )
}

export default function App() {
  return (
    <AppProvider>
      <MainLayout />
    </AppProvider>
  )
}

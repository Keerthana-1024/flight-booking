# ✈️ SkyBook — Flight Booking System

A full-stack flight booking application built with **Java 17 + Spring Boot 3** (backend) and **React 18 + Vite** (frontend). Supports real-time seat locking, Stripe card/UPI payments, booking history, and partial seat cancellations.

---

## Architecture

![System Architecture](docs/architecture.png)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          CLIENT (Browser)                               │
│                     React 18 SPA  ·  Vite  ·  Stripe.js                │
└───────────────────────────┬─────────────────────────────────────────────┘
                            │  HTTP / REST  (JSON)
                            ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    SPRING BOOT 3  ·  Java 17                            │
│                                                                         │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────────────┐    │
│  │  AuthController  │  │ FlightController │  │  PaymentController   │    │
│  │   (JWT / BCrypt) │  │  (Seats / Lock)  │  │  (Stripe Card / UPI) │    │
│  └────────┬─────────┘  └────────┬─────────┘  └──────────┬───────────┘    │
│           │                    │                        │                │
│  ┌────────▼────────────────────▼────────────────────────▼───────────┐   │
│  │                        Service Layer                              │   │
│  │  FlightService  ·  SeatLockService  ·  StripeService              │   │
│  └───────────────────────┬────────────────────────┬──────────────────┘   │
│                          │  JPA / Hibernate        │  Lettuce (TLS)      │
│  ┌────────────────────────▼──────────┐  ┌──────────▼────────────────┐   │
│  │   Spring Data JPA   (Hibernate)   │  │   Spring Data Redis        │   │
│  └────────────────────────┬──────────┘  └──────────┬────────────────┘   │
└───────────────────────────┼──────────────────────────┼────────────────────┘
                            │  JDBC / SSL              │  rediss:// TLS
                            ▼                          ▼
                 ┌─────────────────┐        ┌──────────────────┐
                 │  Supabase       │        │  Upstash Redis   │
                 │  PostgreSQL     │        │  (Seat Locks)    │
                 └─────────────────┘        └──────────────────┘

                             External APIs
                 ┌──────────────────────────────────────┐
                 │  Stripe API  ·  Amadeus Flight API    │
                 └──────────────────────────────────────┘
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.2, Spring Security, Spring Data JPA |
| Frontend | React 18, Vite, Stripe.js Elements |
| Database | Supabase (PostgreSQL), managed via Hibernate auto-DDL |
| Cache / Locks | Upstash Redis (TLS, via Lettuce) |
| Payments | Stripe (Card, UPI, Payment Intents) |
| Auth | JWT (JJWT 0.11), BCrypt |
| Flights | Mock engine (deterministic seeded RNG) + Amadeus Java SDK |
| Deployment | Render (backend), bundled React served as static files |

---

## Features

- 🔐 **JWT Auth** — Register / Login with BCrypt password hashing
- ✈️ **Flight Search** — Multi-date range, sortable by price/duration/layovers
- 💺 **Live Seat Map** — Real-time Redis-backed seat locking with 3-minute countdown
- 👑 **My Seats** — Gold-pulsing indicators for your own booked seats
- 💳 **Stripe Payments** — Card (with 3D Secure) and UPI NetBanking
- 💾 **Saved Methods** — Save and reuse cards and UPI IDs
- 🧾 **Booking History** — Full trip details, cost, timestamps, per-seat cancellation status
- ❌ **Partial Cancellation** — Cancel individual seats on a booking; seat becomes available to others instantly

---

## Project Structure

```
flights/
├── src/
│   └── main/
│       ├── java/com/flights/
│       │   ├── config/          # Security, Redis, CORS config
│       │   ├── controller/      # Auth, Flight, Payment REST controllers
│       │   ├── dto/             # Request/Response DTOs
│       │   ├── exception/       # Global exception handler
│       │   ├── model/           # JPA entities (User, Booking, SavedCard, ...)
│       │   ├── repository/      # Spring Data JPA repositories
│       │   ├── security/        # JWT filter
│       │   └── service/         # Business logic services
│       └── resources/
│           ├── application.properties
│           └── static/          # Built React app (served by Spring Boot)
├── ui/                          # React 18 + Vite source
│   └── src/
│       ├── context/             # Global AppContext (state management)
│       ├── lib/api.js           # All API fetch calls
│       └── pages/               # SearchPage, SeatSelectionPage, PaymentPage, ProfilePage
├── .env                         # ← NOT committed (see .gitignore)
├── .gitignore
└── pom.xml
```

---

## Environment Variables

Create a `.env` file in the `flights/` root (never commit this):

```env
# ─── Supabase PostgreSQL ───────────────────────────────────────────────
SUPABASE_CONNECTION_STRING=jdbc:postgresql://<host>:5432/postgres?user=postgres.<ref>&password=<password>

# ─── Upstash Redis ─────────────────────────────────────────────────────
UPSTASH_REDIS_URL=rediss://default:<token>@<host>.upstash.io:6379

# ─── Stripe ────────────────────────────────────────────────────────────
STRIPE_SECRET_KEY=sk_test_...
STRIPE_PUBLISHABLE_KEY=pk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...

# ─── JWT ───────────────────────────────────────────────────────────────
JWT_SECRET=your_minimum_32_character_secret_here

# ─── Amadeus (optional — mock flights used if blank) ───────────────────
AMADEUS_API_KEY=
AMADEUS_API_SECRET=
```

> **Supabase connection:** Use **Session Pooler** or **Direct Connection** (not Transaction Pooler — Spring Boot's HikariCP requires persistent sessions).
>
> **Upstash Redis:** Use the `rediss://` (TLS) native Redis URL, not the REST URL.

---

## Run Commands

### 1. Prerequisites

```bash
# Java 17
java -version  # must be 17+

# Maven 3.8+
mvn -version

# Node 18+  (for React dev server)
node -version
```

### 2. Backend (Spring Boot)

```bash
cd flights/

# Copy and fill in your credentials
cp .env.example .env   # edit with your Supabase / Upstash / Stripe keys

# Run (auto-compiles and starts on port 8080)
mvn spring-boot:run
```

> Hibernate will auto-create/migrate database tables on first boot (`ddl-auto=update`).

### 3. Frontend — Development Mode

```bash
cd flights/ui

npm install
npm run dev    # starts Vite dev server on http://localhost:5173
```

> In dev mode, Vite proxies all `/api/*` requests to `http://localhost:8080`.

### 4. Frontend — Build & Bundle into Spring Boot

```bash
cd flights/ui

npm run build         # outputs to ui/dist/
cp -r dist/* ../src/main/resources/static/

# Then restart Spring Boot — React is served at http://localhost:8080
mvn spring-boot:run
```

### 5. All-in-One (WSL / Linux)

```bash
# Terminal 1 — Backend
cd /path/to/flights && mvn spring-boot:run

# Terminal 2 — Frontend (dev)
cd /path/to/flights/ui && npm run dev
```

---

## API Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Register a new user |
| `POST` | `/api/auth/login` | Login → returns JWT |
| `GET` | `/api/auth/me` | Get logged-in user profile |
| `GET` | `/api/flights/airports?keyword=` | Airport autocomplete |
| `GET` | `/api/flights/search` | Search flights (date range) |
| `GET` | `/api/flights/seatmap/{flightId}` | Get seat map with lock status |
| `POST` | `/api/flights/seats/lock` | Lock a seat (Redis, 3 min TTL) |
| `POST` | `/api/flights/seats/unlock` | Unlock a seat |
| `GET` | `/api/payment/config` | Get Stripe publishable key |
| `POST` | `/api/payment/card` | Pay by card (Stripe Payment Intent) |
| `POST` | `/api/payment/upi` | Pay by UPI |
| `GET` | `/api/payment/bookings` | Get user's booking history |
| `POST` | `/api/payment/bookings/{id}/cancel-seat` | Cancel a specific seat |

---

## Stripe Test Cards

Since this app uses Stripe **Test Mode**, use these test card numbers:

| Card | Number | Result |
|---|---|---|
| Visa (Success) | `4242 4242 4242 4242` | ✅ Succeeds |
| 3D Secure | `4000 0025 0000 3155` | Triggers 3DS |
| Decline | `4000 0000 0000 9995` | ❌ Insufficient funds |

> Use any future expiry (e.g. `12/30`), any CVC (`123`), any ZIP (`12345`).

---

## Deployment on Render

1. Create a new **Web Service** on [render.com](https://render.com)
2. Point it to this GitHub repo
3. Set **Build Command:** `mvn clean package -DskipTests`
4. Set **Start Command:** `java -jar target/flights-1.0.0.jar`
5. Add all `.env` variables in Render's **Environment** settings (no `.env` file needed on Render)

---

## License

MIT

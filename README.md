# Job Tracker

Ek personal job-application tracker. Tum apni job applications ek jagah save karte ho, unka status track karte ho (APPLIED → INTERVIEW → OFFER / REJECTED), follow-up reminders dekhte ho, resume upload karte ho, aur Google Gemini AI se follow-up email ka draft banwa sakte ho.

Ye project **koi web framework use nahi karta** (Spring ya koi aur nahi). HTTP server, routing, JWT, password hashing, aur rate limiting sab Java ke built-in tools aur chhoti libraries se khud likhe gaye hain. Isliye ye samajhne ke liye achha hai ki backend ke andar asal mein kya hota hai.

---

# Job Tracker UI Screens
<img width="939" height="460" alt="Screenshot 2026-11-02 162958" src="https://github.com/user-attachments/assets/596166da-04a3-4e98-bd77-13f4f3e12635" />
<img width="932" height="466" alt="Screenshot 2026-11-02 164616" src="https://github.com/user-attachments/assets/c76cf4d9-2abd-4038-a25b-6a9ff9ee0a0e" />
<img width="916" height="464" alt="Screenshot 2026-11-02 164700" src="https://github.com/user-attachments/assets/eb72276c-3e34-4740-879d-cb81ce299ca0" />
<img width="935" height="468" alt="Screenshot 2026-11-02 164756" src="https://github.com/user-attachments/assets/e16ab278-3075-4ac3-9303-3cab93a5e8cf" />


## Features

| Feature | Kya karta hai |
|---|---|
| Register + OTP | Naam, email, password se signup. 6 digit OTP console mein dikhta hai, usse verify karna padta hai |
| Login / Logout | Access token (JWT, 15 min) JSON mein, refresh token HttpOnly cookie mein (7 din) |
| Refresh rotation | Har refresh par naya refresh token, purana band. Revoked token dobara aaye to us user ke saare sessions band |
| Applications CRUD | Company, job title, status, applied date, follow-up date, job URL, notes |
| Status filter | `?status=INTERVIEW` jaisa filter |
| Ownership check | Kisi aur ki application dekhne ya badalne par `403` |
| Dashboard | Har status ka count, total, aaj ke due reminders |
| Reminders | Follow-up date aaj ya pehle ki, aur status APPLIED ya INTERVIEW ho |
| Resume upload | Sirf PDF ya DOCX, 5 MB tak. File ka asli content bhi check hota hai (sirf extension nahi) |
| AI follow-up email | Gemini se short, polite follow-up email draft. AI sirf draft deta hai, email tum khud bhejte ho |
| Rate limiting | Login, OTP verify aur AI endpoint par 5 requests/minute per IP (AI par per user) |
| Admin list | `ADMIN` role wale user ke liye saare users ki list |

---

## Tech Stack

### Backend

| Technology | Version | Kaam | Kyun |
|---|---|---|---|
| **Java** | 17 (`maven.compiler.release`) | Main language | Modern features (records, text blocks, `var`) |
| **JDK HttpServer** (`com.sun.net.httpserver`) | JDK built-in | HTTP server | Koi server software nahi chahiye, ek jar se chalta hai |
| **Custom Router + Dispatcher** | - | URL matching, auth check, error handling | Framework ke bina routing samajhne ke liye |
| **SQLite** via **sqlite-jdbc** | 3.46.1.0 | Database | Ek file (`jobtracker.db`), install ki zarurat nahi |
| **JDBC** (`java.sql`) | JDK built-in | SQL queries | Raw SQL with `PreparedStatement` (SQL injection se bachav) |
| **Gson** | 2.11.0 | JSON read/write | Request body parse aur response banana |
| **JWT (HS256)** | custom | Access token | `JwtUtil.java` mein HMAC-SHA256 se khud banaya, koi JWT library nahi |
| **PBKDF2WithHmacSHA256** | JDK built-in | Password hashing | 120,000 iterations, har user ka random salt |
| **SecureRandom** | JDK built-in | OTP, refresh token | Cryptographically secure random |
| **Java HttpClient** | JDK built-in | Gemini API call | Koi extra HTTP library nahi |
| **Fixed window rate limiter** | custom | 429 response | `RateLimiter.java` |

### AI

| Service | Model | Kaam |
|---|---|---|
| **Google Gemini API** (`generativelanguage.googleapis.com`) | `gemini-3.5-flash-lite` (env se change ho sakta hai) | Follow-up email draft |

### Frontend

| Technology | Kaam |
|---|---|
| **HTML5** | `index.html`: login, dashboard, form, table, resume section |
| **CSS3** | `style.css`: layout aur styling |
| **Vanilla JavaScript** | `app.js`: `fetch` se API calls, DOM update. Koi React, Angular ya Vue nahi |

Frontend `src/main/resources/static/` mein hai, aur Java hi use serve karta hai (`StaticController`).

### Build

| Tool | Kaam |
|---|---|
| **Maven** | Build aur dependencies |
| **JUnit** | Abhi tests nahi hain (future mein add kar sakte ho) |

### Frameworks ka short answer
Koi web framework nahi. Backend **pure Java** hai (JDK HttpServer + Gson + SQLite JDBC). Frontend **vanilla HTML, CSS, JS** hai.

---

## Architecture

```
Browser (index.html + app.js)
        │
        ▼
┌──────────────────────────────────────────┐
│ Dispatcher (HttpHandler)                 │
│  - Route dhoondhna (Router)              │
│  - Login check (JwtUtil.verify) → 401    │
│  - ApiException → JSON error             │
└──────────────────────────────────────────┘
        │
        ▼
┌──────────────────────────────────────────┐
│ Controllers                              │
│  AuthController, ApplicationController,  │
│  ResumeController, AiController,         │
│  StaticController                        │
│  (HTTP samajhte hain: body, cookie, code)│
└──────────────────────────────────────────┘
        │
        ▼
┌──────────────────────────────────────────┐
│ Services (business rules)                │
│  AuthService, ApplicationService,        │
│  ResumeService, AiService                │
└──────────────────────────────────────────┘
        │
        ▼
┌──────────────────────────────────────────┐
│ DAOs (SQL)                               │
│  UserDao, TokenDao, ApplicationDao,      │
│  ResumeDao                               │
└──────────────────────────────────────────┘
        │
        ▼
   SQLite (jobtracker.db)      uploads/ (resume files)
```

`JobTrackerApplication.main()` sab objects banakar constructor se pass karta hai. Ye dependency injection ka hi kaam hai, bina framework ke.

---

## Folder structure

```
job-tracker/
├── pom.xml
├── README.md
├── .gitignore
├── src/main/java/com/example/jobtracker/
│   ├── JobTrackerApplication.java    # main(): sab wire karta hai, server start
│   ├── config/
│   │   └── Config.java               # environment variables padhna
│   ├── db/
│   │   └── Database.java             # connection + tables banana
│   ├── model/
│   │   └── Models.java               # User, Application, Resume, RefreshToken, Otp, Dashboard (records)
│   ├── dao/                          # SQL queries
│   │   ├── UserDao.java
│   │   ├── TokenDao.java             # OTP + refresh tokens
│   │   ├── ApplicationDao.java
│   │   └── ResumeDao.java
│   ├── service/                      # business rules
│   │   ├── AuthService.java
│   │   ├── ApplicationService.java
│   │   ├── ResumeService.java
│   │   └── AiService.java
│   ├── controller/                   # HTTP endpoints
│   │   ├── AuthController.java
│   │   ├── ApplicationController.java
│   │   ├── ResumeController.java
│   │   ├── AiController.java
│   │   └── StaticController.java
│   ├── http/                         # HTTP ka khud ka framework
│   │   ├── Router.java               # URL + method matching, :id params
│   │   ├── Route.java
│   │   ├── Handler.java
│   │   ├── Dispatcher.java           # auth check + error handling
│   │   ├── Ctx.java                  # request/response helpers, cookies
│   │   └── ApiException.java         # status code + message
│   ├── security/
│   │   ├── JwtUtil.java              # HS256 JWT
│   │   ├── PasswordHasher.java       # PBKDF2
│   │   └── RateLimiter.java          # fixed window
│   └── validation/
│       └── Errors.java               # field-level validation
└── src/main/resources/static/
    ├── index.html
    ├── style.css
    └── app.js
```

---

## Requirements

- **JDK 17 ya usse upar** (JDK 21 bhi chalega)
- **Maven**
- Internet (pehli baar dependencies ke liye, aur AI feature ke liye)
- Gemini API key (sirf AI feature ke liye, optional)

---

## Setup aur Run

### IntelliJ IDEA
1. **File → Open**, `job-tracker` folder select karo (`pom.xml` par Open)
2. Maven dependencies download hone do
3. `src/main/java/com/example/jobtracker/JobTrackerApplication.java` kholo
4. `main` ke paas ▶ **Run**

### VS Code
1. **Extension Pack for Java** install karo
2. `job-tracker` folder kholo
3. `JobTrackerApplication.java` mein **Run**, ya terminal mein `mvn compile exec:java -Dexec.mainClass=com.example.jobtracker.JobTrackerApplication`

### Browser
- http://localhost:8080 kholo
- Console mein `Job Tracker chal raha hai` aur `Admin ready` dikhega

**Pehla test:** Register karo, console mein OTP dikhega, use verify karo, phir login karo.

**Admin:** `admin@example.com` / `Admin@1234` (pehli run par ban jata hai).

---

## Environment variables

Secrets code mein nahi hone chahiye. Ye environment variables se lo:

| Variable | Default | Kaam |
|---|---|---|
| `JWT_SECRET` | dev value (code mein) | JWT sign karne ki key. Production mein zaroor badlo, kam se kam 32 characters |
| `GEMINI_API_KEY` | (koi nahi) | Gemini API key. Ye **hona hi chahiye** AI ke liye |
| `GEMINI_MODEL` | `gemini-3.5-flash-lite` | Gemini model name |
| `PORT` | `8080` | Server port |
| `COOKIE_SECURE` | `false` | `true` karo jab HTTPS ho |

### Environment variable kaise set karein
- **IntelliJ:** Run → Edit Configurations → JobTrackerApplication → Environment variables
- **Windows PowerShell:** `$env:GEMINI_API_KEY="your-key"`, phir run karo
- **Mac/Linux:** `export GEMINI_API_KEY="your-key"`, phir run karo

### Gemini key kahan se milegi
Google AI Studio (aistudio.google.com) par jao, **Get API key** section se key banao. Key ko code, chat ya GitHub mein mat dalna.

---

## Database

SQLite ek file hai: `jobtracker.db`. Pehli run par tables apne aap ban jati hain.

| Table | Kaam | Main columns |
|---|---|---|
| `users` | Users | id, email (unique), name, password_hash, role (USER/ADMIN), verified |
| `otp_tokens` | Email verification OTP | email, code_hash, expires_at, created_at |
| `refresh_tokens` | Refresh tokens | token (primary key), user_id, expires_at, revoked |
| `applications` | Job applications | user_id, company, job_title, status, applied_on, follow_up_on, job_url, notes |
| `resumes` | Resume metadata (ek user ka ek resume) | user_id (primary key), filename, content_type, size, stored_path |

Foreign keys ON hain, aur user delete hone par uska data `ON DELETE CASCADE` se chala jata hai.

---

## API Reference

| Method | URL | Login | Kaam | Success | Errors |
|---|---|---|---|---|---|
| POST | `/api/auth/register` | No | Signup + OTP | 201 | 400, 409 |
| POST | `/api/auth/verify-otp` | No | Email verify | 200 | 400, 422, 429 |
| POST | `/api/auth/login` | No | Login | 200 | 400, 401, 403, 429 |
| POST | `/api/auth/refresh` | Cookie | Naya access token | 200 | 401 |
| POST | `/api/auth/logout` | Cookie | Refresh revoke | 204 | - |
| GET | `/api/auth/me` | Yes | Apni info | 200 | 401 |
| GET | `/api/admin/users` | ADMIN | Sab users | 200 | 401, 403 |
| GET | `/api/applications?status=` | Yes | Applications list | 200 | 401, 422 |
| POST | `/api/applications` | Yes | Naya application | 201 | 400, 401 |
| GET | `/api/applications/:id` | Yes | Ek application | 200 | 401, 403, 404 |
| PATCH | `/api/applications/:id` | Yes | Fields badlo | 200 | 400, 401, 403, 404 |
| DELETE | `/api/applications/:id` | Yes | Delete | 204 | 401, 403, 404 |
| GET | `/api/dashboard` | Yes | Counts + total + due | 200 | 401 |
| GET | `/api/reminders` | Yes | Aaj ke follow-ups | 200 | 401 |
| POST | `/api/resume?filename=x.pdf` | Yes | Resume upload (body = file) | 201 | 401, 413, 422 |
| GET | `/api/resume` | Yes | Resume info | 200 | 401, 404 |
| GET | `/api/resume/download` | Yes | Resume file | 200 | 401, 404 |
| DELETE | `/api/resume` | Yes | Resume delete | 204 | 401, 404 |
| POST | `/api/ai/followup-email` | Yes | AI follow-up draft | 200 | 401, 404, 429, 502, 503 |

### Request examples

```bash
# Register
curl -X POST localhost:8080/api/auth/register -H "Content-Type: application/json" \
  -d '{"name":"Rahul","email":"rahul@example.com","password":"Pass1234"}'

# Verify OTP (OTP console mein)
curl -X POST localhost:8080/api/auth/verify-otp -H "Content-Type: application/json" \
  -d '{"email":"rahul@example.com","code":"123456"}'

# Login (cookie cookies.txt mein)
curl -c cookies.txt -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"rahul@example.com","password":"Pass1234"}'

# Application banao
curl -X POST localhost:8080/api/applications -H "Authorization: Bearer TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"company":"Acme","jobTitle":"Backend Developer","status":"APPLIED","appliedOn":"2026-10-30","followUpOn":"2026-11-06"}'

# Status update (PATCH: sirf status)
curl -X PATCH localhost:8080/api/applications/1 -H "Authorization: Bearer TOKEN" \
  -H "Content-Type: application/json" -d '{"status":"INTERVIEW"}'

# Resume upload
curl -X POST "localhost:8080/api/resume?filename=Rahul_Resume.pdf" \
  -H "Authorization: Bearer TOKEN" --data-binary @resume.pdf

# Refresh
curl -b cookies.txt -c cookies.txt -X POST localhost:8080/api/auth/refresh
```

---

## Validation rules

| Field | Rule |
|---|---|
| `email` | Valid format (`x@y.z`) |
| `password` | 8+ characters, ek capital letter, ek number |
| `name` | Required, max 100 |
| `company`, `jobTitle` | Required, max 100 |
| `status` | `APPLIED`, `INTERVIEW`, `OFFER`, `REJECTED` |
| `appliedOn`, `followUpOn` | `YYYY-MM-DD` format |
| `jobUrl` | `http://` ya `https://` se shuru |
| `notes` | Max 1000 |
| Resume | PDF ya DOCX, 5 MB tak, aur file ka asli content (magic bytes) match hona chahiye |

Validation fail hone par `400` ya `422` aata hai, aur `fields` mein bataya jaata hai kaunsa field galat hai.

---

## Security decisions

| Decision | Kyun |
|---|---|
| PBKDF2, 120k iterations, random salt | Password DB leak hone par bhi safe, aur brute force slow |
| JWT signature check (`MessageDigest.isEqual`) | Timing attack se bachav |
| Access token memory mein (JS variable) | localStorage se XSS chori se bachav |
| Refresh token HttpOnly cookie | JavaScript use padh nahi sakta |
| Cookie `SameSite=Strict` | CSRF se bachav |
| Cookie `Path=/api/auth` | Refresh token sirf auth APIs ko jata hai |
| Refresh rotation + reuse detection | Chori hua token use hua to sab sessions band |
| OTP hashed store | DB leak par OTP bhi safe |
| Same error for wrong email/password | Email list leak nahi hoti |
| Ownership check (`owned()`) | User sirf apni application dekh/badal sake |
| Prepared statements (`PreparedStatement`) | SQL injection se bachav |
| Resume file name sanitize | Path traversal (`../../`) se bachav |
| Resume magic bytes check | Extension badal kar virus upload se bachav |
| Static file allowlist (`StaticController`) | Sirf 3 files serve hoti hain, koi bhi path nahi |
| `textContent` in frontend | Server response ko HTML ki tarah render nahi karte (XSS se bachav) |
| Resume download par `nosniff` | Browser file ko galat type mein nahi padhta |
| AI sirf draft | Email automatically nahi jata, user khud review karke bhejta hai |

---

## Known issues aur next steps

Ye honest list hai. Inme se kuch pehle theek karne chahiye:

1. **Hardcoded Gemini API key:** `Config.java` mein `geminiKey()` ke andar ek real-looking key likhi hai. Ye GitHub par push hui to leak ho jayegi. **Turant:** (a) us line se key hata do, sirf `get("GEMINI_API_KEY", "")` rakho, (b) Google AI Studio mein us key ko **revoke** karke nayi banao.
2. **Default JWT secret:** `JWT_SECRET` set na ho to code wali dev value use hoti hai. Production mein env variable zaroor do.
3. **Personal resume `uploads/2.pdf`:** ye tumhari personal file hai. `.gitignore` mein `uploads/` hai, to ye GitHub par nahi jayegi, par zip share karne se pehle hata do.
4. **`target/` aur `jobtracker.db`:** build output aur local database hain, ye GitHub par nahi jaane chahiye (`.gitignore` mein already hain).
5. **OTP console mein print hota hai:** demo ke liye theek hai. Real email ke liye `EmailService` jaisa code lagana padega.
6. **Rate limiter memory mein hai:** multiple servers par Redis chahiye.
7. **Expired OTP aur refresh tokens delete nahi hote:** cleanup job add karna chahiye.
8. **Koi automated tests nahi:** `JwtUtil`, `PasswordHasher`, `ResumeService.sanitize` aur `ApplicationService` ke liye JUnit tests resume ke liye useful honge.
9. **CSRF token nahi:** `SameSite=Strict` cookie se kaam chalta hai, lekin state-changing requests ke liye explicit CSRF check better hoga.
10. **Refresh token DB mein plain text:** hash karke store karna better hai.

---

## Future plans

- React + TypeScript frontend (components, protected routes, state management)
- Job URL se automatic details fetch (company, title)
- Interview calendar aur follow-up email reminders
- Docker se deploy

---

## Learning resources

- JDK HttpServer: https://docs.oracle.com/en/java/javase/17/docs/api/jdk.httpserver/
- SQLite JDBC: https://github.com/xerial/sqlite-jdbc
- Gson: https://github.com/google/gson
- Gemini API: https://ai.google.dev/
- JWT (RFC 7519): https://datatracker.ietf.org/doc/html/rfc7519

# 📋 Expense Tracker — Step-by-Step Implementation Guide

> **Reference:** [plan.md](file:///e:/Expense_Tracker/plan.md)  
> **Total Duration:** ~16 weeks across 4 phases  
> **Tech Stack:** Angular 20 · Java 21 · Spring Boot 3 · MongoDB · AI (LLM API)

---

## Table of Contents

1. [Environment Setup](#step-1-environment-setup)
2. [Repository Setup](#step-2-repository-setup)
3. [Backend Scaffolding](#step-3-backend-scaffolding-spring-boot)
4. [Frontend Scaffolding](#step-4-frontend-scaffolding-angular-20)
5. [Database Setup](#step-5-database-setup-mongodb)
6. [Phase 1 — Auth & Ingestion Engine](#phase-1--foundation--ingestion-engine-weeks-16)
7. [Phase 2 — Advanced Features](#phase-2--advanced-features-weeks-710)
8. [Phase 3 — AI Integration](#phase-3--ai-insights--analytics-weeks-1114)
9. [Phase 4 — Testing, Polish & Deployment](#phase-4--testing-polish--deployment-weeks-1516)

---

## Step 1: Environment Setup

### 1.1 Install Prerequisites

| Tool | Installation | Verify |
|------|-------------|--------|
| **Node.js 22.x LTS** | [https://nodejs.org](https://nodejs.org) | `node -v` → v22.x |
| **Angular CLI 20** | `npm install -g @angular/cli@20` | `ng version` → 20.x |
| **Java 21 (LTS)** | [https://adoptium.net](https://adoptium.net) — Temurin JDK 21 | `java -version` → 21.x |
| **Maven** | [https://maven.apache.org](https://maven.apache.org/download.cgi) | `mvn -v` |
| **MongoDB 7.x** | [https://www.mongodb.com/try/download/community](https://www.mongodb.com/try/download/community) | `mongod --version` |
| **MongoDB Compass** | [https://www.mongodb.com/products/compass](https://www.mongodb.com/products/compass) | Open GUI |
| **Git** | [https://git-scm.com](https://git-scm.com) | `git --version` |
| **Docker** (optional) | [https://www.docker.com](https://www.docker.com) | `docker --version` |
| **IDE** | IntelliJ IDEA (backend) + VS Code (frontend) | — |

### 1.2 VS Code Extensions (recommended)

```
Angular Language Service
Tailwind CSS IntelliSense
ESLint
Prettier
Thunder Client (API testing)
```

### 1.3 IntelliJ Plugins (recommended)

```
Spring Boot Assistant
Lombok
MongoDB Navigator
```

---

## Step 2: Repository Setup

### 2.1 Initialize Git Repository

```bash
cd e:\Expense_Tracker
git init
```

### 2.2 Create `.gitignore`

```gitignore
# Java / Maven
backend/target/
*.class
*.jar
*.log

# Angular / Node
frontend/node_modules/
frontend/dist/
frontend/.angular/

# IDE
.idea/
.vscode/
*.iml

# Environment
.env
*.env.local
application-local.yml

# OS
Thumbs.db
.DS_Store

# MongoDB
data/
```

### 2.3 Create Project Folder Structure

```bash
mkdir backend
mkdir frontend
mkdir docs
```

### 2.4 Initial Commit

```bash
git add .
git commit -m "chore: initial project structure"
```

### 2.5 (Optional) Create Remote Repository

```bash
# GitHub
gh repo create expense-tracker --public --source=. --push

# Or manually add remote
git remote add origin https://github.com/<username>/expense-tracker.git
git push -u origin main
```

---

## Step 3: Backend Scaffolding (Spring Boot)

### 3.1 Generate Spring Boot Project

Go to [https://start.spring.io](https://start.spring.io) or use the command below:

**Settings:**
| Field | Value |
|-------|-------|
| Project | Maven |
| Language | Java |
| Spring Boot | 3.3.x (latest stable) |
| Group | `com.expensetracker` |
| Artifact | `expense-tracker-api` |
| Name | `ExpenseTrackerApi` |
| Package name | `com.expensetracker` |
| Packaging | Jar |
| Java | 21 |

**Dependencies to add:**
- Spring Web
- Spring Data MongoDB
- Spring Security
- Spring Boot DevTools
- Lombok
- Validation (Jakarta Bean Validation)
- Spring Boot Actuator

### 3.2 Download & Extract to `/backend`

```bash
# Download the generated zip, extract contents into backend/
# OR use Spring Initializr CLI:
curl "https://start.spring.io/starter.zip?type=maven-project&language=java&bootVersion=3.3.0&groupId=com.expensetracker&artifactId=expense-tracker-api&name=ExpenseTrackerApi&packageName=com.expensetracker&javaVersion=21&dependencies=web,data-mongodb,security,devtools,lombok,validation,actuator" -o backend.zip
```

### 3.3 Add Additional Maven Dependencies

Add these to `backend/pom.xml`:

```xml
<!-- JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.5</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.5</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.5</version>
    <scope>runtime</scope>
</dependency>

<!-- OpenAPI / Swagger -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.5.0</version>
</dependency>

<!-- Apache PDFBox (Bank Statement PDF parsing) -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.2</version>
</dependency>

<!-- Apache POI (Excel/CSV parsing) -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>

<!-- OpenCSV -->
<dependency>
    <groupId>com.opencsv</groupId>
    <artifactId>opencsv</artifactId>
    <version>5.9</version>
</dependency>

<!-- Jakarta Mail (Email parsing via IMAP) -->
<dependency>
    <groupId>org.eclipse.angus</groupId>
    <artifactId>angus-mail</artifactId>
    <version>2.0.3</version>
</dependency>

<!-- Google OAuth Client (for Gmail API) -->
<dependency>
    <groupId>com.google.api-client</groupId>
    <artifactId>google-api-client</artifactId>
    <version>2.6.0</version>
</dependency>
<dependency>
    <groupId>com.google.apis</groupId>
    <artifactId>google-api-services-gmail</artifactId>
    <version>v1-rev20240520-2.0.0</version>
</dependency>

<!-- Bucket4j (Rate limiting) -->
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.10.1</version>
</dependency>

<!-- Test dependencies -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mongodb</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

### 3.4 Configure `application.yml`

Create `backend/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: expense-tracker-api
  data:
    mongodb:
      uri: mongodb://localhost:27017/expense_tracker
      database: expense_tracker
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB

server:
  port: 8080

# Custom app properties
app:
  jwt:
    secret: ${JWT_SECRET:your-256-bit-secret-key-change-in-production}
    access-token-expiry: 900000       # 15 minutes
    refresh-token-expiry: 604800000   # 7 days
  ai:
    api-key: ${AI_API_KEY:}
    api-url: https://generativelanguage.googleapis.com/v1beta
    model: gemini-2.0-flash
  cors:
    allowed-origins: http://localhost:4200

springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
```

Create `backend/src/main/resources/application-dev.yml`:

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/expense_tracker_dev

logging:
  level:
    com.expensetracker: DEBUG
    org.springframework.data.mongodb: DEBUG
```

### 3.5 Create Backend Package Structure

```bash
cd backend/src/main/java/com/expensetracker

mkdir -p config
mkdir -p auth/controller auth/service auth/dto auth/jwt
mkdir -p user/controller user/service user/model user/repository
mkdir -p ingestion/controller ingestion/service ingestion/parser ingestion/model ingestion/repository ingestion/dto
mkdir -p transaction/controller transaction/service transaction/model transaction/repository transaction/dto
mkdir -p goal/controller goal/service goal/model goal/repository
mkdir -p investment/controller investment/service investment/model investment/repository
mkdir -p budget/controller budget/service budget/model budget/repository
mkdir -p analytics/controller analytics/service
mkdir -p ai/controller ai/service ai/client ai/dto
mkdir -p common/exception common/dto common/util
```

### 3.6 Verify Backend Runs

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
# Should start on http://localhost:8080
# Swagger UI at http://localhost:8080/swagger-ui.html
```

### 3.7 Commit

```bash
git add .
git commit -m "feat: backend scaffolding with Spring Boot 3 + dependencies"
```

---

## Step 4: Frontend Scaffolding (Angular 20)

### 4.1 Generate Angular Project

```bash
cd e:\Expense_Tracker
ng new frontend --style=scss --routing --ssr=false --skip-tests=false
cd frontend
```

### 4.2 Install Dependencies

```bash
# PrimeNG
npm install primeng primeicons @primeng/themes

# Tailwind CSS
npm install -D tailwindcss @tailwindcss/postcss postcss

# Charts
npm install chart.js ng2-charts

# Additional utilities
npm install @angular/cdk
```

### 4.3 Configure Tailwind CSS

Create `frontend/postcss.config.js`:

```js
module.exports = {
  plugins: {
    "@tailwindcss/postcss": {},
  },
};
```

Update `frontend/src/styles.scss`:

```scss
@import "tailwindcss";

// PrimeNG theme
@import "primeng/resources/themes/lara-dark-teal/theme.css";
@import "primeng/resources/primeng.min.css";
@import "primeicons/primeicons.css";

// Custom theme variables
:root {
  --bg-primary: #0F0F1A;
  --bg-secondary: #1A1A2E;
  --bg-card: #16213E;
  --accent-primary: #00D2FF;
  --accent-secondary: #7B2FF7;
  --text-primary: #FFFFFF;
  --text-secondary: #A0AEC0;
  --success: #00E676;
  --warning: #FFD600;
  --danger: #FF5252;
}

body {
  margin: 0;
  font-family: 'Inter', sans-serif;
  background-color: var(--bg-primary);
  color: var(--text-primary);
}
```

### 4.4 Add Google Fonts

Update `frontend/src/index.html`:

```html
<head>
  ...
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
</head>
```

### 4.5 Create Frontend Folder Structure

```bash
cd frontend/src/app

# Core
mkdir -p core/services core/guards core/interceptors core/models

# Features
mkdir -p features/auth/login features/auth/register
mkdir -p features/dashboard/widgets
mkdir -p features/data-ingestion/sms-import features/data-ingestion/email-connect features/data-ingestion/statement-upload features/data-ingestion/ingestion-log
mkdir -p features/transactions/transaction-list features/transactions/transaction-review features/transactions/transaction-detail
mkdir -p features/goals
mkdir -p features/investments
mkdir -p features/budgets
mkdir -p features/analytics/spending-trends features/analytics/category-analysis features/analytics/platform-comparison
mkdir -p features/ai-insights/insights-panel features/ai-insights/chat-query features/ai-insights/recommendations
mkdir -p features/settings

# Shared
mkdir -p shared/components/navbar shared/components/sidebar shared/components/card shared/components/modal shared/components/loading-spinner
mkdir -p shared/pipes shared/directives

# Assets
mkdir -p ../../assets/images ../../assets/icons
```

### 4.6 Configure Environment Files

Create `frontend/src/environments/environment.ts`:

```typescript
export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8080/api/v1',
};
```

Create `frontend/src/environments/environment.prod.ts`:

```typescript
export const environment = {
  production: true,
  apiBaseUrl: '/api/v1',
};
```

### 4.7 Set Up Proxy for Development

Create `frontend/proxy.conf.json`:

```json
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true
  }
}
```

Update `angular.json` → `serve` → `options`:

```json
"proxyConfig": "proxy.conf.json"
```

### 4.8 Verify Frontend Runs

```bash
cd frontend
ng serve --open
# Should open http://localhost:4200
```

### 4.9 Commit

```bash
git add .
git commit -m "feat: frontend scaffolding with Angular 20 + PrimeNG + Tailwind"
```

---

## Step 5: Database Setup (MongoDB)

### 5.1 Start MongoDB Locally

```bash
# If installed via MSI (Windows):
net start MongoDB

# If using Docker:
docker run -d --name expense-mongo -p 27017:27017 -v expense-data:/data/db mongo:7
```

### 5.2 Create Database & Collections via MongoDB Compass

1. Open MongoDB Compass
2. Connect to `mongodb://localhost:27017`
3. Create database: `expense_tracker_dev`
4. Create collections:
   - `users`
   - `transactions`
   - `goals`
   - `investments`
   - `budgets`
   - `ai_insights`
   - `raw_ingestion_log`

### 5.3 Create Indexes

In Compass (or via `mongosh`):

```javascript
// Users
db.users.createIndex({ "email": 1 }, { unique: true });

// Transactions
db.transactions.createIndex({ "userId": 1, "date": -1 });
db.transactions.createIndex({ "userId": 1, "category": 1 });
db.transactions.createIndex({ "userId": 1, "source": 1 });
db.transactions.createIndex({ "userId": 1, "status": 1 });
db.transactions.createIndex({ "userId": 1, "upiPlatform": 1 });

// Raw Ingestion Log
db.raw_ingestion_log.createIndex({ "userId": 1, "createdAt": -1 });
db.raw_ingestion_log.createIndex({ "userId": 1, "parsingStatus": 1 });
db.raw_ingestion_log.createIndex({ "rawContent": "hashed" });  // For deduplication

// Goals
db.goals.createIndex({ "userId": 1, "status": 1 });

// Investments
db.investments.createIndex({ "userId": 1, "type": 1 });

// Budgets
db.budgets.createIndex({ "userId": 1, "month": 1 }, { unique: true });

// AI Insights
db.ai_insights.createIndex({ "userId": 1, "generatedAt": -1 });
db.ai_insights.createIndex({ "expiresAt": 1 }, { expireAfterSeconds: 0 });
```

### 5.4 Commit

```bash
git add .
git commit -m "docs: database setup instructions and index definitions"
```

---

## Phase 1 — Foundation & Ingestion Engine (Weeks 1–6)

---

### Week 1: Authentication System

#### Backend Steps

**Step 1.1 — User Model**

Create `backend/src/.../user/model/User.java`:
- Fields: `id`, `email`, `passwordHash`, `fullName`, `age`, `monthlyIncome`, `currency`, `avatarUrl`, `preferences` (embedded), `createdAt`, `updatedAt`
- Use `@Document(collection = "users")` annotation
- Use Java Records for the `Preferences` embedded object

**Step 1.2 — User Repository**

Create `UserRepository.java`:
- Extend `MongoRepository<User, String>`
- Add `Optional<User> findByEmail(String email)`
- Add `boolean existsByEmail(String email)`

**Step 1.3 — Security Config**

Create `config/SecurityConfig.java`:
- Configure `SecurityFilterChain` bean
- Permit `/api/v1/auth/**` endpoints
- Require authentication for all other endpoints
- Add JWT filter before `UsernamePasswordAuthenticationFilter`
- Configure CORS from `app.cors.allowed-origins`

**Step 1.4 — JWT Token Provider**

Create `auth/jwt/JwtTokenProvider.java`:
- `generateAccessToken(String userId)` — 15 min expiry
- `generateRefreshToken(String userId)` — 7 day expiry
- `validateToken(String token)` — returns boolean
- `getUserIdFromToken(String token)` — extract subject

**Step 1.5 — JWT Authentication Filter**

Create `auth/jwt/JwtAuthenticationFilter.java`:
- Extend `OncePerRequestFilter`
- Extract JWT from `Authorization: Bearer <token>` header
- Validate token and set `SecurityContextHolder`

**Step 1.6 — Auth DTOs**

Create request/response DTOs:
- `RegisterRequest`: email, password, fullName, age, monthlyIncome
- `LoginRequest`: email, password
- `AuthResponse`: accessToken, refreshToken, user details
- `TokenRefreshRequest`: refreshToken

**Step 1.7 — Auth Service**

Create `auth/service/AuthService.java`:
- `register(RegisterRequest)` — validate, hash password (BCrypt 12), save user, return tokens
- `login(LoginRequest)` — authenticate, generate tokens
- `refreshToken(TokenRefreshRequest)` — validate refresh token, issue new access token

**Step 1.8 — Auth Controller**

Create `auth/controller/AuthController.java`:
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- Add proper validation annotations (`@Valid`)

**Step 1.9 — Global Exception Handler**

Create `common/exception/GlobalExceptionHandler.java`:
- Handle `MethodArgumentNotValidException` (400)
- Handle `AuthenticationException` (401)
- Handle `AccessDeniedException` (403)
- Handle generic exceptions (500)
- Return standardized `ApiResponse` DTO

**Step 1.10 — CORS Config**

Create `config/CorsConfig.java`:
- Allow `http://localhost:4200` in dev
- Allow configured origins from `application.yml`

#### Frontend Steps

**Step 1.11 — Auth Service**

Create `core/services/auth.service.ts`:
- `register(data)`, `login(data)`, `refreshToken()`, `logout()`
- Store JWT in `localStorage` or `sessionStorage`
- `isAuthenticated()` signal
- `currentUser()` signal

**Step 1.12 — JWT Interceptor**

Create `core/interceptors/jwt.interceptor.ts`:
- Attach `Authorization: Bearer <token>` to all API requests
- Auto-refresh token on 401 response

**Step 1.13 — Auth Guard**

Create `core/guards/auth.guard.ts`:
- Redirect to `/login` if not authenticated

**Step 1.14 — Login Page**

Create `features/auth/login/`:
- Email + password form with validation
- Google OAuth button (placeholder)
- Link to register page
- Dark themed, animated background
- PrimeNG InputText, Password, Button components

**Step 1.15 — Register Page**

Create `features/auth/register/`:
- Full registration form: name, email, password, age, monthly income
- Form validation (email format, password strength, age 18–30)
- Link to login page

**Step 1.16 — App Routes**

Configure `app.routes.ts`:
- `/login` → Login component
- `/register` → Register component
- `/dashboard` → Dashboard (guarded)
- `/` → redirect to `/dashboard`
- Lazy-load all feature routes

**Step 1.17 — Navbar & Sidebar**

Create `shared/components/navbar/` and `shared/components/sidebar/`:
- App logo, user avatar, theme toggle
- Sidebar: Dashboard, Import Data, Transactions, Goals, Investments, Analytics, AI Insights, Settings

#### ✅ Week 1 Checkpoint

```bash
# Test: register a user, login, access protected endpoint
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"Test1234!","fullName":"Test User","age":24,"monthlyIncome":50000}'
```

```bash
git add . && git commit -m "feat(auth): complete authentication system with JWT"
```

---

### Week 2: SMS Parsing & Ingestion

#### Backend Steps

**Step 2.1 — Raw Ingestion Log Model**

Create `ingestion/model/RawIngestionLog.java`:
- Fields: `id`, `userId`, `source` (enum: SMS, EMAIL, BANK_STATEMENT), `rawContent`, `parsedFields` (embedded), `transactionId`, `parsingStatus` (enum), `parsingConfidence`, `fileName`, `errorMessage`, `createdAt`

**Step 2.2 — Ingestion Log Repository**

Create `ingestion/repository/IngestionLogRepository.java`:
- `findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable)`
- `existsByUserIdAndRawContent(String userId, String rawContent)` — deduplication

**Step 2.3 — SMS Regex Patterns**

Create `ingestion/parser/SmsRegexPatterns.java`:
- Define regex patterns for major Indian banks and UPI apps:
  - **SBI**: `"Rs.{amount} debited from A/C XX{last4} on {date}"`
  - **HDFC**: `"INR {amount} debited from HDFC Bank A/C **{last4}"`
  - **ICICI**: `"Your ICICI Bank Acct XX{last4} is debited with Rs.{amount}"`
  - **GPay/PhonePe/Paytm**: UPI-specific patterns
- Extract: amount, merchant/payee, date, reference number, bank name, UPI app
- Return a `ParsedTransactionDTO` with confidence score

**Step 2.4 — SMS Parser Service**

Create `ingestion/service/SmsParserService.java`:
- `parseSms(String userId, String smsText)` — try regex patterns → return parsed fields
- `parseBulkSms(String userId, List<String> smsTexts)` — batch processing
- Call `DeduplicationService` to check for duplicates
- Log raw SMS to `raw_ingestion_log`
- If regex confidence < 0.7, fallback to AI-based extraction via `TransactionExtractor`

**Step 2.5 — Transaction Extractor (AI Fallback)**

Create `ingestion/service/TransactionExtractor.java`:
- Uses LLM API to extract transaction fields from unstructured text
- Prompt: "Extract amount, merchant, date, payment mode, UPI platform from this SMS: ..."
- Returns structured `ParsedTransactionDTO`

**Step 2.6 — Deduplication Service**

Create `ingestion/service/DeduplicationService.java`:
- Check `raw_ingestion_log` for duplicate raw content (hash matching)
- Check `transactions` for same amount + merchant + date within ±5 minutes
- Return duplicate status and reference to existing transaction

**Step 2.7 — Transaction Model (updated)**

Create `transaction/model/Transaction.java`:
- All fields from plan.md §6.2 including `source`, `sourceRef`, `parsingConfidence`, `status`

**Step 2.8 — Transaction Repository**

Create `transaction/repository/TransactionRepository.java`:
- Common queries: by userId + date range, by category, by status, by source

**Step 2.9 — Ingestion DTOs**

Create:
- `SmsIngestionRequest.java`: `{ smsTexts: List<String> }`
- `ParsedTransactionDTO.java`: all parsed fields + confidence
- `IngestionResponse.java`: parsed count, duplicate count, failed count, transactions

**Step 2.10 — Ingestion Controller (SMS endpoints)**

Create `ingestion/controller/IngestionController.java`:
- `POST /api/v1/ingest/sms` — single SMS
- `POST /api/v1/ingest/sms/bulk` — multiple SMS messages
- `GET /api/v1/ingest/log` — paginated ingestion audit log

#### Frontend Steps

**Step 2.11 — Ingestion Service**

Create `core/services/ingestion.service.ts`:
- `submitSms(smsTexts: string[])`
- `getIngestionLog(page, size)`

**Step 2.12 — Data Ingestion Hub**

Create `features/data-ingestion/` parent component:
- Card-based UI with three options: SMS Import, Email Connect, Statement Upload
- Shows last sync time, total imported count, pending review count

**Step 2.13 — SMS Import Screen**

Create `features/data-ingestion/sms-import/`:
- Textarea to paste one or multiple SMS messages (separated by newlines)
- "Parse" button → shows parsed preview (editable fields)
- Confidence indicator per transaction (green/yellow/red)
- "Import All" / "Import Selected" buttons
- Success/error toast notifications

**Step 2.14 — Ingestion Log Screen**

Create `features/data-ingestion/ingestion-log/`:
- Table showing raw ingestion history
- Columns: Source, Raw Text (truncated), Status, Confidence, Date
- Filter by source, status

#### ✅ Week 2 Checkpoint

```bash
# Test: submit SMS for parsing
curl -X POST http://localhost:8080/api/v1/ingest/sms \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"smsTexts": ["Rs.450.00 debited from A/C XX1234 on 08-Mar-26 via UPI to SWIGGY. Ref 412345678901"]}'
```

```bash
git add . && git commit -m "feat(ingestion): SMS parsing with regex + AI fallback"
```

---

### Week 3: Email Parsing & Ingestion

#### Backend Steps

**Step 3.1 — Email Parser Service**

Create `ingestion/service/EmailParserService.java`:
- `connectGmail(String userId, String authCode)` — OAuth 2.0 flow with Gmail API
- `connectImap(String userId, ImapConfig config)` — IMAP connection for non-Gmail
- `scanEmails(String userId)` — scan inbox for bank/UPI transaction alert emails
- Extract transaction data from email subject + body
- Handle HTML emails (extract text, parse amounts/merchants)

**Step 3.2 — Email Scanning Logic**

- Search for emails from known bank sender addresses:
  - `alerts@hdfcbank.net`, `transaction@icicibank.com`, `noreply@sbi.co.in`, etc.
  - UPI receipt emails from GPay, PhonePe, Paytm
- Extract: amount, merchant, date, transaction type, reference number
- Use regex + AI fallback for unstructured emails

**Step 3.3 — Google OAuth Config**

- Set up Google Cloud project, enable Gmail API
- Configure OAuth consent screen and credentials
- Store refresh tokens securely (encrypted in user document)

**Step 3.4 — Ingestion Controller (Email endpoints)**

Add to `IngestionController.java`:
- `POST /api/v1/ingest/email/connect` — initiate Email OAuth / IMAP connection
- `POST /api/v1/ingest/email/sync` — trigger email scan
- `GET /api/v1/ingest/email/status` — sync status (in-progress, last sync time, count)

#### Frontend Steps

**Step 3.5 — Email Connect Screen**

Create `features/data-ingestion/email-connect/`:
- "Connect Gmail" button → initiates OAuth flow → redirect callback
- Alternative: IMAP configuration form (host, port, email, password)
- Connected status indicator with last sync time
- "Sync Now" button to trigger manual scan
- Preview of detected transaction emails before import

#### ✅ Week 3 Checkpoint

```bash
git add . && git commit -m "feat(ingestion): email parsing with Gmail OAuth + IMAP support"
```

---

### Week 4: Bank Statement Upload & Parsing

#### Backend Steps

**Step 4.1 — PDF Statement Parser**

Create `ingestion/parser/PdfStatementParser.java`:
- Use Apache PDFBox to extract text from PDF bank statements
- Define bank-specific parsing templates (table layouts vary by bank):
  - **HDFC**: column positions for date, description, debit, credit, balance
  - **SBI**: different column layout
  - **ICICI, Axis, Kotak**: custom parsers
- Return `List<ParsedTransactionDTO>`

**Step 4.2 — CSV/Excel Statement Parser**

Create `ingestion/parser/CsvStatementParser.java`:
- Use OpenCSV for CSV files
- Use Apache POI for Excel (.xlsx) files
- Auto-detect column headers (Date, Description, Amount, Debit, Credit)
- Map to `ParsedTransactionDTO`

**Step 4.3 — Statement Parser Service**

Create `ingestion/service/StatementParserService.java`:
- `parseStatement(String userId, MultipartFile file, String bankName)`:
  - Auto-detect file type (PDF/CSV/XLSX)
  - Select appropriate parser
  - Run AI categorization on each parsed transaction
  - Check for duplicates
  - Save to `raw_ingestion_log` and create `Transaction` entries
- Return `StatementUploadResponse` with summary stats

**Step 4.4 — Ingestion Controller (Statement endpoints)**

Add to `IngestionController.java`:
- `POST /api/v1/ingest/statement/upload` — multipart file upload + `bankName` param
- `GET /api/v1/ingest/statement/{id}/status` — parsing progress (for large files)

#### Frontend Steps

**Step 4.5 — Statement Upload Screen**

Create `features/data-ingestion/statement-upload/`:
- Drag-and-drop file upload zone (accepts .pdf, .csv, .xlsx)
- Bank selector dropdown (HDFC, SBI, ICICI, Axis, Kotak, Other)
- Upload progress bar
- Preview of parsed transactions in a table before confirming import
- Summary: total rows, successfully parsed, duplicates found, errors

#### ✅ Week 4 Checkpoint

```bash
# Test: upload a sample bank statement
curl -X POST http://localhost:8080/api/v1/ingest/statement/upload \
  -H "Authorization: Bearer <token>" \
  -F "file=@sample_hdfc_statement.pdf" \
  -F "bankName=HDFC"
```

```bash
git add . && git commit -m "feat(ingestion): bank statement parsing (PDF/CSV/Excel)"
```

---

### Week 5: AI Auto-Categorization & Transaction Review

#### Backend Steps

**Step 5.1 — Categorization Service**

Create `ai/service/CategorizationService.java`:
- `categorize(String merchant, String description)` → category + subCategory
- **Rules engine first** (fast, no API cost):
  - Map known merchants: "Swiggy" → Food/Delivery, "Uber" → Transport/Ride, "Netflix" → Entertainment/Subscription
  - Maintain a `merchant_category_map` in MongoDB or in-memory
- **AI fallback** for unknown merchants:
  - Send to LLM: "Categorize this transaction: merchant={X}, amount={Y}. Categories: Food, Transport, Shopping, Bills, Entertainment, Health, Education, Other"
  - Cache the result for future use

**Step 5.2 — Transaction Service (Review Flow)**

Create `transaction/service/TransactionService.java`:
- `getTransactions(userId, filters, pageable)` — paginated, filtered list
- `reviewTransaction(transactionId, reviewAction)` — mark as REVIEWED / CORRECTED / REJECTED
- `updateTransaction(transactionId, updates)` — edit parsed fields
- `getDuplicates(userId)` — find potential duplicate transactions
- `getTransactionSummary(userId, month)` — aggregated stats

**Step 5.3 — Transaction Controller**

Create `transaction/controller/TransactionController.java`:
- `GET /api/v1/transactions` — with query params: dateFrom, dateTo, category, source, status, page, size
- `GET /api/v1/transactions/{id}` — single transaction
- `PUT /api/v1/transactions/{id}` — edit/correct
- `PUT /api/v1/transactions/{id}/review` — approve
- `PUT /api/v1/transactions/{id}/reject` — reject
- `DELETE /api/v1/transactions/{id}`
- `GET /api/v1/transactions/duplicates`
- `GET /api/v1/transactions/summary`

#### Frontend Steps

**Step 5.4 — Transaction Service**

Create `core/services/transaction.service.ts`:
- CRUD + review/reject operations
- Summary and duplicate detection

**Step 5.5 — Transaction List**

Create `features/transactions/transaction-list/`:
- PrimeNG DataTable with:
  - Columns: Date, Merchant, Amount, Category (with icon), UPI Platform, Source badge (SMS/Email/Statement), Confidence, Status
  - Inline filters: date range picker, category dropdown, source dropdown, status toggle
  - Sort by date, amount
  - Pagination
  - Click row to expand detail view

**Step 5.6 — Transaction Review Flow**

Create `features/transactions/transaction-review/`:
- Filter: show only `AUTO_PARSED` status transactions
- Bulk approve button
- Per-transaction: Approve ✅ / Edit ✏️ / Reject ❌ buttons
- Edit mode: inline editing of category, merchant, amount
- Confidence indicator: green (>0.9), yellow (0.7–0.9), red (<0.7)
- Duplicate alert banner linking to the matching transaction

#### ✅ Week 5 Checkpoint

```bash
git add . && git commit -m "feat(transactions): AI categorization + review/approve/reject flow"
```

---

### Week 6: Dashboard & Search

#### Backend Steps

**Step 6.1 — Dashboard Aggregation API**

Create `analytics/service/AnalyticsService.java` (basic endpoints):
- `getMonthSummary(userId, yearMonth)`:
  - Total income, total expense, net savings
  - Category-wise breakdown (MongoDB `$group` aggregation)
  - Top 5 merchants by spend
  - Daily spending trend (line chart data)
- `getIncomeVsExpense(userId, months)`:
  - Monthly income vs expense for last N months

**Step 6.2 — Income Auto-Detection**

In `TransactionService.java`:
- Auto-detect salary credits: transactions with type=INCOME, amount > threshold, recurring monthly
- Mark salary transactions with tag "salary"

**Step 6.3 — Search & Filter API**

Enhance `TransactionController.java`:
- Full-text search on merchant, notes fields
- Combined filters: date range + category + source + UPI platform + amount range

#### Frontend Steps

**Step 6.4 — Dashboard**

Create `features/dashboard/dashboard.component.ts`:
- **Hero card**: Current month balance (income - expenses), with trend arrow
- **Widgets** (grid layout):
  - Spending by category (donut chart via ng2-charts)
  - Income vs Expense (bar chart, last 6 months)
  - Recent transactions (last 10, compact list)
  - Data source status (connected SMS/Email/Statement, last import time)
  - Goal progress (if goals exist)
- **Import CTA**: prompt to import data if no transactions exist

**Step 6.5 — Search Bar**

Add global search to navbar:
- Type-ahead search across merchants and notes
- Search results dropdown with quick navigation

**Step 6.6 — Loading Skeletons & Empty States**

- Add PrimeNG Skeleton components while data loads
- Empty state illustrations when no data exists
- Onboarding prompt: "Import your first transactions via SMS, Email, or Bank Statement"

#### ✅ Week 6 / Phase 1 Checkpoint

```bash
git add . && git commit -m "feat(dashboard): dashboard with charts, search, income detection"
git tag v0.1.0 -m "Phase 1 complete: Auth + Ingestion Engine + Dashboard"
```

**Phase 1 Complete! ✅** Users can:
- Register / Login
- Import transactions via SMS, Email, or Bank Statement
- AI auto-categorizes transactions
- Review, correct, or reject parsed transactions
- View dashboard with spending charts and summaries
- Search and filter transaction history

---

## Phase 2 — Advanced Features (Weeks 7–10)

---

### Week 7: Goals

#### Backend

**Step 7.1** — Create `Goal` model, repository, service, controller
- CRUD for goals
- `POST /api/v1/goals/{id}/contribute` — add savings contribution
- Milestone tracking: auto-mark milestones as reached
- Calculate progress percentage

#### Frontend

**Step 7.2** — Goals page
- Card grid with progress rings (PrimeNG Knob component)
- Add Goal modal: title, target amount, deadline, priority
- Goal detail view with milestone timeline
- "Contribute" button to log savings

```bash
git add . && git commit -m "feat(goals): savings goals with milestones and progress tracking"
```

---

### Week 8: Investments

#### Backend

**Step 8.1** — Create `Investment` model, repository, service, controller
- CRUD for investments
- Portfolio summary: total invested, current value, overall returns
- Group by type (SIP, Stock, FD, Crypto)

#### Frontend

**Step 8.2** — Investments page
- Portfolio overview pie chart
- Investment list with return indicators (green/red arrows)
- Add Investment form: type, name, invested amount, current value, platform, dates

```bash
git add . && git commit -m "feat(investments): investment tracking with portfolio overview"
```

---

### Week 9: Budgets & Recurring Transactions

#### Backend

**Step 9.1** — Create `Budget` model, repository, service, controller
- Set monthly budget per category
- Auto-calculate "spent" from transactions in that month
- Alert when approaching/exceeding budget (>80%, >100%)

**Step 9.2** — Recurring Transaction Detection
- Scheduled job: analyze transactions for recurring patterns
- Match by: similar amount (±5%), same merchant, monthly interval (±3 days)
- Mark transactions as `isRecurring = true`, group by `recurringId`

#### Frontend

**Step 9.3** — Budget page
- Category budget cards with progress bars
- Set budget form: category dropdown + amount
- Budget vs Actual comparison chart

**Step 9.4** — Recurring transactions view
- List of detected subscriptions/recurring payments
- Monthly cost, next expected date, cancel link (informational)

```bash
git add . && git commit -m "feat(budgets): monthly budgets + recurring transaction detection"
```

---

### Week 10: Export & Multi-UPI

#### Backend

**Step 10.1** — Export Service
- CSV export: generate CSV from filtered transactions
- PDF export: generate formatted PDF report (using iText or JasperReports)
- Monthly/yearly report generation

**Step 10.2** — Multi-UPI Aggregation
- UPI platform already auto-tagged from parsed SMS/emails
- Aggregation API: spending breakdown by UPI platform

#### Frontend

**Step 10.3** — Export functionality
- Export button on transaction list page
- Format selector: CSV or PDF
- Date range picker for export scope

**Step 10.4** — UPI Platform Comparison
- Bar/pie chart comparing spend across GPay, PhonePe, Paytm, CRED
- In analytics section

```bash
git add . && git commit -m "feat: export (CSV/PDF) + UPI platform comparison"
git tag v0.2.0 -m "Phase 2 complete: Goals, Investments, Budgets, Export, Multi-UPI"
```

**Phase 2 Complete! ✅**

---

## Phase 3 — AI Insights & Analytics (Weeks 11–14)

---

### Week 11: AI Service Setup & Spending Trends

#### Backend

**Step 11.1** — LLM Client
- Create `ai/client/LLMClient.java`
- HTTP client for Gemini/OpenAI API
- Request/response DTOs
- Retry logic and error handling
- Response caching in `ai_insights` collection

**Step 11.2** — Spending Trend Analysis
- `GET /api/v1/analytics/spending-trend` — weekly/monthly aggregation
- MongoDB aggregation pipeline: group by week → compute totals → compute % change
- LLM generates natural language summary of trends

#### Frontend

**Step 11.3** — AI Insights Panel
- Feed of insight cards with icons (trend up/down, warning, tip)
- Spending trend line chart (weekly/monthly toggle)
- Natural language trend summary text

```bash
git add . && git commit -m "feat(ai): LLM client + spending trend analysis"
```

---

### Week 12: Anomaly Detection & Recommendations

#### Backend

**Step 12.1** — Anomaly Detection
- Compare current month category spend vs. 3-month rolling average
- Flag if > 30% deviation → generate alert insight
- Store in `ai_insights` with severity level

**Step 12.2** — Smart Recommendations
- Rules engine + LLM for personalized tips:
  - Identify unused subscriptions
  - Suggest budget adjustments
  - Highlight saving opportunities

#### Frontend

**Step 12.3** — Anomaly alerts & recommendation cards
- Alert badges on dashboard
- Recommendation feed with actionable tips
- Dismiss / save for later functionality

```bash
git add . && git commit -m "feat(ai): anomaly detection + smart recommendations"
```

---

### Week 13: Budget Forecasting & Goal Feasibility

#### Backend

**Step 13.1** — Budget Forecasting
- Linear regression / moving average on historical spending
- Project month-end totals by category
- LLM narrates: "At this rate, you'll exceed food budget by ₹2,000"

**Step 13.2** — Goal Feasibility
- Available monthly savings = avg income - avg expenses
- Calculate months needed to reach goal
- Feasibility score: GREEN (on track), YELLOW (tight), RED (unlikely)

#### Frontend

**Step 13.3** — Forecast charts and goal indicator
- Forecast line on spending chart (dashed line for projected)
- Goal cards show feasibility badge + recommended monthly saving

```bash
git add . && git commit -m "feat(ai): budget forecasting + goal feasibility"
```

---

### Week 14: Natural Language Queries & Polish

#### Backend

**Step 14.1** — Natural Language Query Endpoint
- `POST /api/v1/ai/ask` — receives `{ question: "How much did I spend on food last week?" }`
- LLM parses intent → maps to database query → executes → formats response
- Support questions about: spending, trends, comparisons, goals

**Step 14.2** — Caching & Rate Limiting
- Cache AI responses with TTL (24 hours for insights, 1 hour for queries)
- Rate limit: 20 AI queries per user per day (Bucket4j)
- Background job: generate daily/weekly insights overnight

#### Frontend

**Step 14.3** — AI Chat Interface
- Chat-style UI in AI Insights section
- Text input with send button
- Response cards with data visualizations
- Suggested questions: "What's my biggest expense?", "Am I on track for my goals?"

**Step 14.4** — Micro-Animations & Polish
- Page transition animations
- Chart entrance animations
- Loading states with shimmer effects
- Toast notifications (PrimeNG Toast)
- Success/error feedback on all actions

```bash
git add . && git commit -m "feat(ai): natural language queries + UI polish"
git tag v0.3.0 -m "Phase 3 complete: Full AI integration"
```

**Phase 3 Complete! ✅**

---

## Phase 4 — Testing, Polish & Deployment (Weeks 15–16)

---

### Week 15: Testing

#### Backend Tests

**Step 15.1** — Unit Tests (JUnit 5 + Mockito)

```bash
# Test files to create:
src/test/java/com/expensetracker/
├── auth/AuthServiceTest.java
├── ingestion/SmsParserServiceTest.java         # Critical: test regex patterns
├── ingestion/StatementParserServiceTest.java    # Test with sample PDF/CSV fixtures
├── ingestion/DeduplicationServiceTest.java
├── transaction/TransactionServiceTest.java
├── ai/CategorizationServiceTest.java
└── analytics/AnalyticsServiceTest.java
```

**Step 15.2** — Integration Tests (Testcontainers)

- Spin up MongoDB in Docker via Testcontainers
- Test full ingestion flow: SMS → parse → save → retrieve
- Test auth flow: register → login → access protected endpoint

**Step 15.3** — Parser Accuracy Tests

- Create sample SMS fixtures from each bank (10+ patterns each)
- Create sample bank statement PDFs/CSVs
- Assert parsing accuracy > 90% on test fixtures

#### Frontend Tests

**Step 15.4** — Component Tests

```bash
ng test
```

- Test auth components (form validation, submit flow)
- Test ingestion components (SMS paste, file upload)
- Test transaction review flow

**Step 15.5** — E2E Tests (Playwright or Cypress)

```bash
npm install -D @playwright/test
npx playwright install
```

Test scenarios:
1. Register → Login → Arrive at dashboard
2. Import SMS → Review transactions → Approve
3. Upload bank statement → Preview → Confirm import
4. View dashboard with charts populated
5. Create a goal → Contribute → Check progress

```bash
git add . && git commit -m "test: unit, integration, and E2E tests"
```

---

### Week 16: Deployment

#### Step 16.1 — Dockerize Backend

Create `backend/Dockerfile`:

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### Step 16.2 — Dockerize Frontend

Create `frontend/Dockerfile`:

```dockerfile
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build -- --configuration production

FROM nginx:alpine
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

Create `frontend/nginx.conf`:

```nginx
server {
    listen 80;
    server_name _;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

#### Step 16.3 — Docker Compose

Create `docker-compose.yml` in project root:

```yaml
version: '3.8'

services:
  mongodb:
    image: mongo:7
    ports:
      - "27017:27017"
    volumes:
      - mongo-data:/data/db
    environment:
      MONGO_INITDB_DATABASE: expense_tracker

  backend:
    build: ./backend
    ports:
      - "8080:8080"
    environment:
      SPRING_DATA_MONGODB_URI: mongodb://mongodb:27017/expense_tracker
      JWT_SECRET: ${JWT_SECRET}
      AI_API_KEY: ${AI_API_KEY}
    depends_on:
      - mongodb

  frontend:
    build: ./frontend
    ports:
      - "80:80"
    depends_on:
      - backend

volumes:
  mongo-data:
```

#### Step 16.4 — Environment Variables

Create `.env` (git-ignored):

```env
JWT_SECRET=your-super-secret-256-bit-key-change-this
AI_API_KEY=your-gemini-or-openai-api-key
```

#### Step 16.5 — CI/CD Pipeline (GitHub Actions)

Create `.github/workflows/ci.yml`:

```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  backend-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - run: cd backend && ./mvnw test

  frontend-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
      - run: cd frontend && npm ci && npm test -- --watch=false

  build-and-push:
    needs: [backend-test, frontend-test]
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v4
      - name: Build and deploy
        run: |
          docker compose build
          # Push to registry / deploy to cloud
```

#### Step 16.6 — Cloud Deployment (Choose one)

**Option A: Railway / Render (Easiest)**
- Push Docker images → auto-deploy
- Add MongoDB Atlas as managed DB

**Option B: AWS / GCP / Azure**
- Deploy containers to ECS / Cloud Run / Azure Container Apps
- Use MongoDB Atlas for managed database
- Set up load balancer + SSL certificate

#### Step 16.7 — MongoDB Atlas (Production Database)

1. Go to [https://cloud.mongodb.com](https://cloud.mongodb.com)
2. Create free cluster (M0 Sandbox)
3. Create database user
4. Whitelist application IP / allow all (0.0.0.0/0)
5. Get connection string → update `SPRING_DATA_MONGODB_URI` env var
6. Run index creation from Step 5.3

#### Step 16.8 — Production Checklist

- [ ] Change `JWT_SECRET` to a strong random value
- [ ] Set `AI_API_KEY` in production environment
- [ ] Enable HTTPS (SSL/TLS certificate)
- [ ] Set CORS to production domain only
- [ ] Enable MongoDB authentication
- [ ] Set up monitoring (Spring Boot Actuator + Prometheus/Grafana or cloud monitoring)
- [ ] Set up error tracking (Sentry or similar)
- [ ] Configure backup schedule for MongoDB
- [ ] Test all ingestion flows in production
- [ ] Load test with concurrent users

#### Step 16.9 — Final Commit & Tag

```bash
git add .
git commit -m "chore: Docker, CI/CD pipeline, deployment config"
git tag v1.0.0 -m "v1.0.0: Production release"
git push origin main --tags
```

---

## 🎉 Project Complete!

### Quick Reference — Run Commands

```bash
# Development (run individually)
cd backend  && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
cd frontend && ng serve --proxy-config proxy.conf.json

# Development (Docker all-in-one)
docker compose up --build

# Production build
docker compose -f docker-compose.yml up -d

# Run tests
cd backend  && ./mvnw test
cd frontend && npm test
cd frontend && npx playwright test
```

### Swagger API Docs

```
http://localhost:8080/swagger-ui.html
```

### Git Tags for Each Phase

| Tag | Phase | Description |
|-----|-------|-------------|
| `v0.1.0` | Phase 1 | Auth + Ingestion Engine + Dashboard |
| `v0.2.0` | Phase 2 | Goals, Investments, Budgets, Export |
| `v0.3.0` | Phase 3 | AI Insights & Analytics |
| `v1.0.0` | Phase 4 | Production Release |
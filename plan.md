# 📊 Expense Tracker Management — Project Plan

> **Target Audience:** Age group 18–30 | **Problem Space:** Daily UPI transaction management  
> **Tech Stack:** Angular 20 · Java 21 (Spring Boot 3) · MongoDB · AI/ML  
> **Date:** 2026-03-08

---

## 1. Executive Summary

A full-stack, AI-powered **Expense Tracker Management** application designed for young adults (18–30) who frequently use UPI platforms (GPay, PhonePe, Paytm, CRED, etc.) for daily transactions. The system **automatically captures transactions** via **SMS parsing, Email parsing, and Bank Statement upload** — eliminating manual data entry entirely. It will **intelligently categorize spending**, **track salaries & investments**, **set and monitor financial goals**, and leverage **Artificial Intelligence** to surface spending trends, forecast expenses, and deliver personalized financial recommendations.

---

## 2. Problem Statement

Young adults (18–30) perform dozens of digital transactions daily across multiple UPI apps but lack a **unified, intelligent view** of their finances. Existing tools are either too generic, too complex, or don't leverage AI to provide actionable insights. This leads to:

- No clear picture of where money goes each month
- Difficulty tracking recurring payments, subscriptions & EMIs
- No easy way to set savings goals tied to real spending data
- Missed opportunities to save or invest based on spending patterns

---

## 3. Key Features

### 3.1 Core Features (MVP — Phase 1)

| # | Feature | Description |
|---|---------|-------------|
| 1 | **User Authentication & Profiles** | Sign-up / login via email, Google OAuth 2.0. Profile with name, age, monthly income, preferred currency |
| 2 | **SMS Parsing (Transaction Ingestion)** | Parse UPI/bank transaction SMS messages to auto-extract amount, merchant, date, UPI platform, and payment mode. Uses regex patterns + AI for intelligent extraction. User grants SMS read permission (Android) or forwards/pastes SMS on web |
| 3 | **Email Parsing (Transaction Ingestion)** | Connect email account (Gmail OAuth / IMAP) to scan for bank transaction alerts, UPI receipts, and payment confirmations. AI extracts structured transaction data from email body/HTML |
| 4 | **Bank Statement Upload** | Upload bank statement files (PDF / CSV / Excel) from any bank. Backend parser extracts all transactions in bulk. Supports major Indian banks (SBI, HDFC, ICICI, Axis, Kotak, etc.) |
| 5 | **AI-Powered Auto-Categorization** | Automatically categorize parsed transactions into pre-defined categories (Food, Transport, Shopping, Bills, Entertainment, Health, Education) using merchant name + AI. Users can review & correct categories |
| 6 | **Transaction Review & Correction** | View all auto-parsed transactions. Edit incorrectly parsed fields (category, merchant, amount). Flag/delete duplicates. Approve or reject parsed entries |
| 7 | **Salary & Income Tracking** | Record monthly salary, freelance income, side-hustle earnings. Auto-detect salary credits from parsed SMS/email/statements |
| 8 | **Dashboard & Overview** | At-a-glance view: total spend this month, income vs. expense, category-wise breakdown (pie/bar charts) |
| 9 | **Search & Filters** | Filter transactions by date range, category, UPI platform, amount range, source (SMS/Email/Statement). Full-text search |
| 10 | **Responsive UI** | Mobile-first design optimized for 18–30 age group. Dark mode support |

### 3.2 Advanced Features (Phase 2)

| # | Feature | Description |
|---|---------|-------------|
| 11 | **Investment Tracker** | Track SIPs, mutual funds, stocks, FDs, crypto. Show current portfolio value & returns |
| 12 | **Future Goals** | Define savings goals (e.g., "Trip to Goa ₹30K by Dec 2026"). Visual progress tracker. AI-based goal feasibility check |
| 13 | **Recurring Transaction Detection** | AI auto-detects recurring payments (rent, subscriptions, EMIs) from parsed data. Reminders before due dates |
| 14 | **Multi-UPI Aggregation** | Auto-tagged UPI platform from parsed SMS/emails. Compare spending across platforms (e.g., GPay vs PhonePe) |
| 15 | **Export & Reports** | Download transaction history as CSV/PDF. Monthly/yearly summary reports |

### 3.3 AI-Powered Features (Phase 3)

| # | Feature | Description |
|---|---------|-------------|
| 16 | **Spending Pattern Analysis** | AI analyzes transaction history to identify trends: weekday vs weekend spending, seasonal spikes, category drift |
| 17 | **Anomaly Detection** | Flag unusual transactions (e.g., abnormally high spend in a category) |
| 18 | **Smart Recommendations** | Personalized tips: "You spent 40% more on food this month — here are ways to optimize" |
| 19 | **Budget Forecasting** | Predict next month's expenses based on historical data. "At this rate, you'll exceed your food budget by ₹2,000" |
| 20 | **Goal-Based Savings Advice** | AI recommends how much to save monthly to meet goals. Adjusts based on actual spending |
| 21 | **Natural Language Querying** | Ask questions like "How much did I spend on food last week?" and get instant answers |

---

## 4. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENT (Angular 20)                      │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────────────┐  │
│  │Dashboard │ │Transactions│ │ Goals   │ │ AI Insights Panel │  │
│  │          │ │ (Review)   │ │         │ │                   │  │
│  └──────────┘ └──────────┘ └──────────┘ └───────────────────┘  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │         Data Ingestion UI (SMS / Email / Upload)         │   │
│  └──────────────────────────────────────────────────────────┘   │
└──────────────────────────┬──────────────────────────────────────┘
                           │ HTTP / REST + WebSocket
┌──────────────────────────▼──────────────────────────────────────┐
│                  API GATEWAY (Spring Boot 3)                    │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────┐  │
│  │Auth API  │ │Txn API   │ │Goals API │ │ AI/Analytics API │  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────────┘  │
│                       │            │                            │
│  ┌────────────────────▼────────────┼──────────────────────────┐ │
│  │         INGESTION ENGINE        │                          │ │
│  │  ┌──────────┐ ┌──────────┐ ┌───▼────────┐                 │ │
│  │  │SMS Parser│ │Email     │ │ Statement  │                 │ │
│  │  │(Regex+AI)│ │Parser    │ │ Parser     │                 │ │
│  │  │          │ │(IMAP/API)│ │ (PDF/CSV)  │                 │ │
│  │  └────┬─────┘ └────┬─────┘ └────┬───────┘                 │ │
│  │       └────────────┼────────────┘                          │ │
│  │                    ▼                                       │ │
│  │          ┌──────────────────┐  ┌──────────────────────┐   │ │
│  │          │ AI Categorizer   │  │  AI/ML Engine        │   │ │
│  │          │ & Deduplicator   │  │  (Insights/Forecast) │   │ │
│  │          └──────────────────┘  └──────────────────────┘   │ │
│  └────────────────────┬──────────────────────────────────────┘ │
└───────────────────────┼────────────────────────────────────────┘
                        │
               ┌────────▼──────────────────┐
               │       MongoDB Atlas       │
               │  ┌──────┐ ┌────────────┐  │
               │  │Users │ │Transactions│  │
               │  └──────┘ └────────────┘  │
               │  ┌──────┐ ┌────────────┐  │
               │  │Goals │ │Investments │  │
               │  └──────┘ └────────────┘  │
               │  ┌────────────────────┐   │
               │  │  Raw Ingestion Log │   │
               │  └────────────────────┘   │
               └───────────────────────────┘
```

### 4.1 Architecture Style

- **Modular Monolith** (Phase 1–2) → easy to develop, test, and deploy for a small team
- **Microservices** (Phase 3) → extract AI/ML engine as a separate Python/Java microservice when complexity grows

### 4.2 Communication

| Layer | Protocol | Details |
|-------|----------|---------|
| Angular ↔ Spring Boot | REST (JSON) | Standard CRUD + pagination |
| Real-time updates (optional) | WebSocket / SSE | Live dashboard refresh |
| Spring Boot ↔ AI Engine | REST or gRPC | Send transaction data, receive insights |
| Spring Boot ↔ MongoDB | MongoDB Java Driver / Spring Data MongoDB | Reactive or blocking |

---

## 5. Tech Stack — Detailed Breakdown

### 5.1 Frontend — Angular 20

| Concern | Choice | Why |
|---------|--------|-----|
| Framework | Angular 20 (Standalone Components, Signals) | Latest Angular with improved reactivity, SSR support |
| State Management | Angular Signals + RxJS | Signals for local state; RxJS for async streams |
| UI Components |  PrimeNG | Rich, accessible component library |
| Charts | ng2-charts (Chart.js wrapper) or ngx-echarts | Interactive, beautiful visualizations |
| HTTP | Angular HttpClient | Built-in, interceptors for auth tokens |
| Routing | Angular Router with lazy-loaded standalone components | Optimal performance |
| Styling | SCSS + CSS Variables and Tailwind | Dark/light theme support |
| Build | esbuild (Angular CLI default) | Fast builds |

### 5.2 Backend — Java 21 + Spring Boot 3

| Concern | Choice | Why |
|---------|--------|-----|
| Framework | Spring Boot 3.3+ | Production-grade, massive ecosystem |
| Language features | Java 21 (Records, Pattern Matching, Virtual Threads) | Modern, concise code; Virtual Threads for scalability |
| API | Spring Web MVC (REST) | Simple, well-documented |
| Security | Spring Security 6 + JWT | Token-based auth, stateless |
| Database | Spring Data MongoDB | Elegant repository abstraction |
| Validation | Jakarta Bean Validation | Declarative request validation |
| Documentation | SpringDoc OpenAPI (Swagger UI) | Auto-generated API docs |
| Testing | JUnit 5 + Mockito + Testcontainers | Integration tests with real MongoDB in Docker |
| Build tool | Maven | Dependency management, CI/CD pipeline |

### 5.3 Database — MongoDB

| Concern | Details |
|---------|---------|
| Engine | MongoDB 7.x (Community Edition) |
| GUI | MongoDB Compass (local development & admin) |
| Hosting (prod) | MongoDB Atlas (free tier to start) |
| ODM | Spring Data MongoDB (Java driver under the hood) |
| Indexing | Compound indexes on `userId + date`, `userId + category`, text index on `notes` |

### 5.4 AI / ML Layer

| Approach | Option A (Recommended for MVP) | Option B (Advanced) |
|----------|-------------------------------|---------------------|
| Engine | **OpenAI / Gemini API** via REST | Python microservice with scikit-learn / TensorFlow |
| Use case | Natural language insights, recommendations, anomaly detection | Custom-trained models on user data |
| Integration | Spring Boot calls AI API, formats response for frontend | Separate Flask/FastAPI service; Spring Boot proxies |
| Cost | Pay-per-token (manageable at small scale) | Infrastructure cost for model hosting |
| Pros | Fast to build, high-quality outputs, no ML expertise needed | Full control, no API costs at scale, privacy-first |

**Recommended approach:** Start with **Option A** (LLM API) for Phase 1–2 to move fast. Transition to **Option B** when user base grows and custom models provide better ROI.

---

## 6. Data Models (MongoDB Collections)

### 6.1 `users`

```json
{
  "_id": "ObjectId",
  "email": "user@example.com",
  "passwordHash": "bcrypt_hash",
  "fullName": "Rahul Sharma",
  "age": 24,
  "monthlyIncome": 50000,
  "currency": "INR",
  "avatarUrl": "https://...",
  "preferences": {
    "theme": "dark",
    "defaultUpiApp": "gpay",
    "notificationsEnabled": true
  },
  "createdAt": "ISODate",
  "updatedAt": "ISODate"
}
```

### 6.2 `transactions`

```json
{
  "_id": "ObjectId",
  "userId": "ObjectId (ref: users)",
  "type": "EXPENSE | INCOME | TRANSFER",
  "amount": 450.00,
  "currency": "INR",
  "category": "Food",
  "subCategory": "Restaurant",
  "upiPlatform": "gpay | phonepe | paytm | cred | other",
  "paymentMode": "UPI | Cash | Card | NetBanking",
  "merchant": "Swiggy",
  "notes": "auto-parsed from SMS",
  "tags": ["weekend", "food-delivery"],
  "source": "SMS | EMAIL | BANK_STATEMENT",
  "sourceRef": "ObjectId (ref: raw_ingestion_log)",
  "parsingConfidence": 0.95,
  "status": "AUTO_PARSED | USER_REVIEWED | CORRECTED | REJECTED",
  "isRecurring": false,
  "recurringId": null,
  "date": "ISODate",
  "createdAt": "ISODate",
  "updatedAt": "ISODate"
}
```

### 6.3 `goals`

```json
{
  "_id": "ObjectId",
  "userId": "ObjectId (ref: users)",
  "title": "Trip to Goa",
  "targetAmount": 30000,
  "savedAmount": 12000,
  "deadline": "ISODate (2026-12-01)",
  "priority": "HIGH | MEDIUM | LOW",
  "status": "IN_PROGRESS | ACHIEVED | ABANDONED",
  "milestones": [
    { "amount": 10000, "reachedAt": "ISODate" },
    { "amount": 20000, "reachedAt": null }
  ],
  "createdAt": "ISODate",
  "updatedAt": "ISODate"
}
```

### 6.4 `investments`

```json
{
  "_id": "ObjectId",
  "userId": "ObjectId (ref: users)",
  "type": "SIP | MUTUAL_FUND | STOCK | FD | CRYPTO | OTHER",
  "name": "Axis Bluechip Fund",
  "investedAmount": 50000,
  "currentValue": 58000,
  "returns": 16.0,
  "startDate": "ISODate",
  "maturityDate": "ISODate | null",
  "platformName": "Groww",
  "notes": "Monthly SIP of ₹5000",
  "createdAt": "ISODate",
  "updatedAt": "ISODate"
}
```

### 6.5 `budgets`

```json
{
  "_id": "ObjectId",
  "userId": "ObjectId (ref: users)",
  "month": "2026-03",
  "categoryBudgets": [
    { "category": "Food", "limit": 8000, "spent": 5400 },
    { "category": "Transport", "limit": 3000, "spent": 2100 },
    { "category": "Entertainment", "limit": 2000, "spent": 800 }
  ],
  "totalBudget": 30000,
  "createdAt": "ISODate"
}
```

### 6.6 `ai_insights` (cached AI responses)

```json
{
  "_id": "ObjectId",
  "userId": "ObjectId (ref: users)",
  "type": "TREND | ANOMALY | RECOMMENDATION | FORECAST",
  "title": "Food spending increased 40%",
  "description": "Your food expenses rose from ₹6,000 to ₹8,400 this month...",
  "data": { "chartType": "line", "points": [...] },
  "severity": "INFO | WARNING | CRITICAL",
  "isRead": false,
  "generatedAt": "ISODate",
  "expiresAt": "ISODate"
}
```

### 6.7 `raw_ingestion_log` (audit trail for parsed data)

```json
{
  "_id": "ObjectId",
  "userId": "ObjectId (ref: users)",
  "source": "SMS | EMAIL | BANK_STATEMENT",
  "rawContent": "Rs.450 debited from A/C XX1234 via UPI to SWIGGY on 08-03-26. Ref 412345678901",
  "parsedFields": {
    "amount": 450.00,
    "merchant": "Swiggy",
    "upiPlatform": "gpay",
    "date": "ISODate",
    "refNumber": "412345678901"
  },
  "transactionId": "ObjectId (ref: transactions) | null",
  "parsingStatus": "SUCCESS | FAILED | DUPLICATE | SKIPPED",
  "parsingConfidence": 0.95,
  "fileName": "HDFC_March_2026.pdf (for statement uploads)",
  "errorMessage": null,
  "createdAt": "ISODate"
}
```

---

## 7. API Design (RESTful)

### 7.1 Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/auth/register` | Register new user |
| POST | `/api/v1/auth/login` | Login, returns JWT |
| POST | `/api/v1/auth/refresh` | Refresh access token |
| POST | `/api/v1/auth/google` | Google OAuth login |
| POST | `/api/v1/auth/logout` | Invalidate refresh token |

### 7.2 Data Ingestion

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/ingest/sms` | Submit SMS text(s) for parsing |
| POST | `/api/v1/ingest/sms/bulk` | Submit multiple SMS messages in batch |
| POST | `/api/v1/ingest/email/connect` | Connect email account (Gmail OAuth / IMAP) |
| POST | `/api/v1/ingest/email/sync` | Trigger email scan for transaction alerts |
| GET | `/api/v1/ingest/email/status` | Check email sync status |
| POST | `/api/v1/ingest/statement/upload` | Upload bank statement (PDF/CSV/Excel) |
| GET | `/api/v1/ingest/statement/{id}/status` | Check statement parsing progress |
| GET | `/api/v1/ingest/log` | View raw ingestion audit log (paginated) |

### 7.3 Transactions

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/transactions` | List parsed transactions (paginated, filtered) |
| GET | `/api/v1/transactions/{id}` | Get single transaction |
| PUT | `/api/v1/transactions/{id}` | Edit/correct a parsed transaction |
| PUT | `/api/v1/transactions/{id}/review` | Mark transaction as reviewed/approved |
| PUT | `/api/v1/transactions/{id}/reject` | Reject a falsely parsed transaction |
| DELETE | `/api/v1/transactions/{id}` | Delete transaction |
| GET | `/api/v1/transactions/summary` | Monthly summary stats |
| GET | `/api/v1/transactions/export` | Export as CSV/PDF |
| GET | `/api/v1/transactions/duplicates` | List potential duplicate transactions |

### 7.4 Goals

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/goals` | List all goals |
| POST | `/api/v1/goals` | Create goal |
| PUT | `/api/v1/goals/{id}` | Update goal |
| DELETE | `/api/v1/goals/{id}` | Delete goal |
| POST | `/api/v1/goals/{id}/contribute` | Add savings to goal |

### 7.5 Investments

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/investments` | List all investments |
| POST | `/api/v1/investments` | Add investment |
| PUT | `/api/v1/investments/{id}` | Update investment |
| DELETE | `/api/v1/investments/{id}` | Delete investment |
| GET | `/api/v1/investments/portfolio` | Portfolio summary |

### 7.6 Budgets

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/budgets` | Get current month's budget |
| POST | `/api/v1/budgets` | Set budget for month |
| PUT | `/api/v1/budgets/{id}` | Update budget |

### 7.7 AI & Analytics

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/analytics/spending-trend` | Time-series spending data |
| GET | `/api/v1/analytics/category-breakdown` | Category-wise analysis |
| GET | `/api/v1/analytics/platform-comparison` | UPI platform comparison |
| GET | `/api/v1/ai/insights` | Get AI-generated insights |
| POST | `/api/v1/ai/ask` | Natural language query |
| GET | `/api/v1/ai/forecast` | Expense forecast |
| GET | `/api/v1/ai/recommendations` | Personalized tips |

---

## 8. Project Structure

### 8.1 Frontend (`/frontend`)

```
frontend/
├── src/
│   ├── app/
│   │   ├── core/                     # Singleton services, guards, interceptors
│   │   │   ├── services/
│   │   │   │   ├── auth.service.ts
│   │   │   │   ├── ingestion.service.ts
│   │   │   │   ├── transaction.service.ts
│   │   │   │   ├── goal.service.ts
│   │   │   │   ├── investment.service.ts
│   │   │   │   ├── analytics.service.ts
│   │   │   │   └── ai.service.ts
│   │   │   ├── guards/
│   │   │   │   └── auth.guard.ts
│   │   │   ├── interceptors/
│   │   │   │   └── jwt.interceptor.ts
│   │   │   └── models/
│   │   │       ├── transaction.model.ts
│   │   │       ├── ingestion.model.ts
│   │   │       ├── goal.model.ts
│   │   │       ├── user.model.ts
│   │   │       └── investment.model.ts
│   │   │
│   │   ├── features/                 # Lazy-loaded feature modules
│   │   │   ├── auth/
│   │   │   │   ├── login/
│   │   │   │   └── register/
│   │   │   ├── dashboard/
│   │   │   │   ├── dashboard.component.ts
│   │   │   │   ├── widgets/
│   │   │   │   │   ├── spending-chart.component.ts
│   │   │   │   │   ├── income-vs-expense.component.ts
│   │   │   │   │   ├── recent-transactions.component.ts
│   │   │   │   │   └── goal-progress.component.ts
│   │   │   ├── data-ingestion/        # Core: SMS, Email, Statement
│   │   │   │   ├── sms-import/
│   │   │   │   ├── email-connect/
│   │   │   │   ├── statement-upload/
│   │   │   │   └── ingestion-log/
│   │   │   ├── transactions/
│   │   │   │   ├── transaction-list/
│   │   │   │   ├── transaction-review/
│   │   │   │   └── transaction-detail/
│   │   │   ├── goals/
│   │   │   ├── investments/
│   │   │   ├── budgets/
│   │   │   ├── analytics/
│   │   │   │   ├── spending-trends/
│   │   │   │   ├── category-analysis/
│   │   │   │   └── platform-comparison/
│   │   │   ├── ai-insights/
│   │   │   │   ├── insights-panel/
│   │   │   │   ├── chat-query/
│   │   │   │   └── recommendations/
│   │   │   └── settings/
│   │   │
│   │   ├── shared/                   # Reusable components, pipes, directives
│   │   │   ├── components/
│   │   │   │   ├── navbar/
│   │   │   │   ├── sidebar/
│   │   │   │   ├── card/
│   │   │   │   ├── modal/
│   │   │   │   └── loading-spinner/
│   │   │   ├── pipes/
│   │   │   └── directives/
│   │   │
│   │   ├── app.component.ts
│   │   ├── app.config.ts
│   │   └── app.routes.ts
│   │
│   ├── assets/
│   ├── environments/
│   └── styles/
│       ├── _variables.scss
│       ├── _mixins.scss
│       ├── _themes.scss
│       └── styles.scss
│
├── angular.json
├── package.json
└── tsconfig.json
```

### 8.2 Backend (`/backend`)

```
backend/
├── src/main/java/com/expensetracker/
│   ├── ExpenseTrackerApplication.java
│   │
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   ├── MongoConfig.java
│   │   ├── CorsConfig.java
│   │   └── AIConfig.java
│   │
│   ├── auth/
│   │   ├── controller/AuthController.java
│   │   ├── service/AuthService.java
│   │   ├── dto/LoginRequest.java
│   │   ├── dto/RegisterRequest.java
│   │   └── jwt/JwtTokenProvider.java
│   │
│   ├── user/
│   │   ├── controller/UserController.java
│   │   ├── service/UserService.java
│   │   ├── model/User.java
│   │   └── repository/UserRepository.java
│   │
│   ├── ingestion/                         # NEW: Core ingestion engine
│   │   ├── controller/IngestionController.java
│   │   ├── service/
│   │   │   ├── SmsParserService.java
│   │   │   ├── EmailParserService.java
│   │   │   ├── StatementParserService.java
│   │   │   ├── TransactionExtractor.java  # AI-powered field extraction
│   │   │   └── DeduplicationService.java
│   │   ├── parser/
│   │   │   ├── SmsRegexPatterns.java       # Bank-specific SMS regex
│   │   │   ├── PdfStatementParser.java     # Apache PDFBox
│   │   │   └── CsvStatementParser.java
│   │   ├── model/RawIngestionLog.java
│   │   ├── repository/IngestionLogRepository.java
│   │   └── dto/
│   │       ├── SmsIngestionRequest.java
│   │       ├── StatementUploadResponse.java
│   │       └── ParsedTransactionDTO.java
│   │
│   ├── transaction/
│   │   ├── controller/TransactionController.java
│   │   ├── service/TransactionService.java
│   │   ├── model/Transaction.java
│   │   ├── repository/TransactionRepository.java
│   │   └── dto/TransactionDTO.java
│   │
│   ├── goal/
│   │   ├── controller/GoalController.java
│   │   ├── service/GoalService.java
│   │   ├── model/Goal.java
│   │   └── repository/GoalRepository.java
│   │
│   ├── investment/
│   │   ├── controller/InvestmentController.java
│   │   ├── service/InvestmentService.java
│   │   ├── model/Investment.java
│   │   └── repository/InvestmentRepository.java
│   │
│   ├── budget/
│   │   ├── controller/BudgetController.java
│   │   ├── service/BudgetService.java
│   │   ├── model/Budget.java
│   │   └── repository/BudgetRepository.java
│   │
│   ├── analytics/
│   │   ├── controller/AnalyticsController.java
│   │   └── service/AnalyticsService.java
│   │
│   ├── ai/
│   │   ├── controller/AIController.java
│   │   ├── service/AIService.java
│   │   ├── service/CategorizationService.java  # AI auto-categorization
│   │   ├── client/LLMClient.java
│   │   └── dto/InsightResponse.java
│   │
│   └── common/
│       ├── exception/GlobalExceptionHandler.java
│       ├── dto/ApiResponse.java
│       └── util/DateUtils.java
│
├── src/main/resources/
│   ├── application.yml
│   └── application-dev.yml
│
├── src/test/java/com/expensetracker/
│   ├── transaction/TransactionServiceTest.java
│   ├── auth/AuthServiceTest.java
│   └── ...
│
├── pom.xml
└── Dockerfile
```

---

## 9. UI/UX Design Guidelines

### 9.1 Design Principles (for 18–30 age group)

- **Mobile-first** — responsive layout, touch-friendly interactions
- **Dark mode default** — with easy light mode toggle
- **Zero manual entry** — all transactions come from SMS, Email, or Bank Statement uploads
- **Gamification** — progress bars for goals, streaks for daily logging, achievement badges
- **Modern aesthetic** — glassmorphism, gradient accents, smooth micro-animations
- **Color palette** — Deep dark backgrounds (`#0F0F1A`), vibrant accent gradients (teal-to-purple), neon status colors for visual pop

### 9.2 Key Screens

| Screen | Description |
|--------|-------------|
| **Login/Register** | Clean, minimal form with Google OAuth option. Animated background |
| **Dashboard** | Hero card with balance summary. Chart widgets below. Data source connection status |
| **Data Ingestion Hub** | Central screen to connect SMS, link email, or upload bank statements. Shows sync status, last import time, and pending items |
| **SMS Import** | Paste SMS messages or grant SMS access (Android). Shows parsing preview before importing |
| **Email Connect** | Gmail OAuth flow or IMAP config. Lists detected transaction emails with checkbox selection |
| **Statement Upload** | Drag-and-drop file upload (PDF/CSV/Excel). Bank selector dropdown. Preview parsed rows before confirming |
| **Transactions (Review)** | List of auto-parsed transactions with confidence indicators. Users can approve, correct, or reject entries. Duplicate detection alerts |
| **Goals** | Card-based layout with progress rings. "Add Goal" CTA |
| **Investments** | Portfolio pie chart. List of investments with return indicators |
| **AI Insights** | Feed-style cards with insights. Chat interface for natural-language queries |
| **Analytics** | Full-page charts: line (trends), bar (comparison), donut (category split) |
| **Settings** | Profile, theme toggle, notification preferences, connected accounts, export data |

---

## 10. AI Strategy — Detailed

### 10.1 What the AI Layer Does

| Capability | Input | Output | How |
|------------|-------|--------|-----|
| **Spending Trend Analysis** | Last 3-6 months of transactions | Line chart data + text summary | Aggregate by week/month, compute % change, detect upward/downward trends |
| **Category Anomaly Detection** | Current month vs. historical average | Alert card: "Food spend is 40% higher than your 3-month average" | Z-score / threshold-based detection, then LLM formats the alert |
| **Smart Recommendations** | Full transaction history + goals | Personalized tips (e.g., "Cancel unused subscription to save ₹499/mo") | Rules engine + LLM for natural language formatting |
| **Budget Forecasting** | Historical data + current month partial | Projected month-end totals per category | Linear regression or moving average; LLM narrates forecast |
| **Goal Feasibility** | Goal target, deadline, current savings rate | Feasibility score + recommended monthly saving | Simple arithmetic + LLM for friendly explanation |
| **Natural Language Query** | User question in plain English/Hindi | Structured answer with data | LLM parses intent → maps to DB query → formats response |

### 10.2 Prompt Engineering Approach (LLM-based MVP)

```
System Prompt:
"You are a personal finance assistant for a young Indian professional.
You have access to their transaction data, goals, and budget.
Provide insights in a friendly, concise tone. Use ₹ for currency.
When giving recommendations, be specific and actionable.
If the data is insufficient, say so honestly."
```

The backend will:
1. Pre-process and aggregate raw transaction data
2. Build a structured context (JSON summary of relevant data)
3. Send the context + user query to the LLM API
4. Parse and cache the response
5. Return formatted insights to the frontend

### 10.3 Privacy & Data Handling

- **No raw transaction data** is sent to external AI APIs — only aggregated summaries
- User can **opt-out** of AI features
- AI insights are **cached** in `ai_insights` collection to minimize API calls
- **Rate limiting** on AI endpoints (e.g., 20 queries/day per user)

---

## 11. Security Considerations

| Area | Strategy |
|------|----------|
| Authentication | JWT (access + refresh tokens). Access token: 15 min; Refresh: 7 days |
| Password Storage | BCrypt hashing (strength 12) |
| API Security | Spring Security filter chain. All endpoints require auth except `/auth/**` |
| CORS | Whitelist only Angular dev server and production domain |
| Input Validation | Jakarta Bean Validation on all DTOs. Sanitize all user inputs |
| Rate Limiting | Bucket4j or Resilience4j for API rate limiting |
| HTTPS | Enforce TLS in production |
| MongoDB | Authentication enabled, role-based access, encryption at rest (Atlas) |

---

## 12. Development Phases & Timeline

### Phase 1 — Foundation & Ingestion Engine (Weeks 1–6)

| Week | Backend | Frontend |
|------|---------|----------|
| 1 | Project setup, MongoDB config, User model, Auth (register/login/JWT) | Angular 20 scaffolding, routing, auth pages, theme setup (PrimeNG + Tailwind) |
| 2 | SMS Parser service (regex patterns for major banks + UPI apps), Raw ingestion log model | Data Ingestion Hub UI, SMS import screen (paste/bulk input) |
| 3 | Email Parser service (Gmail OAuth + IMAP), Email scanning & transaction extraction | Email connect flow (OAuth UI), email scan results preview |
| 4 | Bank Statement Parser (PDF via Apache PDFBox, CSV/Excel via Apache POI), multi-bank support | Statement upload UI (drag-and-drop), bank selector, parsed rows preview |
| 5 | AI auto-categorization service, Deduplication engine, Transaction review API | Transaction review list with confidence scores, approve/reject/correct flow, duplicate alerts |
| 6 | Dashboard summary API, Income auto-detection from parsed data, Search & filter API | Dashboard with charts, income vs expense widget, search bar, filters, loading skeletons |

**Milestone:** ✅ Users can register, log in, import transactions via SMS/Email/Bank Statement, review & correct parsed entries, and see a basic dashboard.

---

### Phase 2 — Advanced Features (Weeks 7–10)

| Week | Backend | Frontend |
|------|---------|----------|
| 7 | Goals CRUD API, Milestone tracking, Contribution endpoint | Goals page, progress rings, add goal modal |
| 8 | Investment CRUD API, Portfolio summary, Returns calculation | Investment tracker, portfolio pie chart |
| 9 | Budget API, AI-powered recurring transaction detection | Budget setup page, recurring transactions view |
| 10 | Export service (CSV/PDF), Multi-UPI aggregation API (auto-tagged from parsed data) | Export button, UPI platform comparison chart |

**Milestone:** ✅ Full financial tracking — auto-imported transactions, goals, investments, budgets with export capability.

---

### Phase 3 — AI Insights & Analytics (Weeks 11–14)

| Week | Backend | Frontend |
|------|---------|----------|
| 11 | AI service setup, LLM client, Spending trend analysis endpoint | AI insights panel, trend chart widget |
| 12 | Anomaly detection, Smart recommendations endpoint | Anomaly alert cards, recommendation feed |
| 13 | Budget forecasting, Goal feasibility API | Forecast charts, goal feasibility indicator |
| 14 | Natural language query endpoint, Caching, Rate limiting | Chat interface for AI queries, polish & animations |

**Milestone:** ✅ AI-powered insights, recommendations, forecasting, and natural-language queries.

---

### Phase 4 — Polish & Deploy (Weeks 15–16)

| Task | Details |
|------|---------|
| Testing | Unit tests (JUnit 5), integration tests (Testcontainers), E2E (Cypress/Playwright). Parser accuracy tests with sample SMS/email/statement fixtures |
| Performance | MongoDB indexing optimization, lazy loading, statement parsing performance tuning |
| Documentation | API docs (Swagger), README, supported banks list, deployment guide |
| Deployment | Docker Compose (dev), Cloud deployment (AWS/GCP/Azure or Railway/Render) |
| CI/CD | GitHub Actions pipeline: lint → test → build → deploy |

**Milestone:** ✅ Production-ready application with CI/CD pipeline.

---

## 13. Development Environment Setup

### Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Node.js | 22.x LTS | Angular CLI & dev server |
| Angular CLI | 20.x | Frontend scaffolding & builds |
| Java | 21 (LTS) | Backend runtime |
| Maven | Latest | Build tool |
| MongoDB | 7.x | Database (local or Atlas) |
| MongoDB Compass | Latest | GUI for database management |
| Git | Latest | Version control |
| Docker | Latest | Containerization (optional for dev) |
| IDE | IntelliJ IDEA / VS Code | Development |

### Quick Start Commands

```bash
# Clone and setup
git clone <repo-url>
cd Expense_Tracker

# Backend
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Frontend (new terminal)
cd frontend
npm install
ng serve --open

# MongoDB (if local)
mongod --dbpath ./data/db
```

---

## 14. Non-Functional Requirements

| Category | Requirement |
|----------|-------------|
| **Performance** | API response time < 200ms (p95). Dashboard load < 2s |
| **Scalability** | Support 10K concurrent users (MongoDB Atlas auto-scaling) |
| **Availability** | 99.5% uptime (cloud-hosted) |
| **Compatibility** | Chrome, Firefox, Safari, Edge (last 2 versions). iOS/Android responsive |
| **Accessibility** | WCAG 2.1 AA compliance |
| **Localization** | English (primary), Hindi (future) |
| **Data Retention** | Transaction history retained for 5 years |

---

## 15. Future Enhancements (Post-MVP)

| Enhancement | Description |
|-------------|-------------|
| **Real-time SMS Listener** | Background service on Android that auto-captures UPI SMS in real-time (no manual paste needed) |
| **WhatsApp/UPI App Integration** | Direct integration with UPI apps for automatic transaction sync |
| **OCR for Paper Receipts** | Scan physical receipts using camera and extract transaction data |
| **Split Expenses** | Share and split bills with friends (like Splitwise) |
| **Notifications** | Push notifications for budget alerts, goal milestones, bill reminders |
| **PWA Support** | Progressive Web App for offline access and home-screen install |
| **Multi-currency** | Support for international transactions and currency conversion |
| **Family Accounts** | Shared household expense tracking |
| **Gamification** | Badges, streaks, leaderboards for consistent expense tracking |

---

## 16. Risk Assessment

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| AI API costs escalate | High | Medium | Implement caching, rate limiting, batch queries. Plan migration to self-hosted models |
| MongoDB performance at scale | Medium | Low | Proper indexing, pagination, Atlas auto-scaling |
| Angular 20 breaking changes | Medium | Low | Follow official migration guides, use stable APIs |
| Data privacy concerns | High | Medium | Anonymize data for AI, encrypt at rest, clear privacy policy |
| User adoption | High | Medium | Focus on UX, onboarding flow, minimal friction |

---

## 17. Success Metrics

| Metric | Target |
|--------|--------|
| User registration rate | 100+ users in first 3 months |
| Daily active users | 30% of registered users |
| Avg. transactions logged/user/day | 3+ |
| Goal completion rate | 40% of goals marked achieved |
| AI insight engagement | 50% of users interact with AI features weekly |
| App performance (Lighthouse) | Score > 90 |

---

> **Next Steps:** Review this plan, finalize feature scope for Phase 1 MVP, set up the repository structure, and begin development with Sprint 1 (Auth + Ingestion Engine).
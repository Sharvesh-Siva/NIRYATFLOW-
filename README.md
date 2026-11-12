# NiryatFlow AI — Export Intelligence & Cross-Border Trade Finance Platform (Android + Web SaaS)

**NiryatFlow AI** is a dual-platform (**Android Kotlin/Jetpack Compose** + **React/TypeScript/Vite Web SaaS**) export management, deterministic trade finance, compliance document verification, and order digital twin platform engineered for Indian MSME exporters.

Both the **original Android implementation (`/app`)** and the **complete Web Application (`/web`)** co-exist in this workspace without overwriting or breaking any existing functionality.

---

## 1. Project Workspace Architecture

```text
├── app/                                 # Complete Native Android Application (Kotlin + Jetpack Compose)
│   ├── src/main/java/com/example/
│   │   ├── data/model/NiryatModels.kt
│   │   ├── data/sample/SampleBusinessWorkspaceSeeder.kt
│   │   ├── data/sample/SampleWorkspaceLocalStore.kt
│   │   ├── domain/FinancialCalculationEngine.kt
│   │   ├── domain/TradeRiskEngine.kt
│   │   ├── service/DemoPaymentService.kt
│   │   ├── service/PayPalSandboxService.kt
│   │   ├── service/GeminiIntelligenceService.kt
│   │   ├── ui/screens/...
│   │   └── ui/viewmodel/NiryatViewModel.kt
│   └── src/test/java/com/example/       # Unit & Robolectric Automated Verification Suite
├── web/                                 # Complete Web SaaS Application (React 18 + TypeScript + Vite)
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   ├── index.html
│   └── src/
│       ├── types/models.ts              # Shared domain data models
│       ├── domain/financialEngine.ts    # Deterministic INR math, 8-stage Digital Twin, Period Analytics & Risk Engine
│       ├── data/sampleWorkspaceSeeder.ts# 6 Multi-currency orders, 6 assessments, 30 docs, 6 payments, localStorage isolation
│       ├── services/demoPaymentService.ts# Zero-Cost Demo Mode payment lifecycle & idempotency
│       ├── services/aiIntelligenceService.ts # AI PO Extraction, Financial Viability Analyst & Natural-Language Query Engine
│       ├── styles/fintech.css           # Responsive Warm Minimal Fintech design system
│       ├── App.tsx                      # Complete 8-module Web SaaS interface
│       └── main.tsx
├── firestore.rules                      # Strict per-user Firestore security rules
└── .env.example                         # Environment configuration template (PayPal backend optional)
```

---

## 2. Web Application Setup & Deployment (`/web`)

### Local Development
1. Install dependencies:
   ```bash
   npm --prefix web install
   ```
2. Launch the Vite development server:
   ```bash
   npm --prefix web run dev
   ```
   Open `http://localhost:5173` in any desktop or mobile browser.

### Production Web Build & Static Deployment
1. Build the production bundle:
   ```bash
   npm --prefix web run build
   ```
   This compiles TypeScript (`tsc -b`) and generates a static production bundle in `web/dist/`.
2. Preview the production bundle locally:
   ```bash
   npm --prefix web run preview
   ```
3. Deploy `web/dist/` directly to **Firebase Hosting**, **Vercel**, **Netlify**, **Cloudflare Pages**, or **GitHub Pages** (`vite.config.ts` is pre-configured with `base: './'` for portable asset resolution).

---

## 3. Android Application Build & Test (`/app`)

1. **Zero-Cost Demo Mode by Default**: No `PAYPAL_BACKEND_BASE_URL`, PayPal Client Secret, or Firebase Blaze upgrade is required to build or run the Sample Business Workspace.
2. **Build Debug APK**:
   ```bash
   gradle assembleDebug
   ```
3. **Run Unit & Robolectric Tests**:
   ```bash
   gradle :app:testDebugUnitTest
   ```

---

## 4. Capability Matrix (Implemented, Simulated & Optional Capabilities)

| Capability / Module | Web (`/web`) | Android (`/app`) | Implementation Status & Transparency Notes |
| :--- | :---: | :---: | :--- |
| **1. Executive Dashboard (`1D`, `1W`, `1M`, `3M`, `1Y`, `ALL`)** | ✅ Implemented | ✅ Implemented | Real-time period filtering, prior-period growth comparison, dual-series SVG/Canvas telemetry curve, and destination corridor breakdown. |
| **2. Export Order Management** | ✅ Implemented | ✅ Implemented | Full Create, Status Update, Search/Filter, Delete, and CSV ledger export. |
| **3. AI Order Intelligence (PO Extraction)** | ✅ Implemented | ✅ Implemented | Deterministic local PO parser in Zero-Cost Demo Mode + optional live Gemini 2.5 Flash structured JSON extraction with human-in-the-loop confirmation. |
| **4. Autonomous AI Advisor & NL Query Engine** | ✅ Implemented | ✅ Implemented | Answers natural-language queries grounded strictly in live workspace orders, documents, and payment records without hallucinating external data. |
| **5. Financial Intelligence & What-If Simulator** | ✅ Implemented | ✅ Implemented | Deterministic INR math, FX/Advance/Fee calculation, expandable formula audit, and isolated hypothetical What-If sliders. |
| **6. Trade Risk Intelligence (`TradeRiskEngine`)** | ✅ Implemented | ✅ Implemented | Evaluates documentation, receivable settlement, and thin-margin risks with evidence and one-click action routing. |
| **7. 8-Stage Export Order Digital Twin** | ✅ Implemented | ✅ Implemented | End-to-end 8-stage readiness tracker with outstanding pre-shipment blockers and stage status chips. |
| **8. Payment Hub (`DemoPaymentService`)** | ⚡ Simulated (Demo Mode) | ⚡ Simulated (Demo Mode) | Supports `DEMO_PENDING`, `DEMO_CAPTURED`, `DEMO_FAILED`, `DEMO_CANCELLED`, and idempotent `DEMO-TXN-*` / `DEMO-CAP-*` references. Explicitly labeled: `"SIMULATED PAYMENT — NOT A REAL PAYPAL TRANSACTION"`. |
| **9. Document Centre & SHA-256 Hashing** | ✅ Implemented (Browser Local) | ✅ Implemented (Device Local) | Computes real `SHA-256` checksums (`crypto.subtle.digest` on Web, `MessageDigest` on Android), validates `<= 15 MB` PDF/Image size, and tracks versions (`v1`, `v2`). Explicitly labeled as locally stored and not officially certified by DGFT/Customs. |
| **10. Sample Business Workspace** | ✅ Implemented | ✅ Implemented | Pre-loaded with *Tamil Nadu Global Exports Pvt. Ltd.* (6 orders `ORDER-001`..`ORDER-006` in `EUR`, `USD`, `AED`, `GBP`, `JPY`, 6 financial assessments, 30 document records, 6 payments, and activity logs). |
| **11. Persistence, Reset & Isolation** | ✅ Implemented | ✅ Implemented | Sample workspace edits persist in `localStorage` (`niryat_sample_workspace_v1`) / `SharedPreferences` with one-click Reset, strictly isolated from Production Workspace (`niryat_production_workspace_v1` / Firestore). |
| **12. Live PayPal Backend / Customs ICEGATE API** | ⚠️ Optional / External | ⚠️ Optional / External | Live PayPal OAuth2 requires an external backend (`PAYPAL_BACKEND_BASE_URL`) so `PAYPAL_SANDBOX_CLIENT_SECRET` is never bundled in client code. Direct government ICEGATE/DGFT API filing is not connected. |

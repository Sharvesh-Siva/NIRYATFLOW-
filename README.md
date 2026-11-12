# NiryatFlow AI
### Intelligent Export Management Platform
**Move Trade Forward. Think Beyond Borders.**

NiryatFlow AI is a multi-platform export operations and trade-finance workspace designed to help exporters organise purchase orders, monitor export workflows, understand financial exposure, manage trade documents, assess operational risks, and test cross-border payment flows.

The repository contains a React/TypeScript web experience, a native Android application built with Kotlin and Jetpack Compose, an Express/Gemini/PayPal Sandbox server, and Firebase Cloud Functions for authenticated payment operations.

> **Project status:** This repository includes working application modules and sample business data. Some integrations require external credentials and deployment configuration. PayPal Sandbox code is present, but a successful end-to-end Sandbox transaction must be independently tested in the target environment before describing it as verified. Demo payments are simulations, not real transactions.

---

## Product overview

NiryatFlow AI brings key export workflows into one interface:

| Module | Purpose |
| --- | --- |
| Executive Dashboard | Portfolio overview, order performance, receivables, and time-period analytics |
| Export Order Management | Create, update, search, filter, and export order records |
| AI Purchase Order Intelligence | Extract structured trade details from purchase-order text, with human review |
| Export Order Digital Twin | Track readiness across an eight-stage export lifecycle |
| Shipment Intelligence | Review shipment and logistics information available in the workspace |
| Document Centre | Organise document records, versions, and SHA-256 integrity digests |
| Financial Intelligence | Calculate order-level financial assessments using deterministic business logic |
| What-If Simulator | Explore hypothetical changes to exchange rates, costs, and payment assumptions |
| Receivables & Cash Flow | Review advance, balance, outstanding, and collection information |
| Buyer Intelligence | Review buyer and portfolio information available in the workspace |
| Trade Risk Intelligence | Surface evidence-based documentation, receivable, and margin alerts |
| AI Advisor | Ask natural-language questions grounded in the supplied workspace data |
| Payment Hub | Separate simulated Demo Mode from PayPal Sandbox payment workflows |
| Reporting & Audit | Export operational/financial reports and review recorded activity |

### Important capability boundaries

- **Demo Mode:** Uses a deterministic local simulation. A demo capture is not a PayPal payment.
- **PayPal Sandbox:** Server-side Orders v2 integration code supports order creation, approval return handling, status lookup, capture, and cancellation in the Express server. Credentials and a reachable backend are required. Configure and test the complete buyer-approval/capture cycle before claiming it is operational.
- **Firebase Cloud Functions:** The `functions/` workspace contains authenticated Firebase Functions for PayPal Sandbox order creation and capture. Configure Firebase Authentication, Firestore, Secret Manager, and production return/cancel URLs before deployment. The current source contains example callback URLs that must be replaced.
- **Document integrity:** SHA-256 can detect a difference from a previously recorded digest. It does not prove the origin, authenticity, or legal validity of a document.
- **Shipment visibility:** The application displays shipment-related workspace information; live carrier tracking requires a connected logistics provider.
- **Compliance:** NiryatFlow AI is an operational support tool, not an official DGFT, Customs, ICEGATE, or legal filing system.

---

## Technology stack

| Layer | Technologies |
| --- | --- |
| Web UI | React, TypeScript, Vite, CSS |
| Android | Kotlin, Jetpack Compose, AndroidX |
| AI | Google Gemini via the `@google/genai` SDK |
| Server | Node.js, Express, TypeScript/TSX |
| Payments | PayPal REST API — Sandbox Orders v2 |
| Backend services | Firebase Admin SDK, Firebase Authentication, Cloud Firestore, Firebase Cloud Functions |
| Integrity | Web Crypto API / SHA-256; Android `MessageDigest` |
| Build & quality | npm, TypeScript, Gradle, Robolectric/JUnit |

---

## Repository structure

```text
.
├── app/                         # Native Android application (Kotlin + Compose)
│   └── src/
│       ├── main/                 # Screens, services, domain logic, assets
│       └── test/                 # Android unit/Robolectric tests
├── src/                         # Primary server-backed React web client
│   ├── components/               # Brand, intelligence, and payment UI
│   ├── data/                     # Sample workspace seeding
│   ├── domain/                   # Financial and trade-domain calculations
│   ├── services/                 # AI, demo payments, PayPal, reporting
│   ├── styles/                   # Fintech design system
│   └── types/                    # Shared TypeScript domain models
├── web/                         # Separate standalone Vite web workspace
├── functions/                   # Firebase Cloud Functions (Node.js 20)
├── public/                      # Public assets and packaged web resources
├── server.ts                    # Express API + Gemini + PayPal Sandbox routes
├── firestore.rules              # Firestore access-control rules
├── firebase.json                # Firebase emulator/deployment configuration
├── .env.example                 # Local environment-variable template
├── package.json                 # Primary web/server scripts and dependencies
└── README.md
```

The root application and `web/` are separate Vite workspaces with separate package manifests. The root scripts run the server-backed application. Use the `web/` instructions only when intentionally working on that standalone workspace.

---

## Getting started

### Prerequisites

- Node.js and npm (use a current LTS release compatible with the installed dependencies)
- Java and Android Studio for Android development
- Android SDK configured through Android Studio
- Optional: Firebase project, Gemini API key, and PayPal Developer Sandbox app for connected services

### 1. Run the primary web application

From the repository root:

```bash
npm install
npm run dev
```

The development server starts the Express API and Vite middleware. Open the local URL printed in the terminal (the server currently listens on port `3000`).

### 2. Build and type-check the web application

```bash
npm run lint
npm run build
```

- `npm run lint` runs the TypeScript compiler in no-emit mode.
- `npm run build` creates the Vite production assets in `dist/`.

To run the Express server against the production assets, build first and start the TypeScript server with the installed TSX runner:

```bash
NODE_ENV=production npx tsx server.ts
```

### 3. Run the standalone `web/` workspace

```bash
cd web
npm install
npm run dev
```

Build it with:

```bash
npm run build
```

### 4. Build and test Android

Open the repository in Android Studio, allow Gradle sync to complete, then run the `app` configuration on an emulator or device.

With Gradle installed and configured:

```bash
gradle assembleDebug
gradle :app:testDebugUnitTest
```

The debug APK is generated under `app/build/outputs/apk/debug/`.

### 5. Build Firebase Functions

```bash
cd functions
npm install
npm run build
```

Deploy only after configuring the Firebase project, Authentication, Firestore, and required secrets:

```bash
firebase deploy --only functions
```

---

## Configuration

Copy `.env.example` to `.env` for local server development and replace placeholders with your own values:

| Variable | Purpose |
| --- | --- |
| `GEMINI_API_KEY` | Server-side Gemini API access |
| `APP_URL` | Public application base URL used by the server |
| `PAYPAL_CLIENT_ID` | PayPal Sandbox REST app client ID |
| `PAYPAL_CLIENT_SECRET` | PayPal Sandbox REST app secret |
| `PAYPAL_ENV` | Sandbox environment selector |

Never commit `.env`, Firebase service-account files, signing keys, access tokens, or real credentials. The repository ignores local environment files and the local PayPal ledger; keep that protection in place.

For Firebase Functions, configure the secrets named `PAYPAL_SANDBOX_CLIENT_ID` and `PAYPAL_SANDBOX_CLIENT_SECRET` using Firebase/Google Cloud Secret Manager. These are distinct from the root Express server's `PAYPAL_CLIENT_ID` and `PAYPAL_CLIENT_SECRET` variables.

See [docs/CONFIGURATION.md](docs/CONFIGURATION.md) and [SECURITY.md](SECURITY.md) before enabling connected services.

---

## Sample workspace and data handling

The seeded demonstration workspace represents **Tamil Nadu Global Exports Pvt. Ltd.** and includes six sample export orders across multiple currencies, with related financial assessments, document records, payment examples, and activity data.

Sample records are for demonstration and testing; they are not live customer, buyer, shipment, or payment records. Workspace persistence differs by platform and mode. Review the application settings and source implementation before using the project with business data.

Financial calculations are implemented in a dedicated domain engine. AI responses are intended to interpret supplied workspace data, not replace authoritative calculations or invent missing records.

---

## Payment integration notes

The Payment Hub separates two flows:

1. **Demo Mode** — local deterministic simulation for demonstrating payment states without contacting PayPal.
2. **PayPal Sandbox** — calls PayPal's Sandbox REST API from a server-side component. The browser must never receive the Client Secret or OAuth access token.

The root Express server exposes health, configuration-status, order-creation, order-status, capture, and cancellation routes. The `functions/` directory contains Firebase Functions that validate Firebase ID tokens and use Secret Manager for PayPal credentials.

These are separate backend paths. Do not assume that configuring one automatically configures the other. The Firebase Functions source currently uses example return/cancel URLs; replace them with the deployed application's verified callback routes before production use. Review [SECURITY.md](SECURITY.md) and [docs/CONFIGURATION.md](docs/CONFIGURATION.md).

---

## Testing and verification

Run the checks relevant to the part you changed:

```bash
npm run lint
npm run build
gradle :app:testDebugUnitTest
```

For payment changes, also test Sandbox order creation, buyer approval, capture, cancellation, duplicate requests, invalid order/amount/currency, missing credentials, and PayPal API failures. A successful build is not proof of a successful live Sandbox transaction.

---

## Roadmap

- Complete and verify end-to-end PayPal Sandbox flows in the deployed environment.
- Replace placeholder payment callback URLs and finalise the chosen backend route.
- Connect live shipment events through a logistics provider.
- Expand document validation and export-compliance support with authoritative sources.
- Improve multi-user production data isolation, monitoring, and deployment automation.
- Add broader automated tests for web workflows, API validation, and payment edge cases.

---

## Contributing

Contributions and issue reports are welcome. Before submitting changes, read [CONTRIBUTING.md](CONTRIBUTING.md). Keep financial calculations deterministic, preserve Demo/Sandbox separation, and never add secrets or real customer data to commits.

## Security

Please do not report vulnerabilities in public issues. Follow the private reporting guidance in [SECURITY.md](SECURITY.md).

## License

No open-source license is currently declared in this repository. Unless a license is added by the project owner, all rights remain reserved and reuse is not automatically permitted.

---

**NiryatFlow AI — Move Trade Forward. Think Beyond Borders.**

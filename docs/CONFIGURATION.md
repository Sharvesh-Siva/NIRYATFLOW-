# Configuration Guide

This guide covers local development and optional external integrations. Keep real credentials out of source control.

## Root web/server application

1. Install root dependencies with `npm install`.
2. Copy `.env.example` to `.env`.
3. Configure only the services you intend to test.
4. Start the application with `npm run dev`.

### Environment variables

| Variable | Required for | Notes |
| --- | --- | --- |
| `GEMINI_API_KEY` | Live Gemini endpoints | Read by the Express server; keep server-side |
| `APP_URL` | Hosted callback/link generation | Set to the actual deployed application URL |
| `PAYPAL_CLIENT_ID` | PayPal Sandbox API | Sandbox REST application client ID |
| `PAYPAL_CLIENT_SECRET` | PayPal Sandbox API | Secret; never expose to the browser |
| `PAYPAL_ENV` | Environment declaration | This implementation currently targets Sandbox |

The root Express server uses the PayPal Sandbox base URL `https://api-m.sandbox.paypal.com`. The source reads credentials from the server process environment. A configured credential pair is necessary but does not, by itself, prove that checkout works; complete a buyer approval and capture test.

## Firebase Cloud Functions

The `functions/` workspace uses Node.js 20 and Firebase Functions v2. It declares these Secret Manager values:

- `PAYPAL_SANDBOX_CLIENT_ID`
- `PAYPAL_SANDBOX_CLIENT_SECRET`

Configure them using the Firebase CLI/Google Cloud Secret Manager workflow for your project. Do not copy the root `.env` file into a public or client bundle.

The current Cloud Function source contains example.com return and cancel URLs. Replace these with real, deployed callback endpoints and implement the corresponding return/cancel handling before using that flow. Confirm that Firebase Authentication and the Firestore `export_orders` and `payment_records` data model match the deployed application.

## Firebase project

Review `firebase.json` and `firestore.rules` before connecting a Firebase project. The checked-in configuration references a specific Firestore database identifier. Change it only deliberately and validate the rules against the intended schema. Never loosen access rules just to bypass an integration error.

## Android

Android build configuration reads local secrets through the Gradle Secrets plugin, using `.env` and `.env.example`. Keep production signing credentials and any API secrets out of the APK. Client applications should call a trusted backend for privileged operations.

## Verification checklist

- [ ] Application starts with no credentials configured in Demo Mode.
- [ ] Gemini endpoints report unavailable when the server key is absent and do not expose the key.
- [ ] PayPal Sandbox config status accurately reports whether server credentials are configured.
- [ ] Order amount and currency are validated on the server.
- [ ] Buyer approval, capture, cancellation, duplicate requests, and failed API responses are tested.
- [ ] Firebase ID token and order ownership checks are exercised for the Cloud Functions path.
- [ ] Callback URLs point to the deployed application and are handled correctly.

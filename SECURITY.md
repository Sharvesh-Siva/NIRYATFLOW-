# Security Policy

## Supported deployment posture

NiryatFlow AI is a development and demonstration project. Do not deploy it for real customer payments or sensitive export records without a security review and environment-specific hardening.

## Reporting a vulnerability

Please do not publish exploitable details in a public issue. Contact the repository owner privately through the contact method listed on their GitHub profile. Include:
- A concise description and affected component or route
- Reproduction steps and the potential impact
- Any suggested mitigation

Allow reasonable time for investigation and remediation before public disclosure.

## Credential handling

- Never commit `.env`, PayPal Client Secrets, Gemini API keys, Firebase service-account JSON, OAuth tokens, Android signing keys, or private certificates.
- Keep PayPal Client Secrets and Gemini API keys on trusted server-side infrastructure.
- Use Firebase/Google Cloud Secret Manager for Firebase Functions secrets.
- Rotate any credential that has been exposed in a commit, log, screenshot, prompt, or public build.
- Do not place secrets in `VITE_*` variables, browser storage, Android resources, or client-side source.

## Payment and API safeguards

- Demo Mode is simulated and must never be represented as a real settlement.
- Use PayPal Sandbox for integration testing; switch to Live only after a separate security and compliance review.
- The root Express API and Firebase Cloud Functions are separate backend implementations. Review authentication, authorization, rate limiting, request validation, callback URLs, and persistence before exposing either publicly.
- The Firebase Functions implementation verifies Firebase ID tokens and checks order ownership. The root Express routes should not be treated as production-authenticated solely because credentials are stored server-side.
- Replace example callback URLs in the Firebase Functions source before deployment.
- Verify transaction status and capture details from PayPal server responses. Never trust a client-side success flag.

## Data and privacy

Use synthetic/sample data in public demonstrations. Do not commit customer names, buyer contact details, bank information, shipping documents, or other personal or commercially sensitive records.

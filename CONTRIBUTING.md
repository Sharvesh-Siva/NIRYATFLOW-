# Contributing

Thanks for taking an interest in NiryatFlow AI.

## Before making changes

1. Open an issue describing the problem or proposed enhancement.
2. Identify whether the change affects the root web/server workspace, the standalone `web/` workspace, Android (`app/`), or Firebase Functions (`functions/`).
3. Keep changes scoped to the relevant workspace. These projects have separate manifests and build paths.

## Development checks

Run the checks relevant to your changes:

```bash
# Root web/server workspace
npm install
npm run lint
npm run build

# Standalone web workspace
cd web
npm install
npm run build

# Android (from repository root, with Gradle and Android SDK configured)
gradle assembleDebug
gradle :app:testDebugUnitTest

# Firebase Functions
cd functions
npm install
npm run build
```

## Engineering guidelines

- Preserve the separation between Demo Mode and PayPal Sandbox.
- Keep financial calculations deterministic and covered by tests where practical.
- Ground AI-generated summaries in supplied workspace data; do not invent order, payment, or compliance facts.
- Treat missing values as unknown rather than filling them with fabricated data.
- Keep secrets server-side and never commit local environment files or signing material.
- Do not weaken Firestore rules to make a feature work.
- Label sample data, simulated transactions, and unverified integrations clearly.
- Update the README or relevant documentation when commands, configuration, routes, or feature status change.

## Pull requests

A useful pull request includes:
- The problem and proposed solution
- The affected workspace(s)
- Screenshots or a short recording for UI changes
- Test/build results
- Any configuration or migration notes

Keep commits focused and avoid including generated build output unless it is explicitly required by the deployment workflow.

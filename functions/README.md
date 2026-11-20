# BukuKira Cloud Functions (Master Licence & Admin API)

HTTPS Callable Cloud Functions targeting the named Firestore database
`ai-studio-android-bukukira-4cf4342e-65eb-431d-b64e-10c2cb4d2180` in Firebase project
`gen-lang-client-0697977355`.

## Callable Endpoints

- **`activateMasterLicence({ businessId, licenceKey })`**
  - Requires authenticated Google Sign-In (`request.auth.uid`) belonging to `businesses/{businessId}`.
  - Reads `masterLicences/{licenceKey}` (server-only collection blocked from clients), validates status, expiry, optional `assignedEmail`, and `maxClaims`, then atomically upgrades `licences/{businessId}` to `PRO` / `ENTERPRISE` and records the claim.
- **`issueLicenceKey({ licenceKey?, plan, durationDays, maxDevices, maxMembers?, maxClaims?, assignedEmail?, notes? })`** *(Admin only)*
- **`extendLicence({ businessId?, licenceKey?, additionalDays?, newExpiryMillis?, newPlan?, maxDevices? })`** *(Admin only)*
- **`suspendLicence({ businessId?, licenceKey?, status?, reason? })`** *(Admin only)*

## Setup & Deployment

1. Install dependencies & run local unit tests:
   ```bash
   cd functions
   npm install
   npm test
   ```
2. Deploy Cloud Functions to Firebase:
   ```bash
   firebase deploy --only functions --project gen-lang-client-0697977355
   ```
3. Grant the developer `admin: true` Firebase Auth custom claim (using Application Default Credentials / Firebase Admin service account):
   ```bash
   node scripts/grant-admin.js cytan828@gmail.com
   ```

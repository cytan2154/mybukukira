/**
 * Firebase Web App Configuration for BukuKira.
 * Connects directly to the same Firebase project and named Firestore database as the Android app.
 *
 * SETUP (One-Time Security Step):
 * 1. Open Google Cloud Console -> Project "gen-lang-client-0697977355" -> APIs & Services -> Credentials.
 * 2. Click "+ Create Credentials" -> "API key" to create a SEPARATE Web-only key
 *    (do NOT add Website restrictions to your Android key in google-services.json).
 * 3. Under Application restrictions -> select "Websites" and allow ONLY your hosting domain:
 *      - GitHub Pages:     https://<your-username>.github.io/*
 *      - Firebase Hosting: https://gen-lang-client-0697977355.web.app/*
 *                          https://gen-lang-client-0697977355.firebaseapp.com/*
 * 4. Under API restrictions -> "Restrict key" -> select ONLY:
 *      - Identity Toolkit API
 *      - Token Service API
 *      - Cloud Firestore API
 * 5. Replace REPLACE_WITH_DOMAIN_RESTRICTED_WEB_API_KEY below with that restricted key,
 *    and add your hosting domain under Firebase Console -> Authentication -> Settings -> Authorized domains.
 */
export const DEFAULT_FIREBASE_CONFIG = {
  apiKey: 'AIzaSyBR-Hyqi-bclxHlnMfqQUadoEWRY8aSjEw',
  authDomain: 'gen-lang-client-0697977355.firebaseapp.com',
  projectId: 'gen-lang-client-0697977355',
  storageBucket: 'gen-lang-client-0697977355.firebasestorage.app',
  messagingSenderId: '872650444986',
  appId: '1:872650444986:web:63b3fd9d10791600ed7283'
};

export const FIRESTORE_DATABASE_ID =
  'ai-studio-android-bukukira-4cf4342e-65eb-431d-b64e-10c2cb4d2180';

export function getActiveFirebaseConfig() {
  return DEFAULT_FIREBASE_CONFIG;
}

export function isFirebaseWebConfigured(cfg = getActiveFirebaseConfig()) {
  const key = String(cfg?.apiKey || '');
  const appId = String(cfg?.appId || '');
  return Boolean(
    cfg &&
      key &&
      cfg.projectId &&
      cfg.authDomain &&
      appId &&
      !key.startsWith('PASTE_') &&
      !key.startsWith('REPLACE_') &&
      !key.startsWith('YOUR_') &&
      !appId.startsWith('PASTE_') &&
      !appId.startsWith('REPLACE_')
  );
}

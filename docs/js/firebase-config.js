/**
 * Firebase Web App Configuration for BukuKira.
 * Connects directly to the same Firebase project and named Firestore database as the Android app.
 *
 * SETUP:
 * 1. Open Firebase Console -> Project "gen-lang-client-0697977355" -> Project Settings -> Your apps -> Add app -> Web (</>).
 * 2. Replace `appId` (and `apiKey` if Firebase Console shows a separate Web key) below,
 *    OR enter your Web App config once inside the PWA setup screen on first launch.
 * 3. Add your hosting domain (e.g. `<username>.github.io` or `gen-lang-client-0697977355.web.app`)
 *    under Firebase Console -> Authentication -> Settings -> Authorized domains.
 */
export const DEFAULT_FIREBASE_CONFIG = {
  apiKey: 'AIzaSyDk3aJaRMr4bTaduZzRG4HxvErvCRpmN_M',
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
  return Boolean(
    cfg &&
      cfg.apiKey &&
      cfg.projectId &&
      cfg.authDomain &&
      cfg.appId &&
      !String(cfg.appId).startsWith('PASTE_')
  );
}

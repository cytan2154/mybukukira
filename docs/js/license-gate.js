const INSTALLATION_ID_REGEX = /^[a-f0-9]{32}$/;
const BUSINESS_ID_REGEX = /^[a-zA-Z0-9_\-]{1,128}$/;

function sanitizePrefsSuffix(raw) {
  return String(raw || '').replace(/[^a-zA-Z0-9_]/g, '_').slice(0, 64);
}

/**
 * Constant-time string comparison matching Android `FirebaseAuthManager.safeEquals`.
 */
export function safeEquals(a, b) {
  const left = String(a ?? '');
  const right = String(b ?? '');
  const len = Math.max(left.length, right.length);
  let diff = left.length ^ right.length;
  for (let i = 0; i < len; i++) {
    const ca = i < left.length ? left.charCodeAt(i) : 0;
    const cb = i < right.length ? right.charCodeAt(i) : 0;
    diff |= ca ^ cb;
  }
  return diff === 0;
}

/**
 * Returns a persistent 32-character lowercase hex installationId per signed-in user
 * on this browser / PWA container (`^[a-f0-9]{32}$`).
 */
export function getOrCreateInstallationId(uid) {
  const key = uid
    ? `bukukira_web_install_id_${sanitizePrefsSuffix(uid)}`
    : 'bukukira_web_install_id';
  try {
    const existing = localStorage.getItem(key);
    if (existing && INSTALLATION_ID_REGEX.test(existing)) {
      return existing;
    }
  } catch {
    // Ignore storage read errors
  }

  const bytes = new Uint8Array(16);
  if (typeof globalThis.crypto !== 'undefined' && globalThis.crypto.getRandomValues) {
    globalThis.crypto.getRandomValues(bytes);
  } else {
    for (let i = 0; i < 16; i++) {
      bytes[i] = Math.floor(Math.random() * 256);
    }
  }
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  try {
    localStorage.setItem(key, hex);
  } catch {
    // Ignore storage write errors
  }
  return hex;
}

/**
 * Produces a concise human-readable label (<= 100 chars) for `/devices/{installationId}`.
 */
export function detectWebDeviceName() {
  if (typeof navigator === 'undefined') return 'Web Browser';
  const ua = navigator.userAgent || '';
  let os = 'Web';
  if (/iPhone/i.test(ua)) os = 'iPhone';
  else if (/iPad|Macintosh/i.test(ua) && navigator.maxTouchPoints > 1) os = 'iPad';
  else if (/Android/i.test(ua)) os = 'Android Web';
  else if (/Macintosh|Mac OS X/i.test(ua)) os = 'Mac';
  else if (/Windows/i.test(ua)) os = 'Windows';
  else if (/Linux/i.test(ua)) os = 'Linux';

  let browser = 'Browser';
  if (/Edg\//i.test(ua)) browser = 'Edge';
  else if (/CriOS/i.test(ua) || (/Chrome\//i.test(ua) && !/Edg\//i.test(ua))) browser = 'Chrome';
  else if (/FxiOS|Firefox\//i.test(ua)) browser = 'Firefox';
  else if (/Safari\//i.test(ua)) browser = 'Safari';

  const isStandalone =
    (typeof window !== 'undefined' &&
      window.matchMedia &&
      window.matchMedia('(display-mode: standalone)').matches) ||
    Boolean(navigator.standalone);

  const label = `${os} ${browser}${isStandalone ? ' PWA' : ''}`.trim().slice(0, 100);
  return label || 'Web Browser';
}

export function isValidBusinessIdFormat(businessId) {
  return BUSINESS_ID_REGEX.test(String(businessId || '').trim());
}

/**
 * Validates a licence against all legacy BukuKira validation rules:
 * non-empty inputs, 32-char hex installationId, constant-time key match,
 * ACTIVE status, startDate <= now <= expiryDate, maxDevices >= 1, and slot availability.
 */
export function validateLegacyLicenseRecord({
  businessId,
  licenceKeyInput,
  installationId,
  licence,
  registeredDevices = [],
  nowMillis = Date.now()
}) {
  const cleanBizId = String(businessId || '').trim();
  const cleanKey = String(licenceKeyInput || '').trim();
  const cleanInstallId = String(installationId || '').trim();

  if (!cleanBizId || !cleanKey) {
    return {
      ok: false,
      code: 'LICENCE_REQUIRED',
      message: 'Business ID and Licence Key are required.'
    };
  }
  if (!INSTALLATION_ID_REGEX.test(cleanInstallId)) {
    return {
      ok: false,
      code: 'INSTALLATION_ID_REQUIRED',
      message: 'Valid 32-character hex installationId is required.'
    };
  }
  if (!licence || licence.businessId !== cleanBizId || !safeEquals(String(licence.licenceKey || '').trim(), cleanKey)) {
    return {
      ok: false,
      code: 'LICENCE_INVALID',
      message: 'Licence key does not match this Business ID.'
    };
  }
  if (licence.status !== 'ACTIVE') {
    return {
      ok: false,
      code: 'LICENCE_INACTIVE',
      message: `Licence status is ${licence.status}.`
    };
  }
  if ((licence.startDateMillis || 0) > 0 && nowMillis < licence.startDateMillis) {
    return {
      ok: false,
      code: 'LICENCE_NOT_STARTED',
      message: 'Licence validity window has not started yet.'
    };
  }
  if (nowMillis > (licence.expiryDateMillis || 0)) {
    return {
      ok: false,
      code: 'LICENCE_EXPIRED',
      message: 'Licence has expired.'
    };
  }
  if ((licence.maxDevices || 0) < 1) {
    return {
      ok: false,
      code: 'MAX_DEVICES_INVALID',
      message: 'Licence maxDevices configuration is invalid.'
    };
  }

  const alreadyRegistered = registeredDevices.some((d) => d.installationId === cleanInstallId);
  if (!alreadyRegistered && registeredDevices.length >= licence.maxDevices) {
    return {
      ok: false,
      code: 'MAX_DEVICES_REACHED',
      message: `Maximum device limit (${licence.maxDevices}) reached for this licence.`
    };
  }

  return { ok: true, licence };
}

/**
 * Combined reactive access gate matching Android `FirebaseAuthManager.evaluateBusinessAccess`.
 */
export function evaluateBusinessAccess({
  uid,
  workspace,
  licence,
  registeredDevices = [],
  installationId,
  nowMillis = Date.now()
}) {
  if (!uid) {
    return { status: 'Unauthenticated', isGranted: false };
  }
  if (!workspace) {
    return { status: 'WorkspaceRequired', uid, isGranted: false };
  }
  const members = Array.isArray(workspace.memberUids) ? workspace.memberUids : [];
  if (uid !== workspace.ownerId && !members.includes(uid)) {
    return {
      status: 'LicenseDenied',
      uid,
      businessId: workspace.id,
      code: 'MEMBERSHIP_REQUIRED',
      reason: `Signed-in Google account is not a member of ${workspace.businessName}.`,
      isGranted: false
    };
  }
  if (!licence) {
    return {
      status: 'LicenseRequired',
      uid,
      businessId: workspace.id,
      code: 'LICENCE_REQUIRED',
      reason: `No active licence record found for ${workspace.businessName}. Enter your Business ID & Licence Key to activate.`,
      isGranted: false
    };
  }
  if (licence.businessId !== workspace.id) {
    return {
      status: 'LicenseDenied',
      uid,
      businessId: workspace.id,
      code: 'LICENCE_INVALID',
      reason: `Licence record does not match workspace ${workspace.id}.`,
      isGranted: false
    };
  }
  if (licence.status !== 'ACTIVE') {
    return {
      status: 'LicenseDenied',
      uid,
      businessId: workspace.id,
      code: 'LICENCE_INACTIVE',
      reason: `Business licence is ${licence.status}.`,
      isGranted: false
    };
  }
  if ((licence.startDateMillis || 0) > 0 && nowMillis < licence.startDateMillis) {
    return {
      status: 'LicenseDenied',
      uid,
      businessId: workspace.id,
      code: 'LICENCE_NOT_STARTED',
      reason: 'Business licence start date has not arrived yet.',
      isGranted: false
    };
  }
  if (nowMillis > (licence.expiryDateMillis || 0)) {
    return {
      status: 'LicenseDenied',
      uid,
      businessId: workspace.id,
      code: 'LICENCE_EXPIRED',
      reason: 'Business licence has expired. Re-verify with a renewed licence key.',
      isGranted: false
    };
  }
  if ((licence.maxDevices || 0) < 1) {
    return {
      status: 'LicenseDenied',
      uid,
      businessId: workspace.id,
      code: 'MAX_DEVICES_INVALID',
      reason: `Invalid device limit (${licence.maxDevices}) on business licence.`,
      isGranted: false
    };
  }

  const cleanInstallId = String(installationId || '').trim();
  if (!INSTALLATION_ID_REGEX.test(cleanInstallId)) {
    return {
      status: 'LicenseRequired',
      uid,
      businessId: workspace.id,
      code: 'INSTALLATION_ID_REQUIRED',
      reason: 'Device installation ID unavailable. Verify your licence key to register this device.',
      isGranted: false
    };
  }

  const isRegisteredForUser = registeredDevices.some(
    (d) => d.installationId === cleanInstallId && (d.userId === uid || !d.userId)
  );
  if (!isRegisteredForUser) {
    if (registeredDevices.length >= licence.maxDevices) {
      return {
        status: 'LicenseDenied',
        uid,
        businessId: workspace.id,
        code: 'MAX_DEVICES_REACHED',
        reason: `Maximum device limit (${licence.maxDevices}) reached. Remove an old device or upgrade your plan.`,
        isGranted: false
      };
    }
    return {
      status: 'LicenseRequired',
      uid,
      businessId: workspace.id,
      code: 'DEVICE_NOT_REGISTERED',
      reason: `This browser/device is not yet activated for ${workspace.businessName}. Enter the Licence Key to unlock access.`,
      isGranted: false
    };
  }

  return {
    status: 'Granted',
    uid,
    businessId: workspace.id,
    licence,
    installationId: cleanInstallId,
    isGranted: true
  };
}

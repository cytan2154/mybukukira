const crypto = require('node:crypto');

const FIRESTORE_DATABASE_ID =
  process.env.BUKUKIRA_FIRESTORE_DB ||
  'ai-studio-android-bukukira-4cf4342e-65eb-431d-b64e-10c2cb4d2180';

const VALID_ID_REGEX = /^[a-zA-Z0-9_-]{1,128}$/;
const VALID_KEY_REGEX = /^[a-zA-Z0-9_-]{6,64}$/;
const ALLOWED_PLANS = new Set(['TRIAL', 'PRO', 'ENTERPRISE']);
const ALLOWED_STATUSES = new Set(['ACTIVE', 'SUSPENDED', 'EXPIRED']);
const DAY_MS = 86_400_000;

class ServiceError extends Error {
  constructor(code, message) {
    super(message);
    this.code = code;
  }
}

function toMillis(value) {
  if (value == null) return 0;
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (value instanceof Date) return value.getTime();
  if (typeof value.toMillis === 'function') return value.toMillis();
  if (typeof value.toDate === 'function') return value.toDate().getTime();
  if (typeof value._seconds === 'number') return value._seconds * 1000;
  return 0;
}

function defaultMaxDevicesForPlan(plan) {
  if (plan === 'ENTERPRISE') return 15;
  return 5;
}

function defaultMaxMembersForPlan(plan) {
  if (plan === 'ENTERPRISE') return 15;
  return 5;
}

function assertAuthenticated(auth) {
  if (!auth || typeof auth.uid !== 'string' || !auth.uid.trim()) {
    throw new ServiceError('unauthenticated', 'Google Sign-In authentication is required.');
  }
  return auth;
}

function assertDeveloperAdmin(auth) {
  const authed = assertAuthenticated(auth);
  if (authed.token?.admin !== true) {
    throw new ServiceError(
      'permission-denied',
      'Developer admin custom claim (admin: true) is required.'
    );
  }
  return authed;
}

function generateMasterLicenceKey(plan) {
  const tokenA = crypto.randomBytes(4).toString('hex').toUpperCase();
  const tokenB = crypto.randomBytes(2).toString('hex').toUpperCase();
  return `BK-${plan}-${tokenA}-${tokenB}`;
}

/**
 * Validates a master key under `masterLicences/{licenceKey}` and upgrades
 * `licences/{businessId}` + `businesses/{businessId}` inside a Firestore transaction.
 */
async function activateMasterLicence(db, auth, data, deps = {}) {
  const authed = assertAuthenticated(auth);
  const nowMs = typeof deps.nowMs === 'function' ? deps.nowMs() : Date.now();
  const makeTimestamp = deps.makeTimestamp || ((ms) => new Date(ms));
  const serverTimestamp = deps.serverTimestamp || (() => new Date(nowMs));

  const businessId = typeof data?.businessId === 'string' ? data.businessId.trim() : '';
  const licenceKey = typeof data?.licenceKey === 'string' ? data.licenceKey.trim() : '';

  if (!VALID_ID_REGEX.test(businessId)) {
    throw new ServiceError('invalid-argument', 'Valid businessId is required.');
  }
  if (!VALID_KEY_REGEX.test(licenceKey)) {
    throw new ServiceError('invalid-argument', 'Valid master licenceKey (6-64 chars) is required.');
  }

  const businessRef = db.collection('businesses').doc(businessId);
  const licenceRef = db.collection('licences').doc(businessId);
  const masterRef = db.collection('masterLicences').doc(licenceKey);

  return db.runTransaction(async (tx) => {
    const [bizSnap, licSnap, masterSnap] = await Promise.all([
      tx.get(businessRef),
      tx.get(licenceRef),
      tx.get(masterRef),
    ]);

    if (!bizSnap.exists) {
      throw new ServiceError('not-found', `Business workspace ${businessId} was not found.`);
    }
    const biz = bizSnap.data();
    const memberUids = Array.isArray(biz.memberUids) ? biz.memberUids : [];
    if (biz.ownerId !== authed.uid && !memberUids.includes(authed.uid)) {
      throw new ServiceError(
        'permission-denied',
        'Your Google account is not a member of this business workspace.'
      );
    }

    if (!masterSnap.exists) {
      throw new ServiceError('not-found', 'Master licence key not found.');
    }
    const master = masterSnap.data();

    if (master.status !== 'ACTIVE') {
      throw new ServiceError(
        'failed-precondition',
        `Master licence key is ${master.status || 'INACTIVE'}.`
      );
    }

    const masterExpiryMs = toMillis(master.expiryDate);
    if (masterExpiryMs > 0 && masterExpiryMs <= nowMs) {
      throw new ServiceError('failed-precondition', 'Master licence key has expired.');
    }

    const assignedEmail =
      typeof master.assignedEmail === 'string' ? master.assignedEmail.trim().toLowerCase() : '';
    const callerEmail =
      typeof authed.token?.email === 'string' ? authed.token.email.trim().toLowerCase() : '';
    if (assignedEmail && assignedEmail !== callerEmail) {
      throw new ServiceError(
        'permission-denied',
        'This master licence key is restricted to a different Google account email.'
      );
    }

    const claimedList = Array.isArray(master.claimedByBusinessIds)
      ? [...master.claimedByBusinessIds]
      : [];
    const maxClaims = Number.isInteger(master.maxClaims) && master.maxClaims >= 1 ? master.maxClaims : 1;
    const alreadyClaimedByBiz = claimedList.includes(businessId);
    if (!alreadyClaimedByBiz && claimedList.length >= maxClaims) {
      throw new ServiceError(
        'resource-exhausted',
        `Master licence key has reached its maximum claim limit (${maxClaims}).`
      );
    }

    const targetPlan = ALLOWED_PLANS.has(master.plan) ? master.plan : 'PRO';
    const durationDays =
      Number.isFinite(Number(master.durationDays)) && Number(master.durationDays) >= 1
        ? Number(master.durationDays)
        : 365;
    const maxDevices =
      Number.isInteger(master.maxDevices) && master.maxDevices >= 1 && master.maxDevices <= 50
        ? master.maxDevices
        : defaultMaxDevicesForPlan(targetPlan);
    const maxMembers =
      Number.isInteger(master.maxMembers) && master.maxMembers >= 1 && master.maxMembers <= 15
        ? master.maxMembers
        : defaultMaxMembersForPlan(targetPlan);

    const startMs = nowMs - 1000;
    const computedExpiryMs =
      masterExpiryMs > nowMs ? masterExpiryMs : nowMs + durationDays * DAY_MS;

    const existingLic = licSnap.exists ? licSnap.data() : null;
    const mergedMembers = Array.from(
      new Set([biz.ownerId, ...memberUids, ...(existingLic?.memberUids || [])].filter(Boolean))
    );

    const nowTs = serverTimestamp();
    if (existingLic) {
      tx.update(licenceRef, {
        licenceKey,
        status: 'ACTIVE',
        plan: targetPlan,
        startDate: makeTimestamp(startMs),
        expiryDate: makeTimestamp(computedExpiryMs),
        maxDevices,
        memberUids: mergedMembers,
        updatedAt: nowTs,
      });
    } else {
      tx.set(licenceRef, {
        ownerId: biz.ownerId,
        memberUids: mergedMembers,
        licenceKey,
        status: 'ACTIVE',
        plan: targetPlan,
        startDate: makeTimestamp(startMs),
        expiryDate: makeTimestamp(computedExpiryMs),
        maxDevices,
        createdAt: nowTs,
        updatedAt: nowTs,
      });
    }

    if ((biz.maxMembers || 5) < maxMembers) {
      tx.update(businessRef, {
        maxMembers,
        updatedAt: nowTs,
      });
    }

    if (!alreadyClaimedByBiz) {
      claimedList.push(businessId);
    }
    tx.update(masterRef, {
      claimedByBusinessIds: claimedList,
      claimCount: claimedList.length,
      lastClaimedAt: nowTs,
      lastClaimedByUid: authed.uid,
      updatedAt: nowTs,
    });

    return {
      ok: true,
      businessId,
      licenceKey,
      plan: targetPlan,
      status: 'ACTIVE',
      maxDevices,
      maxMembers,
      startDateMillis: startMs,
      expiryDateMillis: computedExpiryMs,
    };
  });
}

/**
 * Developer Admin Action: Issues a new commercial key in `masterLicences/{licenceKey}`.
 */
async function issueLicenceKey(db, auth, data, deps = {}) {
  const authed = assertDeveloperAdmin(auth);
  const nowMs = typeof deps.nowMs === 'function' ? deps.nowMs() : Date.now();
  const makeTimestamp = deps.makeTimestamp || ((ms) => new Date(ms));
  const serverTimestamp = deps.serverTimestamp || (() => new Date(nowMs));

  const plan = typeof data?.plan === 'string' ? data.plan.trim().toUpperCase() : 'PRO';
  if (!ALLOWED_PLANS.has(plan)) {
    throw new ServiceError('invalid-argument', 'plan must be TRIAL, PRO, or ENTERPRISE.');
  }

  const durationDays = Number(data?.durationDays ?? (plan === 'TRIAL' ? 30 : 365));
  if (!Number.isFinite(durationDays) || durationDays < 1 || durationDays > 3650) {
    throw new ServiceError('invalid-argument', 'durationDays must be between 1 and 3650.');
  }

  const maxDevices = Number(data?.maxDevices ?? defaultMaxDevicesForPlan(plan));
  if (!Number.isInteger(maxDevices) || maxDevices < 1 || maxDevices > 50) {
    throw new ServiceError('invalid-argument', 'maxDevices must be an integer between 1 and 50.');
  }

  const maxMembers = Number(data?.maxMembers ?? defaultMaxMembersForPlan(plan));
  if (!Number.isInteger(maxMembers) || maxMembers < 1 || maxMembers > 15) {
    throw new ServiceError('invalid-argument', 'maxMembers must be an integer between 1 and 15.');
  }

  const maxClaims = Number(data?.maxClaims ?? 1);
  if (!Number.isInteger(maxClaims) || maxClaims < 1 || maxClaims > 100) {
    throw new ServiceError('invalid-argument', 'maxClaims must be an integer between 1 and 100.');
  }

  const rawKey = typeof data?.licenceKey === 'string' ? data.licenceKey.trim() : '';
  const licenceKey = rawKey || generateMasterLicenceKey(plan);
  if (!VALID_KEY_REGEX.test(licenceKey)) {
    throw new ServiceError(
      'invalid-argument',
      'licenceKey must be 6-64 alphanumeric, underscore, or hyphen characters.'
    );
  }

  const expiryMs =
    Number.isFinite(Number(data?.expiryMillis)) && Number(data.expiryMillis) > nowMs
      ? Number(data.expiryMillis)
      : nowMs + Math.round(durationDays) * DAY_MS;

  const assignedEmail =
    typeof data?.assignedEmail === 'string' && data.assignedEmail.trim()
      ? data.assignedEmail.trim().toLowerCase().slice(0, 200)
      : null;
  const notes = typeof data?.notes === 'string' ? data.notes.trim().slice(0, 300) : '';

  const masterRef = db.collection('masterLicences').doc(licenceKey);
  const existing = await masterRef.get();
  if (existing.exists) {
    throw new ServiceError('already-exists', `Master licence key ${licenceKey} already exists.`);
  }

  const nowTs = serverTimestamp();
  const payload = {
    licenceKey,
    plan,
    status: 'ACTIVE',
    durationDays: Math.round(durationDays),
    expiryDate: makeTimestamp(expiryMs),
    maxDevices,
    maxMembers,
    maxClaims,
    claimCount: 0,
    claimedByBusinessIds: [],
    ...(assignedEmail ? { assignedEmail } : {}),
    ...(notes ? { notes } : {}),
    issuedByUid: authed.uid,
    createdAt: nowTs,
    updatedAt: nowTs,
  };

  await masterRef.set(payload);

  return {
    ok: true,
    licenceKey,
    plan,
    status: 'ACTIVE',
    durationDays: Math.round(durationDays),
    expiryDateMillis: expiryMs,
    maxDevices,
    maxMembers,
    maxClaims,
    assignedEmail,
  };
}

/**
 * Developer Admin Action: Extends or renews `licences/{businessId}` and/or `masterLicences/{licenceKey}`.
 */
async function extendLicence(db, auth, data, deps = {}) {
  assertDeveloperAdmin(auth);
  const nowMs = typeof deps.nowMs === 'function' ? deps.nowMs() : Date.now();
  const makeTimestamp = deps.makeTimestamp || ((ms) => new Date(ms));
  const serverTimestamp = deps.serverTimestamp || (() => new Date(nowMs));

  const businessId = typeof data?.businessId === 'string' ? data.businessId.trim() : '';
  const licenceKey = typeof data?.licenceKey === 'string' ? data.licenceKey.trim() : '';

  if (!businessId && !licenceKey) {
    throw new ServiceError('invalid-argument', 'Provide businessId and/or licenceKey to extend.');
  }
  if (businessId && !VALID_ID_REGEX.test(businessId)) {
    throw new ServiceError('invalid-argument', 'Invalid businessId.');
  }
  if (licenceKey && !VALID_KEY_REGEX.test(licenceKey)) {
    throw new ServiceError('invalid-argument', 'Invalid licenceKey.');
  }

  const additionalDays =
    data?.additionalDays !== undefined ? Number(data.additionalDays) : undefined;
  const newExpiryMillisInput =
    data?.newExpiryMillis !== undefined ? Number(data.newExpiryMillis) : undefined;

  if (additionalDays === undefined && newExpiryMillisInput === undefined) {
    throw new ServiceError('invalid-argument', 'Provide additionalDays or newExpiryMillis.');
  }
  if (additionalDays !== undefined && (!Number.isFinite(additionalDays) || additionalDays <= 0 || additionalDays > 3650)) {
    throw new ServiceError('invalid-argument', 'additionalDays must be between 1 and 3650.');
  }
  if (newExpiryMillisInput !== undefined && (!Number.isFinite(newExpiryMillisInput) || newExpiryMillisInput <= nowMs)) {
    throw new ServiceError('invalid-argument', 'newExpiryMillis must be a future timestamp.');
  }

  const newPlan = typeof data?.newPlan === 'string' ? data.newPlan.trim().toUpperCase() : null;
  if (newPlan && !ALLOWED_PLANS.has(newPlan)) {
    throw new ServiceError('invalid-argument', 'newPlan must be TRIAL, PRO, or ENTERPRISE.');
  }

  const maxDevices = data?.maxDevices !== undefined ? Number(data.maxDevices) : null;
  if (maxDevices !== null && (!Number.isInteger(maxDevices) || maxDevices < 1 || maxDevices > 50)) {
    throw new ServiceError('invalid-argument', 'maxDevices must be an integer between 1 and 50.');
  }

  const results = {};
  const nowTs = serverTimestamp();

  if (businessId) {
    const licRef = db.collection('licences').doc(businessId);
    const snap = await licRef.get();
    if (!snap.exists) {
      throw new ServiceError('not-found', `Business licence ${businessId} not found.`);
    }
    const existing = snap.data();
    const baseMs = Math.max(toMillis(existing.expiryDate), nowMs);
    const computedExpiryMs =
      newExpiryMillisInput !== undefined
        ? newExpiryMillisInput
        : baseMs + additionalDays * DAY_MS;

    const updates = {
      status: 'ACTIVE',
      expiryDate: makeTimestamp(computedExpiryMs),
      updatedAt: nowTs,
    };
    if (newPlan) updates.plan = newPlan;
    if (maxDevices !== null) updates.maxDevices = maxDevices;

    await licRef.update(updates);
    results.businessLicence = {
      businessId,
      status: 'ACTIVE',
      plan: newPlan || existing.plan,
      maxDevices: maxDevices !== null ? maxDevices : existing.maxDevices,
      expiryDateMillis: computedExpiryMs,
    };
  }

  if (licenceKey) {
    const masterRef = db.collection('masterLicences').doc(licenceKey);
    const snap = await masterRef.get();
    if (!snap.exists) {
      throw new ServiceError('not-found', `Master licence ${licenceKey} not found.`);
    }
    const existing = snap.data();
    const baseMs = Math.max(toMillis(existing.expiryDate), nowMs);
    const computedExpiryMs =
      newExpiryMillisInput !== undefined
        ? newExpiryMillisInput
        : baseMs + additionalDays * DAY_MS;

    const updates = {
      status: 'ACTIVE',
      expiryDate: makeTimestamp(computedExpiryMs),
      updatedAt: nowTs,
    };
    if (newPlan) updates.plan = newPlan;
    if (maxDevices !== null) updates.maxDevices = maxDevices;

    await masterRef.update(updates);
    results.masterLicence = {
      licenceKey,
      status: 'ACTIVE',
      plan: newPlan || existing.plan,
      maxDevices: maxDevices !== null ? maxDevices : existing.maxDevices,
      expiryDateMillis: computedExpiryMs,
    };
  }

  return { ok: true, ...results };
}

/**
 * Developer Admin Action: Suspends, expires, or reactivates a business and/or master licence.
 */
async function suspendLicence(db, auth, data, deps = {}) {
  const authed = assertDeveloperAdmin(auth);
  const nowMs = typeof deps.nowMs === 'function' ? deps.nowMs() : Date.now();
  const serverTimestamp = deps.serverTimestamp || (() => new Date(nowMs));

  const businessId = typeof data?.businessId === 'string' ? data.businessId.trim() : '';
  const licenceKey = typeof data?.licenceKey === 'string' ? data.licenceKey.trim() : '';
  const targetStatus =
    typeof data?.status === 'string' ? data.status.trim().toUpperCase() : 'SUSPENDED';
  const reason = typeof data?.reason === 'string' ? data.reason.trim().slice(0, 300) : '';

  if (!businessId && !licenceKey) {
    throw new ServiceError('invalid-argument', 'Provide businessId and/or licenceKey.');
  }
  if (businessId && !VALID_ID_REGEX.test(businessId)) {
    throw new ServiceError('invalid-argument', 'Invalid businessId.');
  }
  if (licenceKey && !VALID_KEY_REGEX.test(licenceKey)) {
    throw new ServiceError('invalid-argument', 'Invalid licenceKey.');
  }
  if (!ALLOWED_STATUSES.has(targetStatus)) {
    throw new ServiceError('invalid-argument', 'status must be ACTIVE, SUSPENDED, or EXPIRED.');
  }

  const nowTs = serverTimestamp();
  const results = {};

  if (businessId) {
    const licRef = db.collection('licences').doc(businessId);
    const snap = await licRef.get();
    if (!snap.exists) {
      throw new ServiceError('not-found', `Business licence ${businessId} not found.`);
    }
    await licRef.update({
      status: targetStatus,
      ...(reason ? { suspensionReason: reason } : {}),
      statusUpdatedByUid: authed.uid,
      updatedAt: nowTs,
    });
    results.businessId = businessId;
  }

  if (licenceKey) {
    const masterRef = db.collection('masterLicences').doc(licenceKey);
    const snap = await masterRef.get();
    if (!snap.exists) {
      throw new ServiceError('not-found', `Master licence ${licenceKey} not found.`);
    }
    await masterRef.update({
      status: targetStatus,
      ...(reason ? { suspensionReason: reason } : {}),
      statusUpdatedByUid: authed.uid,
      updatedAt: nowTs,
    });
    results.licenceKey = licenceKey;
  }

  return {
    ok: true,
    status: targetStatus,
    ...results,
  };
}

module.exports = {
  FIRESTORE_DATABASE_ID,
  ServiceError,
  activateMasterLicence,
  issueLicenceKey,
  extendLicence,
  suspendLicence,
};

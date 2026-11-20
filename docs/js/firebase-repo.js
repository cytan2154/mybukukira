import { initializeApp, getApps } from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-app.js';
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  signInWithRedirect,
  getRedirectResult,
  signOut as fbSignOut,
  onAuthStateChanged
} from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-auth.js';
import {
  getFirestore,
  initializeFirestore,
  persistentLocalCache,
  persistentMultipleTabManager,
  doc,
  collection,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  deleteDoc,
  query,
  where,
  onSnapshot,
  writeBatch,
  serverTimestamp,
  Timestamp,
  arrayUnion,
  terminate,
  clearIndexedDbPersistence
} from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-firestore.js';

import { FIRESTORE_DATABASE_ID, getActiveFirebaseConfig, isFirebaseWebConfigured } from './firebase-config.js';
import { sampleTransactions } from './accounting-catalog.js';
import { defaultBeginningBalances } from './accounting-engine.js';
import {
  detectWebDeviceName,
  getOrCreateInstallationId,
  isValidBusinessIdFormat,
  validateLegacyLicenseRecord
} from './license-gate.js';

let appInstance = null;
let authInstance = null;
let dbInstance = null;

function randomIdHex(len = 16) {
  const bytes = new Uint8Array(Math.ceil(len / 2));
  if (globalThis.crypto?.getRandomValues) {
    globalThis.crypto.getRandomValues(bytes);
  } else {
    for (let i = 0; i < bytes.length; i++) bytes[i] = Math.floor(Math.random() * 256);
  }
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('').slice(0, len);
}

export async function initFirebaseClients() {
  const cfg = getActiveFirebaseConfig();
  if (!isFirebaseWebConfigured(cfg)) {
    return { ready: false, reason: 'CONFIG_REQUIRED' };
  }

  if (!appInstance) {
    appInstance = getApps().length ? getApps()[0] : initializeApp(cfg);
  }
  if (!authInstance) {
    authInstance = getAuth(appInstance);
    try {
      await getRedirectResult(authInstance);
    } catch (err) {
      console.warn('Redirect sign-in check:', err?.message || err);
    }
  }
  if (!dbInstance) {
    try {
      dbInstance = initializeFirestore(
        appInstance,
        {
          localCache: persistentLocalCache({
            tabManager: persistentMultipleTabManager()
          })
        },
        FIRESTORE_DATABASE_ID
      );
    } catch {
      // Fallback if Safari private mode or persistent cache was already initialized
      dbInstance = getFirestore(appInstance, FIRESTORE_DATABASE_ID);
    }
  }
  return { ready: true, app: appInstance, auth: authInstance, db: dbInstance };
}

export function getClients() {
  if (!authInstance || !dbInstance) {
    throw new Error('Firebase is not initialized yet.');
  }
  return { auth: authInstance, db: dbInstance };
}

export function observeAuth(callback) {
  const { auth } = getClients();
  return onAuthStateChanged(auth, callback);
}

export async function signInWithGoogleWeb({ forceRedirect = false } = {}) {
  const { auth } = getClients();
  const provider = new GoogleAuthProvider();
  provider.setCustomParameters({ prompt: 'select_account' });

  if (forceRedirect) {
    await signInWithRedirect(auth, provider);
    return { redirected: true };
  }

  try {
    const result = await signInWithPopup(auth, provider);
    return { user: result.user };
  } catch (err) {
    const code = err?.code || '';
    // iOS Safari standalone PWA or strict popup blockers require redirect flow
    if (
      code === 'auth/popup-blocked' ||
      code === 'auth/operation-not-supported-in-this-environment'
    ) {
      await signInWithRedirect(auth, provider);
      return { redirected: true };
    }
    throw err;
  }
}

export async function signOutWeb() {
  const { auth, db } = getClients();
  await fbSignOut(auth);
  try {
    await terminate(db);
    await clearIndexedDbPersistence(db);
    dbInstance = null;
  } catch {
    // Persistence cleanup is best-effort
  }
}

// ---- Snapshot Mappers ----

function snapToUserProfile(snap) {
  if (!snap.exists()) return null;
  const d = snap.data() || {};
  return {
    userId: d.userId || snap.id,
    email: d.email || '',
    displayName: d.displayName || '',
    preferredLanguage: ['en', 'zh'].includes(d.preferredLanguage) ? d.preferredLanguage : 'ms',
    activeBusinessId: d.activeBusinessId || null
  };
}

function snapToWorkspace(snap) {
  if (!snap.exists()) return null;
  const d = snap.data() || {};
  const ts = snap.get('updatedAt', { serverTimestamps: 'estimate' });
  return {
    id: snap.id,
    ownerId: d.ownerId || '',
    memberUids: Array.isArray(d.memberUids) ? d.memberUids : [],
    memberRoles: d.memberRoles && typeof d.memberRoles === 'object' ? d.memberRoles : {},
    maxMembers: Number(d.maxMembers) || 5,
    businessName: d.businessName || '',
    businessRegNo: d.businessRegNo || '',
    businessType: d.businessType || '',
    businessAddress: d.businessAddress || '',
    ownerName: d.ownerName || '',
    currency: d.currency || 'RM',
    financialYear: d.financialYear || '2026',
    closingStockByYear:
      d.closingStockByYear && typeof d.closingStockByYear === 'object' ? d.closingStockByYear : {},
    beginningBalances: defaultBeginningBalances(d.beginningBalances),
    lastInviteCode: d.lastInviteCode || null,
    updatedAtMillis: ts?.toMillis ? ts.toMillis() : 0
  };
}

function snapToLicence(snap) {
  if (!snap.exists()) return null;
  const d = snap.data() || {};
  return {
    businessId: snap.id,
    ownerId: d.ownerId || '',
    memberUids: Array.isArray(d.memberUids) ? d.memberUids : [],
    licenceKey: d.licenceKey || '',
    status: d.status || 'EXPIRED',
    plan: d.plan || 'TRIAL',
    startDateMillis: d.startDate?.toMillis ? d.startDate.toMillis() : 0,
    expiryDateMillis: d.expiryDate?.toMillis ? d.expiryDate.toMillis() : 0,
    maxDevices: Number(d.maxDevices) || 5
  };
}

function snapToDevice(snap) {
  if (!snap.exists()) return null;
  const d = snap.data() || {};
  const ts = snap.get('lastValidatedAt', { serverTimestamps: 'estimate' });
  return {
    installationId: d.installationId || snap.id,
    userId: d.userId || '',
    deviceName: d.deviceName || '',
    platform: d.platform || 'ANDROID',
    lastValidatedAtMillis: ts?.toMillis ? ts.toMillis() : 0
  };
}

function snapToOwnerPrivateInfo(snap) {
  if (!snap.exists()) return null;
  const d = snap.data() || {};
  return {
    ownerId: d.ownerId || '',
    ownerIdNumber: d.ownerIdNumber || '',
    ownerPhone: d.ownerPhone || '',
    ownerEmail: d.ownerEmail || ''
  };
}

function snapToTransaction(snap) {
  if (!snap.exists()) return null;
  const d = snap.data() || {};
  const ts = snap.get('updatedAt', { serverTimestamps: 'estimate' });
  return {
    id: snap.id,
    businessId: d.businessId || '',
    createdByUid: d.createdByUid || '',
    memberUids: Array.isArray(d.memberUids) ? d.memberUids : [],
    date: d.date || '',
    type: d.type || 'IN',
    categoryId: d.categoryId || 'exp_others',
    method: d.method || 'CASH',
    amount: Number(d.amount) || 0,
    note: d.note || '',
    partyName: d.partyName || null,
    invoiceNo: d.invoiceNo || null,
    disposalAssetType: d.disposalAssetType || null,
    disposalAssetCost: d.disposalAssetCost != null ? Number(d.disposalAssetCost) : null,
    updatedAtMillis: ts?.toMillis ? ts.toMillis() : 0,
    hasPendingWrites: Boolean(snap.metadata?.hasPendingWrites)
  };
}

function txToCreateMap(t) {
  const payload = {
    businessId: t.businessId,
    createdByUid: t.createdByUid,
    memberUids: t.memberUids,
    date: t.date,
    type: t.type,
    categoryId: String(t.categoryId || 'exp_others').slice(0, 60),
    method: t.method,
    amount: Number(t.amount) || 0,
    note: String(t.note || '').slice(0, 500),
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  };
  if (t.partyName && String(t.partyName).trim()) {
    payload.partyName = String(t.partyName).trim().slice(0, 120);
  }
  if (t.invoiceNo && String(t.invoiceNo).trim()) {
    payload.invoiceNo = String(t.invoiceNo).trim().slice(0, 60);
  }
  if (t.disposalAssetType) {
    payload.disposalAssetType = t.disposalAssetType;
  }
  if (t.disposalAssetCost != null && Number.isFinite(Number(t.disposalAssetCost)) && Number(t.disposalAssetCost) >= 0) {
    payload.disposalAssetCost = Number(t.disposalAssetCost);
  }
  return payload;
}

// ---- Users ----

export async function ensureUserProfile(uid, email, displayName) {
  const { db } = getClients();
  const ref = doc(db, 'users', uid);
  const snap = await getDoc(ref);
  const safeEmail = String(email || '').slice(0, 200);
  const safeName = (String(displayName || '').trim() || 'Pemilik Perniagaan').slice(0, 120);

  if (!snap.exists()) {
    await setDoc(ref, {
      userId: uid,
      email: safeEmail,
      displayName: safeName,
      preferredLanguage: 'ms',
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    });
  } else {
    await updateDoc(ref, {
      email: safeEmail,
      displayName: safeName,
      updatedAt: serverTimestamp()
    });
  }
}

export function observeUserProfile(uid, callback, onError) {
  const { db } = getClients();
  return onSnapshot(
    doc(db, 'users', uid),
    { includeMetadataChanges: true },
    (snap) => callback(snapToUserProfile(snap)),
    onError
  );
}

export async function updatePreferredLanguage(uid, lang) {
  const { db } = getClients();
  await updateDoc(doc(db, 'users', uid), {
    preferredLanguage: ['ms', 'en', 'zh'].includes(lang) ? lang : 'ms',
    updatedAt: serverTimestamp()
  });
}

export async function setActiveBusinessId(uid, businessId) {
  const { db } = getClients();
  await updateDoc(doc(db, 'users', uid), {
    activeBusinessId: businessId,
    updatedAt: serverTimestamp()
  });
}

// ---- Workspaces, Licences, Devices & Owner PII ----

export function observeMemberBusinesses(uid, callback, onError) {
  const { db } = getClients();
  const q = query(collection(db, 'businesses'), where('memberUids', 'array-contains', uid));
  return onSnapshot(
    q,
    { includeMetadataChanges: true },
    (snap) => {
      const list = snap.docs
        .map(snapToWorkspace)
        .filter(Boolean)
        .sort((a, b) => a.businessName.toLowerCase().localeCompare(b.businessName.toLowerCase()));
      callback(list);
    },
    onError
  );
}

export function observeLicence(businessId, callback, onError) {
  const { db } = getClients();
  return onSnapshot(
    doc(db, 'licences', businessId),
    { includeMetadataChanges: true },
    (snap) => callback(snapToLicence(snap)),
    onError
  );
}

export function observeRegisteredDevices(businessId, callback, onError) {
  const { db } = getClients();
  return onSnapshot(
    collection(db, 'licences', businessId, 'devices'),
    { includeMetadataChanges: true },
    (snap) => {
      const list = snap.docs
        .map(snapToDevice)
        .filter(Boolean)
        .sort((a, b) => (b.lastValidatedAtMillis || 0) - (a.lastValidatedAtMillis || 0));
      callback(list);
    },
    onError
  );
}

export function observeOwnerPrivateInfo(businessId, callback, onError) {
  const { db } = getClients();
  return onSnapshot(
    doc(db, 'businesses', businessId, 'private', 'ownerInfo'),
    { includeMetadataChanges: true },
    (snap) => callback(snapToOwnerPrivateInfo(snap)),
    onError
  );
}

async function upsertWebDeviceDoc(businessId, installId, uid, existsAlready) {
  const { db } = getClients();
  const devRef = doc(db, 'licences', businessId, 'devices', installId);
  const deviceLabel = detectWebDeviceName();
  if (existsAlready) {
    await updateDoc(devRef, {
      userId: uid,
      deviceName: deviceLabel,
      platform: 'WEB',
      lastValidatedAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    });
  } else {
    await setDoc(devRef, {
      installationId: installId,
      userId: uid,
      deviceName: deviceLabel,
      platform: 'WEB',
      lastValidatedAt: serverTimestamp(),
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    });
  }
}

export async function verifyAndActivateBusinessAccess(uid, businessIdInput, licenceKeyInput) {
  const { db } = getClients();
  const businessId = String(businessIdInput || '').trim();
  const licenceKey = String(licenceKeyInput || '').trim();
  if (!businessId || !licenceKey) {
    throw new Error('Business ID and Licence Key are required.');
  }
  if (!isValidBusinessIdFormat(businessId)) {
    throw new Error('Invalid Business ID format.');
  }

  const installId = getOrCreateInstallationId(uid);
  const licenceSnap = await getDoc(doc(db, 'licences', businessId));
  const licence = snapToLicence(licenceSnap);
  if (!licence) {
    throw new Error('Business licence not found or your Google account does not belong to this workspace.');
  }
  if (licence.ownerId !== uid && !licence.memberUids.includes(uid)) {
    throw new Error('Your Google account is not a member of this business workspace.');
  }

  const devSnap = await getDocs(collection(db, 'licences', businessId, 'devices'));
  const devices = devSnap.docs.map(snapToDevice).filter(Boolean);

  const validation = validateLegacyLicenseRecord({
    businessId,
    licenceKeyInput: licenceKey,
    installationId: installId,
    licence,
    registeredDevices: devices
  });
  if (!validation.ok) {
    throw new Error(validation.message || 'Licence validation failed.');
  }

  await upsertWebDeviceDoc(
    businessId,
    installId,
    uid,
    devices.some((d) => d.installationId === installId)
  );
  await setActiveBusinessId(uid, businessId);
  return { businessId, licence, installationId: installId };
}

export async function registerCurrentDevice(uid, businessId) {
  const { db } = getClients();
  const installId = getOrCreateInstallationId(uid);
  const licenceSnap = await getDoc(doc(db, 'licences', businessId));
  const licence = snapToLicence(licenceSnap);
  if (!licence) {
    throw new Error('Licence record not found.');
  }
  const devSnap = await getDocs(collection(db, 'licences', businessId, 'devices'));
  const devices = devSnap.docs.map(snapToDevice).filter(Boolean);
  const validation = validateLegacyLicenseRecord({
    businessId,
    licenceKeyInput: licence.licenceKey,
    installationId: installId,
    licence,
    registeredDevices: devices
  });
  if (!validation.ok) {
    throw new Error(validation.message || 'Device registration rejected by licence rules.');
  }
  await upsertWebDeviceDoc(
    businessId,
    installId,
    uid,
    devices.some((d) => d.installationId === installId)
  );
}

export async function removeRegisteredDevice(businessId, installationId) {
  const { db } = getClients();
  await deleteDoc(doc(db, 'licences', businessId, 'devices', installationId));
}

export async function createBusinessWorkspace(uid, input) {
  const { db } = getClients();
  const businessId = `biz_${randomIdHex(16)}`;
  const trialKey = `BK-TRIAL-${randomIdHex(8).toUpperCase()}`;

  const nowMs = Date.now();
  const startTs = Timestamp.fromMillis(nowMs - 1000);
  // 60s safety margin below server request.time + 30d to respect trial create rule
  const expiryTs = Timestamp.fromMillis(nowMs + 30 * 86_400_000 - 60_000);
  const stockMap = input.loadSampleData ? { '2026': 650.0 } : {};
  const safeYear = /^[0-9]{4}$/.test(String(input.financialYear || '')) ? input.financialYear : '2026';
  const safeCurrency = ['RM', 'SGD', 'USD', 'Rp'].includes(input.currency) ? input.currency : 'RM';

  const batch = writeBatch(db);
  batch.set(doc(db, 'licences', businessId), {
    ownerId: uid,
    memberUids: [uid],
    licenceKey: trialKey,
    status: 'ACTIVE',
    plan: 'TRIAL',
    startDate: startTs,
    expiryDate: expiryTs,
    maxDevices: 5,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  });

  batch.set(doc(db, 'businesses', businessId), {
    ownerId: uid,
    memberUids: [uid],
    memberRoles: { [uid]: 'OWNER' },
    maxMembers: 5,
    businessName: (String(input.businessName || '').trim() || 'Perniagaan Saya').slice(0, 120),
    businessRegNo: String(input.businessRegNo || '').trim().slice(0, 60),
    businessType: String(input.businessType || '').trim().slice(0, 100),
    businessAddress: String(input.businessAddress || '').trim().slice(0, 250),
    ownerName: (String(input.ownerName || '').trim() || 'Pemilik').slice(0, 120),
    currency: safeCurrency,
    financialYear: safeYear,
    closingStockByYear: stockMap,
    beginningBalances: defaultBeginningBalances(),
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  });

  batch.update(doc(db, 'users', uid), {
    activeBusinessId: businessId,
    updatedAt: serverTimestamp()
  });

  await batch.commit();

  if (input.loadSampleData) {
    const txs = sampleTransactions(businessId, uid, [uid]);
    const txBatch = writeBatch(db);
    for (const t of txs) {
      txBatch.set(doc(db, 'businesses', businessId, 'transactions', t.id), txToCreateMap(t));
    }
    await txBatch.commit();
  }

  await registerCurrentDevice(uid, businessId);
  return businessId;
}

export async function updateBusinessSettings(businessId, fields) {
  const { db } = getClients();
  const safeYear = /^[0-9]{4}$/.test(String(fields.financialYear || '')) ? fields.financialYear : '2026';
  const safeCurrency = ['RM', 'SGD', 'USD', 'Rp'].includes(fields.currency) ? fields.currency : 'RM';
  await updateDoc(doc(db, 'businesses', businessId), {
    businessName: (String(fields.businessName || '').trim() || 'Perniagaan Saya').slice(0, 120),
    businessRegNo: String(fields.businessRegNo || '').trim().slice(0, 60),
    businessType: String(fields.businessType || '').trim().slice(0, 100),
    businessAddress: String(fields.businessAddress || '').trim().slice(0, 250),
    ownerName: (String(fields.ownerName || '').trim() || 'Pemilik').slice(0, 120),
    currency: safeCurrency,
    financialYear: safeYear,
    updatedAt: serverTimestamp()
  });
}

export async function upsertOwnerPrivateInfo(uid, businessId, info) {
  const { db } = getClients();
  const ref = doc(db, 'businesses', businessId, 'private', 'ownerInfo');
  const snap = await getDoc(ref);
  const payload = {
    ownerIdNumber: String(info.ownerIdNumber || '').trim().slice(0, 40),
    ownerPhone: String(info.ownerPhone || '').trim().slice(0, 40),
    ownerEmail: String(info.ownerEmail || '').trim().slice(0, 120),
    updatedAt: serverTimestamp()
  };
  if (!snap.exists()) {
    await setDoc(ref, {
      ownerId: uid,
      ...payload,
      createdAt: serverTimestamp()
    });
  } else {
    await updateDoc(ref, payload);
  }
}

export async function updateClosingStock(businessId, currentMap, year, amount) {
  const { db } = getClients();
  const updated = { ...(currentMap || {}), [year]: Math.max(0, Number(amount) || 0) };
  await updateDoc(doc(db, 'businesses', businessId), {
    closingStockByYear: updated,
    updatedAt: serverTimestamp()
  });
}

export async function updateBeginningBalances(businessId, balances) {
  const { db } = getClients();
  await updateDoc(doc(db, 'businesses', businessId), {
    beginningBalances: defaultBeginningBalances(balances),
    updatedAt: serverTimestamp()
  });
}

// ---- Team Invites & Roles ----

export async function createTeamInvite(uid, businessId, businessName, role) {
  const { db } = getClients();
  if (role === 'OWNER') throw new Error('Cannot invite another OWNER.');
  const inviteCode = `BKINV_${randomIdHex(16).toUpperCase()}`;
  const expiresAt = Timestamp.fromMillis(Date.now() + 3 * 86_400_000);
  await setDoc(doc(db, 'invites', inviteCode), {
    businessId,
    businessName: String(businessName || '').slice(0, 120),
    role,
    createdByUid: uid,
    expiresAt,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  });
  return inviteCode;
}

export async function redeemInviteCode(uid, rawInviteCode) {
  const { db } = getClients();
  const code = String(rawInviteCode || '').trim();
  if (!/^[a-zA-Z0-9_\-]{16,128}$/.test(code)) {
    throw new Error('Invalid invite code format.');
  }
  const inviteRef = doc(db, 'invites', code);
  const inviteSnap = await getDoc(inviteRef);
  if (!inviteSnap.exists()) throw new Error('Invite code not found.');

  const d = inviteSnap.data() || {};
  if (d.redeemedByUid) throw new Error('This invite code has already been used.');
  const expMs = d.expiresAt?.toMillis ? d.expiresAt.toMillis() : 0;
  if (expMs <= Date.now()) throw new Error('This invite code has expired.');

  const batch = writeBatch(db);
  batch.update(inviteRef, {
    redeemedByUid: uid,
    updatedAt: serverTimestamp()
  });
  batch.update(doc(db, 'businesses', d.businessId), {
    memberUids: arrayUnion(uid),
    [`memberRoles.${uid}`]: d.role,
    lastInviteCode: code,
    updatedAt: serverTimestamp()
  });
  batch.update(doc(db, 'licences', d.businessId), {
    memberUids: arrayUnion(uid),
    updatedAt: serverTimestamp()
  });
  await batch.commit();

  await setActiveBusinessId(uid, d.businessId);
  try {
    await registerCurrentDevice(uid, d.businessId);
  } catch {
    // Device gate will inform user if slot limit reached
  }
  return d.businessId;
}

export async function updateMemberRole(workspace, targetUid, newRole) {
  const { db } = getClients();
  if (targetUid === workspace.ownerId || newRole === 'OWNER') {
    throw new Error('Owner role cannot be reassigned here.');
  }
  const nextRoles = { ...(workspace.memberRoles || {}), [targetUid]: newRole };
  await updateDoc(doc(db, 'businesses', workspace.id), {
    memberRoles: nextRoles,
    updatedAt: serverTimestamp()
  });
}

export async function removeTeamMember(ownerUid, workspace, targetUid) {
  const { db } = getClients();
  if (targetUid === workspace.ownerId) throw new Error('Cannot remove workspace owner.');

  const updatedUids = (workspace.memberUids || []).filter((id) => id !== targetUid);
  const updatedRoles = {};
  for (const [k, v] of Object.entries(workspace.memberRoles || {})) {
    if (k !== targetUid) updatedRoles[k] = v;
  }

  const batch = writeBatch(db);
  batch.update(doc(db, 'businesses', workspace.id), {
    memberUids: updatedUids,
    memberRoles: updatedRoles,
    updatedAt: serverTimestamp()
  });
  batch.update(doc(db, 'licences', workspace.id), {
    memberUids: updatedUids,
    updatedAt: serverTimestamp()
  });
  await batch.commit();

  const txSnap = await getDocs(
    query(
      collection(db, 'businesses', workspace.id, 'transactions'),
      where('memberUids', 'array-contains', ownerUid)
    )
  );
  const docs = txSnap.docs;
  for (let i = 0; i < docs.length; i += 400) {
    const txBatch = writeBatch(db);
    docs.slice(i, i + 400).forEach((d) => {
      txBatch.update(d.ref, {
        memberUids: updatedUids,
        updatedAt: serverTimestamp()
      });
    });
    await txBatch.commit();
  }
}

export async function syncMembersToTransactions(ownerUid, workspace) {
  const { db } = getClients();
  if (workspace.ownerId !== ownerUid) return;
  const txSnap = await getDocs(
    query(
      collection(db, 'businesses', workspace.id, 'transactions'),
      where('memberUids', 'array-contains', ownerUid)
    )
  );
  const needsUpdate = txSnap.docs.filter((d) => {
    const existing = d.get('memberUids') || [];
    return JSON.stringify(existing) !== JSON.stringify(workspace.memberUids);
  });
  for (let i = 0; i < needsUpdate.length; i += 400) {
    const batch = writeBatch(db);
    needsUpdate.slice(i, i + 400).forEach((d) => {
      batch.update(d.ref, {
        memberUids: workspace.memberUids,
        updatedAt: serverTimestamp()
      });
    });
    await batch.commit();
  }
}

// ---- Transactions ----

export function observeTransactions(businessId, uid, callback, onError) {
  const { db } = getClients();
  const q = query(
    collection(db, 'businesses', businessId, 'transactions'),
    where('memberUids', 'array-contains', uid)
  );
  return onSnapshot(
    q,
    { includeMetadataChanges: true },
    (snap) => {
      const list = snap.docs
        .map(snapToTransaction)
        .filter(Boolean)
        .sort((a, b) => {
          const dc = String(b.date || '').localeCompare(String(a.date || ''));
          if (dc !== 0) return dc;
          return (b.updatedAtMillis || 0) - (a.updatedAtMillis || 0);
        });
      callback(list);
    },
    onError
  );
}

export async function createTransaction(uid, workspace, input) {
  const { db } = getClients();
  const amount = Number(input.amount);
  if (!Number.isFinite(amount) || amount < 0) throw new Error('Amount must be non-negative.');
  if (!/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(String(input.date || ''))) {
    throw new Error('Date must be yyyy-MM-dd.');
  }

  const txId = `tx_${randomIdHex(16)}`;
  const payload = txToCreateMap({
    businessId: workspace.id,
    createdByUid: uid,
    memberUids: workspace.memberUids,
    date: input.date,
    type: input.type,
    categoryId: input.categoryId,
    method: input.method,
    amount,
    note: String(input.note || '').trim(),
    partyName: input.partyName || null,
    invoiceNo: input.invoiceNo || null,
    disposalAssetType: input.disposalAssetType || null,
    disposalAssetCost: input.disposalAssetCost ?? null
  });
  await setDoc(doc(db, 'businesses', workspace.id, 'transactions', txId), payload);
  return txId;
}

export async function deleteTransaction(businessId, txId) {
  const { db } = getClients();
  await deleteDoc(doc(db, 'businesses', businessId, 'transactions', txId));
}

export async function loadSampleTransactions(uid, workspace) {
  const { db } = getClients();
  const sample = sampleTransactions(workspace.id, uid, workspace.memberUids);
  const batch = writeBatch(db);
  for (const tx of sample) {
    batch.set(doc(db, 'businesses', workspace.id, 'transactions', tx.id), txToCreateMap(tx));
  }
  batch.update(doc(db, 'businesses', workspace.id), {
    closingStockByYear: { ...(workspace.closingStockByYear || {}), '2026': 650.0 },
    updatedAt: serverTimestamp()
  });
  await batch.commit();
}

export async function restoreBackup(uid, workspace, payload) {
  const { db } = getClients();
  if (workspace.ownerId === uid) {
    const updates = { updatedAt: serverTimestamp() };
    if (payload.businessName) updates.businessName = payload.businessName.slice(0, 120);
    if (payload.businessRegNo) updates.businessRegNo = payload.businessRegNo.slice(0, 60);
    if (payload.businessType) updates.businessType = payload.businessType.slice(0, 100);
    if (payload.businessAddress) updates.businessAddress = payload.businessAddress.slice(0, 250);
    if (payload.ownerName) updates.ownerName = payload.ownerName.slice(0, 120);
    if (payload.currency) updates.currency = payload.currency;
    if (payload.financialYear) updates.financialYear = payload.financialYear;
    if (payload.closingStockByYear && Object.keys(payload.closingStockByYear).length > 0) {
      updates.closingStockByYear = payload.closingStockByYear;
    }
    if (payload.beginningBalances) {
      updates.beginningBalances = defaultBeginningBalances(payload.beginningBalances);
    }
    await updateDoc(doc(db, 'businesses', workspace.id), updates);
  }

  const txs = payload.transactions || [];
  for (let i = 0; i < txs.length; i += 400) {
    const batch = writeBatch(db);
    txs.slice(i, i + 400).forEach((tx) => {
      batch.set(
        doc(db, 'businesses', workspace.id, 'transactions', tx.id),
        txToCreateMap({
          ...tx,
          businessId: workspace.id,
          createdByUid: uid,
          memberUids: workspace.memberUids
        })
      );
    });
    await batch.commit();
  }
  return txs.length;
}

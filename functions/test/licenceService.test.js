const test = require('node:test');
const assert = require('node:assert/strict');
const {
  ServiceError,
  activateMasterLicence,
  issueLicenceKey,
  extendLicence,
  suspendLicence,
} = require('../licenceService');

function createFakeDb(seed = {}) {
  const store = new Map();
  for (const [path, value] of Object.entries(seed)) {
    store.set(path, structuredClone(value));
  }

  function makeRef(path) {
    return {
      path,
      async get() {
        const data = store.get(path);
        return {
          exists: data !== undefined,
          data: () => (data === undefined ? undefined : structuredClone(data)),
        };
      },
      async set(val) {
        store.set(path, structuredClone(val));
      },
      async update(updates) {
        const current = store.get(path);
        if (!current) throw new Error(`Document missing at ${path}`);
        store.set(path, { ...current, ...structuredClone(updates) });
      },
    };
  }

  return {
    store,
    collection(colName) {
      return {
        doc(docId) {
          return makeRef(`${colName}/${docId}`);
        },
      };
    },
    async runTransaction(fn) {
      const staged = [];
      const tx = {
        async get(ref) {
          return ref.get();
        },
        set(ref, val) {
          staged.push(() => ref.set(val));
        },
        update(ref, updates) {
          staged.push(() => ref.update(updates));
        },
      };
      const result = await fn(tx);
      for (const apply of staged) {
        await apply();
      }
      return result;
    },
  };
}

const FIXED_NOW = 1_760_000_000_000;
const deps = {
  nowMs: () => FIXED_NOW,
  makeTimestamp: (ms) => new Date(ms),
  serverTimestamp: () => new Date(FIXED_NOW),
};

test('issueLicenceKey requires admin custom claim and creates masterLicences doc', async () => {
  const db = createFakeDb();

  await assert.rejects(
    () => issueLicenceKey(db, { uid: 'user_1', token: {} }, { plan: 'PRO' }, deps),
    (err) => err instanceof ServiceError && err.code === 'permission-denied'
  );

  const res = await issueLicenceKey(
    db,
    { uid: 'admin_1', token: { admin: true, email: 'cytan828@gmail.com' } },
    {
      licenceKey: 'BK-PRO-MYKEY-2026',
      plan: 'PRO',
      durationDays: 365,
      maxDevices: 8,
      maxClaims: 1,
      assignedEmail: 'owner@kopimaju.my',
    },
    deps
  );

  assert.equal(res.ok, true);
  assert.equal(res.licenceKey, 'BK-PRO-MYKEY-2026');
  assert.equal(res.plan, 'PRO');
  assert.equal(res.maxDevices, 8);

  const stored = db.store.get('masterLicences/BK-PRO-MYKEY-2026');
  assert.equal(stored.status, 'ACTIVE');
  assert.equal(stored.assignedEmail, 'owner@kopimaju.my');
  assert.deepEqual(stored.claimedByBusinessIds, []);
});

test('activateMasterLicence validates member, expiry, email restriction, maxClaims, and upgrades workspace', async () => {
  const db = createFakeDb({
    'businesses/biz_1': {
      ownerId: 'owner_1',
      memberUids: ['owner_1', 'staff_1'],
      maxMembers: 5,
    },
    'licences/biz_1': {
      ownerId: 'owner_1',
      memberUids: ['owner_1', 'staff_1'],
      licenceKey: 'BK-TRIAL-INIT',
      status: 'ACTIVE',
      plan: 'TRIAL',
      startDate: new Date(FIXED_NOW - 86_400_000),
      expiryDate: new Date(FIXED_NOW + 10 * 86_400_000),
      maxDevices: 5,
    },
    'businesses/biz_2': {
      ownerId: 'other_owner',
      memberUids: ['other_owner'],
      maxMembers: 5,
    },
    'masterLicences/BK-ENT-MASTER-01': {
      licenceKey: 'BK-ENT-MASTER-01',
      plan: 'ENTERPRISE',
      status: 'ACTIVE',
      durationDays: 365,
      expiryDate: new Date(FIXED_NOW + 365 * 86_400_000),
      maxDevices: 15,
      maxMembers: 15,
      maxClaims: 1,
      claimCount: 0,
      claimedByBusinessIds: [],
      assignedEmail: 'owner@kopimaju.my',
    },
  });

  // Wrong email rejected
  await assert.rejects(
    () =>
      activateMasterLicence(
        db,
        { uid: 'owner_1', token: { email: 'wrong@kopimaju.my' } },
        { businessId: 'biz_1', licenceKey: 'BK-ENT-MASTER-01' },
        deps
      ),
    (err) => err instanceof ServiceError && err.code === 'permission-denied'
  );

  // Authorized owner with matching email upgrades TRIAL -> ENTERPRISE
  const res = await activateMasterLicence(
    db,
    { uid: 'owner_1', token: { email: 'owner@kopimaju.my' } },
    { businessId: 'biz_1', licenceKey: 'BK-ENT-MASTER-01' },
    deps
  );

  assert.equal(res.ok, true);
  assert.equal(res.plan, 'ENTERPRISE');
  assert.equal(res.maxDevices, 15);
  assert.equal(db.store.get('licences/biz_1').plan, 'ENTERPRISE');
  assert.equal(db.store.get('licences/biz_1').licenceKey, 'BK-ENT-MASTER-01');
  assert.equal(db.store.get('businesses/biz_1').maxMembers, 15);
  assert.deepEqual(db.store.get('masterLicences/BK-ENT-MASTER-01').claimedByBusinessIds, ['biz_1']);

  // Second business exceeding maxClaims is rejected
  await db.collection('masterLicences').doc('BK-ENT-MASTER-01').update({ assignedEmail: null });
  await assert.rejects(
    () =>
      activateMasterLicence(
        db,
        { uid: 'other_owner', token: { email: 'other@test.my' } },
        { businessId: 'biz_2', licenceKey: 'BK-ENT-MASTER-01' },
        deps
      ),
    (err) => err instanceof ServiceError && err.code === 'resource-exhausted'
  );
});

test('extendLicence and suspendLicence require admin claim and update documents', async () => {
  const db = createFakeDb({
    'licences/biz_1': {
      ownerId: 'owner_1',
      memberUids: ['owner_1'],
      licenceKey: 'BK-PRO-1',
      status: 'EXPIRED',
      plan: 'PRO',
      expiryDate: new Date(FIXED_NOW - 5 * 86_400_000),
      maxDevices: 5,
    },
  });

  const adminAuth = { uid: 'admin_1', token: { admin: true } };

  await assert.rejects(
    () => extendLicence(db, { uid: 'owner_1', token: {} }, { businessId: 'biz_1', additionalDays: 30 }, deps),
    (err) => err instanceof ServiceError && err.code === 'permission-denied'
  );

  const extended = await extendLicence(
    db,
    adminAuth,
    { businessId: 'biz_1', additionalDays: 90, maxDevices: 10 },
    deps
  );
  assert.equal(extended.businessLicence.status, 'ACTIVE');
  assert.equal(extended.businessLicence.maxDevices, 10);
  assert.equal(extended.businessLicence.expiryDateMillis, FIXED_NOW + 90 * 86_400_000);

  const suspended = await suspendLicence(
    db,
    adminAuth,
    { businessId: 'biz_1', status: 'SUSPENDED', reason: 'Chargeback review' },
    deps
  );
  assert.equal(suspended.status, 'SUSPENDED');
  assert.equal(db.store.get('licences/biz_1').status, 'SUSPENDED');
  assert.equal(db.store.get('licences/biz_1').suspensionReason, 'Chargeback review');
});

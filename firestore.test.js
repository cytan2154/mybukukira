const test = require('node:test');
const fs = require('node:fs');
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require('@firebase/rules-unit-testing');

const PROJECT_ID = 'demo-no-project';
const DATABASE_ID = 'ai-studio-android-bukukira-4cf4342e-65eb-431d-b64e-10c2cb4d2180';

let testEnv;

function ownerCtx(uid = 'owner_uid_1') {
  return testEnv.authenticatedContext(uid, { email: `${uid}@example.com` }).firestore();
}

function unauthCtx() {
  return testEnv.unauthenticatedContext().firestore();
}

function pastDate(ms = 2000) {
  return new Date(Date.now() - ms);
}

function futureDate(days = 365) {
  return new Date(Date.now() + days * 24 * 60 * 60 * 1000);
}

function sampleLicence(
  ownerId = 'owner_uid_1',
  status = 'ACTIVE',
  expiryDays = 29,
  plan = 'TRIAL'
) {
  const now = pastDate(2000);
  return {
    ownerId,
    memberUids: [ownerId],
    licenceKey: plan === 'TRIAL' ? 'BK-TRIAL-20268899' : 'BK-PRO-2026-8899',
    status,
    plan,
    startDate: pastDate(86400000),
    expiryDate: expiryDays >= 0 ? futureDate(expiryDays) : pastDate((-expiryDays) * 86400000),
    maxDevices: 5,
    createdAt: now,
    updatedAt: now,
  };
}

function sampleBusiness(ownerId = 'owner_uid_1') {
  const now = pastDate(2000);
  return {
    ownerId,
    memberUids: [ownerId],
    memberRoles: { [ownerId]: 'OWNER' },
    maxMembers: 5,
    businessName: 'Kedai Kopi & Roti Maju',
    businessRegNo: 'SSM-202603104829',
    businessType: 'Gerai Makanan & Minuman',
    businessAddress: 'No. 18, Pekan Changlun, 06010 Kedah',
    ownerName: 'Ahmad bin Ali',
    currency: 'RM',
    financialYear: '2026',
    closingStockByYear: { '2026': 650.0 },
    beginningBalances: { openingStock: 0.0, cash: 0.0, bank: 0.0 },
    createdAt: now,
    updatedAt: now,
  };
}

function sampleTransaction(businessId, createdByUid, memberUids, overrides = {}) {
  const now = pastDate(2000);
  return {
    businessId,
    createdByUid,
    memberUids,
    date: '2026-03-15',
    type: 'IN',
    categoryId: 'sales_goods',
    method: 'CASH',
    amount: 4850.0,
    note: 'Jualan tunai kedai',
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

async function seedBusinessAndLicence(businessId = 'biz_kopi_maju', ownerId = 'owner_uid_1', status = 'ACTIVE', expiryDays = 365) {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection('licences').doc(businessId).set(sampleLicence(ownerId, status, expiryDays));
    await db.collection('businesses').doc(businessId).set(sampleBusiness(ownerId));
  });
}

test.before(async () => {
  const [host, portStr] = (process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8085').split(':');
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      host,
      port: Number(portStr),
      rules: fs.readFileSync('firestore.rules', 'utf8'),
    },
  });
});

test.after(async () => {
  if (testEnv) await testEnv.cleanup();
});

test.beforeEach(async () => {
  await testEnv.clearFirestore();
});

test('Unauthenticated users cannot read or write any collection', async () => {
  await seedBusinessAndLicence('biz_1', 'owner_1');
  const anon = unauthCtx();
  const now = pastDate(2000);
  await assertFails(anon.collection('businesses').doc('biz_1').get());
  await assertFails(anon.collection('licences').doc('biz_1').get());
  await assertFails(anon.collection('users').doc('u1').set({
    userId: 'u1',
    email: 'a@b.com',
    displayName: 'Anon',
    preferredLanguage: 'ms',
    createdAt: now,
    updatedAt: now,
  }));
});

test('User profile enforces UID ownership, allowed fields, and timestamp guards', async () => {
  const db = ownerCtx('owner_1');
  const now = pastDate(5000);
  await assertSucceeds(db.collection('users').doc('owner_1').set({
    userId: 'owner_1',
    email: 'owner@kopimaju.my',
    displayName: 'Ahmad',
    preferredLanguage: 'ms',
    createdAt: now,
    updatedAt: now,
  }));

  await assertFails(db.collection('users').doc('other_user').set({
    userId: 'other_user',
    email: 'other@kopimaju.my',
    displayName: 'Other',
    preferredLanguage: 'en',
    createdAt: now,
    updatedAt: now,
  }));

  // Reject future tampered timestamp
  await assertFails(db.collection('users').doc('owner_1').update({
    preferredLanguage: 'en',
    updatedAt: new Date(Date.now() + 120000),
  }));

  // Partial update with valid timestamp succeeds
  await assertSucceeds(db.collection('users').doc('owner_1').update({
    preferredLanguage: 'en',
    activeBusinessId: 'biz_1',
    updatedAt: pastDate(1000),
  }));

  // Reject immutable createdAt mutation
  await assertFails(db.collection('users').doc('owner_1').update({
    createdAt: pastDate(500),
    updatedAt: pastDate(500),
  }));
});

test('Owner creates TRIAL licence and business atomically via WriteBatch; PRO/31d creation and standalone business fail', async () => {
  const db = ownerCtx('owner_1');

  // Creating business without licence fails
  await assertFails(db.collection('businesses').doc('biz_batch').set(sampleBusiness('owner_1')));

  // Creating a client-side PRO or ENTERPRISE licence fails
  const proBatch = db.batch();
  proBatch.set(db.collection('licences').doc('biz_pro_spoof'), sampleLicence('owner_1', 'ACTIVE', 365, 'PRO'));
  proBatch.set(db.collection('businesses').doc('biz_pro_spoof'), sampleBusiness('owner_1'));
  await assertFails(proBatch.commit());

  // Creating a TRIAL licence exceeding 30 days fails
  const longTrialBatch = db.batch();
  longTrialBatch.set(db.collection('licences').doc('biz_long_trial'), sampleLicence('owner_1', 'ACTIVE', 31, 'TRIAL'));
  longTrialBatch.set(db.collection('businesses').doc('biz_long_trial'), sampleBusiness('owner_1'));
  await assertFails(longTrialBatch.commit());

  // Creating valid TRIAL (<= 30 days) + business in a WriteBatch succeeds
  const batch = db.batch();
  batch.set(db.collection('licences').doc('biz_batch'), sampleLicence('owner_1', 'ACTIVE', 29, 'TRIAL'));
  batch.set(db.collection('businesses').doc('biz_batch'), sampleBusiness('owner_1'));
  await assertSucceeds(batch.commit());
});

test('Owner PII private/ownerInfo is accessible only by business owner', async () => {
  await seedBusinessAndLicence('biz_pii', 'owner_1');
  const ownerDb = ownerCtx('owner_1');
  const staffDb = ownerCtx('staff_1');
  const now = pastDate(2000);

  await assertSucceeds(
    ownerDb.collection('businesses').doc('biz_pii').collection('private').doc('ownerInfo').set({
      ownerId: 'owner_1',
      ownerIdNumber: '850214-02-5431',
      ownerPhone: '012-456 7890',
      ownerEmail: 'ahmad@kopimaju.my',
      createdAt: now,
      updatedAt: now,
    })
  );

  // Add staff_1 via admin seed to verify even a member cannot read private ownerInfo
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection('businesses').doc('biz_pii').update({
      memberUids: ['owner_1', 'staff_1'],
      memberRoles: { owner_1: 'OWNER', staff_1: 'STAFF' },
    });
  });

  await assertSucceeds(ownerDb.collection('businesses').doc('biz_pii').collection('private').doc('ownerInfo').get());
  await assertFails(staffDb.collection('businesses').doc('biz_pii').collection('private').doc('ownerInfo').get());
});

test('Invite creation and atomic single-use redemption by Staff/Accountant', async () => {
  await seedBusinessAndLicence('biz_team', 'owner_1');
  const ownerDb = ownerCtx('owner_1');
  const staffDb = ownerCtx('staff_1');
  const inviteCode = 'INVITE_CODE_ABC123456789';
  const now = pastDate(2000);

  await assertSucceeds(ownerDb.collection('invites').doc(inviteCode).set({
    businessId: 'biz_team',
    businessName: 'Kedai Kopi & Roti Maju',
    role: 'STAFF',
    createdByUid: 'owner_1',
    expiresAt: futureDate(3),
    createdAt: now,
    updatedAt: now,
  }));

  // Enumeration of invites is forbidden
  await assertFails(staffDb.collection('invites').get());
  // Direct get with known high-entropy inviteCode succeeds
  await assertSucceeds(staffDb.collection('invites').doc(inviteCode).get());

  // Atomic 3-doc WriteBatch redemption succeeds
  const joinNow = pastDate(1000);
  const joinBatch = staffDb.batch();
  joinBatch.update(staffDb.collection('invites').doc(inviteCode), {
    redeemedByUid: 'staff_1',
    updatedAt: joinNow,
  });
  joinBatch.update(staffDb.collection('businesses').doc('biz_team'), {
    memberUids: ['owner_1', 'staff_1'],
    memberRoles: { owner_1: 'OWNER', staff_1: 'STAFF' },
    lastInviteCode: inviteCode,
    updatedAt: joinNow,
  });
  joinBatch.update(staffDb.collection('licences').doc('biz_team'), {
    memberUids: ['owner_1', 'staff_1'],
    updatedAt: joinNow,
  });
  await assertSucceeds(joinBatch.commit());

  // Re-redeeming the same invite by another user fails
  const secondUserDb = ownerCtx('staff_2');
  const replayBatch = secondUserDb.batch();
  replayBatch.update(secondUserDb.collection('invites').doc(inviteCode), {
    redeemedByUid: 'staff_2',
    updatedAt: pastDate(500),
  });
  replayBatch.update(secondUserDb.collection('businesses').doc('biz_team'), {
    memberUids: ['owner_1', 'staff_1', 'staff_2'],
    memberRoles: { owner_1: 'OWNER', staff_1: 'STAFF', staff_2: 'STAFF' },
    lastInviteCode: inviteCode,
    updatedAt: pastDate(500),
  });
  replayBatch.update(secondUserDb.collection('licences').doc('biz_team'), {
    memberUids: ['owner_1', 'staff_1', 'staff_2'],
    updatedAt: pastDate(500),
  });
  await assertFails(replayBatch.commit());
});

test('Role permissions: Staff cannot edit business settings or delete owner transactions; Accountant can update closingStockByYear', async () => {
  await seedBusinessAndLicence('biz_roles', 'owner_1');
  const now = pastDate(5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection('businesses').doc('biz_roles').update({
      memberUids: ['owner_1', 'acct_1', 'staff_1'],
      memberRoles: { owner_1: 'OWNER', acct_1: 'ACCOUNTANT', staff_1: 'STAFF' },
    });
    await db.collection('licences').doc('biz_roles').update({
      memberUids: ['owner_1', 'acct_1', 'staff_1'],
    });
    await db.collection('businesses').doc('biz_roles').collection('transactions').doc('tx_owner').set({
      businessId: 'biz_roles',
      createdByUid: 'owner_1',
      memberUids: ['owner_1', 'acct_1', 'staff_1'],
      date: '2026-01-05',
      type: 'OUT',
      categoryId: 'buy_equipment',
      method: 'BANK',
      amount: 1800.0,
      note: 'Mesin kopi',
      createdAt: now,
      updatedAt: now,
    });
  });

  const acctDb = ownerCtx('acct_1');
  const staffDb = ownerCtx('staff_1');

  // Staff creates own transaction, including asset disposal optional fields (NO id field)
  await assertSucceeds(
    staffDb.collection('businesses').doc('biz_roles').collection('transactions').doc('tx_staff').set(
      sampleTransaction('biz_roles', 'staff_1', ['owner_1', 'acct_1', 'staff_1'], {
        categoryId: 'asset_disposal',
        disposalAssetType: 'equipment',
        disposalAssetCost: 1200.0,
      })
    )
  );

  // Reject accidental `id` field inside transaction payload
  await assertFails(
    staffDb.collection('businesses').doc('biz_roles').collection('transactions').doc('tx_bad_id').set(
      sampleTransaction('biz_roles', 'staff_1', ['owner_1', 'acct_1', 'staff_1'], {
        id: 'tx_bad_id',
      })
    )
  );

  // Staff cannot delete owner's transaction, but Accountant can
  await assertFails(staffDb.collection('businesses').doc('biz_roles').collection('transactions').doc('tx_owner').delete());
  await assertSucceeds(acctDb.collection('businesses').doc('biz_roles').collection('transactions').doc('tx_owner').delete());

  // Staff cannot update closingStockByYear; Accountant can update closingStockByYear but not businessName
  await assertFails(staffDb.collection('businesses').doc('biz_roles').update({
    closingStockByYear: { '2026': 900.0 },
    updatedAt: pastDate(1000),
  }));
  await assertSucceeds(acctDb.collection('businesses').doc('biz_roles').update({
    closingStockByYear: { '2026': 900.0 },
    updatedAt: pastDate(1000),
  }));
  await assertFails(acctDb.collection('businesses').doc('biz_roles').update({
    businessName: 'Hijacked Name',
    updatedAt: pastDate(1000),
  }));
});

test('Query enforcement requires whereArrayContains(memberUids, uid) and blocks unfiltered list', async () => {
  await seedBusinessAndLicence('biz_query', 'owner_1');
  const ownerDb = ownerCtx('owner_1');
  await assertSucceeds(
    ownerDb.collection('businesses').doc('biz_query').collection('transactions').doc('tx_1').set(
      sampleTransaction('biz_query', 'owner_1', ['owner_1'])
    )
  );

  // Unfiltered list fails
  await assertFails(ownerDb.collection('businesses').get());
  await assertFails(ownerDb.collection('businesses').doc('biz_query').collection('transactions').get());

  // Scoped array-contains query succeeds
  await assertSucceeds(ownerDb.collection('businesses').where('memberUids', 'array-contains', 'owner_1').get());
  await assertSucceeds(
    ownerDb.collection('businesses').doc('biz_query').collection('transactions').where('memberUids', 'array-contains', 'owner_1').get()
  );
});

test('Expired or suspended licence allows reads but blocks new transaction writes', async () => {
  await seedBusinessAndLicence('biz_expired', 'owner_1', 'EXPIRED', -5);
  const ownerDb = ownerCtx('owner_1');

  await assertSucceeds(ownerDb.collection('businesses').doc('biz_expired').get());
  await assertFails(
    ownerDb.collection('businesses').doc('biz_expired').collection('transactions').doc('tx_fail').set(
      sampleTransaction('biz_expired', 'owner_1', ['owner_1'])
    )
  );
});

test('Device registration succeeds when licence active and validates hex installationId', async () => {
  await seedBusinessAndLicence('biz_dev', 'owner_1');
  const ownerDb = ownerCtx('owner_1');
  const installId = '0123456789abcdef0123456789abcdef';
  const now = pastDate(2000);

  await assertSucceeds(
    ownerDb.collection('licences').doc('biz_dev').collection('devices').doc(installId).set({
      installationId: installId,
      userId: 'owner_1',
      deviceName: 'Pixel 9 Pro',
      platform: 'ANDROID',
      lastValidatedAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  const webInstallId = 'fedcba9876543210fedcba9876543210';
  await assertSucceeds(
    ownerDb.collection('licences').doc('biz_dev').collection('devices').doc(webInstallId).set({
      installationId: webInstallId,
      userId: 'owner_1',
      deviceName: 'iPhone Safari PWA',
      platform: 'WEB',
      lastValidatedAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    ownerDb.collection('licences').doc('biz_dev').collection('devices').doc('bad_id').set({
      installationId: 'bad_id',
      userId: 'owner_1',
      deviceName: 'Pixel 9 Pro',
      platform: 'ANDROID',
      lastValidatedAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );
});

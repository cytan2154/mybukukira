const { initializeApp, getApps } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');

async function main() {
  const target = process.argv[2];
  const revoke = process.argv.includes('--revoke');
  if (!target) {
    console.error('Usage: node scripts/grant-admin.js <email-or-uid> [--revoke]');
    process.exit(1);
  }

  const app = getApps().length ? getApps()[0] : initializeApp();
  const auth = getAuth(app);
  const user = target.includes('@')
    ? await auth.getUserByEmail(target)
    : await auth.getUser(target);

  const existingClaims = user.customClaims || {};
  const nextClaims = { ...existingClaims };
  if (revoke) {
    delete nextClaims.admin;
  } else {
    nextClaims.admin = true;
  }

  await auth.setCustomUserClaims(user.uid, nextClaims);
  console.log(
    `${revoke ? 'Revoked' : 'Granted'} admin custom claim for ${user.email || user.uid} (uid=${user.uid}).`
  );
  console.log('User must sign out and sign back in (or force token refresh) for the claim to take effect.');
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});

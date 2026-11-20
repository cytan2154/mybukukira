const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { initializeApp, getApps } = require('firebase-admin/app');
const { getFirestore, FieldValue, Timestamp } = require('firebase-admin/firestore');
const {
  FIRESTORE_DATABASE_ID,
  ServiceError,
  activateMasterLicence,
  issueLicenceKey,
  extendLicence,
  suspendLicence,
} = require('./licenceService');

const app = getApps().length ? getApps()[0] : initializeApp();
const db = getFirestore(app, FIRESTORE_DATABASE_ID);

const deps = {
  nowMs: () => Date.now(),
  makeTimestamp: (ms) => Timestamp.fromMillis(ms),
  serverTimestamp: () => FieldValue.serverTimestamp(),
};

function wrapCallable(handler) {
  return onCall({ cors: true }, async (request) => {
    try {
      return await handler(db, request.auth, request.data || {}, deps);
    } catch (err) {
      if (err instanceof ServiceError) {
        throw new HttpsError(err.code, err.message);
      }
      throw new HttpsError('internal', err?.message || 'Unexpected licence service error.');
    }
  });
}

exports.activateMasterLicence = wrapCallable(activateMasterLicence);
exports.issueLicenceKey = wrapCallable(issueLicenceKey);
exports.extendLicence = wrapCallable(extendLicence);
exports.suspendLicence = wrapCallable(suspendLicence);

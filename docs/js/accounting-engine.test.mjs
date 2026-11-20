import test from 'node:test';
import assert from 'node:assert/strict';

import { sampleTransactions } from './accounting-catalog.js';
import { computeStatements, defaultBeginningBalances } from './accounting-engine.js';
import { buildCsvReport, buildJsonBackup, buildPrintableHtml, parseJsonBackup } from './export-utils.js';
import { evaluateBusinessAccess, validateLegacyLicenseRecord } from './license-gate.js';

function approx(actual, expected, msg = '') {
  assert.ok(Math.abs(actual - expected) <= 0.001, `${msg} expected=${expected} actual=${actual}`);
}

test('sampleBusinessData_2026 matches BukuKira Android reference and balances', () => {
  const txs = sampleTransactions('biz_1', 'owner_1', ['owner_1']);
  const stats = computeStatements(txs, defaultBeginningBalances(), { '2026': 650.0 }, '2026', 'ms');

  approx(stats.totalRevenue, 8450.0);
  approx(stats.totalPurchasesCogs, 2400.0);
  approx(stats.openingStock, 0.0);
  approx(stats.closingStock, 650.0);
  approx(stats.netCogs, 1750.0);
  approx(stats.grossProfit, 6700.0);
  approx(stats.totalOperatingExpenses, 2400.0);
  approx(stats.netProfit, 4300.0);
  approx(stats.cashBalance, 2050.0);
  approx(stats.bankBalance, 5300.0);
  approx(stats.totalMoneyNow, 7350.0);
  approx(stats.equipmentAsset, 1800.0);
  approx(stats.totalAssets, 9800.0);
  approx(stats.ownerCapital, 6000.0);
  approx(stats.ownerDrawings, 500.0);
  approx(stats.carriedForward, 3800.0);
  approx(stats.totalLiabilitiesAndEquity, 9800.0);
  approx(stats.balanceDifference, 0.0);
  assert.equal(stats.isBalanced, true);
});

test('assetDisposal computes net gain/loss and removes carrying cost from balance sheet', () => {
  const txs = [
    { id: '1', businessId: 'b', createdByUid: 'u', memberUids: ['u'], date: '2026-01-01', type: 'IN', categoryId: 'owner_capital', method: 'BANK', amount: 10000.0, note: 'Modal' },
    { id: '2', businessId: 'b', createdByUid: 'u', memberUids: ['u'], date: '2026-02-01', type: 'OUT', categoryId: 'buy_vehicle', method: 'BANK', amount: 4000.0, note: 'Van lama' },
    {
      id: '3',
      businessId: 'b',
      createdByUid: 'u',
      memberUids: ['u'],
      date: '2026-05-10',
      type: 'IN',
      categoryId: 'asset_disposal',
      method: 'BANK',
      amount: 4500.0,
      note: 'Jual van terpakai',
      disposalAssetType: 'vehicles',
      disposalAssetCost: 4000.0
    }
  ];
  const stats = computeStatements(txs, defaultBeginningBalances(), {}, '2026', 'en');

  approx(stats.vehicles, 0.0);
  approx(stats.totalOtherIncome, 500.0);
  approx(stats.netProfit, 500.0);
  approx(stats.bankBalance, 10500.0);
  assert.equal(stats.isBalanced, true);
});

test('creditSalesPurchasesNotes update receivables/payables and stay balanced', () => {
  const txs = [
    { id: '1', businessId: 'b', createdByUid: 'u', memberUids: ['u'], date: '2026-01-01', type: 'IN', categoryId: 'owner_capital', method: 'BANK', amount: 5000.0, note: 'Capital' },
    { id: '2', businessId: 'b', createdByUid: 'u', memberUids: ['u'], date: '2026-02-01', type: 'CREDIT_SALE', categoryId: 'credit_sale', method: 'CREDIT', amount: 2000.0, note: 'Inv 001', partyName: 'Restoran A', invoiceNo: 'INV-001' },
    { id: '3', businessId: 'b', createdByUid: 'u', memberUids: ['u'], date: '2026-02-10', type: 'CREDIT_NOTE', categoryId: 'sales_return_refund', method: 'CREDIT', amount: 200.0, note: 'Damaged goods', partyName: 'Restoran A', invoiceNo: 'CN-001' },
    { id: '4', businessId: 'b', createdByUid: 'u', memberUids: ['u'], date: '2026-02-15', type: 'IN', categoryId: 'debtors_payment', method: 'BANK', amount: 800.0, note: 'Partial settlement' },
    { id: '5', businessId: 'b', createdByUid: 'u', memberUids: ['u'], date: '2026-03-01', type: 'CREDIT_PURCHASE', categoryId: 'credit_purchase', method: 'CREDIT', amount: 1500.0, note: 'Stock credit', partyName: 'Pembekal B', invoiceNo: 'BILL-10' },
    { id: '6', businessId: 'b', createdByUid: 'u', memberUids: ['u'], date: '2026-03-05', type: 'DEBIT_NOTE', categoryId: 'purchase_return_refund', method: 'CREDIT', amount: 300.0, note: 'Return stock', partyName: 'Pembekal B', invoiceNo: 'DN-10' },
    { id: '7', businessId: 'b', createdByUid: 'u', memberUids: ['u'], date: '2026-03-10', type: 'CREDIT_PURCHASE', categoryId: 'buy_equipment', method: 'CREDIT', amount: 1200.0, note: 'Mixer on credit', partyName: 'Mesin Sdn Bhd', invoiceNo: 'EQ-99' }
  ];
  const stats = computeStatements(txs, defaultBeginningBalances(), { '2026': 400.0 }, '2026', 'ms');

  approx(stats.totalRevenue, 1800.0);
  approx(stats.totalPurchasesCogs, 1200.0);
  approx(stats.netCogs, 800.0);
  approx(stats.netProfit, 1000.0);
  approx(stats.tradeDebtors, 1000.0);
  approx(stats.tradeCreditors, 1200.0);
  approx(stats.otherCreditors, 1200.0);
  approx(stats.equipmentAsset, 1200.0);
  approx(stats.bankBalance, 5800.0);
  assert.equal(stats.isBalanced, true);
});

test('multiYearStockAndBroughtForwardRollover stays balanced in year 2', () => {
  const txs = [
    { id: '1', businessId: 'b', createdByUid: 'u', memberUids: ['u'], date: '2025-01-01', type: 'IN', categoryId: 'owner_capital', method: 'CASH', amount: 3000.0, note: 'Modal' },
    { id: '2', businessId: 'b', createdByUid: 'u', memberUids: ['u'], date: '2025-06-01', type: 'OUT', categoryId: 'buy_stock', method: 'CASH', amount: 1000.0, note: 'Stok 2025' },
    { id: '3', businessId: 'b', createdByUid: 'u', memberUids: ['u'], date: '2025-09-01', type: 'IN', categoryId: 'sales_goods', method: 'CASH', amount: 1500.0, note: 'Jualan 2025' },
    { id: '4', businessId: 'b', createdByUid: 'u', memberUids: ['u'], date: '2026-03-01', type: 'IN', categoryId: 'sales_goods', method: 'CASH', amount: 900.0, note: 'Jualan 2026' }
  ];
  const stockByYear = { '2025': 400.0, '2026': 100.0 };

  const stats2025 = computeStatements(txs, defaultBeginningBalances(), stockByYear, '2025', 'ms');
  const stats2026 = computeStatements(txs, defaultBeginningBalances(), stockByYear, '2026', 'ms');

  approx(stats2025.netProfit, 900.0);
  assert.equal(stats2025.isBalanced, true);

  approx(stats2026.openingStock, 400.0);
  approx(stats2026.closingStock, 100.0);
  approx(stats2026.netCogs, 300.0);
  approx(stats2026.netProfit, 600.0);
  approx(stats2026.broughtForward, 900.0);
  approx(stats2026.carriedForward, 1500.0);
  assert.equal(stats2026.isBalanced, true);
  assert.ok(
    (stats2026.ledgers['Tunai'] || []).some((e) => e.description === 'Baki Bawa Hadapan' && Math.abs(e.debit - 3500.0) < 0.001)
  );
});

test('unbalancedBeginningBalances reports exact difference', () => {
  const stats = computeStatements(
    [],
    defaultBeginningBalances({ cash: 1000.0, capital: 800.0 }),
    {},
    '2026',
    'en'
  );
  assert.equal(stats.isBalanced, false);
  approx(stats.balanceDifference, 200.0);
});

test('export-utils JSON v2 backup round-trip, CSV BOM, and XSS escaping', () => {
  const ws = {
    id: 'biz_1',
    businessName: 'Kedai <Test> & Co',
    businessRegNo: 'SSM123',
    businessType: 'F&B',
    businessAddress: '1 Jalan Kopi',
    ownerName: 'Ali',
    currency: 'RM',
    financialYear: '2026',
    closingStockByYear: { '2026': 650.0 },
    beginningBalances: defaultBeginningBalances()
  };
  const txs = sampleTransactions('biz_1', 'owner_1', ['owner_1']);
  const json = buildJsonBackup(ws, txs);
  const parsed = parseJsonBackup(json, 'biz_1', 'owner_1', ['owner_1']);

  assert.equal(parsed.businessName, 'Kedai <Test> & Co');
  assert.equal(parsed.transactions.length, 10);
  assert.equal(parsed.closingStockByYear['2026'], 650.0);

  const stats = computeStatements(txs, ws.beginningBalances, ws.closingStockByYear, '2026', 'ms');
  const csv = buildCsvReport(ws, txs, stats, 'ms');
  assert.ok(csv.startsWith('\uFEFF'));
  assert.ok(csv.includes('Jualan Tunai'));

  const printHtml = buildPrintableHtml(ws, stats, 'ms');
  assert.ok(printHtml.includes('Kedai &lt;Test&gt; &amp; Co'));
  assert.ok(!printHtml.includes('Kedai <Test>'));
});

test('license-gate enforces key match, expiry, device slots, and Web device registration', () => {
  const now = 1_750_000_000_000;
  const installId = '0123456789abcdef0123456789abcdef';
  const licence = {
    businessId: 'biz_1',
    ownerId: 'u1',
    memberUids: ['u1'],
    licenceKey: 'BK-PRO-123456',
    status: 'ACTIVE',
    plan: 'PRO',
    startDateMillis: now - 1000,
    expiryDateMillis: now + 86_400_000,
    maxDevices: 2
  };
  const ws = {
    id: 'biz_1',
    ownerId: 'u1',
    memberUids: ['u1'],
    businessName: 'Kedai Kopi'
  };

  assert.equal(
    validateLegacyLicenseRecord({
      businessId: 'biz_1',
      licenceKeyInput: 'WRONG',
      installationId: installId,
      licence,
      registeredDevices: [],
      nowMillis: now
    }).code,
    'LICENCE_INVALID'
  );

  assert.equal(
    evaluateBusinessAccess({
      uid: 'u1',
      workspace: ws,
      licence,
      registeredDevices: [],
      installationId: installId,
      nowMillis: now
    }).status,
    'LicenseRequired'
  );

  assert.equal(
    evaluateBusinessAccess({
      uid: 'u1',
      workspace: ws,
      licence,
      registeredDevices: [{ installationId: installId, userId: 'u1', platform: 'WEB' }],
      installationId: installId,
      nowMillis: now
    }).status,
    'Granted'
  );
});

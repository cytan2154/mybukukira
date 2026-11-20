import { accountMetaFor, OFFICIAL_EXPENSE_LABELS_MS } from './accounting-catalog.js';
import { statementLabels, translateAccount } from './strings.js';

const LEGACY_DISPOSAL_REGEX = /^BK_ASSET_DISPOSAL\|([^|]+)\|([0-9]+(?:\.[0-9]+)?)\|(.*)$/;

function normalizeDisposalType(raw) {
  if (!raw) return null;
  const v = String(raw).trim();
  if (v === 'equipment' || v === 'equipments') return 'equipment';
  if (['landBuilding', 'vehicles', 'otherNonCurrent', 'investments'].includes(v)) return v;
  return null;
}

function parseDisposal(t) {
  const typedType = normalizeDisposalType(t.disposalAssetType);
  const typedCost = Number(t.disposalAssetCost);
  if (typedType && Number.isFinite(typedCost) && typedCost > 0) {
    return { assetType: typedType, cost: typedCost, userNote: t.note || '' };
  }
  const m = LEGACY_DISPOSAL_REGEX.exec(t.note || '');
  if (!m) return null;
  const parsedType = normalizeDisposalType(m[1]) || 'equipment';
  const cost = Number(m[2]);
  if (!Number.isFinite(cost) || cost <= 0) return null;
  return { assetType: parsedType, cost, userNote: m[3] || '' };
}

function formatDisposalNote(t, d, lang) {
  if (!d) return t.note || '';
  const typeKeyMap = {
    equipment: 'Loji dan jentera',
    landBuilding: 'Tanah dan bangunan',
    vehicles: 'Kenderaan',
    otherNonCurrent: 'Aset bukan semasa lain',
    investments: 'Pelaburan'
  };
  const typeLabel = translateAccount(typeKeyMap[d.assetType] || 'Loji dan jentera', lang);
  const base = `Kos RM ${d.cost.toFixed(2)} (${typeLabel})`;
  return d.userNote && d.userNote.trim() ? `${base} - ${d.userNote}` : base;
}

export function defaultBeginningBalances(raw = null) {
  const b = raw || {};
  const n = (k) => (Number.isFinite(Number(b[k])) ? Number(b[k]) : 0);
  return {
    openingStock: n('openingStock'),
    landBuilding: n('landBuilding'),
    equipment: n('equipment'),
    vehicles: n('vehicles'),
    otherNonCurrent: n('otherNonCurrent'),
    investments: n('investments'),
    tradeDebtors: n('tradeDebtors'),
    otherDebtors: n('otherDebtors'),
    otherCurrent: n('otherCurrent'),
    cash: n('cash'),
    bank: n('bank'),
    loans: n('loans'),
    tradeCreditors: n('tradeCreditors'),
    otherCreditors: n('otherCreditors'),
    capital: n('capital'),
    broughtForward: n('broughtForward')
  };
}

export function getOpeningStock(year, closingStockByYear = {}, beginningBalances = {}) {
  let latestYear = null;
  for (const k of Object.keys(closingStockByYear || {})) {
    const ky = Number.parseInt(k, 10);
    if (Number.isFinite(ky) && ky < year && (latestYear === null || ky > latestYear)) {
      latestYear = ky;
    }
  }
  if (latestYear !== null) {
    return Math.max(0, Number(closingStockByYear[String(latestYear)]) || 0);
  }
  return Math.max(0, Number(beginningBalances.openingStock) || 0);
}

export function getClosingStock(year, closingStockByYear = {}, beginningBalances = {}) {
  const yearKey = String(year);
  if (closingStockByYear && Object.prototype.hasOwnProperty.call(closingStockByYear, yearKey)) {
    return Math.max(0, Number(closingStockByYear[yearKey]) || 0);
  }
  return getOpeningStock(year, closingStockByYear, beginningBalances);
}

/**
 * Pure JS on-device accounting engine matching Android `AccountingEngine.kt` 1:1.
 */
export function computeStatements(
  transactions = [],
  beginningBalancesInput = {},
  closingStockByYear = {},
  financialYear = '2026',
  lang = 'ms'
) {
  const parsedYear = Number.parseInt(financialYear, 10);
  const year = Number.isFinite(parsedYear) && parsedYear >= 1900 && parsedYear <= 2200 ? parsedYear : 2026;
  const tL = (key) => translateAccount(key, lang);
  const labels = statementLabels(lang);
  const bb = defaultBeginningBalances(beginningBalancesInput);

  let revenue = 0;
  let otherIncome = 0;
  let purchases = 0;
  let expenses = 0;
  let drawings = 0;
  let priorProfit = 0;

  const revenueByCategory = {};
  const otherIncomeByCategory = {};
  const expensesByCategory = {};

  let disposalGainLoss = 0;
  let hasDisposalRecord = false;

  let cash = bb.cash;
  let bank = bb.bank;
  let land = bb.landBuilding;
  let equipment = bb.equipment;
  let vehicles = bb.vehicles;
  let otherNC = bb.otherNonCurrent;
  let investments = bb.investments;
  let debtors = bb.tradeDebtors;
  let creditors = bb.tradeCreditors;
  let otherCreditors = bb.otherCreditors;
  let otherDebtors = bb.otherDebtors;
  let otherCurrent = bb.otherCurrent;
  let loans = bb.loans;
  let capital = bb.capital;

  const bf = {
    'Tunai': cash,
    'Bank': bank,
    'Penghutang perdagangan': debtors,
    'Pemiutang dagangan': creditors,
    'Pemiutang lain': otherCreditors,
    'Tanah dan bangunan': land,
    'Loji dan jentera': equipment,
    'Kenderaan': vehicles,
    'Aset bukan semasa lain': otherNC,
    'Pelaburan': investments,
    'Pinjaman dan overdraf': loans,
    'Akaun modal': capital
  };

  const sortedTxs = [...transactions].sort((a, b) => {
    const dc = String(a.date || '').localeCompare(String(b.date || ''));
    if (dc !== 0) return dc;
    return String(a.id || '').localeCompare(String(b.id || ''));
  });

  for (const t of sortedTxs) {
    const ty = Number.parseInt(String(t.date || '').slice(0, 4), 10);
    if (!Number.isFinite(ty)) continue;

    const rawAmt = Number(t.amount);
    const amt = Number.isFinite(rawAmt) ? rawAmt : 0;
    const [accLabelMs, st] = accountMetaFor(t.categoryId);
    const isIn = t.type === 'IN';
    const isOut = t.type === 'OUT';
    const isCreditSale = t.type === 'CREDIT_SALE';
    const isCreditPurchase = t.type === 'CREDIT_PURCHASE';
    const isCreditNote = t.type === 'CREDIT_NOTE';
    const isDebitNote = t.type === 'DEBIT_NOTE';
    const isStockReturn = st === 'PNL_COGS' || st === 'PNL_PURCHASE_RETURN';
    const isCash = t.method === 'CASH';

    if (ty === year) {
      if (st === 'PNL_REVENUE') {
        revenue += amt;
        revenueByCategory[accLabelMs] = (revenueByCategory[accLabelMs] || 0) + amt;
      } else if (st === 'PNL_OTHER_INCOME') {
        otherIncome += amt;
        otherIncomeByCategory[accLabelMs] = (otherIncomeByCategory[accLabelMs] || 0) + amt;
      } else if (st === 'BS_ASSET_DISPOSAL') {
        const d = parseDisposal(t);
        if (d) {
          const diff = amt - d.cost;
          disposalGainLoss += diff;
          hasDisposalRecord = true;
          otherIncome += diff;
        }
      } else if (st === 'PNL_SALES_RETURN') {
        revenue -= amt;
        revenueByCategory[accLabelMs] = (revenueByCategory[accLabelMs] || 0) - amt;
      } else if (st === 'PNL_COGS') {
        purchases += amt;
      } else if (st === 'PNL_PURCHASE_RETURN') {
        purchases -= amt;
      } else if (st === 'PNL_EXPENSE') {
        expenses += amt;
        expensesByCategory[accLabelMs] = (expensesByCategory[accLabelMs] || 0) + amt;
      } else if (st === 'BS_EQUITY_DRAW') {
        drawings += amt;
      } else if (st === 'BS_EQUITY_DRAW_RETURN') {
        drawings -= amt;
      }
    } else if (ty < year) {
      if (st === 'PNL_REVENUE' || st === 'PNL_OTHER_INCOME') {
        priorProfit += amt;
      } else if (st === 'BS_ASSET_DISPOSAL') {
        const d = parseDisposal(t);
        priorProfit += d ? amt - d.cost : amt;
      } else if (
        st === 'PNL_SALES_RETURN' ||
        st === 'PNL_COGS' ||
        st === 'PNL_EXPENSE' ||
        st === 'BS_EQUITY_DRAW'
      ) {
        priorProfit -= amt;
      } else if (st === 'PNL_PURCHASE_RETURN' || st === 'BS_EQUITY_DRAW_RETURN') {
        priorProfit += amt;
      }

      if (st === 'BS_TRANSFER') {
        if (t.categoryId === 'transfer_bank_to_cash') {
          bf['Bank'] -= amt;
          bf['Tunai'] += amt;
        } else if (t.categoryId === 'transfer_cash_to_bank') {
          bf['Tunai'] -= amt;
          bf['Bank'] += amt;
        }
      } else if (isIn) {
        if (isCash) bf['Tunai'] += amt;
        else bf['Bank'] += amt;
      } else if (isOut) {
        if (isCash) bf['Tunai'] -= amt;
        else bf['Bank'] -= amt;
      }

      if (st === 'BS_LAND_BUILDING_ADD') bf['Tanah dan bangunan'] += amt;
      else if (st === 'BS_EQUIPMENT_ADD') bf['Loji dan jentera'] += amt;
      else if (st === 'BS_VEHICLES_ADD') bf['Kenderaan'] += amt;
      else if (st === 'BS_OTHER_NONCURRENT_ADD') bf['Aset bukan semasa lain'] += amt;
      else if (st === 'BS_INVESTMENT_ADD') bf['Pelaburan'] += amt;
      else if (st === 'BS_TRADE_DEBTORS_COLLECT') bf['Penghutang perdagangan'] -= amt;
      else if (st === 'BS_TRADE_CREDITORS_PAY') bf['Pemiutang dagangan'] -= amt;
      else if (st === 'BS_OTHER_CREDITORS_PAY') bf['Pemiutang lain'] -= amt;
      else if (st === 'BS_LIABILITY_ADD') bf['Pinjaman dan overdraf'] += amt;
      else if (st === 'BS_LIABILITY_PAY') bf['Pinjaman dan overdraf'] -= amt;
      else if (st === 'BS_EQUITY_ADD') bf['Akaun modal'] += amt;
      else if (st === 'BS_ASSET_DISPOSAL') {
        const d = parseDisposal(t);
        if (d) {
          if (d.assetType === 'landBuilding') bf['Tanah dan bangunan'] -= d.cost;
          else if (d.assetType === 'vehicles') bf['Kenderaan'] -= d.cost;
          else if (d.assetType === 'otherNonCurrent') bf['Aset bukan semasa lain'] -= d.cost;
          else if (d.assetType === 'investments') bf['Pelaburan'] -= d.cost;
          else bf['Loji dan jentera'] -= d.cost;
        }
      }

      if (isCreditSale) bf['Penghutang perdagangan'] += amt;
      if (isCreditPurchase) {
        if (st === 'PNL_COGS') bf['Pemiutang dagangan'] += amt;
        else bf['Pemiutang lain'] += amt;
      }
      if (isCreditNote) bf['Penghutang perdagangan'] -= amt;
      if (isDebitNote) {
        if (isStockReturn) bf['Pemiutang dagangan'] -= amt;
        else bf['Pemiutang lain'] -= amt;
      }
    }

    if (ty <= year) {
      if (st === 'BS_TRANSFER') {
        if (t.categoryId === 'transfer_bank_to_cash') {
          bank -= amt;
          cash += amt;
        } else if (t.categoryId === 'transfer_cash_to_bank') {
          cash -= amt;
          bank += amt;
        }
      } else if (isIn) {
        if (isCash) cash += amt;
        else bank += amt;
      } else if (isOut) {
        if (isCash) cash -= amt;
        else bank -= amt;
      }

      if (st === 'BS_LAND_BUILDING_ADD') land += amt;
      else if (st === 'BS_EQUIPMENT_ADD') equipment += amt;
      else if (st === 'BS_VEHICLES_ADD') vehicles += amt;
      else if (st === 'BS_OTHER_NONCURRENT_ADD') otherNC += amt;
      else if (st === 'BS_INVESTMENT_ADD') investments += amt;
      else if (st === 'BS_TRADE_DEBTORS_COLLECT') debtors -= amt;
      else if (st === 'BS_TRADE_CREDITORS_PAY') creditors -= amt;
      else if (st === 'BS_OTHER_CREDITORS_PAY') otherCreditors -= amt;
      else if (st === 'BS_OTHER_DEBTORS_ADD') otherDebtors += amt;
      else if (st === 'BS_OTHER_CURRENT_ADD') otherCurrent += amt;
      else if (st === 'BS_LIABILITY_ADD') loans += amt;
      else if (st === 'BS_LIABILITY_PAY') loans -= amt;
      else if (st === 'BS_EQUITY_ADD') capital += amt;
      else if (st === 'BS_ASSET_DISPOSAL') {
        const d = parseDisposal(t);
        if (d) {
          if (d.assetType === 'landBuilding') land -= d.cost;
          else if (d.assetType === 'vehicles') vehicles -= d.cost;
          else if (d.assetType === 'otherNonCurrent') otherNC -= d.cost;
          else if (d.assetType === 'investments') investments -= d.cost;
          else equipment -= d.cost;
        }
      }

      if (isCreditSale) debtors += amt;
      if (isCreditPurchase) {
        if (st === 'PNL_COGS') creditors += amt;
        else otherCreditors += amt;
      }
      if (isCreditNote) debtors -= amt;
      if (isDebitNote) {
        if (isStockReturn) creditors -= amt;
        else otherCreditors -= amt;
      }
    }
  }

  // General Ledger (Lejar Am) - 3-column running balance format (Debit | Credit | Balance)
  const ledgers = {};
  const runningBalanceByAcc = {};
  const creditNormalAccounts = new Set([
    'Pinjaman dan overdraf',
    'Pinjaman dan Overdraft',
    'Pemiutang dagangan',
    'Pemiutang lain',
    'Akaun modal',
    'Modal',
    'Jualan Tunai',
    'Jualan Kredit',
    'Dividen',
    'Faedah dan diskaun',
    'Sewa, royalti, dan premium',
    'Pendapatan lain',
    'Pelupusan Aset',
    'Pulangan Belian',
    'Upah Servis / Kerja (rekod lama)'
  ]);
  const addLedger = (accMs, date, desc, dr, cr) => {
    const normalizedMap = {
      'Beli Aset Bukan Semasa Lain': 'Aset bukan semasa lain',
      'Beli Loji / Mesin / Peralatan': 'Loji dan jentera',
      'Beli Kenderaan Perniagaan': 'Kenderaan',
      'Beli Tanah / Bangunan': 'Tanah dan bangunan'
    };
    const normalized = normalizedMap[accMs] || accMs;
    const translatedAcc = tL(normalized);
    if (!ledgers[translatedAcc]) ledgers[translatedAcc] = [];
    const delta = creditNormalAccounts.has(normalized) ? cr - dr : dr - cr;
    const nextBalance = (runningBalanceByAcc[translatedAcc] || 0) + delta;
    runningBalanceByAcc[translatedAcc] = nextBalance;
    ledgers[translatedAcc].push({
      date,
      description: desc,
      debit: dr,
      credit: cr,
      balance: nextBalance
    });
  };

  const debitNormalBf = new Set([
    'Tunai',
    'Bank',
    'Penghutang perdagangan',
    'Tanah dan bangunan',
    'Loji dan jentera',
    'Kenderaan',
    'Aset bukan semasa lain',
    'Pelaburan'
  ]);

  for (const [acc, value] of Object.entries(bf)) {
    if (Math.abs(value) > 0.0001) {
      if (debitNormalBf.has(acc)) {
        addLedger(acc, `${year}-01-01`, tL('Baki Bawa Hadapan'), value > 0 ? value : 0, value < 0 ? -value : 0);
      } else {
        addLedger(acc, `${year}-01-01`, tL('Baki Bawa Hadapan'), value < 0 ? -value : 0, value > 0 ? value : 0);
      }
    }
  }

  for (const t of sortedTxs.filter((item) => Number.parseInt(String(item.date || '').slice(0, 4), 10) === year)) {
    const rawAmt = Number(t.amount);
    const amt = Number.isFinite(rawAmt) ? rawAmt : 0;
    const [accLabelMs, st] = accountMetaFor(t.categoryId);
    const isCreditSale = t.type === 'CREDIT_SALE';
    const isCreditPurchase = t.type === 'CREDIT_PURCHASE';
    const isCreditNote = t.type === 'CREDIT_NOTE';
    const isDebitNote = t.type === 'DEBIT_NOTE';
    const isIn = t.type === 'IN' || isCreditSale;
    const isOut = t.type === 'OUT' || isCreditPurchase;
    const isCash = t.method === 'CASH';

    const displayNote = st === 'BS_ASSET_DISPOSAL' ? formatDisposalNote(t, parseDisposal(t), lang) : (t.note || '');
    let drAcc = '';
    let crAcc = '';

    if (st === 'BS_TRANSFER') {
      if (t.categoryId === 'transfer_bank_to_cash') {
        drAcc = 'Tunai';
        crAcc = 'Bank';
      } else {
        drAcc = 'Bank';
        crAcc = 'Tunai';
      }
    } else if (isCreditNote) {
      drAcc = accLabelMs;
      crAcc = 'Penghutang perdagangan';
    } else if (isDebitNote) {
      drAcc = st === 'PNL_COGS' || st === 'PNL_PURCHASE_RETURN' ? 'Pemiutang dagangan' : 'Pemiutang lain';
      crAcc = accLabelMs;
    } else if (st === 'BS_TRADE_DEBTORS_COLLECT') {
      drAcc = isCash ? 'Tunai' : 'Bank';
      crAcc = 'Penghutang perdagangan';
    } else if (st === 'BS_TRADE_CREDITORS_PAY') {
      drAcc = 'Pemiutang dagangan';
      crAcc = isCash ? 'Tunai' : 'Bank';
    } else if (st === 'BS_OTHER_CREDITORS_PAY') {
      drAcc = 'Pemiutang lain';
      crAcc = isCash ? 'Tunai' : 'Bank';
    } else if (isIn) {
      crAcc = accLabelMs;
      drAcc = isCreditSale ? 'Penghutang perdagangan' : isCash ? 'Tunai' : 'Bank';
    } else if (isOut) {
      drAcc = accLabelMs;
      if (isCreditPurchase) {
        crAcc = st === 'PNL_COGS' ? 'Pemiutang dagangan' : 'Pemiutang lain';
      } else {
        crAcc = isCash ? 'Tunai' : 'Bank';
      }
    }

    if (drAcc && crAcc) {
      const descForDr = `${tL(crAcc)}${displayNote.trim() ? ` - ${displayNote}` : ''}`;
      const descForCr = `${tL(drAcc)}${displayNote.trim() ? ` - ${displayNote}` : ''}`;
      addLedger(drAcc, t.date, descForDr, amt, 0);
      addLedger(crAcc, t.date, descForCr, 0, amt);
    }
  }

  if (hasDisposalRecord) {
    otherIncomeByCategory['Untung / (Rugi) bersih pelupusan aset'] = disposalGainLoss;
  }

  const opening = getOpeningStock(year, closingStockByYear, bb);
  const closing = getClosingStock(year, closingStockByYear, bb);
  const netCOGS = opening + purchases - closing;
  const gross = revenue - netCOGS;
  const netProfit = gross + otherIncome - expenses;

  const firstStock = Math.max(0, bb.openingStock);
  const brought = bb.broughtForward + priorProfit + (opening - firstStock);
  const carried = brought + netProfit - drawings;

  const nonCurrent = land + equipment + vehicles + otherNC;
  const current = closing + debtors + otherDebtors + cash + bank + otherCurrent;
  const totalAssets = nonCurrent + investments + current;
  const totalLiabilities = loans + creditors + otherCreditors;
  const totalEquity = capital + carried;
  const totalLE = totalLiabilities + totalEquity;
  const diff = totalAssets - totalLE;

  const rows = [];
  const addRow = (statementType, section, label, amount, note = '', isTotal = false) => {
    rows.push({ statementType, section, label, amount, note, isTotal });
  };

  Object.keys(revenueByCategory)
    .sort()
    .forEach((k) => addRow('PNL', labels.secRevenue, tL(k), revenueByCategory[k] || 0));
  addRow('PNL', labels.secRevenue, labels.totalRevenue, revenue, '', true);

  Object.keys(otherIncomeByCategory)
    .sort()
    .forEach((k) => {
      const note = k.toLowerCase().includes('pelupusan') ? labels.disposalNote : '';
      addRow('PNL', labels.secOtherIncome, tL(k), otherIncomeByCategory[k] || 0, note);
    });
  addRow('PNL', labels.secOtherIncome, labels.totalOtherIncome, otherIncome, '', true);

  addRow('PNL', labels.secCogs, labels.openingStock, opening);
  addRow('PNL', labels.secCogs, labels.addPurchases, purchases);
  addRow('PNL', labels.secCogs, labels.lessClosingStock, -Math.abs(closing));
  addRow('PNL', labels.secCogs, labels.netCogs, netCOGS, '', true);
  addRow('PNL', labels.secCogs, labels.grossProfit, gross, '', true);

  OFFICIAL_EXPENSE_LABELS_MS.forEach((k) => {
    addRow('PNL', labels.secOpEx, tL(k), expensesByCategory[k] || 0);
  });
  Object.keys(expensesByCategory)
    .filter((k) => !OFFICIAL_EXPENSE_LABELS_MS.includes(k))
    .sort()
    .forEach((k) => addRow('PNL', labels.secExtraOpEx, tL(k), expensesByCategory[k] || 0));
  addRow('PNL', labels.secOpEx, labels.totalOpEx, expenses, '', true);
  addRow('PNL', labels.secOpEx, labels.netProfit, netProfit, '', true);

  addRow('BALANCE_SHEET', labels.secNonCurrentAssets, tL('Tanah dan bangunan'), land);
  addRow('BALANCE_SHEET', labels.secNonCurrentAssets, tL('Loji dan jentera'), equipment);
  addRow('BALANCE_SHEET', labels.secNonCurrentAssets, tL('Kenderaan'), vehicles);
  addRow('BALANCE_SHEET', labels.secNonCurrentAssets, tL('Aset bukan semasa lain'), otherNC);
  addRow('BALANCE_SHEET', labels.secNonCurrentAssets, labels.totalNonCurrentAssets, nonCurrent, '', true);
  addRow('BALANCE_SHEET', labels.secNonCurrentAssets, tL('Pelaburan'), investments);

  addRow('BALANCE_SHEET', labels.secCurrentAssets, labels.closingStockAsset, closing);
  addRow('BALANCE_SHEET', labels.secCurrentAssets, tL('Penghutang perdagangan'), debtors);
  addRow('BALANCE_SHEET', labels.secCurrentAssets, tL('Penghutang lain'), otherDebtors);
  addRow('BALANCE_SHEET', labels.secCurrentAssets, tL('Tunai'), cash);
  addRow('BALANCE_SHEET', labels.secCurrentAssets, tL('Bank'), bank);
  addRow('BALANCE_SHEET', labels.secCurrentAssets, labels.otherCurrentAsset, otherCurrent);
  addRow('BALANCE_SHEET', labels.secCurrentAssets, labels.totalCurrentAssets, current, '', true);
  addRow('BALANCE_SHEET', labels.secCurrentAssets, labels.totalAssets, totalAssets, '', true);

  addRow('BALANCE_SHEET', labels.secLiabilities, tL('Pinjaman dan overdraf'), loans);
  addRow('BALANCE_SHEET', labels.secLiabilities, tL('Pemiutang dagangan'), creditors);
  addRow('BALANCE_SHEET', labels.secLiabilities, tL('Pemiutang lain'), otherCreditors);
  addRow('BALANCE_SHEET', labels.secLiabilities, labels.totalLiabilities, totalLiabilities, '', true);

  addRow('BALANCE_SHEET', labels.secEquity, tL('Akaun modal'), capital);
  addRow('BALANCE_SHEET', labels.secEquity, labels.broughtForward, brought);
  addRow('BALANCE_SHEET', labels.secEquity, labels.currentYearProfit, netProfit);
  addRow('BALANCE_SHEET', labels.secEquity, labels.drawingsNet, -Math.abs(drawings));
  addRow('BALANCE_SHEET', labels.secEquity, labels.carriedForward, carried);
  addRow('BALANCE_SHEET', labels.secEquity, labels.totalEquity, totalEquity, '', true);
  addRow('BALANCE_SHEET', labels.secEquity, labels.totalLiabilitiesAndEquity, totalLE, '', true);

  const isBalanced = Math.abs(diff) < 0.01;
  addRow(
    'CHECK',
    labels.secCheck,
    labels.balanceDiff,
    diff,
    isBalanced ? labels.balanced : labels.unbalanced,
    true
  );

  return {
    selectedYear: String(year),
    cashBalance: cash,
    bankBalance: bank,
    totalMoneyNow: cash + bank,
    totalRevenue: revenue,
    revenueByCategory,
    totalOtherIncome: otherIncome,
    otherIncomeByCategory,
    totalPurchasesCogs: purchases,
    openingStock: opening,
    closingStock: closing,
    netCogs: netCOGS,
    grossProfit: gross,
    totalOperatingExpenses: expenses,
    expensesByCategory,
    totalExpenses: netCOGS + expenses,
    netProfit,
    landBuilding: land,
    equipmentAsset: equipment,
    vehicles,
    otherNonCurrent: otherNC,
    investments,
    tradeDebtors: debtors,
    otherDebtors,
    otherCurrent,
    loansLiability: loans,
    tradeCreditors: creditors,
    otherCreditors,
    ownerCapital: capital,
    ownerDrawings: drawings,
    broughtForward: brought,
    carriedForward: carried,
    nonCurrentAssets: nonCurrent,
    currentAssets: current,
    totalAssets,
    totalLiabilities,
    totalEquity,
    totalLiabilitiesAndEquity: totalLE,
    balanceDifference: diff,
    isBalanced,
    ledgers,
    rows
  };
}

import { defaultBeginningBalances } from './accounting-engine.js';
import { formatCurrency, statementLabels } from './strings.js';

function randomShortHex(len = 16) {
  const bytes = new Uint8Array(Math.ceil(len / 2));
  if (typeof globalThis.crypto !== 'undefined' && globalThis.crypto.getRandomValues) {
    globalThis.crypto.getRandomValues(bytes);
  } else {
    for (let i = 0; i < bytes.length; i++) {
      bytes[i] = Math.floor(Math.random() * 256);
    }
  }
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('').slice(0, len);
}

function normalizeTransactionType(raw) {
  const norm = String(raw || '').trim().toUpperCase();
  if (['IN', 'DUIT MASUK', 'MONEY IN'].includes(norm)) return 'IN';
  if (['OUT', 'DUIT KELUAR', 'MONEY OUT'].includes(norm)) return 'OUT';
  if (['CREDIT_SALE', 'JUALAN KREDIT'].includes(norm)) return 'CREDIT_SALE';
  if (['CREDIT_PURCHASE', 'BELIAN KREDIT'].includes(norm)) return 'CREDIT_PURCHASE';
  if (norm === 'CREDIT_NOTE') return 'CREDIT_NOTE';
  if (norm === 'DEBIT_NOTE') return 'DEBIT_NOTE';
  return 'IN';
}

function normalizePaymentMethod(raw) {
  const norm = String(raw || '').trim().toUpperCase();
  if (['CASH', 'TUNAI'].includes(norm)) return 'CASH';
  if (norm === 'BANK') return 'BANK';
  if (['CREDIT', 'KREDIT'].includes(norm)) return 'CREDIT';
  return 'CASH';
}

function normalizeDisposalType(raw) {
  const v = String(raw || '').trim();
  if (v === 'equipment' || v === 'equipments') return 'equipment';
  if (['landBuilding', 'vehicles', 'otherNonCurrent', 'investments'].includes(v)) return v;
  return null;
}

/**
 * Generates v2 JSON backup string matching Android `ExportManager.buildJsonBackup`.
 */
export function buildJsonBackup(workspace, transactions = []) {
  const sorted = [...transactions].sort((a, b) =>
    String(a.date || '').localeCompare(String(b.date || ''))
  );
  const root = {
    backupVersion: 2,
    exportedAt: new Date().toISOString().replace(/\.\d{3}Z$/, 'Z'),
    settings: {
      businessName: workspace.businessName || '',
      businessRegNo: workspace.businessRegNo || '',
      businessType: workspace.businessType || '',
      businessAddress: workspace.businessAddress || '',
      ownerName: workspace.ownerName || '',
      currency: workspace.currency || 'RM',
      financialYear: workspace.financialYear || '2026',
      closingStockByYear: workspace.closingStockByYear || {},
      beginningBalances: defaultBeginningBalances(workspace.beginningBalances)
    },
    transactions: sorted.map((t) => {
      const obj = {
        id: t.id,
        date: t.date,
        type: t.type,
        categoryId: t.categoryId,
        method: t.method,
        amount: Number(t.amount) || 0,
        note: t.note || ''
      };
      if (t.partyName) obj.partyName = t.partyName;
      if (t.invoiceNo) obj.invoiceNo = t.invoiceNo;
      if (t.disposalAssetType) obj.disposalAssetType = t.disposalAssetType;
      if (t.disposalAssetCost != null && Number.isFinite(Number(t.disposalAssetCost))) {
        obj.disposalAssetCost = Number(t.disposalAssetCost);
      }
      return obj;
    })
  };
  return JSON.stringify(root, null, 2);
}

/**
 * Parses both v2 Android/Web JSON backups and legacy BukuKira HTML JSON backups.
 */
export function parseJsonBackup(rawJson, businessId, uid, memberUids) {
  const root = JSON.parse(rawJson);
  const settings = root.settings && typeof root.settings === 'object' ? root.settings : root;

  const stockMap = {};
  if (settings.closingStockByYear && typeof settings.closingStockByYear === 'object') {
    for (const [k, rawV] of Object.entries(settings.closingStockByYear)) {
      const v = Number(rawV);
      if (Number.isFinite(v) && v >= 0) {
        stockMap[k] = v;
      }
    }
  }

  const bb =
    settings.beginningBalances && typeof settings.beginningBalances === 'object'
      ? defaultBeginningBalances(settings.beginningBalances)
      : null;

  const txArr = Array.isArray(root.transactions) ? root.transactions : [];
  const list = [];
  for (const item of txArr) {
    if (!item || typeof item !== 'object') continue;
    const date = String(item.date || '').slice(0, 10);
    if (!/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(date)) continue;

    const rawId = String(item.id || '').replace(/[^a-zA-Z0-9_\-]/g, '');
    const txId = rawId ? rawId.slice(0, 128) : `tx_${randomShortHex(16)}`;
    const type = normalizeTransactionType(item.type || 'IN');
    const method = normalizePaymentMethod(item.method || 'CASH');
    const rawAmt = Number(item.amount);
    const amount = Number.isFinite(rawAmt) && rawAmt >= 0 ? rawAmt : 0;
    const note = String(item.note || '').slice(0, 500);
    const categoryId = String(item.categoryId || 'exp_others').trim().slice(0, 60) || 'exp_others';
    const partyName = String(item.partyName || '').trim().slice(0, 120) || undefined;
    const invoiceNo = String(item.invoiceNo || '').trim().slice(0, 60) || undefined;
    const disposalAssetType = normalizeDisposalType(item.disposalAssetType) || undefined;
    const rawCost = item.disposalAssetCost != null ? Number(item.disposalAssetCost) : NaN;
    const disposalAssetCost = Number.isFinite(rawCost) && rawCost >= 0 ? rawCost : undefined;

    const tx = {
      id: txId,
      businessId,
      createdByUid: uid,
      memberUids,
      date,
      type,
      categoryId,
      method,
      amount,
      note
    };
    if (partyName) tx.partyName = partyName;
    if (invoiceNo) tx.invoiceNo = invoiceNo;
    if (disposalAssetType) tx.disposalAssetType = disposalAssetType;
    if (disposalAssetCost !== undefined) tx.disposalAssetCost = disposalAssetCost;
    list.push(tx);
  }

  const trimOpt = (v) => {
    const s = String(v || '').trim();
    return s || null;
  };
  const cur = trimOpt(settings.currency);
  const fy = trimOpt(settings.financialYear);

  return {
    businessName: trimOpt(settings.businessName),
    businessRegNo: trimOpt(settings.businessRegNo),
    businessType: trimOpt(settings.businessType),
    businessAddress: trimOpt(settings.businessAddress),
    ownerName: trimOpt(settings.ownerName),
    currency: ['RM', 'SGD', 'USD', 'Rp'].includes(cur) ? cur : null,
    financialYear: fy && /^[0-9]{4}$/.test(fy) ? fy : null,
    closingStockByYear: stockMap,
    beginningBalances: bb,
    transactions: list
  };
}

/**
 * Generates UTF-8 BOM CSV compatible with Microsoft Excel, matching `ExportManager.buildCsvReport`.
 */
export function buildCsvReport(workspace, transactions = [], stats, lang = 'ms') {
  const labels = statementLabels(lang);
  const csvCell = (val) => `"${String(val ?? '').replace(/"/g, '""')}"`;

  const lines = [];
  lines.push(`${csvCell('BukuKira Financial Export')},${csvCell(workspace.businessName)}`);
  lines.push(`${csvCell('Registration No')},${csvCell(workspace.businessRegNo)}`);
  lines.push(`${csvCell('Financial Year')},${csvCell(stats.selectedYear)}`);
  lines.push(`${csvCell('Currency')},${csvCell(workspace.currency)}`);
  lines.push('');

  lines.push(
    ['Date', 'Type', 'Category', 'Method', 'Party', 'Invoice', 'Amount', 'Note']
      .map(csvCell)
      .join(',')
  );

  transactions
    .filter((t) => String(t.date || '').startsWith(stats.selectedYear))
    .sort((a, b) => String(a.date || '').localeCompare(String(b.date || '')))
    .forEach((t) => {
      lines.push(
        [
          t.date,
          t.type,
          t.categoryId,
          t.method,
          t.partyName || '',
          t.invoiceNo || '',
          (Number(t.amount) || 0).toFixed(2),
          t.note || ''
        ]
          .map(csvCell)
          .join(',')
      );
    });

  lines.push('');
  lines.push(`${csvCell(labels.pnlTitle)} / ${csvCell(labels.bsTitle)}`);
  lines.push(
    ['Statement', 'Section', 'Account', `Amount (${workspace.currency})`, 'Note']
      .map(csvCell)
      .join(',')
  );
  (stats.rows || []).forEach((r) => {
    lines.push(
      [
        r.statementType,
        r.section,
        r.label,
        (Number(r.amount) || 0).toFixed(2),
        r.note || ''
      ]
        .map(csvCell)
        .join(',')
    );
  });
  lines.push('');
  lines.push(csvCell(labels.disclaimer));

  return `\uFEFF${lines.join('\n')}\n`;
}

export function escapeHtml(str) {
  return String(str ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

export function buildPrintableHtml(workspace, stats, lang = 'ms') {
  const labels = statementLabels(lang);
  const infoParts = [];
  if (workspace.businessRegNo) infoParts.push(escapeHtml(workspace.businessRegNo));
  if (workspace.ownerName) infoParts.push(escapeHtml(workspace.ownerName));
  infoParts.push(`FY ${escapeHtml(stats.selectedYear)}`);
  const infoLine = infoParts.join(' • ');

  const renderSectionTable = (type, heading) => {
    const filtered = (stats.rows || []).filter(
      (r) => r.statementType === type || (type === 'BALANCE_SHEET' && r.statementType === 'CHECK')
    );
    let lastSection = '';
    const rowsHtml = filtered
      .map((r) => {
        let secHtml = '';
        if (r.section !== lastSection) {
          lastSection = r.section;
          secHtml = `<tr class="print-sec-row"><td colspan="2">${escapeHtml(r.section)}</td></tr>`;
        }
        const cls = r.isTotal ? 'print-total-row' : 'print-data-row';
        const noteHtml = r.note
          ? `<div class="print-note">${escapeHtml(r.note)}</div>`
          : '';
        return `${secHtml}<tr class="${cls}"><td>${escapeHtml(r.label)}${noteHtml}</td><td class="num">${escapeHtml(
          formatCurrency(workspace.currency, r.amount)
        )}</td></tr>`;
      })
      .join('');

    return `
      <section class="print-page">
        <div class="print-header">
          <div class="print-biz">${escapeHtml(workspace.businessName || 'BukuKira')}</div>
          <div class="print-meta">${infoLine}</div>
          ${workspace.businessAddress ? `<div class="print-meta">${escapeHtml(workspace.businessAddress)}</div>` : ''}
          <div class="print-heading">${escapeHtml(heading)} — ${escapeHtml(stats.selectedYear)}</div>
        </div>
        <table class="print-table"><tbody>${rowsHtml}</tbody></table>
        <div class="print-disclaimer">${escapeHtml(labels.disclaimer)}</div>
      </section>
    `;
  };

  const ledgerEntries = Object.entries(stats.ledgers || {});
  const ledgerHtml =
    ledgerEntries.length > 0
      ? `
      <section class="print-page">
        <div class="print-header">
          <div class="print-biz">${escapeHtml(workspace.businessName || 'BukuKira')}</div>
          <div class="print-meta">${infoLine}</div>
          <div class="print-heading">${escapeHtml(labels.ledgerTitle)} — ${escapeHtml(stats.selectedYear)}</div>
        </div>
        ${ledgerEntries
          .map(([accName, entries]) => {
            let drSum = 0;
            let crSum = 0;
            const body = entries
              .map((e) => {
                drSum += Number(e.debit) || 0;
                crSum += Number(e.credit) || 0;
                return `<tr>
                  <td>${escapeHtml(e.date)}</td>
                  <td>${escapeHtml(e.description)}</td>
                  <td class="num">${e.debit > 0 ? Number(e.debit).toFixed(2) : ''}</td>
                  <td class="num">${e.credit > 0 ? Number(e.credit).toFixed(2) : ''}</td>
                </tr>`;
              })
              .join('');
            return `
              <div class="print-ledger-block">
                <div class="print-ledger-title">${escapeHtml(accName)}</div>
                <table class="print-table print-ledger-table">
                  <thead>
                    <tr><th>Date</th><th>Description</th><th class="num">Debit</th><th class="num">Credit</th></tr>
                  </thead>
                  <tbody>
                    ${body}
                    <tr class="print-total-row">
                      <td></td><td>Total</td>
                      <td class="num">${drSum.toFixed(2)}</td>
                      <td class="num">${crSum.toFixed(2)}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            `;
          })
          .join('')}
        <div class="print-disclaimer">${escapeHtml(labels.disclaimer)}</div>
      </section>
    `
      : '';

  return `
    ${renderSectionTable('PNL', labels.pnlTitle)}
    ${renderSectionTable('BALANCE_SHEET', labels.bsTitle)}
    ${ledgerHtml}
  `;
}

export function downloadTextFile(filename, content, mimeType = 'text/plain;charset=utf-8') {
  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  setTimeout(() => URL.revokeObjectURL(url), 1500);
}

export function printStatementsPdf(workspace, stats, lang = 'ms') {
  const printRoot = document.getElementById('print-root');
  if (!printRoot) return;
  printRoot.innerHTML = buildPrintableHtml(workspace, stats, lang);
  window.print();
}

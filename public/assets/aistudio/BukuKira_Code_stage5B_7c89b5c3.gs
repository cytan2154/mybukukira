/**
 * BukuKira private central backend (Master Sheet edition)
 * Deploy this from a PRIVATE Apps Script project owned by the developer.
 * The frontend must call this endpoint; do NOT publish/share this source with customers.
 *
 * Script Properties required:
 *   BUKUKIRA_MASTER_SHEET_ID = ID of the private Master Google Sheet
 *   BUKUKIRA_ADMIN_EMAIL = email address customers must share their own spreadsheet with as Editor
 *
 * Master Sheet tabs (created by setupBukuKiraMaster()):
 *   Licences: LicenceKey, BusinessID, BusinessName, OwnerName, Status, Plan,
 *             StartDate, ExpiryDate, MaxDevices, Notes, UpdatedAt, BoundSheetID
 *   The administrator manually enters each customer's Google Sheet ID in BoundSheetID.
 *   Customers must never submit or choose their own Sheet ID during login.
 *
 * IMPORTANT: this is the central validation/data API foundation. Do not enable paid
 * production access until the frontend is wired to this API and the server-side
 * accounting engine has replaced all client-side statement calculations.
 */
const BK_MASTER_PROPERTY = '1JcEBB3mJbRuMU9nI59jcDclpod85UvVbvcW98Zk7X0U';
const BK_TOKEN_PROPERTY = 'BUKUKIRA_SESSION_SIGNING_SECRET';
const BK_SESSION_TIMEZONE = 'Asia/Kuala_Lumpur';
const BK_LICENCE_HEADERS = ['LicenceKey','BusinessID','BusinessName','OwnerName','Status','Plan','StartDate','ExpiryDate','MaxDevices','Notes','UpdatedAt','BoundSheetID'];
const BK_DEVICE_HEADERS = ['BusinessID','InstallationID','FirstActivated','LastValidated','Platform'];
const BK_CUSTOMER_TX_HEADERS = ['Tarikh','Jenis','Kategori','Cara Bayaran','Jumlah','Nota','CategoryID','ID'];
const BK_CUSTOMER_SETTINGS_HEADERS = ['Key','Value'];

// Stage 4 operational telemetry. Logs contain aggregate timings/counts only:
// never log business IDs, licence/session tokens, transaction values, notes or names.
const BK_METRIC_SAMPLE_RATE = 0.10;
function bkMasterLogMetric_(action, startedAt, result, extra = {}) {
  const ok = !!(result && result.ok === true);
  if (ok && Math.random() > BK_METRIC_SAMPLE_RATE) return;
  const metric = {
    event: 'BUKUKIRA_METRIC', action: String(action || 'UNKNOWN'),
    durationMs: Math.max(0, Date.now() - startedAt), ok: ok,
    code: result && result.code ? String(result.code) : '',
    ...extra
  };
  const line = JSON.stringify(metric);
  if (ok) console.log(line); else console.warn(line);
}
function bkMasterLogSaveMetric_(metric) {
  try {
    if (metric.success && Math.random() > BK_METRIC_SAMPLE_RATE) return;
    const record = Object.assign({event:'BK_SAVE_METRIC', version:'5B', sampleRate:BK_METRIC_SAMPLE_RATE}, metric);
    const line = JSON.stringify(record);
    if (metric.success) console.log(line); else console.warn(line);
  } catch (_) {
    // Monitoring must never affect transaction persistence or its acknowledgement.
  }
}
function bkMasterSaveErrorCategory_(phase, error) {
  const message = String(error && error.message || error || '').toLowerCase();
  if (phase === 'ENCRYPTION' || /encrypt|integrity|base64/.test(message)) return 'ENCRYPTION';
  if (phase === 'VALIDATION' || /transaction id|invalid transaction/.test(message)) return 'VALIDATION';
  if (phase === 'INDEX_READ') return 'SPREADSHEET_READ';
  if (phase === 'WRITE' || phase === 'DELETE') return 'SPREADSHEET_WRITE';
  if (phase === 'SETTINGS') return 'SETTINGS';
  return 'INTERNAL';
}

function setupBukuKiraMaster() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  if (!ss) throw new Error('Open this script from the private Master Google Sheet first.');
  PropertiesService.getScriptProperties().setProperty(BK_MASTER_PROPERTY, ss.getId());
  bkMasterEnsureTab_(ss, 'Licences', BK_LICENCE_HEADERS);
  bkMasterEnsureTab_(ss, 'Devices', BK_DEVICE_HEADERS);
  SpreadsheetApp.flush();
  return 'Master Sheet is ready. Keep this spreadsheet private.';
}

function doGet(e) {
  const requestStartedAt = Date.now();
  const p = (e && e.parameter) || {};
  const action = String(p.action || '').toUpperCase();
  let result;
  if (action === 'PING') result = {ok:true, service:'BukuKira licensing API'};
  else if (action === 'VALIDATE') result = bkMasterValidate_(String(p.licenceKey || ''), String(p.businessId || ''), String(p.installationId || ''), String(p.platform || ''));
  else if (action === 'LOAD' || action === 'STATEMENTS' || action === 'ACK') {
    const auth = bkMasterVerifySession_(String(p.sessionToken || ''));
    if (!auth.ok) result = auth;
    else if (action === 'LOAD') result = bkMasterLoadCustomerData_(auth);
    else if (action === 'STATEMENTS') result = bkMasterGetStatements_(auth, String(p.year || new Date().getFullYear()), String(p.lang || 'ms'));
    else result = bkMasterGetSaveAck_(auth, String(p.batchId || ''));
  } else result = {ok:false, code:'ACTION_NOT_ALLOWED', message:'Unsupported request.'};
  bkMasterLogMetric_(action, requestStartedAt, result);
  return bkMasterOutput_(result, String(p.callback || ''));
}

function doPost(e) {
  const requestStartedAt = Date.now();
  let body = {};
  try { body = JSON.parse((e && e.postData && e.postData.contents) || '{}'); }
  catch (_) { return bkMasterJson_({ok:false, code:'BAD_REQUEST', message:'Invalid request.'}); }
  const action = String(body.action || '').toUpperCase();
  const saveMetrics = action === 'SAVE' ? {
    total_ms:0, lock_wait_ms:0, lock_held_ms:0, index_read_ms:0,
    encryption_ms:0, write_ms:0, delete_ms:0, settings_ms:0, ack_ms:0,
    batch_size:Array.isArray(body.transactions) ? body.transactions.length : 0,
    insert_count:0, update_count:0, delete_count:0, busy_retry:0,
    settings_included:!!(body.settings && typeof body.settings === 'object' && Object.keys(body.settings).length),
    success:false, error_category:''
  } : null;
  if (action === 'VALIDATE') {
    const validation = bkMasterValidate_(String(body.licenceKey || ''), String(body.businessId || ''), String(body.installationId || ''), String(body.platform || ''));
    bkMasterLogMetric_('VALIDATE', requestStartedAt, validation);
    return bkMasterJson_(validation);
  }

  // Normal data operations verify a signed, time-limited session token. They do not reread the Licences tab.
  const auth = bkMasterVerifySession_(String(body.sessionToken || ''));
  if (!auth.ok) {
    if (saveMetrics) {
      saveMetrics.total_ms = Date.now() - requestStartedAt;
      saveMetrics.error_category = 'SESSION_AUTH';
      bkMasterLogSaveMetric_(saveMetrics);
    }
    return bkMasterJson_(auth);
  }
  let result;
  if (action === 'STATEMENTS' || action === 'CALCULATE') result = bkMasterGetStatements_(auth, String(body.year || new Date().getFullYear()), String(body.lang || 'ms'));
  else if (action === 'LOAD') result = bkMasterLoadCustomerData_(auth);
  else if (action === 'SAVE') {
    result = bkMasterSaveCustomerData_(auth, body, saveMetrics);
    if (result && result.ok === true) bkMasterInvalidateBusinessCache_(auth.businessId);
    // Cache a compact acknowledgement only after the customer-sheet write has succeeded.
    // If the cache entry expires or is evicted, the client safely retries the idempotent batch.
    const batchId = String(body.batchId || '');
    const ackStartedAt = Date.now();
    if (result && result.ok === true && /^[A-Za-z0-9_-]{16,80}$/.test(batchId)) {
      const ack = {ok:true, batchId:batchId, savedCount:Number(result.savedCount)||0, deletedCount:Number(result.deletedCount)||0};
      try {
        CacheService.getScriptCache().put(bkMasterAckCacheKey_(auth.businessId, batchId), JSON.stringify(ack), 21600);
      } catch (cacheError) {
        // A cache failure must not undo a successful sheet write. The client will safely retry
        // the idempotent batch if its acknowledgement cannot be retrieved.
        console.error('BukuKira acknowledgement cache write failed:', cacheError);
      }
    }
    saveMetrics.ack_ms = Date.now() - ackStartedAt;
    saveMetrics.total_ms = Date.now() - requestStartedAt;
    saveMetrics.success = !!(result && result.ok === true);
    if (result && result.code === 'BUSY_RETRY') saveMetrics.busy_retry = 1;
    if (!saveMetrics.success && !saveMetrics.error_category) saveMetrics.error_category = String(result && result.code || 'UNKNOWN').replace(/[^A-Z0-9_]/g, '').slice(0,40) || 'UNKNOWN';
    bkMasterLogSaveMetric_(saveMetrics);
  }
  else result = {ok:false, code:'ACTION_NOT_ALLOWED', message:'Unsupported request.'};
  if (action !== 'SAVE') bkMasterLogMetric_(action, requestStartedAt, result);
  return bkMasterJson_(result);
}

function bkMasterAckCacheKey_(businessId, batchId) {
  const digest = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, String(businessId), Utilities.Charset.UTF_8);
  const shortBusinessKey = Utilities.base64EncodeWebSafe(digest).replace(/=+$/g, '').slice(0, 22);
  return 'BK_ACK_' + shortBusinessKey + '_' + String(batchId);
}

function bkMasterGetSaveAck_(auth, batchId) {
  if (!/^[A-Za-z0-9_-]{16,80}$/.test(batchId)) return {ok:false, code:'INVALID_BATCH_ID', message:'Invalid synchronisation reference.'};
  const cached = CacheService.getScriptCache().get(bkMasterAckCacheKey_(auth.businessId, batchId));
  if (!cached) return {ok:false, code:'ACK_PENDING', batchId:batchId};
  try {
    const ack = JSON.parse(cached);
    return ack && ack.ok === true && ack.batchId === batchId
      ? ack
      : {ok:false, code:'ACK_PENDING', batchId:batchId};
  } catch (_) {
    return {ok:false, code:'ACK_PENDING', batchId:batchId};
  }
}

// Private server-side accounting classification. Do not move this map into the public frontend.
const BK_ACCOUNT_MAP = {
 sales_goods:['Jualan Tunai','PNL_REVENUE'], other_income_dividend:['Dividen','PNL_OTHER_INCOME'], other_income_interest:['Faedah dan diskaun','PNL_OTHER_INCOME'], other_income_rent_royalty:['Sewa, royalti, dan premium','PNL_OTHER_INCOME'], other_income_general:['Pendapatan lain','PNL_OTHER_INCOME'], asset_disposal:['Pelupusan Aset','BS_ASSET_DISPOSAL'], purchase_return_refund:['Pulangan Belian','PNL_PURCHASE_RETURN'], transfer_bank_to_cash:['Pindahan Bank ke Tunai','BS_TRANSFER'], sales_service:['Upah Servis / Kerja (rekod lama)','PNL_REVENUE'], credit_sale:['Jualan Kredit','PNL_REVENUE'],
 debtors_payment:['Penghutang perdagangan','BS_TRADE_DEBTORS_COLLECT'], owner_capital:['Modal','BS_EQUITY_ADD'], drawing_return:['Ambilan','BS_EQUITY_DRAW_RETURN'], loan_received:['Pinjaman dan Overdraft','BS_LIABILITY_ADD'],
 buy_stock:['Belian','PNL_COGS'], sales_return_refund:['Pulangan Jualan','PNL_SALES_RETURN'], transfer_cash_to_bank:['Pindahan Tunai ke Bank','BS_TRANSFER'], credit_purchase:['Belian','PNL_COGS'], exp_loan_interest:['Faedah pinjaman','PNL_EXPENSE'], staff_wages:['Gaji dan upah','PNL_EXPENSE'], rent_utilities:['Sewa/pajakan','PNL_EXPENSE'], exp_contract:['Kontrak dan subkontrak','PNL_EXPENSE'], exp_commission:['Komisen','PNL_EXPENSE'], exp_bad_debt:['Hutang lapuk','PNL_EXPENSE'], transport_fuel:['Perjalanan dan pengangkutan','PNL_EXPENSE'], exp_repair:['Pembaikan dan penyelenggaraan','PNL_EXPENSE'], exp_promo:['Promosi dan iklan','PNL_EXPENSE'], exp_others:['Perbelanjaan-perbelanjaan lain','PNL_EXPENSE'],
 buy_land_building:['Tanah dan Bangunan','BS_LAND_BUILDING_ADD'], buy_equipment:['Loji dan Jentera','BS_EQUIPMENT_ADD'], buy_vehicle:['Kenderaan','BS_VEHICLES_ADD'], buy_other_noncurrent:['Aset Bukan Semasa Lain','BS_OTHER_NONCURRENT_ADD'], buy_investment:['Pelaburan','BS_INVESTMENT_ADD'], advance_other_debtor:['Penghutang lain','BS_OTHER_DEBTORS_ADD'], prepayment_other_current:['Deposit / Bayaran Prabayar','BS_OTHER_CURRENT_ADD'], owner_drawing:['Ambilan','BS_EQUITY_DRAW'], loan_repay:['Pinjaman dan Overdraft','BS_LIABILITY_PAY'], creditors_payment:['Bayar Pemiutang Dagangan','BS_TRADE_CREDITORS_PAY'], other_creditors_payment:['Pemiutang Lain','BS_OTHER_CREDITORS_PAY']
};
const BK_EXPENSE_LABELS_SERVER = ['Faedah pinjaman','Gaji dan upah','Sewa/pajakan','Kontrak dan subkontrak','Komisen','Hutang lapuk','Perjalanan dan pengangkutan','Pembaikan dan penyelenggaraan','Promosi dan iklan','Perbelanjaan-perbelanjaan lain'];

function bkMasterBusinessCacheKey_(businessId) {
  const digest = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, String(businessId), Utilities.Charset.UTF_8);
  return Utilities.base64EncodeWebSafe(digest).replace(/=+$/g, '').slice(0, 24);
}

function bkMasterInvalidateBusinessCache_(businessId) {
  // A versioned key invalidates every year/language statement cache for this business
  // without needing to enumerate cache entries. Statement payloads expire after 3 minutes.
  try {
    CacheService.getScriptCache().put('BK_STMT_VER_' + bkMasterBusinessCacheKey_(businessId), Utilities.getUuid(), 21600);
  } catch (err) {
    console.error('BukuKira statement cache invalidation failed:', err);
  }
}

function bkMasterGetStatements_(auth, yearText, lang = 'ms') {
  try {
    const year = parseInt(yearText,10);
    if (!Number.isInteger(year) || year < 1900 || year > 2200) return {ok:false,code:'INVALID_YEAR',message:'Invalid financial year.'};
    const statementCache = CacheService.getScriptCache();
    const businessCacheKey = bkMasterBusinessCacheKey_(auth.businessId);
    const versionKey = 'BK_STMT_VER_' + businessCacheKey;
    const cacheVersion = statementCache.get(versionKey) || '0';
    const statementCacheKey = 'BK_STMT_' + businessCacheKey + '_' + cacheVersion + '_' + year + '_' + (['en','zh'].includes(lang) ? lang : 'ms');
    try {
      const cachedStatement = statementCache.get(statementCacheKey);
      if (cachedStatement) return JSON.parse(cachedStatement);
    } catch (cacheReadError) {
      console.error('BukuKira statement cache read failed:', cacheReadError);
    }
    
    // Ledger Translations - Extended for all transaction categories
    const LEDGER_TRANSLATIONS = {
      'ms': {},
      'en': {
        'Tunai': 'Cash', 'Bank': 'Bank', 'Penghutang perdagangan': 'Trade receivables', 'Pemiutang dagangan': 'Trade payables',
        'Pemiutang lain': 'Other payables', 'Tanah dan bangunan': 'Land and buildings', 'Loji dan jentera': 'Plant and machinery',
        'Kenderaan': 'Vehicles', 'Aset bukan semasa lain': 'Other non-current assets', 'Pelaburan': 'Investments',
        'Pinjaman dan overdraf': 'Loans and overdrafts', 'Akaun modal': 'Capital account', 'Penghutang lain': 'Other receivables',
        'Baki Bawa Hadapan': 'Balance Brought Forward', 'Belian': 'Purchases', 'Ambilan': 'Drawings',
        'Jualan Tunai': 'Cash Sales', 'Jualan Kredit': 'Credit Sales', 'Dividen': 'Dividend',
        'Faedah dan diskaun': 'Interest and discounts', 'Sewa, royalti, dan premium': 'Rent, royalty, and premium',
        'Pendapatan lain': 'Other income', 'Pelupusan Aset': 'Asset Disposal', 'Pulangan Belian': 'Purchase Return',
        'Pindahan Bank ke Tunai': 'Bank to Cash Transfer', 'Upah Servis / Kerja (rekod lama)': 'Service Wages',
        'Modal': 'Capital', 'Pinjaman dan Overdraft': 'Loans and Overdrafts', 'Pulangan Jualan': 'Sales Return',
        'Pindahan Tunai ke Bank': 'Cash to Bank Transfer', 'Faedah pinjaman': 'Loan interest', 'Gaji dan upah': 'Salaries and wages',
        'Sewa/pajakan': 'Rent / lease', 'Kontrak dan subkontrak': 'Contract and subcontract', 'Komisen': 'Commission',
        'Hutang lapuk': 'Bad debts', 'Perjalanan dan pengangkutan': 'Travel and transportation',
        'Pembaikan dan penyelenggaraan': 'Repairs and maintenance', 'Promosi dan iklan': 'Promotion and advertising',
        'Perbelanjaan-perbelanjaan lain': 'Other expenses', 'Deposit / Bayaran Prabayar': 'Deposit / Prepayments'
      },
      'zh': {
        'Tunai': '现金', 'Bank': '银行', 'Penghutang perdagangan': '贸易应收款', 'Pemiutang dagangan': '贸易应付款',
        'Pemiutang lain': '其他应付款', 'Tanah dan bangunan': '土地及建筑物', 'Loji dan jentera': '厂房及机器',
        'Kenderaan': '车辆', 'Aset bukan semasa lain': '其他非流动资产', 'Pelaburan': '投资',
        'Pinjaman dan overdraf': '贷款及透支', 'Akaun modal': '资本账户', 'Penghutang lain': '其他应收款',
        'Baki Bawa Hadapan': '期初结转余额', 'Belian': '采购', 'Ambilan': '提款',
        'Jualan Tunai': '现金销售', 'Jualan Kredit': '赊销', 'Dividen': '股息',
        'Faedah dan diskaun': '利息和折扣', 'Sewa, royalti, dan premium': '租金、特许权使用费及溢价',
        'Pendapatan lain': '其他收入', 'Pelupusan Aset': '资产处置', 'Pulangan Belian': '采购退货',
        'Pindahan Bank ke Tunai': '银行转现金', 'Upah Servis / Kerja (rekod lama)': '服务费',
        'Modal': '资本', 'Pinjaman dan Overdraft': '贷款及透支', 'Pulangan Jualan': '销售退货',
        'Pindahan Tunai ke Bank': '现金转银行', 'Faedah pinjaman': '贷款利息', 'Gaji dan upah': '工资',
        'Sewa/pajakan': '租金 / 租赁', 'Kontrak dan subkontrak': '合同和分包', 'Komisen': '佣金',
        'Hutang lapuk': '坏账', 'Perjalanan dan pengangkutan': '交通费',
        'Pembaikan dan penyelenggaraan': '维修和保养', 'Promosi dan iklan': '推广和广告',
        'Perbelanjaan-perbelanjaan lain': '其他费用', 'Deposit / Bayaran Prabayar': '押金 / 预付款项'
      }
    };
    
    // Support ms, en, and zh correctly
    const tLang = ['en', 'zh'].includes(lang) ? lang : 'ms';
    const tL = (key) => LEDGER_TRANSLATIONS[tLang][key] || key;

    const ss = SpreadsheetApp.openById(auth.customerSheetId);
    const txSheet = ss.getSheetByName('Transactions');
    const settingsSheet = ss.getSheetByName('Settings');
    const txs = [];
    if (txSheet && txSheet.getLastRow()>1) {
      const vals=txSheet.getRange(2,1,txSheet.getLastRow()-1,8).getValues();
      vals.forEach(r=>{ const d=bkTxRowDecode_(r, auth.businessId); if(d) txs.push({date:d.date,type:d.type,categoryId:d.categoryId,method:d.method,amount:d.amount,note:d.note,id:d.id}); });
    }
    const cfg={};
    if(settingsSheet && settingsSheet.getLastRow()>1) settingsSheet.getRange(2,1,settingsSheet.getLastRow()-1,2).getValues().forEach(r=>{let v=bkSettingDecode_(r[1], auth.businessId); if(typeof v==='string'){try{v=JSON.parse(v)}catch(e){}} cfg[String(r[0])]=v;});
    const settings=cfg;
    const balances = typeof settings.beginningBalances==='object' && settings.beginningBalances ? settings.beginningBalances : {};
    let revenue=0,otherIncome=0,purchases=0,expenses=0,drawings=0,priorProfit=0; const revenueByCategory={},otherIncomeByCategory={},expensesByCategory={};
    const parseDisposal = t => { const m=String(t.note||'').match(/^BK_ASSET_DISPOSAL\|([^|]+)\|([0-9]+(?:\.[0-9]+)?)\|(.*)$/); if(!m) return null; const cost=Number(m[2]); return Number.isFinite(cost)&&cost>0 ? {assetType:m[1],cost,note:m[3]||''} : null; };
    let disposalGainLoss=0; let hasDisposalRecord=false;
    let cash=Number(balances.cash)||0, bank=Number(balances.bank)||0, land=Number(balances.landBuilding)||0, equipment=Number(balances.equipment)||0, vehicles=Number(balances.vehicles)||0, otherNC=Number(balances.otherNonCurrent)||0, investments=Number(balances.investments)||0, debtors=Number(balances.tradeDebtors)||0, creditors=Number(balances.tradeCreditors)||0, otherCreditors=Number(balances.otherCreditors)||0, otherDebtors=Number(balances.otherDebtors)||0, otherCurrent=Number(balances.otherCurrent)||0, loans=Number(balances.loans)||0, capital=Number(balances.capital)||0;
let bf = { 'Tunai': cash, 'Bank': bank, 'Penghutang perdagangan': debtors, 'Pemiutang dagangan': creditors, 'Pemiutang lain': otherCreditors, 'Tanah dan bangunan': land, 'Loji dan jentera': equipment, 'Kenderaan': vehicles, 'Aset bukan semasa lain': otherNC, 'Pelaburan': investments, 'Pinjaman dan overdraf': loans, 'Akaun modal': capital };
    txs.forEach(t=>{
      const ty=parseInt(String(t.date).slice(0,4),10), amt=Number(t.amount)||0, meta=BK_ACCOUNT_MAP[t.categoryId]||['Kategori lain','PNL_EXPENSE'], st=meta[1]; const typ=String(t.type||'').toUpperCase(); const method=String(t.method||'').toUpperCase(); const isIn=(typ==='IN'||typ==='DUIT MASUK'||typ==='MONEY IN'); const isOut=(typ==='OUT'||typ==='DUIT KELUAR'||typ==='MONEY OUT'); const isCreditSale=(typ==='CREDIT_SALE'||typ==='JUALAN KREDIT'); const isCreditPurchase=(typ==='CREDIT_PURCHASE'||typ==='BELIAN KREDIT'); const isCreditNote=(typ==='CREDIT_NOTE'); const isDebitNote=(typ==='DEBIT_NOTE'); const isStockReturn=(st==='PNL_COGS'||st==='PNL_PURCHASE_RETURN'); const isCash=(method==='CASH'||method==='TUNAI');
      if(ty===year){
        if(st==='PNL_REVENUE'){revenue+=amt;revenueByCategory[meta[0]]=(revenueByCategory[meta[0]]||0)+amt;}
        if(st==='PNL_OTHER_INCOME'){otherIncome+=amt;otherIncomeByCategory[meta[0]]=(otherIncomeByCategory[meta[0]]||0)+amt;}
        if(st==='BS_ASSET_DISPOSAL'){ const d=parseDisposal(t); if(d){ const diff=amt-d.cost; disposalGainLoss+=diff; hasDisposalRecord=true; otherIncome+=diff; } else { /* Legacy records lack the asset's carrying amount, so do not treat gross proceeds as profit. */ } }
        if(st==='PNL_SALES_RETURN'){revenue-=amt;revenueByCategory[meta[0]]=(revenueByCategory[meta[0]]||0)-amt;}
        if(st==='PNL_COGS')purchases+=amt; if(st==='PNL_PURCHASE_RETURN')purchases-=amt;
        if(st==='PNL_EXPENSE'){expenses+=amt;expensesByCategory[meta[0]]=(expensesByCategory[meta[0]]||0)+amt;}
        if(st==='BS_EQUITY_DRAW')drawings+=amt; if(st==='BS_EQUITY_DRAW_RETURN')drawings-=amt;
      } else if(ty<year){
        if(st==='PNL_REVENUE'||st==='PNL_OTHER_INCOME')priorProfit+=amt;
        if(st==='BS_ASSET_DISPOSAL'){const d=parseDisposal(t); priorProfit+=d?(amt-d.cost):amt;}
        if(st==='PNL_SALES_RETURN'||st==='PNL_COGS'||st==='PNL_EXPENSE'||st==='BS_EQUITY_DRAW')priorProfit-=amt;
        if(st==='PNL_PURCHASE_RETURN'||st==='BS_EQUITY_DRAW_RETURN')priorProfit+=amt;
        
        if(st==='BS_TRANSFER'){if(t.categoryId==='transfer_bank_to_cash'){bf['Baki dalam bank']-=amt;bf['Baki tunai']+=amt;}else if(t.categoryId==='transfer_cash_to_bank'){bf['Baki tunai']-=amt;bf['Baki dalam bank']+=amt;}}
        else if(isIn){if(isCash)bf['Baki tunai']+=amt;else bf['Baki dalam bank']+=amt;} else if(isOut){if(isCash)bf['Baki tunai']-=amt;else bf['Baki dalam bank']-=amt;}
        if(st==='BS_LAND_BUILDING_ADD')bf['Tanah dan bangunan']+=amt; if(st==='BS_EQUIPMENT_ADD'||st==='BS_ASSET_ADD')bf['Loji dan jentera']+=amt; if(st==='BS_VEHICLES_ADD')bf['Kenderaan']+=amt; if(st==='BS_OTHER_NONCURRENT_ADD')bf['Aset bukan semasa lain']+=amt;
        if(st==='BS_ASSET_DISPOSAL'){const d=parseDisposal(t); if(d){if(d.assetType==='landBuilding')bf['Tanah dan bangunan']-=d.cost; else if(d.assetType==='vehicles')bf['Kenderaan']-=d.cost; else if(d.assetType==='otherNonCurrent')bf['Aset bukan semasa lain']-=d.cost; else if(d.assetType==='investments')bf['Pelaburan']-=d.cost; else bf['Loji dan jentera']-=d.cost;}} if(st==='BS_INVESTMENT_ADD')bf['Pelaburan']+=amt; if(st==='BS_TRADE_DEBTORS_COLLECT')bf['Penghutang perdagangan']-=amt; if(st==='BS_TRADE_CREDITORS_PAY')bf['Pemiutang dagangan']-=amt; if(isCreditSale)bf['Penghutang perdagangan']+=amt; if(st==='BS_OTHER_CREDITORS_PAY')bf['Pemiutang lain']-=amt; if(isCreditPurchase){if(st==='PNL_COGS')bf['Pemiutang dagangan']+=amt;else bf['Pemiutang lain']+=amt;} if(isCreditNote)bf['Penghutang perdagangan']-=amt; if(isDebitNote){if(isStockReturn)bf['Pemiutang dagangan']-=amt;else bf['Pemiutang lain']-=amt;} if(st==='BS_LIABILITY_ADD')bf['Pinjaman dan overdraf']+=amt; if(st==='BS_LIABILITY_PAY')bf['Pinjaman dan overdraf']-=amt; if(st==='BS_EQUITY_ADD')bf['Akaun modal']+=amt;
      }
      if(ty<=year){
        if(st==='BS_TRANSFER'){if(t.categoryId==='transfer_bank_to_cash'){bank-=amt;cash+=amt;}else if(t.categoryId==='transfer_cash_to_bank'){cash-=amt;bank+=amt;}}
        else if(isIn){if(isCash)cash+=amt;else bank+=amt;} else if(isOut){if(isCash)cash-=amt;else bank-=amt;}
        if(st==='BS_LAND_BUILDING_ADD')land+=amt; if(st==='BS_EQUIPMENT_ADD'||st==='BS_ASSET_ADD')equipment+=amt; if(st==='BS_VEHICLES_ADD')vehicles+=amt; if(st==='BS_OTHER_NONCURRENT_ADD')otherNC+=amt;
        if(st==='BS_ASSET_DISPOSAL'){const d=parseDisposal(t); if(d){if(d.assetType==='landBuilding')land-=d.cost; else if(d.assetType==='vehicles')vehicles-=d.cost; else if(d.assetType==='otherNonCurrent')otherNC-=d.cost; else if(d.assetType==='investments')investments-=d.cost; else equipment-=d.cost;}} if(st==='BS_INVESTMENT_ADD')investments+=amt; if(st==='BS_TRADE_DEBTORS_COLLECT')debtors-=amt; if(st==='BS_TRADE_CREDITORS_PAY')creditors-=amt; if(isCreditSale)debtors+=amt; if(st==='BS_OTHER_CREDITORS_PAY')otherCreditors-=amt; if(isCreditPurchase){if(st==='PNL_COGS')creditors+=amt;else otherCreditors+=amt;} if(isCreditNote)debtors-=amt; if(isDebitNote){if(isStockReturn)creditors-=amt;else otherCreditors-=amt;} if(st==='BS_OTHER_DEBTORS_ADD')otherDebtors+=amt; if(st==='BS_OTHER_CURRENT_ADD')otherCurrent+=amt; if(st==='BS_LIABILITY_ADD')loans+=amt; if(st==='BS_LIABILITY_PAY')loans-=amt; if(st==='BS_EQUITY_ADD')capital+=amt;
      }
    });
    const ledgers = {};
    const addLedger = (acc, date, desc, dr, cr) => {
      if(acc === 'Beli Aset Bukan Semasa Lain') acc = 'Aset bukan semasa lain';
      if(acc === 'Beli Loji / Mesin / Peralatan') acc = 'Loji dan jentera';
      if(acc === 'Beli Kenderaan Perniagaan') acc = 'Kenderaan';
      if(acc === 'Beli Tanah / Bangunan') acc = 'Tanah dan bangunan';
      
      const translatedAcc = tL(acc);
      if(!ledgers[translatedAcc]) ledgers[translatedAcc] = [];
      ledgers[translatedAcc].push({date, description: desc, debit: dr, credit: cr});
    };

    Object.keys(bf).forEach(acc => {
       if (bf[acc] !== 0) {
           const isDebit = ['Tunai','Bank','Penghutang perdagangan','Tanah dan bangunan','Loji dan jentera','Kenderaan','Aset bukan semasa lain','Pelaburan'].includes(acc);
           if (isDebit) addLedger(acc, `${year}-01-01`, tL('Baki Bawa Hadapan'), bf[acc] > 0 ? bf[acc] : 0, bf[acc] < 0 ? -bf[acc] : 0);
           else addLedger(acc, `${year}-01-01`, tL('Baki Bawa Hadapan'), bf[acc] < 0 ? -bf[acc] : 0, bf[acc] > 0 ? bf[acc] : 0);
       }
    });

    txs.filter(t => parseInt(String(t.date).slice(0,4),10) === year).forEach(t => {
      const amt = Number(t.amount)||0;
      const meta = BK_ACCOUNT_MAP[t.categoryId]||['Kategori lain','PNL_EXPENSE'];
      const st = meta[1];
      const typ = String(t.type||'').toUpperCase();
      const method = String(t.method||'').toUpperCase();
      
      const isCreditSale = (typ==='CREDIT_SALE'||typ==='JUALAN KREDIT');
      const isCreditPurchase = (typ==='CREDIT_PURCHASE'||typ==='BELIAN KREDIT');
      const isCreditNote = (typ==='CREDIT_NOTE');
      const isDebitNote = (typ==='DEBIT_NOTE');
      // 2. Masukkan jualan/belian kredit ke dalam tangkapan isIn dan isOut
      const isIn = (typ==='IN'||typ==='DUIT MASUK'||typ==='MONEY IN'||isCreditSale);
      const isOut = (typ==='OUT'||typ==='DUIT KELUAR'||typ==='MONEY OUT'||isCreditPurchase);
      const isCash = (method==='CASH'||method==='TUNAI');
      
      let drAcc = '', crAcc = '', note = t.note || '';
      
      if (note.startsWith('BK_ASSET_DISPOSAL|')) {
          const parts = note.split('|');
          const assetLabels = { 
              equipments: 'Loji & Jentera', 
              landBuilding: 'Tanah & Bangunan', 
              vehicles: 'Kenderaan', 
              otherNonCurrent: 'Aset Lain', 
              investments: 'Pelaburan' 
          };
          const aType = assetLabels[parts[1]] || parts[1];
          const aCost = parts[2];
          const userNote = parts.slice(3).join(' - ');
          note = `Kos RM ${aCost} (${aType})` + (userNote ? ` - ${userNote}` : '');
      }
      
      if (st === 'BS_TRANSFER') {
          if (t.categoryId === 'transfer_bank_to_cash') { drAcc = 'Tunai'; crAcc = 'Bank'; }
          else { drAcc = 'Bank'; crAcc = 'Tunai'; }
      } else if (isCreditNote) {
          drAcc = meta[0]; crAcc = 'Penghutang perdagangan';
      } else if (isDebitNote) {
          drAcc = (st==='PNL_COGS'||st==='PNL_PURCHASE_RETURN') ? 'Pemiutang dagangan' : 'Pemiutang lain'; crAcc = meta[0];
      } else if (st === 'BS_TRADE_DEBTORS_COLLECT') {
          drAcc = isCash ? 'Tunai' : 'Bank'; crAcc = 'Penghutang perdagangan';
      } else if (st === 'BS_TRADE_CREDITORS_PAY') {
          drAcc = 'Pemiutang dagangan'; crAcc = isCash ? 'Tunai' : 'Bank';
      } else if (st === 'BS_OTHER_CREDITORS_PAY') {
          drAcc = 'Pemiutang lain'; crAcc = isCash ? 'Tunai' : 'Bank';
      } else if (isIn) {
          crAcc = meta[0];
          if (isCreditSale) drAcc = 'Penghutang perdagangan';
          else drAcc = isCash ? 'Tunai' : 'Bank';
      } else if (isOut) {
          drAcc = meta[0];
          if (isCreditPurchase) crAcc = st==='PNL_COGS' ? 'Pemiutang dagangan' : 'Pemiutang lain';
          else crAcc = isCash ? 'Tunai' : 'Bank';
      }
      
      if (drAcc && crAcc) {
          const formatDesc = (mainAcc, oppAcc) => {
              const oppTranslated = tL(oppAcc);
              return note ? oppTranslated + ' - ' + note : oppTranslated;
          };
          addLedger(drAcc, t.date, formatDesc(drAcc, crAcc), amt, 0);
          addLedger(crAcc, t.date, formatDesc(crAcc, drAcc), 0, amt);
      }
    });
    // Present all disposal gains and losses as one net line, never separate gain/loss rows.
    if(hasDisposalRecord){ otherIncomeByCategory['Untung / (Rugi) bersih pelupusan aset']=disposalGainLoss; }
    const stockMap=typeof settings.closingStockByYear==='object'&&settings.closingStockByYear?settings.closingStockByYear:{};
    let latest=null; Object.keys(stockMap).forEach(k=>{const ky=parseInt(k,10);if(Number.isFinite(ky)&&ky<year&&(latest===null||ky>latest))latest=ky;});
    const opening=latest!==null?Math.max(0,Number(stockMap[String(latest)])||0):Math.max(0,Number(balances.openingStock)||0);
    const closing=Object.prototype.hasOwnProperty.call(stockMap,String(year))?Math.max(0,Number(stockMap[String(year)])||0):opening;
    const netCOGS=opening+purchases-closing, gross=revenue-netCOGS, netProfit=gross+otherIncome-expenses;
    const firstStock=Math.max(0,Number(balances.openingStock)||0), brought=(Number(balances.broughtForward)||0)+priorProfit+(opening-firstStock), carried=brought+netProfit-drawings;
    const nonCurrent=land+equipment+vehicles+otherNC, current=closing+debtors+otherDebtors+cash+bank+otherCurrent, totalAssets=nonCurrent+investments+current, totalLiabilities=loans+creditors+otherCreditors, totalEquity=capital+carried, totalLE=totalLiabilities+totalEquity;
    const stats={selectedYear:String(year),totalExpenses:netCOGS+expenses,cashBalance:cash,bankBalance:bank,totalMoneyNow:cash+bank,totalRevenue:revenue,revenueByCategory,totalOtherIncome:otherIncome,otherIncomeByCategory,totalPurchasesCOGS:purchases,openingStock:opening,closingStock:closing,netCOGS,grossProfit:gross,totalOperatingExpenses:expenses,expensesByCategory,netProfit,landBuilding:land,equipmentAsset:equipment,vehicles,otherNonCurrent:otherNC,investments,tradeDebtors:debtors,otherDebtors,otherCurrent,loansLiability:loans,tradeCreditors:creditors,otherCreditors,ownerCapital:capital,ownerDrawings:drawings,broughtForward:brought,carriedForward:carried,nonCurrentAssets:nonCurrent,currentAssets:current,totalAssets,totalLiabilities,totalEquity,totalLiabilitiesAndEquity:totalLE,balanceDifference:totalAssets-totalLE,isBalanced:Math.abs(totalAssets-totalLE)<0.01, ledgers:ledgers};
    const rows=[]; const add=(statement,section,label,amount,note='')=>rows.push({statement,section,label,amount:Number(amount)||0,note});
    Object.keys(revenueByCategory).sort().forEach(k=>add('Penyata Untung Rugi','JUALAN / HASIL PERNIAGAAN',k,revenueByCategory[k])); add('Penyata Untung Rugi','JUALAN / HASIL PERNIAGAAN','JUMLAH JUALAN / HASIL',revenue); Object.keys(otherIncomeByCategory).sort().filter(k=>!k.toLowerCase().includes('kos asal diperlukan')).forEach(k=>add('Penyata Untung Rugi','PENDAPATAN LAIN',k,otherIncomeByCategory[k],k.toLowerCase().includes('pelupusan')?'Amaun bersih sahaja: hasil jualan aset ditolak amaun aset yang direkodkan; tiada susut nilai automatik.':'')); add('Penyata Untung Rugi','PENDAPATAN LAIN','JUMLAH PENDAPATAN LAIN',otherIncome);
    add('Penyata Untung Rugi','KOS JUALAN (STOK BARANG NIAGA)','Stok awal tahun',opening); add('Penyata Untung Rugi','KOS JUALAN (STOK BARANG NIAGA)','Tambah: Belian stok dan bahan mentah',purchases); add('Penyata Untung Rugi','KOS JUALAN (STOK BARANG NIAGA)','Tolak: Baki stok akhir tahun',-Math.abs(closing)); add('Penyata Untung Rugi','KOS JUALAN (STOK BARANG NIAGA)','KOS JUALAN BERSIH',netCOGS); add('Penyata Untung Rugi','RINGKASAN UNTUNG RUGI','UNTUNG KASAR',gross);
    BK_EXPENSE_LABELS_SERVER.forEach(k=>add('Penyata Untung Rugi','PERBELANJAAN OPERASI',k,expensesByCategory[k]||0)); Object.keys(expensesByCategory).filter(k=>!BK_EXPENSE_LABELS_SERVER.includes(k)).forEach(k=>add('Penyata Untung Rugi','PERBELANJAAN OPERASI - KATEGORI TAMBAHAN',k,expensesByCategory[k])); add('Penyata Untung Rugi','PERBELANJAAN OPERASI','JUMLAH PERBELANJAAN OPERASI',expenses); add('Penyata Untung Rugi','RINGKASAN UNTUNG RUGI','Tambah: Pendapatan lain',otherIncome); add('Penyata Untung Rugi','RINGKASAN UNTUNG RUGI','Tolak: Perbelanjaan operasi',-expenses); add('Penyata Untung Rugi','RINGKASAN UNTUNG RUGI','UNTUNG / (RUGI) BERSIH',netProfit);
    [['Tanah dan bangunan',land],['Loji dan jentera',equipment],['Kenderaan',vehicles],['Aset bukan semasa lain',otherNC],['JUMLAH ASET BUKAN SEMASA',nonCurrent],['Pelaburan',investments],['Inventori / baki stok akhir',closing],['Penghutang perdagangan',debtors],['Penghutang lain',otherDebtors],['Baki tunai',cash],['Baki dalam bank',bank],['Aset semasa lain',otherCurrent],['JUMLAH ASET SEMASA',current],['JUMLAH ASET',totalAssets],['Pinjaman dan overdraf',loans],['Pemiutang dagangan',creditors],['Pemiutang lain',otherCreditors],['JUMLAH LIABILITI',totalLiabilities],['Akaun modal',capital],['Baki akaun semasa bawa hadapan',brought],['Untung / (rugi) tahun semasa',netProfit],['Ambilan / pendahuluan (bersih)',-Math.abs(drawings)],['Baki akaun semasa hantar hadapan',carried],['JUMLAH EKUITI PEMILIK',totalEquity],['JUMLAH LIABILITI DAN EKUITI',totalLE]].forEach((r,i)=>{let sec=i<6?'ASET BUKAN SEMASA':(i<14?'ASET / ASET SEMASA':(i<18?'LIABILITI':'EKUITI PEMILIK'));add('Kunci Kira-Kira',sec,r[0],r[1]);});
    add('Semakan','SEMAKAN IMBANGAN AKAUN','Perbezaan: Jumlah Aset - Liabiliti dan Ekuiti',stats.balanceDifference,stats.isBalanced?'SEIMBANG':'TIDAK SEIMBANG');
    const statementResult = {ok:true,year:String(year),stats:stats,statements:rows,generatedAt:new Date().toISOString(),transactionCount:txs.length};
    try {
      const cachePayload = JSON.stringify(statementResult);
      // Apps Script CacheService has a per-entry size limit; skip caching large ledger responses.
      if (cachePayload.length <= 90000) statementCache.put(statementCacheKey, cachePayload, 180);
    } catch (cacheWriteError) {
      console.error('BukuKira statement cache write failed:', cacheWriteError);
    }
    return statementResult;
  } catch(err) {
    // Temporary diagnostics: the previous version concealed the actual cause.
    const detail = (err && err.message) ? String(err.message) : String(err || 'Unknown error');
    console.error('BukuKira statement generation failed: ' + detail + '\n' + ((err && err.stack) ? err.stack : 'No stack trace available'));
    return {
      ok:false,
      code:'STATEMENTS_FAILED',
      message:'Penyata gagal dijana: ' + detail,
      diagnostic:'Semak Apps Script > Executions untuk stack trace penuh.'
    };
  }
}
function bkMasterOutput_(obj, callback) {
  const json=JSON.stringify(obj);
  if(callback && /^[A-Za-z_$][0-9A-Za-z_$\.]{0,100}$/.test(callback)) return ContentService.createTextOutput(callback+'('+json+');').setMimeType(ContentService.MimeType.JAVASCRIPT);
  return bkMasterJson_(obj);
}

// A signed session avoids rereading the Master Licences tab for every sync/load request.
// Tokens expire at 00:00 on the first day of the next calendar month in Malaysia time.
function bkMasterGetSigningSecret_() {
  const props = PropertiesService.getScriptProperties();
  let secret = props.getProperty(BK_TOKEN_PROPERTY);
  if (!secret) {
    secret = Utilities.getUuid() + Utilities.getUuid() + Utilities.getUuid();
    props.setProperty(BK_TOKEN_PROPERTY, secret);
  }
  return secret;
}
// ---- At-rest encryption of customer sheet data (HMAC-SHA256 counter-mode stream cipher + encrypt-then-MAC tag) ----
// The master key lives only in Script Properties; each customer's keys are derived from it plus their Business ID.
const BK_ENC_KEY_PROPERTY = 'BUKUKIRA_DATA_ENCRYPTION_KEY';
const BK_ENC_PREFIX = 'enc2.';
const BK_ENC_PREFIX_LEGACY = 'enc1.';
const BK_ENC_KEY_CACHE = {};
function bkEncMasterKey_() {
  const props = PropertiesService.getScriptProperties();
  let k = props.getProperty(BK_ENC_KEY_PROPERTY);
  if (!k) { k = Utilities.getUuid() + Utilities.getUuid() + Utilities.getUuid() + Utilities.getUuid(); props.setProperty(BK_ENC_KEY_PROPERTY, k); }
  return k;
}
function bkEncHmac_(data, key) { return Utilities.computeHmacSha256Signature(data, key).map(b => b & 255); }
function bkEncKeys_(scope) {
  // scope = Business ID; empty scope is the legacy shared key used only to read old enc1 data.
  const cacheKey = String(scope || '');
  if (BK_ENC_KEY_CACHE[cacheKey]) return BK_ENC_KEY_CACHE[cacheKey];
  let base = Utilities.newBlob(bkEncMasterKey_()).getBytes();
  if (cacheKey) base = bkEncHmac_(Utilities.newBlob('bukukira-customer:' + cacheKey).getBytes(), base);
  return BK_ENC_KEY_CACHE[cacheKey] = { enc: bkEncHmac_(Utilities.newBlob('bukukira-enc').getBytes(), base), mac: bkEncHmac_(Utilities.newBlob('bukukira-mac').getBytes(), base) };
}
function bkEncStream_(nonce, length, encKey) {
  const out = [];
  for (let counter = 0; out.length < length; counter++) {
    out.push.apply(out, bkEncHmac_(nonce.concat([(counter >>> 24) & 255, (counter >>> 16) & 255, (counter >>> 8) & 255, counter & 255]), encKey));
  }
  return out.slice(0, length);
}
function bkEncrypt_(plaintext, scope) {
  if (!scope) throw new Error('Business ID is required to encrypt data.');
  const keys = bkEncKeys_(scope);
  const data = Utilities.newBlob(String(plaintext)).getBytes().map(b => b & 255);
  const nonce = bkEncHmac_(Utilities.newBlob(Utilities.getUuid() + ':' + Date.now()).getBytes(), keys.enc).slice(0, 16);
  const stream = bkEncStream_(nonce, data.length, keys.enc);
  const cipher = data.map((b, i) => b ^ stream[i]);
  const tag = bkEncHmac_(nonce.concat(cipher), keys.mac).slice(0, 16);
  return BK_ENC_PREFIX + bkMasterBase64Url_(nonce.concat(cipher, tag).map(b => b > 127 ? b - 256 : b));
}
function bkIsEncrypted_(value) { return typeof value === 'string' && (value.indexOf(BK_ENC_PREFIX) === 0 || value.indexOf(BK_ENC_PREFIX_LEGACY) === 0); }
function bkDecrypt_(value, scope) {
  value = String(value);
  const legacy = value.indexOf(BK_ENC_PREFIX_LEGACY) === 0;
  if (!legacy && !scope) throw new Error('Business ID is required to decrypt data.');
  const keys = bkEncKeys_(legacy ? '' : scope);
  const all = Utilities.base64DecodeWebSafe(value.slice(BK_ENC_PREFIX.length)).map(b => b & 255);
  if (all.length < 32) throw new Error('Encrypted value is too short.');
  const nonce = all.slice(0, 16), cipher = all.slice(16, all.length - 16), tag = all.slice(all.length - 16);
  const expected = bkEncHmac_(nonce.concat(cipher), keys.mac).slice(0, 16);
  let diff = 0; for (let i = 0; i < 16; i++) diff |= expected[i] ^ tag[i];
  if (diff !== 0) throw new Error('Encrypted data failed integrity check.');
  const stream = bkEncStream_(nonce, cipher.length, keys.enc);
  return Utilities.newBlob(cipher.map((b, i) => { const x = b ^ stream[i]; return x > 127 ? x - 256 : x; })).getDataAsString();
}
// Sheet row layout is unchanged: column A holds the encrypted payload, column H keeps the plain record ID for upserts.
function bkTxRowEncode_(t, scope) {
  const payload = JSON.stringify({date:String(t.date||''), type:String(t.type||''), categoryLabel:String(t.categoryLabel||''), method:String(t.method||''), amount:Number(t.amount)||0, note:String(t.note||''), categoryId:String(t.categoryId||'')});
  return [bkEncrypt_(payload, scope), '', '', '', '', '', '', String(t.id)];
}
// Reads both encrypted rows and legacy plaintext rows (legacy rows are encrypted on the next save).
function bkTxRowDecode_(r, scope) {
  const id = String(r[7] || '').trim();
  if (!id) return null;
  if (bkIsEncrypted_(r[0])) { const p = JSON.parse(bkDecrypt_(r[0], scope)); p.id = id; return p; }
  const date = r[0] instanceof Date ? Utilities.formatDate(r[0], Session.getScriptTimeZone(), 'yyyy-MM-dd') : String(r[0] || '').slice(0,10);
  if (!date) return null;
  return {date:date, type:String(r[1]||''), categoryLabel:String(r[2]||''), method:String(r[3]||''), amount:Number(r[4])||0, note:String(r[5]||''), categoryId:String(r[6]||''), id:id};
}
function bkSettingDecode_(v, scope) { return bkIsEncrypted_(v) ? bkDecrypt_(v, scope) : v; }

function bkMasterBase64Url_(bytesOrText) {
  const bytes = typeof bytesOrText === 'string' ? Utilities.newBlob(bytesOrText).getBytes() : bytesOrText;
  return Utilities.base64EncodeWebSafe(bytes).replace(/=+$/g, '');
}
function bkMasterMonthlyExpiry_() {
  const now = new Date();
  const year = Number(Utilities.formatDate(now, BK_SESSION_TIMEZONE, 'yyyy'));
  const month = Number(Utilities.formatDate(now, BK_SESSION_TIMEZONE, 'M'));
  // Kuala Lumpur is UTC+8; the next local month starts at 16:00 UTC on the previous day.
  const boundaryUtc = Date.UTC(year, month, 1, 0, 0, 0) - 8 * 60 * 60 * 1000;
  return new Date(boundaryUtc);
}
function bkMasterRegisterDevice_(master, businessId, installationId, maxDevices, platform) {
  if (!Number.isInteger(maxDevices) || maxDevices < 1) {
    return {ok:false, code:'MAX_DEVICES_INVALID', message:'The device limit is not configured. Please contact the administrator.'};
  }
  const sheet = bkMasterEnsureTab_(master, 'Devices', BK_DEVICE_HEADERS);
  const lastRow = sheet.getLastRow();
  const rows = lastRow > 1 ? sheet.getRange(2, 1, lastRow - 1, BK_DEVICE_HEADERS.length).getValues() : [];
  const devices = new Set();
  let existingRow = 0;
  rows.forEach((row, index) => {
    if (String(row[0]).trim() !== businessId) return;
    const id = String(row[1] || '').trim();
    if (!id) return;
    devices.add(id);
    if (id === installationId) existingRow = index + 2;
  });
  const now = new Date();
  if (existingRow) {
    sheet.getRange(existingRow, 4).setValue(now);
    sheet.getRange(existingRow, 5).setValue(platform);
  } else {
    if (devices.size >= maxDevices) {
      return {ok:false, code:'MAX_DEVICES_REACHED', message:'The maximum number of devices has been reached. Please contact the administrator.'};
    }
    sheet.getRange(lastRow + 1, 1, 1, BK_DEVICE_HEADERS.length).setValues([[businessId, installationId, now, now, platform]]);
  }
  return {ok:true};
}
function bkMasterIssueSession_(businessId, sheetId, installationId) {
  const expiresAt = bkMasterMonthlyExpiry_();
  const payload = {v:2, businessId:String(businessId), sheetId:String(sheetId), installationId:String(installationId), exp:expiresAt.getTime()};
  const encoded = bkMasterBase64Url_(JSON.stringify(payload));
  const sig = Utilities.computeHmacSha256Signature(encoded, bkMasterGetSigningSecret_());
  return {token:encoded + '.' + bkMasterBase64Url_(sig), expiresAt:expiresAt.toISOString()};
}
function bkMasterVerifySession_(token) {
  token = String(token || '').trim();
  const parts = token.split('.');
  if (parts.length !== 2 || !parts[0] || !parts[1]) return {ok:false, code:'SESSION_REQUIRED', message:'Sesi lesen tiada. Sila sambung ke internet dan sahkan lesen semula.'};
  try {
    const expected = bkMasterBase64Url_(Utilities.computeHmacSha256Signature(parts[0], bkMasterGetSigningSecret_()));
    if (expected !== parts[1]) return {ok:false, code:'SESSION_INVALID', message:'Sesi lesen tidak sah. Sila sahkan lesen semula.'};
    const payloadText = Utilities.newBlob(Utilities.base64DecodeWebSafe(parts[0])).getDataAsString();
    const payload = JSON.parse(payloadText);
    if (payload.v !== 2 || !payload.businessId || !payload.sheetId || !/^[a-f0-9]{32}$/.test(String(payload.installationId || '')) || !Number(payload.exp)) throw new Error('Invalid claims');
    if (Date.now() >= Number(payload.exp)) return {ok:false, code:'SESSION_EXPIRED', message:'Pengesahan bulanan diperlukan. Sambung ke internet dan sahkan lesen semula.'};
    if (!/^[a-zA-Z0-9-_]{20,}$/.test(String(payload.sheetId))) throw new Error('Invalid Sheet ID');
    return {ok:true, businessId:String(payload.businessId), customerSheetId:String(payload.sheetId), sessionExpiresAt:new Date(Number(payload.exp)).toISOString()};
  } catch (err) {
    return {ok:false, code:'SESSION_INVALID', message:'Sesi lesen tidak sah. Sila sahkan lesen semula.'};
  }
}

function bkMasterValidate_(key, businessId, installationId, platform) {
  key = String(key || '').trim();
  businessId = String(businessId || '').trim();
  installationId = String(installationId || '').trim().toLowerCase();
  platform = platform === 'APK' ? 'APK' : 'Browser';
  if (!key || !businessId) return {ok:false, code:'LICENCE_REQUIRED', message:'Enter your Business ID and licence key.'};
  if (!/^[a-f0-9]{32}$/.test(installationId)) return {ok:false, code:'INSTALLATION_ID_REQUIRED', message:'Update the app and validate the licence again.'};

  const master = bkMasterSpreadsheet_();
  const sh = master.getSheetByName('Licences');
  if (!sh || sh.getLastRow() < 2) return {ok:false, code:'LICENCE_INVALID', message:'Licence not recognised.'};

  // Ensure the administrator-assigned binding column exists on older Master Sheets.
  let headers = sh.getRange(1,1,1,Math.max(sh.getLastColumn(),1)).getDisplayValues()[0].map(x=>String(x||'').trim());
  let bindingIndex = headers.indexOf('BoundSheetID');
  if (bindingIndex < 0) {
    bindingIndex = headers.length;
    sh.getRange(1,bindingIndex+1).setValue('BoundSheetID').setFontWeight('bold').setWrap(true);
    headers.push('BoundSheetID');
  }
  const col = {}; headers.forEach((h,i)=>{ if(h) col[h]=i; });
  const required = ['LicenceKey','BusinessID','BusinessName','OwnerName','Status','Plan','StartDate','ExpiryDate','MaxDevices','BoundSheetID'];
  if (required.some(h=>col[h] === undefined)) return {ok:false,code:'MASTER_HEADERS_INVALID',message:'The Licences tab is missing one or more required column headers.'};

  const rows = sh.getRange(2,1,sh.getLastRow()-1,headers.length).getValues();
  let found = null;
  for (let i=0;i<rows.length;i++) {
    if (bkMasterSafeEqual_(rows[i][col.LicenceKey], key)) { found = rows[i]; break; }
  }
  const get = name => found && found[col[name]] !== undefined ? found[col[name]] : '';
  if (!found || String(get('BusinessID')).trim() !== businessId) {
    return {ok:false, code:'LICENCE_INVALID', message:'Licence not recognised for this business.'};
  }
  const status = String(get('Status') || '').trim().toUpperCase();
  const now = new Date();
  const start = bkMasterParseDate_(get('StartDate'));
  const expiry = bkMasterParseDate_(get('ExpiryDate'));
  if (status !== 'ACTIVE') return {ok:false, code:'LICENCE_INACTIVE', message:'This licence is inactive.'};
  if (start && now < start) return {ok:false, code:'LICENCE_NOT_STARTED', message:'This licence is not active yet.'};
  if (expiry && now > expiry) return {ok:false, code:'LICENCE_EXPIRED', message:'This licence has expired.'};

  // The customer Sheet ID is assigned by the administrator in the private Master Sheet.
  const sheetId = String(get('BoundSheetID') || '').trim();
  if (!sheetId) return {ok:false, code:'SHEET_NOT_ASSIGNED', message:'Your business spreadsheet has not been assigned yet. Please contact BukuKira administrator.'};
  if (!/^[a-zA-Z0-9-_]{20,}$/.test(sheetId)) return {ok:false, code:'MASTER_SHEET_ID_INVALID', message:'The assigned Google Sheet ID in the Master Sheet is invalid. Please contact BukuKira administrator.'};

  // Confirm the pre-assigned spreadsheet is accessible to the backend account.
  let customerSs;
  try { customerSs = SpreadsheetApp.openById(sheetId); }
  catch (err) {
    const adminEmail = PropertiesService.getScriptProperties().getProperty('BUKUKIRA_ADMIN_EMAIL') || '';
    const msg = adminEmail
      ? 'The assigned spreadsheet is not accessible. Share it with ' + adminEmail + ' as Editor.'
      : 'The assigned spreadsheet is not accessible. Share it with the Google account that owns/runs the BukuKira backend as Editor.';
    return {ok:false, code:'SHEET_ACCESS_REQUIRED', message:msg};
  }

  // Recheck the assignment under a lock before creating tabs and issuing a token.
  const lock = LockService.getScriptLock();
  if (!lock.tryLock(8000)) return {ok:false, code:'BUSY_RETRY', message:'The licence service is busy. Please retry shortly.'};
  try {
    const freshHeaders = sh.getRange(1,1,1,sh.getLastColumn()).getDisplayValues()[0].map(x=>String(x||'').trim());
    const freshCol = {}; freshHeaders.forEach((h,i)=>{ if(h) freshCol[h]=i; });
    const lastRow = sh.getLastRow();
    const currentKeyRows = lastRow >= 2 ? sh.getRange(2,1,lastRow-1,sh.getLastColumn()).getValues() : [];
    let currentRow = -1;
    for (let i=0;i<currentKeyRows.length;i++) {
      if (bkMasterSafeEqual_(currentKeyRows[i][freshCol.LicenceKey], key)) { currentRow=i+2; break; }
    }
    if (currentRow < 0) return {ok:false, code:'LICENCE_INVALID', message:'Licence not recognised.'};
    const currentBusiness = String(sh.getRange(currentRow,freshCol.BusinessID+1).getDisplayValue() || '').trim();
    const currentSheetId = String(sh.getRange(currentRow,freshCol.BoundSheetID+1).getDisplayValue() || '').trim();
    if (currentBusiness !== businessId) return {ok:false, code:'LICENCE_INVALID', message:'Licence not recognised for this business.'};
    if (!currentSheetId || currentSheetId !== sheetId) return {ok:false, code:'SHEET_ASSIGNMENT_CHANGED', message:'The assigned business spreadsheet has changed. Please validate your licence again.'};
    const currentLicence = currentKeyRows[currentRow - 2];
    const currentStatus = String(currentLicence[freshCol.Status] || '').trim().toUpperCase();
    const currentStart = bkMasterParseDate_(currentLicence[freshCol.StartDate]);
    const currentExpiry = bkMasterParseDate_(currentLicence[freshCol.ExpiryDate]);
    const currentTime = new Date();
    if (currentStatus !== 'ACTIVE') return {ok:false, code:'LICENCE_INACTIVE', message:'This licence is inactive.'};
    if (currentStart && currentTime < currentStart) return {ok:false, code:'LICENCE_NOT_STARTED', message:'This licence is not active yet.'};
    if (currentExpiry && currentTime > currentExpiry) return {ok:false, code:'LICENCE_EXPIRED', message:'This licence has expired.'};
    const rawLimit = currentLicence[freshCol.MaxDevices];
    const maxDevices = rawLimit === '' || rawLimit == null ? 0 : Number(rawLimit);
    if (!Number.isInteger(maxDevices) || maxDevices < 1) return {ok:false, code:'MAX_DEVICES_INVALID', message:'The device limit is not configured. Please contact the administrator.'};
    try {
      bkMasterEnsureTab_(customerSs, 'Transactions', BK_CUSTOMER_TX_HEADERS);
      bkMasterEnsureTab_(customerSs, 'Settings', BK_CUSTOMER_SETTINGS_HEADERS);
    } catch (err) {
      return {ok:false, code:'SHEET_SETUP_FAILED', message:'BukuKira could not set up the assigned spreadsheet. Check that the backend account has Editor access.'};
    }
    const registration = bkMasterRegisterDevice_(master, businessId, installationId, maxDevices, platform);
    if (!registration.ok) return registration;
    SpreadsheetApp.flush();
  } finally {
    lock.releaseLock();
  }

  const plan = String(get('Plan') || '');
  const businessName = String(get('BusinessName') || '');
  const ownerName = String(get('OwnerName') || '');
  const expiryText = get('ExpiryDate') instanceof Date ? Utilities.formatDate(get('ExpiryDate'), Session.getScriptTimeZone(), 'yyyy-MM-dd') : String(get('ExpiryDate') || '');
  const session = bkMasterIssueSession_(businessId, sheetId, installationId);
  return {ok:true, businessId:businessId, businessName:businessName, ownerName:ownerName, plan:plan, customerSheetId:sheetId, expires:expiryText, sheetReady:true, sheetBound:true, sessionToken:session.token, sessionExpiresAt:session.expiresAt, deviceRegistrationVersion:1};
}

function bkMasterLoadCustomerData_(auth) {
  try {
    const ss = SpreadsheetApp.openById(auth.customerSheetId);
    const tx = ss.getSheetByName('Transactions');
    const settings = ss.getSheetByName('Settings');
    const transactions = [];
    if (tx && tx.getLastRow() > 1) {
      const values = tx.getRange(2, 1, tx.getLastRow()-1, BK_CUSTOMER_TX_HEADERS.length).getValues();
      values.forEach(r => {
        const d = bkTxRowDecode_(r, auth.businessId);
        if (d) transactions.push({date:d.date,type:d.type,categoryLabel:d.categoryLabel,method:d.method,amount:d.amount,note:d.note,categoryId:d.categoryId,id:d.id,syncStatus:'synced'});
      });
    }
    const config = {};
    if (settings && settings.getLastRow() > 1) settings.getRange(2,1,settings.getLastRow()-1,2).getValues().forEach(r => config[String(r[0])] = bkSettingDecode_(r[1], auth.businessId));
    return {ok:true, transactions:transactions, settings:config, businessId:auth.businessId};
  } catch (err) {
    return {ok:false, code:'CUSTOMER_SHEET_ERROR', message:'Unable to access this business spreadsheet.'};
  }
}

function bkMasterSaveCustomerData_(auth, body, metrics) {
  // Incremental, idempotent upsert. Existing encrypted transaction payloads are not
  // decrypted or rewritten unless that transaction is actually updated.
  const incoming = Array.isArray(body.transactions) ? body.transactions : [];
  const deleteIds = Array.isArray(body.deleteIds) ? body.deleteIds.map(String).filter(Boolean) : [];
  metrics = metrics || {};
  metrics.batch_size = incoming.length;
  metrics.insert_count = 0;
  metrics.update_count = 0;
  metrics.delete_count = 0;
  metrics.busy_retry = 0;
  metrics.success = false;
  const BK_SAVE_BATCH_LIMIT = 100;
  if (incoming.length > BK_SAVE_BATCH_LIMIT || deleteIds.length > BK_SAVE_BATCH_LIMIT) {
    metrics.error_category = 'BATCH_TOO_LARGE';
    return {ok:false, code:'BATCH_TOO_LARGE', message:'Save up to 100 changed records per request.'};
  }

  // Open the assigned spreadsheet and resolve existing tabs before taking the global
  // lock. Licence validation creates these tabs, so routine syncs should not perform
  // sheet creation while holding the lock.
  let ss, tx, settings;
  try {
    ss = SpreadsheetApp.openById(auth.customerSheetId);
    tx = ss.getSheetByName('Transactions');
    settings = ss.getSheetByName('Settings');
    if (!tx || !settings) {
      metrics.error_category = 'SHEET_SETUP_REQUIRED';
      return {ok:false, code:'SHEET_SETUP_REQUIRED', message:'Please validate the BukuKira licence again to prepare the business spreadsheet.'};
    }
  } catch (openError) {
    metrics.error_category = 'SPREADSHEET_OPEN';
    console.error('BukuKira customer sheet open failed:', metrics.error_category);
    return {ok:false, code:'CUSTOMER_SHEET_ERROR', message:'Unable to access this business spreadsheet.'};
  }

  // Lock remains global for correctness: Apps Script has no keyed per-business lock.
  // The critical section is shorter because spreadsheet opening/tab setup is outside it.
  const lock = LockService.getScriptLock();
  const lockWaitStartedAt = Date.now();
  if (!lock.tryLock(8000)) {
    metrics.lock_wait_ms = Date.now() - lockWaitStartedAt;
    metrics.busy_retry = 1;
    metrics.error_category = 'BUSY_RETRY';
    console.warn(JSON.stringify({event:'BUKUKIRA_LOCK_CONTENTION', waitMs:metrics.lock_wait_ms, batchSize:incoming.length, busy_retry:1}));
    return {ok:false, code:'BUSY_RETRY', message:'The sync service is busy. Retry shortly.'};
  }
  const lockWaitMs = Date.now() - lockWaitStartedAt;
  metrics.lock_wait_ms = lockWaitMs;
  if (lockWaitMs >= 1000) console.warn(JSON.stringify({event:'BUKUKIRA_LOCK_WAIT', waitMs:lockWaitMs, incomingCount:incoming.length, deleteCount:deleteIds.length}));
  const lockHeldStartedAt = Date.now();
  let phase = 'INDEX_READ';
  let phaseStartedAt = Date.now();
  try {
    const last = tx.getLastRow();

    // Column H intentionally stores the transaction ID in plaintext for upserts;
    // scan only this narrow index column, not all encrypted transaction payloads.
    const rowById = new Map();
    if (last > 1) {
      const ids = tx.getRange(2, 8, last - 1, 1).getDisplayValues();
      ids.forEach((r, i) => { const id = String(r[0] || '').trim(); if (id) rowById.set(id, i + 2); });
    }
    metrics.index_read_ms = Date.now() - phaseStartedAt;

    const upserts = [];
    const incomingIds = new Set();
    phase = 'ENCRYPTION';
    phaseStartedAt = Date.now();
    incoming.forEach(t => {
      if (!t || !String(t.id || '').trim()) throw new Error('Transaction ID missing.');
      const amount = Number(t.amount);
      if (!Number.isFinite(amount) || amount < 0) throw new Error('Invalid transaction amount.');
      const id = String(t.id).trim();
      incomingIds.add(id);
      const record = {date:String(t.date || ''), type:String(t.type || ''), categoryLabel:String(t.categoryLabel || t.categoryId || ''), method:String(t.method || ''), amount:amount, note:String(t.note || ''), categoryId:String(t.categoryId || ''), id:id};
      upserts.push({row:rowById.get(id) || null, values:bkTxRowEncode_(record, auth.businessId)});
    });
    metrics.encryption_ms = Date.now() - phaseStartedAt;

    // Write existing records in contiguous row groups to reduce Spreadsheet calls.
    const updates = upserts.filter(x => x.row !== null).sort((a,b) => a.row - b.row);
    const inserts = upserts.filter(x => x.row === null).map(x => x.values);
    metrics.update_count = updates.length;
    metrics.insert_count = inserts.length;
    phase = 'WRITE';
    phaseStartedAt = Date.now();
    for (let i = 0; i < updates.length;) {
      let j = i + 1;
      while (j < updates.length && updates[j].row === updates[j-1].row + 1) j++;
      tx.getRange(updates[i].row, 1, j - i, BK_CUSTOMER_TX_HEADERS.length)
        .setValues(updates.slice(i, j).map(x => x.values));
      i = j;
    }

    if (inserts.length) {
      const appendAt = Math.max(2, tx.getLastRow() + 1);
      tx.getRange(appendAt, 1, inserts.length, BK_CUSTOMER_TX_HEADERS.length).setValues(inserts);
    }
    metrics.write_ms = Date.now() - phaseStartedAt;

    // Delete from the bottom upward so earlier row numbers remain valid.
    // If an ID is both deleted and upserted in one request, the upsert wins,
    // matching the previous delete-then-upsert behaviour.
    phase = 'DELETE';
    phaseStartedAt = Date.now();
    const rowsToDelete = [...new Set(deleteIds.filter(id => !incomingIds.has(id)).map(id => rowById.get(id)).filter(Boolean))].sort((a,b) => b-a);
    metrics.delete_count = rowsToDelete.length;
    // Delete contiguous row ranges in one Spreadsheet call per range instead of
    // issuing one deleteRow() call for every tombstone. Ranges are processed
    // bottom-up so deleting lower rows never shifts a range still to be removed.
    for (let i = 0; i < rowsToDelete.length;) {
      let rangeStart = rowsToDelete[i];
      let rangeEnd = rangeStart;
      let j = i + 1;
      while (j < rowsToDelete.length && rowsToDelete[j] === rangeEnd - 1) {
        rangeEnd = rowsToDelete[j];
        j++;
      }
      tx.deleteRows(rangeEnd, rangeStart - rangeEnd + 1);
      i = j;
    }
    metrics.delete_ms = Date.now() - phaseStartedAt;

    phase = 'SETTINGS';
    phaseStartedAt = Date.now();
    const cfg = body.settings && typeof body.settings === 'object' ? body.settings : {};
    if (Object.keys(cfg).length) {
      const existing = {};
      if (settings.getLastRow() > 1) settings.getRange(2, 1, settings.getLastRow() - 1, 2).getValues().forEach(r => existing[String(r[0])] = bkSettingDecode_(r[1], auth.businessId));
      Object.keys(cfg).forEach(k => {
        if (['businessName','ownerName','businessRegistrationNo','businessType','businessAddress','ownerIdNumber','ownerPhone','ownerEmail','financialYear','currency','beginningBalances','closingStockByYear'].includes(k)) existing[k] = JSON.stringify(cfg[k]);
      });
      const settingRows = Object.keys(existing).map(k => [k, bkEncrypt_(String(existing[k]), auth.businessId)]);
      if (settings.getLastRow() > 1) settings.getRange(2, 1, settings.getLastRow() - 1, 2).clearContent();
      if (settingRows.length) settings.getRange(2, 1, settingRows.length, 2).setValues(settingRows);
    }
    metrics.settings_ms = Date.now() - phaseStartedAt;
    metrics.success = true;
    metrics.error_category = '';
    return {ok:true, savedCount:incoming.length, insertedCount:inserts.length, updatedCount:updates.length, deletedCount:rowsToDelete.length, totalCount:tx.getLastRow() - 1};
  } catch (err) {
    metrics.success = false;
    const failedPhaseMs = Date.now() - phaseStartedAt;
    if (phase === 'INDEX_READ') metrics.index_read_ms = failedPhaseMs;
    else if (phase === 'ENCRYPTION') metrics.encryption_ms = failedPhaseMs;
    else if (phase === 'WRITE') metrics.write_ms = failedPhaseMs;
    else if (phase === 'DELETE') metrics.delete_ms = failedPhaseMs;
    else if (phase === 'SETTINGS') metrics.settings_ms = failedPhaseMs;
    metrics.error_category = bkMasterSaveErrorCategory_(phase, err);
    console.error('BukuKira incremental save failed:', metrics.error_category);
    return {ok:false, code:'SAVE_FAILED', message:'Unable to save business records.'};
  } finally {
    metrics.lock_held_ms = Date.now() - lockHeldStartedAt;
    lock.releaseLock();
  }
}

function bkMasterSpreadsheet_() {
  const id = PropertiesService.getScriptProperties().getProperty(BK_MASTER_PROPERTY);
  if (!id) throw new Error('Run setupBukuKiraMaster() from the Master Sheet first.');
  return SpreadsheetApp.openById(id);
}
function bkMasterEnsureTab_(ss, name, headers) {
  let sh = ss.getSheetByName(name) || ss.insertSheet(name);
  if (sh.getLastRow() === 0) sh.getRange(1,1,1,headers.length).setValues([headers]);
  sh.setFrozenRows(1); sh.getRange(1,1,1,headers.length).setFontWeight('bold').setWrap(true);
  return sh;
}
function bkMasterSafeEqual_(a,b) { a=String(a||''); b=String(b||''); if(a.length!==b.length)return false; let diff=0; for(let i=0;i<a.length;i++)diff|=a.charCodeAt(i)^b.charCodeAt(i); return diff===0; }
function bkMasterParseDate_(value) { if(!value)return null; const d=new Date(value); return isNaN(d.getTime())?null:d; }
function bkMasterJson_(obj) { return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON); }
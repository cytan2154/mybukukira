export const OFFICIAL_EXPENSE_LABELS_MS = [
  'Faedah pinjaman',
  'Gaji dan upah',
  'Sewa/pajakan',
  'Kontrak dan subkontrak',
  'Komisen',
  'Hutang lapuk',
  'Perjalanan dan pengangkutan',
  'Pembaikan dan penyelenggaraan',
  'Promosi dan iklan',
  'Perbelanjaan-perbelanjaan lain'
];

export const IN_CATEGORIES = [
  {
    id: 'sales_goods',
    labelMs: 'Jualan Tunai',
    subMs: 'Jual makanan, barang atau produk',
    labelEn: 'Cash Sales',
    subEn: 'Food, goods, or product sales',
    labelZh: '现金销售',
    subZh: '销售食品、商品或产品',
    accountKeyMs: 'Jualan Tunai',
    classification: 'PNL_REVENUE',
    direction: 'IN'
  },
  {
    id: 'other_income_dividend',
    labelMs: 'Dividen',
    subMs: 'Pendapatan dividen diterima',
    labelEn: 'Dividend',
    subEn: 'Dividend income received',
    labelZh: '股息',
    subZh: '收到的股息收入',
    accountKeyMs: 'Dividen',
    classification: 'PNL_OTHER_INCOME',
    direction: 'IN',
    groupKey: 'other_income'
  },
  {
    id: 'other_income_interest',
    labelMs: 'Faedah dan diskaun',
    subMs: 'Faedah simpanan atau diskaun diterima',
    labelEn: 'Interest and discounts',
    subEn: 'Savings interest or discounts received',
    labelZh: '利息和折扣',
    subZh: '储蓄利息或收到的折扣',
    accountKeyMs: 'Faedah dan diskaun',
    classification: 'PNL_OTHER_INCOME',
    direction: 'IN'
  },
  {
    id: 'other_income_rent_royalty',
    labelMs: 'Sewa, royalti, dan premium',
    subMs: 'Pendapatan sewa atau royalti diterima',
    labelEn: 'Rent, royalty, and premium',
    subEn: 'Rental or royalty income received',
    labelZh: '租金、特许权使用费及溢价',
    subZh: '收到的租金或特许权使用费',
    accountKeyMs: 'Sewa, royalti, dan premium',
    classification: 'PNL_OTHER_INCOME',
    direction: 'IN'
  },
  {
    id: 'other_income_general',
    labelMs: 'Pendapatan lain',
    subMs: 'Pendapatan lain perniagaan',
    labelEn: 'Other income',
    subEn: 'Other business income',
    labelZh: '其他收入',
    subZh: '其他营业收入',
    accountKeyMs: 'Pendapatan lain',
    classification: 'PNL_OTHER_INCOME',
    direction: 'IN'
  },
  {
    id: 'asset_disposal',
    labelMs: 'Penerimaan Pelupusan Aset',
    subMs: 'Rekod hasil jualan & kos asal aset',
    labelEn: 'Asset Disposal Proceeds',
    subEn: 'Record sale proceeds & original cost',
    labelZh: '资产处置收入',
    subZh: '记录出售收入及原成本以从资产负债表移除资产',
    accountKeyMs: 'Pelupusan Aset',
    classification: 'BS_ASSET_DISPOSAL',
    direction: 'IN',
    groupKey: 'special'
  },
  {
    id: 'purchase_return_refund',
    labelMs: 'Pulangan Belian / Bayaran Balik',
    subMs: 'Wang diterima semula daripada pembekal',
    labelEn: 'Purchase Return / Supplier Refund',
    subEn: 'Cash/bank refunded by supplier',
    labelZh: '采购退货/供应商退款',
    subZh: '从供应商收到的退款',
    accountKeyMs: 'Pulangan Belian',
    classification: 'PNL_PURCHASE_RETURN',
    direction: 'IN'
  },
  {
    id: 'transfer_bank_to_cash',
    labelMs: 'Pindahan Bank ke Tunai',
    subMs: 'Pindahkan wang bank ke tunai; bukan hasil',
    labelEn: 'Bank to Cash Transfer',
    subEn: 'Move bank funds to cash; not revenue',
    labelZh: '银行转现金',
    subZh: '将银行资金转至现金；非收入',
    accountKeyMs: 'Pindahan Bank ke Tunai',
    classification: 'BS_TRANSFER',
    direction: 'IN'
  },
  {
    id: 'debtors_payment',
    labelMs: 'Bayaran Penghutang',
    subMs: 'Terima bayaran hutang daripada pelanggan',
    labelEn: 'Receivable Payment',
    subEn: 'Collect debt payment from customer',
    labelZh: '应收账款回款',
    subZh: '收到客户的欠款',
    accountKeyMs: 'Penghutang perdagangan',
    classification: 'BS_TRADE_DEBTORS_COLLECT',
    direction: 'IN'
  },
  {
    id: 'owner_capital',
    labelMs: 'Modal Duit Sendiri',
    subMs: 'Duit pemilik masuk dalam bisnes',
    labelEn: 'Owner Capital Injection',
    subEn: 'Owner funds put into business',
    labelZh: '业主注资',
    subZh: '业主投入企业的资金',
    accountKeyMs: 'Modal',
    classification: 'BS_EQUITY_ADD',
    direction: 'IN'
  },
  {
    id: 'drawing_return',
    labelMs: 'Pulangan Ambilan Pemilik',
    subMs: 'Duit peribadi dipulangkan ke bisnes',
    labelEn: 'Drawings Returned',
    subEn: 'Personal funds returned to business',
    labelZh: '业主退还提款',
    subZh: '退回企业的个人资金（减少提款）',
    accountKeyMs: 'Ambilan',
    classification: 'BS_EQUITY_DRAW_RETURN',
    direction: 'IN'
  },
  {
    id: 'loan_received',
    labelMs: 'Pinjaman Bisnes Masuk',
    subMs: 'Pinjam dari bank / agensi / keluarga',
    labelEn: 'Business Loan Received',
    subEn: 'Borrowed from bank / agency / family',
    labelZh: '收到企业贷款',
    subZh: '向银行/机构/亲友借款',
    accountKeyMs: 'Pinjaman dan Overdraft',
    classification: 'BS_LIABILITY_ADD',
    direction: 'IN'
  }
];

export const OUT_CATEGORIES = [
  {
    id: 'buy_stock',
    labelMs: 'Beli Stok / Bahan Mentah',
    subMs: 'Barang niaga & bahan untuk dijual',
    labelEn: 'Buy Stock / Raw Materials',
    subEn: 'Trading goods & materials for sale',
    labelZh: '采购库存/原材料',
    subZh: '用于销售的商品及原材料',
    accountKeyMs: 'Belian',
    classification: 'PNL_COGS',
    direction: 'OUT',
    groupKey: 'cogs'
  },
  {
    id: 'sales_return_refund',
    labelMs: 'Pulangan Jualan / Bayaran Balik',
    subMs: 'Bayaran balik kepada pelanggan; kurangkan jualan',
    labelEn: 'Sales Return / Customer Refund',
    subEn: 'Refund to customer; reduces revenue',
    labelZh: '销售退货/客户退款',
    subZh: '退还给客户；减少销售额',
    accountKeyMs: 'Pulangan Jualan',
    classification: 'PNL_SALES_RETURN',
    direction: 'OUT',
    groupKey: 'returns_transfers'
  },
  {
    id: 'transfer_cash_to_bank',
    labelMs: 'Pindahan Tunai ke Bank',
    subMs: 'Deposit tunai ke bank; bukan perbelanjaan',
    labelEn: 'Cash to Bank Transfer',
    subEn: 'Deposit cash to bank; not an expense',
    labelZh: '现金存银行',
    subZh: '将现金存入银行；非费用',
    accountKeyMs: 'Pindahan Tunai ke Bank',
    classification: 'BS_TRANSFER',
    direction: 'OUT'
  },
  {
    id: 'exp_loan_interest',
    labelMs: 'Faedah pinjaman',
    subMs: 'Caj faedah / bunga pinjaman bank',
    labelEn: 'Loan interest',
    subEn: 'Bank loan interest charges',
    labelZh: '贷款利息',
    subZh: '银行贷款利息费用',
    accountKeyMs: 'Faedah pinjaman',
    classification: 'PNL_EXPENSE',
    direction: 'OUT',
    groupKey: 'operating_expenses'
  },
  {
    id: 'staff_wages',
    labelMs: 'Gaji dan upah',
    subMs: 'Gaji pekerja, KWSP, PERKESO & elaun',
    labelEn: 'Salaries and wages',
    subEn: 'Staff pay, EPF, SOCSO & allowances',
    labelZh: '工资与薪金',
    subZh: '员工工资、公积金、社险及津贴',
    accountKeyMs: 'Gaji dan upah',
    classification: 'PNL_EXPENSE',
    direction: 'OUT'
  },
  {
    id: 'rent_utilities',
    labelMs: 'Sewa/pajakan',
    subMs: 'Sewa kedai, tapak niaga, khemah / mesin',
    labelEn: 'Rent / lease',
    subEn: 'Shop, stall, canopy, or equipment rent',
    labelZh: '租金 / 租赁',
    subZh: '店面、摊位、帐篷或机器租金',
    accountKeyMs: 'Sewa/pajakan',
    classification: 'PNL_EXPENSE',
    direction: 'OUT'
  },
  {
    id: 'exp_contract',
    labelMs: 'Kontrak dan subkontrak',
    subMs: 'Upah subkontraktor / kerja luar',
    labelEn: 'Contract and subcontract',
    subEn: 'Subcontractor / outsourced labour',
    labelZh: '合同和分包',
    subZh: '分包商/外包劳务费',
    accountKeyMs: 'Kontrak dan subkontrak',
    classification: 'PNL_EXPENSE',
    direction: 'OUT'
  },
  {
    id: 'exp_commission',
    labelMs: 'Komisen',
    subMs: 'Komisen ejen, dropship atau jurujual',
    labelEn: 'Commission',
    subEn: 'Agent, dropship, or sales commission',
    labelZh: '佣金',
    subZh: '代理、代发或销售佣金',
    accountKeyMs: 'Komisen',
    classification: 'PNL_EXPENSE',
    direction: 'OUT'
  },
  {
    id: 'exp_bad_debt',
    labelMs: 'Hutang lapuk',
    subMs: 'Hutang pelanggan yang tidak dapat dikutip',
    labelEn: 'Bad debts',
    subEn: 'Uncollectible customer receivables',
    labelZh: '坏账',
    subZh: '无法收回的客户欠款',
    accountKeyMs: 'Hutang lapuk',
    classification: 'PNL_EXPENSE',
    direction: 'OUT'
  },
  {
    id: 'transport_fuel',
    labelMs: 'Perjalanan dan pengangkutan',
    subMs: 'Minyak, tol, pos, penghantaran & tambang',
    labelEn: 'Travel and transportation',
    subEn: 'Fuel, tolls, postage, delivery & fares',
    labelZh: '差旅和交通费',
    subZh: '燃油、过路费、邮寄、运费及车资',
    accountKeyMs: 'Perjalanan dan pengangkutan',
    classification: 'PNL_EXPENSE',
    direction: 'OUT'
  },
  {
    id: 'exp_repair',
    labelMs: 'Pembaikan dan penyelenggaraan',
    subMs: 'Servis mesin, baiki kedai & kenderaan',
    labelEn: 'Repairs and maintenance',
    subEn: 'Machine service, shop & vehicle upkeep',
    labelZh: '维修和保养',
    subZh: '机器保养、店铺及车辆维修',
    accountKeyMs: 'Pembaikan dan penyelenggaraan',
    classification: 'PNL_EXPENSE',
    direction: 'OUT'
  },
  {
    id: 'exp_promo',
    labelMs: 'Promosi dan iklan',
    subMs: 'Iklan FB/TikTok, banner, bunting & diskaun',
    labelEn: 'Promotion and advertising',
    subEn: 'Social ads, banners, flyers & promos',
    labelZh: '推广和广告',
    subZh: '社交广告、横幅、传单及促销',
    accountKeyMs: 'Promosi dan iklan',
    classification: 'PNL_EXPENSE',
    direction: 'OUT'
  },
  {
    id: 'exp_others',
    labelMs: 'Perbelanjaan-perbelanjaan lain',
    subMs: 'Bil elektrik, air, internet, plastik & alat tulis',
    labelEn: 'Other expenses',
    subEn: 'Utilities, internet, packaging & stationery',
    labelZh: '其他费用',
    subZh: '水电费、网络、包装及文具',
    accountKeyMs: 'Perbelanjaan-perbelanjaan lain',
    classification: 'PNL_EXPENSE',
    direction: 'OUT'
  },
  {
    id: 'buy_land_building',
    labelMs: 'Beli Tanah / Bangunan',
    subMs: 'Tanah, kedai atau bangunan perniagaan',
    labelEn: 'Buy Land / Building',
    subEn: 'Business land, shop, or premises',
    labelZh: '购买土地/建筑物',
    subZh: '企业土地、店铺或建筑',
    accountKeyMs: 'Tanah dan bangunan',
    classification: 'BS_LAND_BUILDING_ADD',
    direction: 'OUT',
    groupKey: 'assets'
  },
  {
    id: 'buy_equipment',
    labelMs: 'Beli Loji / Mesin / Peralatan',
    subMs: 'Mesin pengeluaran & peralatan khusus industri',
    labelEn: 'Buy Plant / Machinery / Equipment',
    subEn: 'Production machinery & industrial equipment',
    labelZh: '购买厂房/机器/设备',
    subZh: '生产机器及工业专用设备',
    accountKeyMs: 'Loji dan jentera',
    classification: 'BS_EQUIPMENT_ADD',
    direction: 'OUT'
  },
  {
    id: 'buy_vehicle',
    labelMs: 'Beli Kenderaan Perniagaan',
    subMs: 'Kereta, van, lori atau motosikal bisnes',
    labelEn: 'Buy Business Vehicle',
    subEn: 'Car, van, lorry, or motorcycle for business',
    labelZh: '购买商业车辆',
    subZh: '用于业务的汽车、货车、罗里或摩托',
    accountKeyMs: 'Kenderaan',
    classification: 'BS_VEHICLES_ADD',
    direction: 'OUT'
  },
  {
    id: 'buy_other_noncurrent',
    labelMs: 'Beli Aset Bukan Semasa Lain',
    subMs: 'Aircond, peti ais, komputer, perabot kedai',
    labelEn: 'Buy Other Non-Current Asset',
    subEn: 'AC, fridge, computer, shop furniture',
    labelZh: '购买其他非流动资产',
    subZh: '空调、冰箱、电脑、店铺家具等',
    accountKeyMs: 'Aset bukan semasa lain',
    classification: 'BS_OTHER_NONCURRENT_ADD',
    direction: 'OUT'
  },
  {
    id: 'buy_investment',
    labelMs: 'Beli Pelaburan',
    subMs: 'Saham atau pelaburan jangka panjang',
    labelEn: 'Buy Investment',
    subEn: 'Shares or long-term business investments',
    labelZh: '购买投资',
    subZh: '股票或长期商业投资',
    accountKeyMs: 'Pelaburan',
    classification: 'BS_INVESTMENT_ADD',
    direction: 'OUT'
  },
  {
    id: 'advance_other_debtor',
    labelMs: 'Pendahuluan / Pinjaman Diberi',
    subMs: 'Wang didahulukan atau dipinjamkan',
    labelEn: 'Advance / Loan Given',
    subEn: 'Advances or loans given to third parties',
    labelZh: '预付款/借出款项',
    subZh: '向第三方预付或借出的资金',
    accountKeyMs: 'Penghutang lain',
    classification: 'BS_OTHER_DEBTORS_ADD',
    direction: 'OUT'
  },
  {
    id: 'prepayment_other_current',
    labelMs: 'Deposit / Bayaran Prabayar',
    subMs: 'Deposit sewa, insurans prabayar & aset semasa lain',
    labelEn: 'Deposit / Prepayments',
    subEn: 'Rental deposits, prepaid insurance & current assets',
    labelZh: '押金 / 预付款项',
    subZh: '租金押金、预付保险及其他流动资产',
    accountKeyMs: 'Deposit / Bayaran Prabayar',
    classification: 'BS_OTHER_CURRENT_ADD',
    direction: 'OUT'
  },
  {
    id: 'owner_drawing',
    labelMs: 'Ambil Duit Untuk Sendiri',
    subMs: 'Kegunaan peribadi / belanja keluarga',
    labelEn: 'Owner Drawings',
    subEn: 'Personal use / family expenses',
    labelZh: '业主提款',
    subZh: '个人使用或家庭开支',
    accountKeyMs: 'Ambilan',
    classification: 'BS_EQUITY_DRAW',
    direction: 'OUT',
    groupKey: 'equity_loans'
  },
  {
    id: 'loan_repay',
    labelMs: 'Bayar Pokok Pinjaman',
    subMs: 'Bayar balik hutang pokok pinjaman bisnes',
    labelEn: 'Repay Loan Principal',
    subEn: 'Repay business loan principal',
    labelZh: '偿还贷款本金',
    subZh: '偿还企业贷款本金',
    accountKeyMs: 'Pinjaman dan Overdraft',
    classification: 'BS_LIABILITY_PAY',
    direction: 'OUT'
  },
  {
    id: 'creditors_payment',
    labelMs: 'Bayar Pemiutang Dagangan',
    subMs: 'Bayaran kepada pembekal stok / bahan mentah',
    labelEn: 'Pay Trade Creditor',
    subEn: 'Payment to stock / raw material supplier',
    labelZh: '支付贸易应付款',
    subZh: '向库存/原材料供应商付款',
    accountKeyMs: 'Pemiutang dagangan',
    classification: 'BS_TRADE_CREDITORS_PAY',
    direction: 'OUT'
  },
  {
    id: 'other_creditors_payment',
    labelMs: 'Bayar Pemiutang Lain',
    subMs: 'Bayaran hutang beli aset (mesin, kenderaan, dll.)',
    labelEn: 'Pay Other Creditor',
    subEn: 'Payment for asset purchases on credit',
    labelZh: '支付其他应付款',
    subZh: '支付赊购资产（机器、车辆等）的款项',
    accountKeyMs: 'Pemiutang lain',
    classification: 'BS_OTHER_CREDITORS_PAY',
    direction: 'OUT'
  }
];

export const CREDIT_CATEGORIES = {
  credit_sale: {
    id: 'credit_sale',
    labelMs: 'Jualan Kredit',
    subMs: 'Jualan secara invois / hutang pelanggan',
    labelEn: 'Credit Sale',
    subEn: 'Invoiced sales on credit terms',
    labelZh: '赊销',
    subZh: '按信用条款开票销售',
    accountKeyMs: 'Jualan Kredit',
    classification: 'PNL_REVENUE',
    direction: 'CREDIT_SALE'
  },
  credit_purchase: {
    id: 'credit_purchase',
    labelMs: 'Belian Kredit (Stok)',
    subMs: 'Pembelian stok secara kredit daripada pembekal',
    labelEn: 'Credit Purchase (Stock)',
    subEn: 'Stock purchase on credit from supplier',
    labelZh: '赊购（库存）',
    subZh: '向供应商赊购库存',
    accountKeyMs: 'Belian',
    classification: 'PNL_COGS',
    direction: 'CREDIT_PURCHASE'
  },
  sales_service: {
    id: 'sales_service',
    labelMs: 'Upah Servis / Kerja (rekod lama)',
    subMs: 'Rekod hasil servis',
    labelEn: 'Service Revenue (legacy)',
    subEn: 'Service income',
    labelZh: '服务费（旧记录）',
    subZh: '服务收入',
    accountKeyMs: 'Upah Servis / Kerja (rekod lama)',
    classification: 'PNL_REVENUE',
    direction: 'IN'
  }
};

export const CREDIT_PURCHASE_OPTIONS = [
  {
    id: 'credit_purchase',
    labelMs: 'Stok / Bahan Mentah',
    effectMs: 'Belian dalam Penyata Pendapatan bertambah & Pemiutang Dagangan bertambah.',
    labelEn: 'Stock / Raw Materials',
    effectEn: 'Purchases in Income Statement increase & Trade Payables increase.',
    labelZh: '库存 / 原材料',
    effectZh: '损益表采购增加，贸易应付款增加。'
  },
  {
    id: 'buy_land_building',
    labelMs: 'Tanah / Bangunan',
    effectMs: 'Tanah / Bangunan dalam Kunci Kira-Kira bertambah & Pemiutang Lain bertambah.',
    labelEn: 'Land / Building',
    effectEn: 'Land & Building in Balance Sheet increase & Other Payables increase.',
    labelZh: '土地 / 建筑物',
    effectZh: '资产负债表土地及建筑物增加，其他应付款增加。'
  },
  {
    id: 'buy_equipment',
    labelMs: 'Loji / Mesin / Peralatan',
    effectMs: 'Loji, Mesin & Peralatan bertambah & Pemiutang Lain bertambah.',
    labelEn: 'Plant / Machinery / Equipment',
    effectEn: 'Plant & Machinery increase & Other Payables increase.',
    labelZh: '厂房 / 机器 / 设备',
    effectZh: '厂房及机器增加，其他应付款增加。'
  },
  {
    id: 'buy_vehicle',
    labelMs: 'Kenderaan Perniagaan',
    effectMs: 'Kenderaan dalam Kunci Kira-Kira bertambah & Pemiutang Lain bertambah.',
    labelEn: 'Business Vehicle',
    effectEn: 'Vehicles in Balance Sheet increase & Other Payables increase.',
    labelZh: '商业车辆',
    effectZh: '车辆增加，其他应付款增加。'
  },
  {
    id: 'buy_other_noncurrent',
    labelMs: 'Aset Bukan Semasa Lain',
    effectMs: 'Aset Bukan Semasa Lain bertambah & Pemiutang Lain bertambah.',
    labelEn: 'Other Non-Current Asset',
    effectEn: 'Other Non-Current Assets increase & Other Payables increase.',
    labelZh: '其他非流动资产',
    effectZh: '其他非流动资产增加，其他应付款增加。'
  }
];

const ALL_BY_ID = new Map(
  [...IN_CATEGORIES, ...OUT_CATEGORIES, ...Object.values(CREDIT_CATEGORIES)].map((c) => [c.id, c])
);

export function findCategoryById(categoryId) {
  return ALL_BY_ID.get(categoryId) || null;
}

export function accountMetaFor(categoryId) {
  const found = ALL_BY_ID.get(categoryId);
  if (found) {
    return [found.accountKeyMs, found.classification];
  }
  return ['Kategori lain', 'PNL_EXPENSE'];
}

export function localizedCategoryLabel(categoryId, lang) {
  const spec = ALL_BY_ID.get(categoryId);
  if (!spec) return categoryId;
  if (lang === 'en') return spec.labelEn;
  if (lang === 'zh') return spec.labelZh;
  return spec.labelMs;
}

export function localizedCategorySub(categoryId, lang) {
  const spec = ALL_BY_ID.get(categoryId);
  if (!spec) return '';
  if (lang === 'en') return spec.subEn;
  if (lang === 'zh') return spec.subZh;
  return spec.subMs;
}

export function sampleTransactions(businessId, ownerUid, memberUids) {
  return [
    { id: 'tx-1', businessId, createdByUid: ownerUid, memberUids, date: '2026-01-02', type: 'IN', categoryId: 'owner_capital', method: 'BANK', amount: 5000.0, note: 'Modal permulaan bank' },
    { id: 'tx-2', businessId, createdByUid: ownerUid, memberUids, date: '2026-01-03', type: 'IN', categoryId: 'owner_capital', method: 'CASH', amount: 1000.0, note: 'Tunai permulaan dalam laci' },
    { id: 'tx-3', businessId, createdByUid: ownerUid, memberUids, date: '2026-01-05', type: 'OUT', categoryId: 'buy_equipment', method: 'BANK', amount: 1800.0, note: 'Beli mesin kopi & pemanas' },
    { id: 'tx-4', businessId, createdByUid: ownerUid, memberUids, date: '2026-02-10', type: 'OUT', categoryId: 'buy_stock', method: 'CASH', amount: 2400.0, note: 'Beli biji kopi & roti' },
    { id: 'tx-5', businessId, createdByUid: ownerUid, memberUids, date: '2026-03-15', type: 'IN', categoryId: 'sales_goods', method: 'CASH', amount: 4850.0, note: 'Jualan tunai kedai' },
    { id: 'tx-6', businessId, createdByUid: ownerUid, memberUids, date: '2026-04-20', type: 'IN', categoryId: 'sales_goods', method: 'BANK', amount: 3600.0, note: 'Jualan DuitNow QR' },
    { id: 'tx-7', businessId, createdByUid: ownerUid, memberUids, date: '2026-06-01', type: 'OUT', categoryId: 'rent_utilities', method: 'BANK', amount: 1200.0, note: 'Sewa kedai' },
    { id: 'tx-8', businessId, createdByUid: ownerUid, memberUids, date: '2026-08-12', type: 'OUT', categoryId: 'staff_wages', method: 'CASH', amount: 900.0, note: 'Upah pekerja' },
    { id: 'tx-9', businessId, createdByUid: ownerUid, memberUids, date: '2026-08-25', type: 'OUT', categoryId: 'exp_others', method: 'BANK', amount: 300.0, note: 'Bil utiliti' },
    { id: 'tx-10', businessId, createdByUid: ownerUid, memberUids, date: '2026-09-18', type: 'OUT', categoryId: 'owner_drawing', method: 'CASH', amount: 500.0, note: 'Ambilan belanja rumah' }
  ];
}

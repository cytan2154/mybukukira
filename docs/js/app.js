import {
  CREDIT_PURCHASE_OPTIONS,
  IN_CATEGORIES,
  OUT_CATEGORIES,
  localizedCategoryLabel
} from './accounting-catalog.js';
import { formatCurrency, formatNumber, uiStrings } from './strings.js';
import { computeStatements, defaultBeginningBalances, getClosingStock } from './accounting-engine.js';
import { evaluateBusinessAccess, getOrCreateInstallationId } from './license-gate.js';
import {
  buildCsvReport,
  buildJsonBackup,
  downloadTextFile,
  escapeHtml,
  parseJsonBackup,
  printStatementsPdf
} from './export-utils.js';
import * as repo from './firebase-repo.js';

// ---- Inline Crisp SVG Icons (Material-style) ----
const ICONS = {
  home: `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z"/></svg>`,
  records: `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2zM7 7v2h14V7H7z"/></svg>`,
  credit: `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M20 4H4c-1.11 0-1.99.89-1.99 2L2 18c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V6c0-1.11-.89-2-2-2zm0 14H4v-6h16v6zm0-10H4V6h16v2z"/></svg>`,
  statements: `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-2 10H7v-2h10v2zm0-4H7V7h10v2zm-4 8H7v-2h6v2z"/></svg>`,
  key: `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12.65 10A5.99 5.99 0 0 0 7 6c-3.31 0-6 2.69-6 6s2.69 6 6 6a5.99 5.99 0 0 0 5.65-4H17v4h4v-4h2v-4H12.65zM7 14c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z"/></svg>`,
  group: `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z"/></svg>`,
  lang: `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12.87 15.07l-2.54-2.51.03-.03A17.52 17.52 0 0 0 14.07 6H17V4h-7V2H8v2H1v1.99h11.17C11.5 7.92 10.44 9.75 9 11.35 8.07 10.32 7.3 9.19 6.69 8h-2c.73 1.63 1.73 3.17 2.98 4.56l-5.09 5.02L4 19l5-5 3.11 3.11.76-2.04zM18.5 10h-2L12 22h2l1.12-3h4.75L21 22h2l-4.5-12zm-2.62 7l1.62-4.33L19.12 17h-3.24z"/></svg>`,
  settings: `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.49.49 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.48.48 0 0 0-.48-.41h-3.84a.48.48 0 0 0-.48.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96a.49.49 0 0 0-.59.22L2.74 8.87a.48.48 0 0 0 .12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.26.41.48.41h3.84c.24 0 .44-.17.48-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32a.49.49 0 0 0-.12-.61l-2.01-1.58zM12 15.6A3.6 3.6 0 1 1 12 8.4a3.6 3.6 0 0 1 0 7.2z"/></svg>`,
  trash: `<svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg>`,
  copy: `<svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18"><path d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"/></svg>`,
  close: `<svg viewBox="0 0 24 24" fill="currentColor" width="22" height="22"><path d="M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>`
};

const state = {
  configReady: false,
  initializing: true,
  authLoading: false,
  user: null,
  profile: null,
  businesses: [],
  activeWorkspace: null,
  licence: null,
  devices: [],
  ownerPrivateInfo: null,
  transactions: [],
  installationId: '',
  language: localStorage.getItem('bukukira_web_lang') || 'ms',
  currentTab: 'home', // 'home' | 'records' | 'credit' | 'statements'
  statementTab: 'pnl', // 'pnl' | 'bs' | 'ledger'
  selectedYear: '2026',
  recordsFilter: 'ALL',
  recordsSearch: '',
  onboardingTab: 'create', // 'create' | 'invite' | 'activate'
  forceOnboarding: false,
  generatedInviteCode: null,
  errorMessage: null,
  deferredInstallPrompt: null,
  iosHintDismissed: localStorage.getItem('bukukira_ios_hint_dismissed') === '1',
  modal: null // { type: 'tx' | 'credit' | 'closingStock' | 'beginningBalances' | 'settings' | 'team' | 'licence' | 'language', ... }
};

let unsubAuth = null;
let unsubProfile = null;
let unsubBusinesses = null;
let unsubLicence = null;
let unsubDevices = null;
let unsubOwnerPrivate = null;
let unsubTransactions = null;
let observedBusinessId = null;
let toastTimer = null;

// ---- Helpers ----

function showToast(msg) {
  const root = document.getElementById('toast-root');
  if (!root) return;
  root.innerHTML = `<div class="toast">${escapeHtml(msg)}</div>`;
  if (toastTimer) clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    root.innerHTML = '';
  }, 3600);
}

async function copyText(text, label = 'Copied') {
  try {
    await navigator.clipboard.writeText(String(text || ''));
    showToast(label);
  } catch {
    showToast(String(text || ''));
  }
}

function todayIso() {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function isIosSafariNotStandalone() {
  const ua = navigator.userAgent || '';
  const isIos = /iPhone|iPad|iPod/i.test(ua);
  const isStandalone =
    (window.matchMedia && window.matchMedia('(display-mode: standalone)').matches) ||
    Boolean(navigator.standalone);
  return isIos && !isStandalone;
}

function availableYears() {
  const set = new Set(['2025', '2026', '2027']);
  if (state.activeWorkspace?.financialYear) set.add(state.activeWorkspace.financialYear);
  Object.keys(state.activeWorkspace?.closingStockByYear || {}).forEach((y) => set.add(y));
  for (const t of state.transactions) {
    const y = String(t.date || '').slice(0, 4);
    if (/^[0-9]{4}$/.test(y)) set.add(y);
  }
  return Array.from(set).sort();
}

function currentAccessStatus() {
  return evaluateBusinessAccess({
    uid: state.user?.uid || null,
    workspace: state.activeWorkspace,
    licence: state.licence,
    registeredDevices: state.devices,
    installationId: state.installationId
  });
}

function currentUserRole() {
  const ws = state.activeWorkspace;
  const uid = state.user?.uid;
  if (!ws || !uid) return 'STAFF';
  if (ws.ownerId === uid) return 'OWNER';
  return ws.memberRoles?.[uid] || 'STAFF';
}

function clearWorkspaceObservers() {
  unsubLicence?.();
  unsubDevices?.();
  unsubOwnerPrivate?.();
  unsubTransactions?.();
  unsubLicence = null;
  unsubDevices = null;
  unsubOwnerPrivate = null;
  unsubTransactions = null;
  observedBusinessId = null;
  state.licence = null;
  state.devices = [];
  state.ownerPrivateInfo = null;
  state.transactions = [];
}

function clearUserObservers() {
  unsubProfile?.();
  unsubBusinesses?.();
  unsubProfile = null;
  unsubBusinesses = null;
  clearWorkspaceObservers();
  state.profile = null;
  state.businesses = [];
  state.activeWorkspace = null;
  state.installationId = '';
}

function syncActiveWorkspaceFromState() {
  const preferredId = state.profile?.activeBusinessId;
  const chosen =
    state.businesses.find((b) => b.id === preferredId) || state.businesses[0] || null;
  state.activeWorkspace = chosen;

  if (!chosen) {
    clearWorkspaceObservers();
    render();
    return;
  }

  if (chosen.id !== observedBusinessId && state.user) {
    clearWorkspaceObservers();
    observedBusinessId = chosen.id;
    state.selectedYear = chosen.financialYear || '2026';

    unsubLicence = repo.observeLicence(
      chosen.id,
      (lic) => {
        state.licence = lic;
        render();
      },
      (err) => {
        state.errorMessage = err?.message || 'Error observing licence.';
        render();
      }
    );

    unsubDevices = repo.observeRegisteredDevices(
      chosen.id,
      (devs) => {
        state.devices = devs;
        render();
      },
      (err) => console.warn('Devices listener:', err?.message || err)
    );

    unsubTransactions = repo.observeTransactions(
      chosen.id,
      state.user.uid,
      (txs) => {
        state.transactions = txs;
        render();
      },
      (err) => {
        state.errorMessage = err?.message || 'Error loading transactions.';
        render();
      }
    );

    if (chosen.ownerId === state.user.uid) {
      unsubOwnerPrivate = repo.observeOwnerPrivateInfo(
        chosen.id,
        (info) => {
          state.ownerPrivateInfo = info;
          render();
        },
        () => {}
      );
    }
  }
  render();
}

async function handleSignedInUser(user) {
  clearUserObservers();
  state.user = user;
  if (!user) {
    state.initializing = false;
    render();
    return;
  }

  state.installationId = getOrCreateInstallationId(user.uid);
  try {
    await repo.ensureUserProfile(user.uid, user.email || '', user.displayName || '');
  } catch (err) {
    console.warn('ensureUserProfile:', err?.message || err);
  }

  unsubProfile = repo.observeUserProfile(
    user.uid,
    (profile) => {
      state.profile = profile;
      if (profile?.preferredLanguage) {
        state.language = profile.preferredLanguage;
        localStorage.setItem('bukukira_web_lang', state.language);
      }
      syncActiveWorkspaceFromState();
    },
    (err) => {
      state.errorMessage = err?.message || 'Unable to load profile.';
      render();
    }
  );

  unsubBusinesses = repo.observeMemberBusinesses(
    user.uid,
    (list) => {
      state.businesses = list;
      state.initializing = false;
      syncActiveWorkspaceFromState();
    },
    (err) => {
      state.initializing = false;
      state.errorMessage = err?.message || 'Unable to load workspaces.';
      render();
    }
  );
}

async function bootApp() {
  state.initializing = true;
  render();
  try {
    const res = await repo.initFirebaseClients();
    state.configReady = res.ready;
    if (!res.ready) {
      state.initializing = false;
      render();
      return;
    }
    unsubAuth?.();
    unsubAuth = repo.observeAuth((user) => {
      handleSignedInUser(user);
    });
  } catch (err) {
    state.initializing = false;
    state.errorMessage = err?.message || 'Failed to initialize Firebase Web SDK.';
    render();
  }
}

// ---- Render Screens ----

function renderPwaInstallBanner() {
  if (state.deferredInstallPrompt) {
    return `
      <div class="banner banner-info">
        <div>
          <strong>Install BukuKira App</strong>
          <div style="font-size:12px;">Pasang pada skrin utama untuk akses pantas & sokongan luar talian.</div>
        </div>
        <button class="btn btn-primary btn-sm" data-action="trigger-pwa-install">Install</button>
      </div>
    `;
  }
  if (isIosSafariNotStandalone() && !state.iosHintDismissed) {
    return `
      <div class="banner banner-info">
        <div style="font-size:12.5px;">
          <strong>Pasang di iPhone / iPad:</strong> Tekan ikon <strong>Kongsi (Share)</strong> di Safari, kemudian pilih <strong>Add to Home Screen</strong>.
        </div>
        <button class="btn btn-outline btn-sm" data-action="dismiss-ios-hint">OK</button>
      </div>
    `;
  }
  return '';
}

function renderUnavailableScreen() {
  return `
    <div class="auth-wrap">
      <div class="card auth-card center-hero">
        <img src="./icons/icon-192.png" alt="BukuKira" />
        <h2 style="margin:4px 0 0;">BukuKira</h2>
        <div class="banner banner-error" style="width:100%;justify-content:center;">
          <span>Aplikasi web belum sedia. Sila hubungi pentadbir sistem.</span>
        </div>
      </div>
    </div>
  `;
}

function renderAuthScreen() {
  return `
    <div class="auth-wrap">
      <div class="card auth-card">
        <div class="center-hero">
          <img src="./icons/icon-192.png" alt="BukuKira" />
          <h1 style="margin:6px 0 2px;font-size:26px;">BukuKira</h1>
          <div class="text-muted" style="font-size:14px;">
            Akaun Perniagaan, Penyata Untung Rugi &amp; Kunci Kira-Kira (Web / iOS / Android)
          </div>
        </div>

        ${renderPwaInstallBanner()}
        ${state.errorMessage ? `<div class="banner banner-error"><span>${escapeHtml(state.errorMessage)}</span></div>` : ''}

        <button class="btn btn-primary btn-full" data-action="google-signin" ${state.authLoading ? 'disabled' : ''}>
          ${state.authLoading ? 'Menyambung ke Google...' : 'Log Masuk dengan Google'}
        </button>

        <button class="btn btn-outline btn-full btn-sm" data-action="google-signin-redirect" ${state.authLoading ? 'disabled' : ''}>
          Guna Redirect Sign-In (jika popup disekat di iPhone/Safari)
        </button>
      </div>
    </div>
  `;
}

function renderOnboardingScreen() {
  const defaultOwner = state.user?.displayName || '';
  return `
    <div class="auth-wrap">
      <div class="card auth-card">
        <div class="row-between">
          <div>
            <div style="font-size:20px;font-weight:700;">Ruang Kerja Perniagaan</div>
            <div class="text-muted" style="font-size:12.5px;">${escapeHtml(state.user?.email || '')}</div>
          </div>
          <div style="display:flex;gap:4px;">
            ${
              state.forceOnboarding
                ? `<button class="btn btn-outline btn-sm" data-action="cancel-onboarding">Batal</button>`
                : ''
            }
            <button class="btn btn-outline btn-sm" data-action="sign-out">Log Keluar</button>
          </div>
        </div>

        ${renderPwaInstallBanner()}
        ${state.errorMessage ? `<div class="banner banner-error"><span>${escapeHtml(state.errorMessage)}</span></div>` : ''}

        <div class="segmented">
          <button class="segmented-btn ${state.onboardingTab === 'create' ? 'active' : ''}" data-action="set-onboarding-tab" data-tab="create">Cipta Baharu</button>
          <button class="segmented-btn ${state.onboardingTab === 'invite' ? 'active' : ''}" data-action="set-onboarding-tab" data-tab="invite">Kod Jemputan</button>
          <button class="segmented-btn ${state.onboardingTab === 'activate' ? 'active' : ''}" data-action="set-onboarding-tab" data-tab="activate">Lesen Sedia Ada</button>
        </div>

        ${
          state.onboardingTab === 'create'
            ? `
          <form id="onboard-create-form" style="display:flex;flex-direction:column;gap:10px;">
            <div class="field"><label>Nama Perniagaan *</label><input class="input" id="ob-biz-name" required placeholder="Contoh: Kedai Kopi Harmoni" /></div>
            <div class="field"><label>No. Pendaftaran (SSM)</label><input class="input" id="ob-reg-no" placeholder="Pilihan" /></div>
            <div class="field"><label>Jenis Perniagaan</label><input class="input" id="ob-biz-type" placeholder="Makanan & Minuman / Runcit / Servis" /></div>
            <div class="field"><label>Nama Pemilik *</label><input class="input" id="ob-owner-name" value="${escapeHtml(defaultOwner)}" required /></div>
            <div class="grid-2">
              <div class="field">
                <label>Mata Wang</label>
                <select class="select" id="ob-currency">
                  <option value="RM" selected>RM</option>
                  <option value="SGD">SGD</option>
                  <option value="USD">USD</option>
                  <option value="Rp">Rp</option>
                </select>
              </div>
              <div class="field"><label>Tahun Kewangan</label><input class="input" id="ob-year" value="2026" maxlength="4" pattern="[0-9]{4}" required /></div>
            </div>
            <label style="display:flex;align-items:center;gap:8px;font-size:13px;padding:4px 0;">
              <input type="checkbox" id="ob-load-sample" checked />
              <span>Muatkan 10 rekod contoh Kedai Kopi (2026) untuk ujian segera</span>
            </label>
            <button type="submit" class="btn btn-primary btn-full">Cipta Ruang Kerja (Percubaan 30 Hari)</button>
          </form>
        `
            : state.onboardingTab === 'invite'
              ? `
          <form id="onboard-invite-form" style="display:flex;flex-direction:column;gap:10px;">
            <div class="text-muted" style="font-size:13px;">Masukkan Kod Jemputan pasukan (bermula dengan <code>BKINV_</code>) daripada pemilik perniagaan.</div>
            <div class="field"><label>Kod Jemputan *</label><input class="input" id="ob-invite-code" required placeholder="BKINV_..." /></div>
            <button type="submit" class="btn btn-primary btn-full">Sertai Ruang Kerja</button>
          </form>
        `
              : `
          <form id="onboard-activate-form" style="display:flex;flex-direction:column;gap:10px;">
            <div class="text-muted" style="font-size:13px;">Sudah cipta akaun di peranti Android? Salin <strong>Business ID</strong> &amp; <strong>Licence Key</strong> dari menu Lesen &amp; Peranti di Android.</div>
            <div class="field"><label>Business ID *</label><input class="input" id="ob-act-biz-id" required placeholder="biz_..." /></div>
            <div class="field"><label>Licence Key *</label><input class="input" id="ob-act-key" required placeholder="BK-TRIAL-... / BK-PRO-..." /></div>
            <button type="submit" class="btn btn-primary btn-full">Sahkan Lesen &amp; Daftar Pelayar Ini</button>
          </form>
        `
        }
      </div>
    </div>
  `;
}

function renderLicenseGateScreen(access) {
  const ws = state.activeWorkspace;
  const switcherOptions =
    state.businesses.length > 1
      ? `
      <div class="field">
        <label>Tukar Ruang Kerja</label>
        <div class="chip-row">
          ${state.businesses
            .map(
              (b) =>
                `<button class="chip ${b.id === ws?.id ? 'active' : ''}" data-action="select-workspace" data-id="${escapeHtml(b.id)}">${escapeHtml(b.businessName)}</button>`
            )
            .join('')}
        </div>
      </div>`
      : '';

  return `
    <div class="auth-wrap">
      <div class="card auth-card">
        <div>
          <div style="font-size:20px;font-weight:700;">Pengesahan Lesen &amp; Peranti Web</div>
          <div class="text-muted" style="font-size:13px;margin-top:4px;">${escapeHtml(ws?.businessName || '')} (${escapeHtml(ws?.id || '')})</div>
        </div>

        <div class="banner banner-warn">
          <span>${escapeHtml(access.reason || 'Pengesahan lesen diperlukan untuk membuka ruang kerja ini.')}</span>
        </div>

        ${state.errorMessage ? `<div class="banner banner-error"><span>${escapeHtml(state.errorMessage)}</span></div>` : ''}

        ${switcherOptions}

        <form id="gate-activate-form" style="display:flex;flex-direction:column;gap:10px;">
          <div class="field">
            <label>Business ID</label>
            <input class="input" id="gate-biz-id" value="${escapeHtml(ws?.id || '')}" required />
          </div>
          <div class="field">
            <label>Licence Key</label>
            <input class="input" id="gate-licence-key" value="${escapeHtml(state.licence?.licenceKey || '')}" placeholder="BK-..." required />
          </div>
          <button type="submit" class="btn btn-primary btn-full">Aktifkan Pelayar Ini</button>
        </form>

        <div class="row-between" style="gap:10px;">
          <button class="btn btn-outline btn-sm" style="flex:1;" data-action="open-onboarding">Ruang Kerja Lain</button>
          <button class="btn btn-outline btn-sm" style="flex:1;color:var(--coral);" data-action="sign-out">Log Keluar</button>
        </div>
      </div>
    </div>
  `;
}

function renderTxCard(tx, currency, lang, canDelete) {
  const isPositive = tx.type === 'IN' || tx.type === 'CREDIT_SALE' || tx.type === 'DEBIT_NOTE';
  const sign = isPositive ? '+' : '-';
  const cls = isPositive ? 'text-emerald' : 'text-coral';
  const label = localizedCategoryLabel(tx.categoryId, lang);
  const s = uiStrings(lang);
  const subParts = [tx.date, tx.method];
  if (tx.partyName) subParts.push(tx.partyName);
  if (tx.invoiceNo) subParts.push(`#${tx.invoiceNo}`);

  return `
    <div class="tx-card">
      <div class="tx-main">
        <div class="tx-title">${escapeHtml(label)}</div>
        <div class="tx-sub">${escapeHtml(subParts.join(' • '))}${tx.note ? ` — ${escapeHtml(tx.note)}` : ''}</div>
        <div class="tx-badges">
          <span class="badge">${escapeHtml(tx.type)}</span>
          <span class="badge ${tx.hasPendingWrites ? 'badge-sync-pending' : 'badge-sync-ok'}">
            ${escapeHtml(tx.hasPendingWrites ? s.pendingSync : s.synced)}
          </span>
        </div>
      </div>
      <div class="tx-right">
        <div class="tx-amount tabular ${cls}">${escapeHtml(sign + formatCurrency(currency, tx.amount))}</div>
        ${
          canDelete
            ? `<button class="icon-btn" style="color:var(--coral);" title="${escapeHtml(s.delete)}" data-action="delete-tx" data-id="${escapeHtml(tx.id)}">${ICONS.trash}</button>`
            : ''
        }
      </div>
    </div>
  `;
}

function renderHomeTab(ws, stats, s, role, isOperational) {
  const lang = state.language;
  const uid = state.user?.uid;
  const yearTxs = state.transactions
    .filter((t) => String(t.date || '').startsWith(state.selectedYear))
    .slice(0, 6);

  return `
    ${renderPwaInstallBanner()}
    <div class="card hero-card">
      <div class="hero-label">${escapeHtml(s.cashAndBank)} (${escapeHtml(state.selectedYear)})</div>
      <div class="hero-amount tabular">${escapeHtml(formatCurrency(ws.currency, stats.totalMoneyNow))}</div>
      <div class="hero-split">
        <div>
          <div class="hero-sub-label">${escapeHtml(s.cashLabel)}</div>
          <div class="hero-sub-val tabular">${escapeHtml(formatCurrency(ws.currency, stats.cashBalance))}</div>
        </div>
        <div>
          <div class="hero-sub-label">${escapeHtml(s.bankLabel)}</div>
          <div class="hero-sub-val tabular">${escapeHtml(formatCurrency(ws.currency, stats.bankBalance))}</div>
        </div>
      </div>
    </div>

    <div class="grid-2">
      <button class="btn btn-primary" ${isOperational ? '' : 'disabled'} data-action="open-tx-modal" data-tx-type="IN">
        + ${escapeHtml(s.addMoneyIn)}
      </button>
      <button class="btn btn-coral" ${isOperational ? '' : 'disabled'} data-action="open-tx-modal" data-tx-type="OUT">
        - ${escapeHtml(s.addMoneyOut)}
      </button>
    </div>

    <div class="grid-2">
      <div class="card metric-card">
        <div class="metric-title">${escapeHtml(s.netProfitYear)} ${escapeHtml(state.selectedYear)}</div>
        <div class="metric-value tabular ${stats.netProfit >= 0 ? 'text-emerald' : 'text-coral'}">
          ${escapeHtml(formatCurrency(ws.currency, stats.netProfit))}
        </div>
      </div>
      <div class="card metric-card">
        <div class="metric-title">${escapeHtml(s.tradeDebtorsLabel)}</div>
        <div class="metric-value tabular text-emerald">
          ${escapeHtml(formatCurrency(ws.currency, stats.tradeDebtors))}
        </div>
      </div>
    </div>

    <div class="row-between" style="margin-top:4px;">
      <strong style="font-size:16px;">${escapeHtml(s.recentRecords)}</strong>
      <button class="btn btn-ghost btn-sm" data-action="switch-tab" data-tab="records">${escapeHtml(s.viewAll)}</button>
    </div>

    ${
      yearTxs.length === 0
        ? `<div class="card text-muted">${escapeHtml(s.noRecordsYet)}</div>`
        : `<div class="tx-list">
            ${yearTxs
              .map((tx) =>
                renderTxCard(
                  tx,
                  ws.currency,
                  lang,
                  isOperational && (role === 'OWNER' || role === 'ACCOUNTANT' || tx.createdByUid === uid)
                )
              )
              .join('')}
          </div>`
    }
  `;
}

function renderRecordsTab(ws, s, role, isOperational) {
  const lang = state.language;
  const uid = state.user?.uid;
  const search = state.recordsSearch.trim().toLowerCase();

  const filtered = state.transactions.filter((tx) => {
    if (!String(tx.date || '').startsWith(state.selectedYear)) return false;
    if (state.recordsFilter === 'IN' && tx.type !== 'IN') return false;
    if (state.recordsFilter === 'OUT' && tx.type !== 'OUT') return false;
    if (
      state.recordsFilter === 'CREDIT' &&
      !['CREDIT_SALE', 'CREDIT_PURCHASE', 'CREDIT_NOTE', 'DEBIT_NOTE'].includes(tx.type)
    ) {
      return false;
    }
    if (!search) return true;
    const catLabel = localizedCategoryLabel(tx.categoryId, lang).toLowerCase();
    return (
      String(tx.note || '').toLowerCase().includes(search) ||
      catLabel.includes(search) ||
      String(tx.partyName || '').toLowerCase().includes(search) ||
      String(tx.invoiceNo || '').toLowerCase().includes(search) ||
      String(tx.date || '').includes(search)
    );
  });

  return `
    <div class="row-between">
      <h2 style="margin:0;font-size:20px;">${escapeHtml(s.navRecords)} (${escapeHtml(state.selectedYear)})</h2>
      <div style="display:flex;gap:6px;">
        <button class="btn btn-primary btn-sm" ${isOperational ? '' : 'disabled'} data-action="open-tx-modal" data-tx-type="IN">+ ${escapeHtml(s.moneyIn)}</button>
        <button class="btn btn-coral btn-sm" ${isOperational ? '' : 'disabled'} data-action="open-tx-modal" data-tx-type="OUT">- ${escapeHtml(s.moneyOut)}</button>
      </div>
    </div>

    <input
      class="input"
      id="records-search-input"
      value="${escapeHtml(state.recordsSearch)}"
      placeholder="${escapeHtml(s.searchPlaceholder)}"
    />

    <div class="chip-row">
      ${[
        ['ALL', s.filterAll],
        ['IN', s.filterIn],
        ['OUT', s.filterOut],
        ['CREDIT', s.filterCredit]
      ]
        .map(
          ([k, label]) =>
            `<button class="chip ${state.recordsFilter === k ? 'active' : ''}" data-action="set-records-filter" data-filter="${k}">${escapeHtml(label)}</button>`
        )
        .join('')}
    </div>

    ${
      filtered.length === 0
        ? `<div class="card text-muted">${escapeHtml(s.noRecordsYet)}</div>`
        : `<div class="tx-list">
            ${filtered
              .map((tx) =>
                renderTxCard(
                  tx,
                  ws.currency,
                  lang,
                  isOperational && (role === 'OWNER' || role === 'ACCOUNTANT' || tx.createdByUid === uid)
                )
              )
              .join('')}
          </div>`
    }
  `;
}

function renderCreditTab(ws, stats, s, role, isOperational) {
  const lang = state.language;
  const uid = state.user?.uid;
  const creditTxs = state.transactions.filter(
    (t) =>
      String(t.date || '').startsWith(state.selectedYear) &&
      ['CREDIT_SALE', 'CREDIT_PURCHASE', 'CREDIT_NOTE', 'DEBIT_NOTE'].includes(t.type)
  );

  return `
    <h2 style="margin:0;font-size:20px;">${escapeHtml(s.creditTitle)}</h2>

    <div class="grid-2">
      <div class="card metric-card">
        <div class="metric-title">${escapeHtml(s.tradeDebtorsLabel)}</div>
        <div class="metric-value tabular text-emerald">${escapeHtml(formatCurrency(ws.currency, stats.tradeDebtors))}</div>
      </div>
      <div class="card metric-card">
        <div class="metric-title">${escapeHtml(s.tradeCreditorsLabel)}</div>
        <div class="metric-value tabular text-coral">${escapeHtml(
          formatCurrency(ws.currency, stats.tradeCreditors + stats.otherCreditors)
        )}</div>
      </div>
    </div>

    <div class="grid-2">
      <button class="btn btn-tonal" ${isOperational ? '' : 'disabled'} data-action="open-credit-modal" data-tx-type="CREDIT_SALE">${escapeHtml(s.creditSale)}</button>
      <button class="btn btn-tonal" ${isOperational ? '' : 'disabled'} data-action="open-credit-modal" data-tx-type="CREDIT_PURCHASE">${escapeHtml(s.creditPurchase)}</button>
      <button class="btn btn-tonal" ${isOperational ? '' : 'disabled'} data-action="open-credit-modal" data-tx-type="CREDIT_NOTE">${escapeHtml(s.creditNote)}</button>
      <button class="btn btn-tonal" ${isOperational ? '' : 'disabled'} data-action="open-credit-modal" data-tx-type="DEBIT_NOTE">${escapeHtml(s.debitNote)}</button>
    </div>

    <div style="font-weight:700;font-size:15px;">${escapeHtml(s.creditTitle)} (${escapeHtml(state.selectedYear)})</div>

    ${
      creditTxs.length === 0
        ? `<div class="card text-muted">${escapeHtml(s.noRecordsYet)}</div>`
        : `<div class="tx-list">
            ${creditTxs
              .map((tx) =>
                renderTxCard(
                  tx,
                  ws.currency,
                  lang,
                  isOperational && (role === 'OWNER' || role === 'ACCOUNTANT' || tx.createdByUid === uid)
                )
              )
              .join('')}
          </div>`
    }
  `;
}

function renderStatementsTab(ws, stats, s, role, isOperational) {
  const years = availableYears();
  const canEditAccounting = isOperational && (role === 'OWNER' || role === 'ACCOUNTANT');
  const closing = getClosingStock(Number.parseInt(state.selectedYear, 10) || 2026, ws.closingStockByYear, ws.beginningBalances);

  let stmtContent = '';
  if (state.statementTab === 'pnl' || state.statementTab === 'bs') {
    const targetType = state.statementTab === 'pnl' ? 'PNL' : 'BALANCE_SHEET';
    const filtered = stats.rows.filter(
      (r) => r.statementType === targetType || (targetType === 'BALANCE_SHEET' && r.statementType === 'CHECK')
    );
    let lastSection = '';
    const rowsHtml = filtered
      .map((r) => {
        let sec = '';
        if (r.section !== lastSection) {
          lastSection = r.section;
          sec = `<div class="stmt-section">${escapeHtml(r.section)}</div>`;
        }
        return `
          ${sec}
          <div class="stmt-row ${r.isTotal ? 'total' : ''}">
            <div style="flex:1;">
              <div>${escapeHtml(r.label)}</div>
              ${r.note ? `<div class="stmt-note">${escapeHtml(r.note)}</div>` : ''}
            </div>
            <div class="tabular" style="white-space:nowrap;">${escapeHtml(formatCurrency(ws.currency, r.amount))}</div>
          </div>
        `;
      })
      .join('');
    stmtContent = `<div class="card">${rowsHtml}</div>`;
  } else {
    const ledgerEntries = Object.entries(stats.ledgers || {});
    stmtContent =
      ledgerEntries.length === 0
        ? `<div class="card text-muted">${escapeHtml(s.noRecordsYet)}</div>`
        : ledgerEntries
            .map(([acc, entries]) => {
              let dr = 0;
              let cr = 0;
              const trs = entries
                .map((e) => {
                  dr += Number(e.debit) || 0;
                  cr += Number(e.credit) || 0;
                  return `<tr>
                    <td>${escapeHtml(e.date)}</td>
                    <td>${escapeHtml(e.description)}</td>
                    <td class="num tabular">${e.debit > 0 ? escapeHtml(formatNumber(e.debit)) : ''}</td>
                    <td class="num tabular">${e.credit > 0 ? escapeHtml(formatNumber(e.credit)) : ''}</td>
                  </tr>`;
                })
                .join('');
              return `
                <div class="card ledger-card">
                  <div style="font-weight:700;margin-bottom:8px;color:var(--emerald);">${escapeHtml(acc)}</div>
                  <div style="overflow-x:auto;">
                    <table class="ledger-table">
                      <thead><tr><th>Date</th><th>Description</th><th class="num">Dr</th><th class="num">Cr</th></tr></thead>
                      <tbody>
                        ${trs}
                        <tr style="font-weight:700;background:var(--emerald-soft);">
                          <td></td><td>Total</td>
                          <td class="num tabular">${escapeHtml(formatNumber(dr))}</td>
                          <td class="num tabular">${escapeHtml(formatNumber(cr))}</td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </div>
              `;
            })
            .join('');
  }

  return `
    <div class="row-between">
      <h2 style="margin:0;font-size:20px;">${escapeHtml(s.navStatements)}</h2>
      <span class="badge ${stats.isBalanced ? 'badge-sync-ok' : 'badge-sync-pending'}" style="font-size:12px;">
        ${stats.isBalanced ? 'Balanced' : `Diff: ${formatCurrency(ws.currency, stats.balanceDifference)}`}
      </span>
    </div>

    <div class="chip-row">
      ${years
        .map(
          (y) =>
            `<button class="chip ${y === state.selectedYear ? 'active' : ''}" data-action="select-year" data-year="${escapeHtml(y)}">${escapeHtml(y)}</button>`
        )
        .join('')}
    </div>

    <div class="grid-2">
      <button class="btn btn-outline btn-sm" ${canEditAccounting ? '' : 'disabled'} data-action="open-closing-stock">
        ${escapeHtml(s.closingStock)}: ${escapeHtml(formatCurrency(ws.currency, closing))}
      </button>
      <button class="btn btn-outline btn-sm" ${canEditAccounting ? '' : 'disabled'} data-action="open-beginning-balances">
        ${escapeHtml(s.beginningBalances)}
      </button>
    </div>

    <div class="grid-2">
      <button class="btn btn-primary btn-sm" data-action="export-pdf">${escapeHtml(s.exportPdf)}</button>
      <button class="btn btn-tonal btn-sm" data-action="export-csv">${escapeHtml(s.exportCsv)}</button>
      <button class="btn btn-outline btn-sm" data-action="backup-json">${escapeHtml(s.backupJson)}</button>
      <label class="btn btn-outline btn-sm" style="margin:0;${isOperational ? '' : 'opacity:0.45;pointer-events:none;'}">
        ${escapeHtml(s.restoreJson)}
        <input type="file" id="restore-json-file" accept=".json,application/json" style="display:none;" />
      </label>
    </div>

    <div class="segmented">
      <button class="segmented-btn ${state.statementTab === 'pnl' ? 'active' : ''}" data-action="set-stmt-tab" data-tab="pnl">${escapeHtml(s.tabPnl)}</button>
      <button class="segmented-btn ${state.statementTab === 'bs' ? 'active' : ''}" data-action="set-stmt-tab" data-tab="bs">${escapeHtml(s.tabBs)}</button>
      <button class="segmented-btn ${state.statementTab === 'ledger' ? 'active' : ''}" data-action="set-stmt-tab" data-tab="ledger">${escapeHtml(s.tabLedger)}</button>
    </div>

    ${stmtContent}
  `;
}

function renderWorkspaceShell() {
  const ws = state.activeWorkspace;
  const s = uiStrings(state.language);
  const access = currentAccessStatus();
  const isOperational = Boolean(state.licence && state.licence.status === 'ACTIVE' && state.licence.expiryDateMillis > Date.now());
  const role = currentUserRole();
  const stats = computeStatements(
    state.transactions,
    ws.beginningBalances,
    ws.closingStockByYear,
    state.selectedYear,
    state.language
  );

  const navItems = [
    ['home', s.navHome, ICONS.home],
    ['records', s.navRecords, ICONS.records],
    ['credit', s.navCredit, ICONS.credit],
    ['statements', s.navStatements, ICONS.statements]
  ];

  let mainBody = '';
  if (state.currentTab === 'home') mainBody = renderHomeTab(ws, stats, s, role, isOperational);
  else if (state.currentTab === 'records') mainBody = renderRecordsTab(ws, s, role, isOperational);
  else if (state.currentTab === 'credit') mainBody = renderCreditTab(ws, stats, s, role, isOperational);
  else mainBody = renderStatementsTab(ws, stats, s, role, isOperational);

  return `
    <div class="app-shell">
      <header class="top-bar">
        <div class="top-bar-inner">
          <div class="brand-title-wrap">
            <img class="brand-icon" src="./icons/icon-192.png" alt="BukuKira" />
            <div class="brand-texts">
              <div class="brand-title">${escapeHtml(ws.businessName || 'BukuKira')}</div>
              <div class="brand-subtitle">
                ${escapeHtml(state.licence?.plan || 'TRIAL')} &bull; ${escapeHtml(role)} &bull; FY ${escapeHtml(state.selectedYear)}
              </div>
            </div>
          </div>
          <div class="top-actions">
            <button class="icon-btn" title="${escapeHtml(s.licenceTitle)}" data-action="open-licence-modal">${ICONS.key}</button>
            <button class="icon-btn" title="${escapeHtml(s.teamTitle)}" data-action="open-team-modal">${ICONS.group}</button>
            <button class="icon-btn" title="Language" data-action="open-language-modal">${ICONS.lang}</button>
            <button class="icon-btn" title="${escapeHtml(s.settingsTitle)}" data-action="open-settings-modal">${ICONS.settings}</button>
          </div>
        </div>
      </header>

      <div class="workspace-body">
        <aside class="side-rail">
          ${navItems
            .map(
              ([id, label, icon]) => `
            <button class="rail-item ${state.currentTab === id ? 'active' : ''}" data-action="switch-tab" data-tab="${id}">
              ${icon}<span>${escapeHtml(label)}</span>
            </button>
          `
            )
            .join('')}
        </aside>

        <main class="main-content">
          ${
            !isOperational || !access.isGranted
              ? `<div class="banner banner-error"><span>${escapeHtml(s.readOnlyLicence)}</span></div>`
              : ''
          }
          ${mainBody}
        </main>
      </div>

      <nav class="bottom-nav">
        ${navItems
          .map(
            ([id, label, icon]) => `
          <button class="nav-item ${state.currentTab === id ? 'active' : ''}" data-action="switch-tab" data-tab="${id}">
            ${icon}<span>${escapeHtml(label)}</span>
          </button>
        `
          )
          .join('')}
      </nav>
    </div>
  `;
}

// ---- Modals ----

function renderModal() {
  const m = state.modal;
  if (!m) return '';
  const ws = state.activeWorkspace;
  const s = uiStrings(state.language);
  const lang = state.language;
  const role = currentUserRole();
  const isOwner = role === 'OWNER';

  let title = '';
  let content = '';

  if (m.type === 'tx') {
    const txType = m.txType === 'OUT' ? 'OUT' : 'IN';
    const cats = txType === 'IN' ? IN_CATEGORIES : OUT_CATEGORIES;
    const catId = m.categoryId || cats[0].id;
    const method = m.method || 'CASH';
    const isDisposal = catId === 'asset_disposal';
    const disposalType = m.disposalAssetType || 'equipment';
    title = txType === 'IN' ? s.addMoneyIn : s.addMoneyOut;
    content = `
      <div class="segmented">
        <button type="button" class="segmented-btn ${txType === 'IN' ? 'active' : ''}" data-action="modal-tx-type" data-tx-type="IN">${escapeHtml(s.moneyIn)}</button>
        <button type="button" class="segmented-btn ${txType === 'OUT' ? 'active' : ''}" data-action="modal-tx-type" data-tx-type="OUT">${escapeHtml(s.moneyOut)}</button>
      </div>
      <div class="chip-row">
        <button type="button" class="chip ${method === 'CASH' ? 'active' : ''}" data-action="modal-tx-method" data-method="CASH">${escapeHtml(s.cashLabel)}</button>
        <button type="button" class="chip ${method === 'BANK' ? 'active' : ''}" data-action="modal-tx-method" data-method="BANK">${escapeHtml(s.bankLabel)}</button>
      </div>
      <form id="tx-form" style="display:flex;flex-direction:column;gap:10px;">
        <div class="grid-2">
          <div class="field"><label>Jumlah (${escapeHtml(ws.currency)}) *</label><input class="input" id="tx-amount" type="number" step="0.01" min="0" value="${escapeHtml(m.amount || '')}" required /></div>
          <div class="field"><label>Tarikh *</label><input class="input" id="tx-date" type="date" value="${escapeHtml(m.date || todayIso())}" required /></div>
        </div>
        <div class="field">
          <label>Kategori</label>
          <div class="chip-row">
            ${cats
              .map(
                (c) =>
                  `<button type="button" class="chip ${c.id === catId ? 'active' : ''}" data-action="modal-tx-cat" data-cat-id="${escapeHtml(c.id)}">${escapeHtml(localizedCategoryLabel(c.id, lang))}</button>`
              )
              .join('')}
          </div>
        </div>
        ${
          isDisposal
            ? `
          <div class="divider"></div>
          <div style="font-weight:700;font-size:13px;color:var(--emerald);">Maklumat Pelupusan Aset</div>
          <div class="chip-row">
            ${['equipment', 'landBuilding', 'vehicles', 'otherNonCurrent', 'investments']
              .map(
                (at) =>
                  `<button type="button" class="chip ${at === disposalType ? 'active' : ''}" data-action="modal-disposal-type" data-asset-type="${at}">${at}</button>`
              )
              .join('')}
          </div>
          <div class="field"><label>Kos Asal Aset (${escapeHtml(ws.currency)}) *</label><input class="input" id="tx-disposal-cost" type="number" step="0.01" min="0.01" value="${escapeHtml(m.disposalAssetCost || '')}" required /></div>
        `
            : ''
        }
        <div class="field"><label>Nota / Butiran</label><input class="input" id="tx-note" value="${escapeHtml(m.note || '')}" maxlength="500" /></div>
        <div class="grid-2" style="margin-top:6px;">
          <button type="button" class="btn btn-outline" data-action="close-modal">${escapeHtml(s.cancel)}</button>
          <button type="submit" class="btn ${txType === 'IN' ? 'btn-primary' : 'btn-coral'}">${escapeHtml(s.save)}</button>
        </div>
      </form>
    `;
  } else if (m.type === 'credit') {
    const txType = m.txType;
    const selectedOpt = m.purchaseOptId || 'credit_purchase';
    title =
      txType === 'CREDIT_SALE'
        ? s.creditSale
        : txType === 'CREDIT_PURCHASE'
          ? s.creditPurchase
          : txType === 'CREDIT_NOTE'
            ? s.creditNote
            : s.debitNote;
    content = `
      <form id="credit-form" style="display:flex;flex-direction:column;gap:10px;">
        ${
          txType === 'CREDIT_PURCHASE'
            ? `
          <div class="field">
            <label>Item Belian Kredit</label>
            <div class="chip-row">
              ${CREDIT_PURCHASE_OPTIONS.map((opt) => {
                const lbl = lang === 'en' ? opt.labelEn : lang === 'zh' ? opt.labelZh : opt.labelMs;
                return `<button type="button" class="chip ${opt.id === selectedOpt ? 'active' : ''}" data-action="modal-credit-opt" data-opt-id="${opt.id}">${escapeHtml(lbl)}</button>`;
              }).join('')}
            </div>
          </div>
        `
            : ''
        }
        <div class="field"><label>Nama Pelanggan / Pembekal</label><input class="input" id="cr-party" maxlength="120" /></div>
        <div class="field"><label>No. Invois / Bil / Nota</label><input class="input" id="cr-invoice" maxlength="60" /></div>
        <div class="grid-2">
          <div class="field"><label>Jumlah (${escapeHtml(ws.currency)}) *</label><input class="input" id="cr-amount" type="number" step="0.01" min="0.01" required /></div>
          <div class="field"><label>Tarikh *</label><input class="input" id="cr-date" type="date" value="${todayIso()}" required /></div>
        </div>
        <div class="field"><label>Nota</label><input class="input" id="cr-note" maxlength="500" /></div>
        <div class="grid-2" style="margin-top:6px;">
          <button type="button" class="btn btn-outline" data-action="close-modal">${escapeHtml(s.cancel)}</button>
          <button type="submit" class="btn btn-primary">${escapeHtml(s.save)}</button>
        </div>
      </form>
    `;
  } else if (m.type === 'closingStock') {
    const curVal = getClosingStock(Number.parseInt(state.selectedYear, 10) || 2026, ws.closingStockByYear, ws.beginningBalances);
    title = `${s.closingStock} (${state.selectedYear})`;
    content = `
      <form id="closing-stock-form" style="display:flex;flex-direction:column;gap:12px;">
        <div class="field">
          <label>Nilai Stok Akhir (${escapeHtml(ws.currency)})</label>
          <input class="input" id="cs-amount" type="number" step="0.01" min="0" value="${curVal.toFixed(2)}" required />
        </div>
        <div class="grid-2">
          <button type="button" class="btn btn-outline" data-action="close-modal">${escapeHtml(s.cancel)}</button>
          <button type="submit" class="btn btn-primary">${escapeHtml(s.save)}</button>
        </div>
      </form>
    `;
  } else if (m.type === 'beginningBalances') {
    const bb = defaultBeginningBalances(ws.beginningBalances);
    const fields = [
      ['openingStock', 'Stok Awal'],
      ['cash', 'Tunai'],
      ['bank', 'Bank'],
      ['tradeDebtors', 'Penghutang Dagangan'],
      ['otherDebtors', 'Penghutang Lain'],
      ['otherCurrent', 'Aset Semasa Lain'],
      ['landBuilding', 'Tanah & Bangunan'],
      ['equipment', 'Loji & Jentera'],
      ['vehicles', 'Kenderaan'],
      ['otherNonCurrent', 'Aset Bukan Semasa Lain'],
      ['investments', 'Pelaburan'],
      ['loans', 'Pinjaman & Overdraf'],
      ['tradeCreditors', 'Pemiutang Dagangan'],
      ['otherCreditors', 'Pemiutang Lain'],
      ['capital', 'Akaun Modal'],
      ['broughtForward', 'Untung Bawa Hadapan']
    ];
    title = s.beginningBalances;
    content = `
      <form id="bb-form" style="display:flex;flex-direction:column;gap:10px;">
        <div class="text-muted" style="font-size:12.5px;">Baki permulaan akaun kunci kira-kira (${escapeHtml(ws.currency)})</div>
        <div class="grid-2">
          ${fields
            .map(
              ([k, lbl]) => `
            <div class="field">
              <label>${escapeHtml(lbl)}</label>
              <input class="input" name="${k}" type="number" step="0.01" value="${bb[k] ? bb[k].toFixed(2) : ''}" placeholder="0.00" />
            </div>
          `
            )
            .join('')}
        </div>
        <div class="grid-2" style="margin-top:6px;">
          <button type="button" class="btn btn-outline" data-action="close-modal">${escapeHtml(s.cancel)}</button>
          <button type="submit" class="btn btn-primary">${escapeHtml(s.save)}</button>
        </div>
      </form>
    `;
  } else if (m.type === 'settings') {
    const priv = state.ownerPrivateInfo || {};
    title = s.settingsTitle;
    content = `
      ${
        state.businesses.length > 1
          ? `
        <div class="field">
          <label>Tukar Ruang Kerja</label>
          <div class="chip-row">
            ${state.businesses
              .map(
                (b) =>
                  `<button type="button" class="chip ${b.id === ws.id ? 'active' : ''}" data-action="select-workspace" data-id="${escapeHtml(b.id)}">${escapeHtml(b.businessName)}</button>`
              )
              .join('')}
          </div>
        </div>
      `
          : ''
      }
      <button type="button" class="btn btn-outline btn-full" data-action="open-onboarding">+ Cipta / Sertai / Aktifkan Perniagaan Lain</button>
      <div class="divider"></div>
      <form id="settings-form" style="display:flex;flex-direction:column;gap:10px;">
        <div class="field"><label>Nama Perniagaan</label><input class="input" id="set-name" value="${escapeHtml(ws.businessName)}" ${isOwner ? '' : 'disabled'} required /></div>
        <div class="field"><label>No. Pendaftaran (SSM)</label><input class="input" id="set-reg" value="${escapeHtml(ws.businessRegNo)}" ${isOwner ? '' : 'disabled'} /></div>
        <div class="field"><label>Jenis Perniagaan</label><input class="input" id="set-type" value="${escapeHtml(ws.businessType)}" ${isOwner ? '' : 'disabled'} /></div>
        <div class="field"><label>Alamat</label><input class="input" id="set-addr" value="${escapeHtml(ws.businessAddress)}" ${isOwner ? '' : 'disabled'} /></div>
        <div class="field"><label>Nama Pemilik</label><input class="input" id="set-owner" value="${escapeHtml(ws.ownerName)}" ${isOwner ? '' : 'disabled'} required /></div>
        <div class="grid-2">
          <div class="field">
            <label>Mata Wang</label>
            <select class="select" id="set-currency" ${isOwner ? '' : 'disabled'}>
              ${['RM', 'SGD', 'USD', 'Rp']
                .map((c) => `<option value="${c}" ${ws.currency === c ? 'selected' : ''}>${c}</option>`)
                .join('')}
            </select>
          </div>
          <div class="field"><label>${escapeHtml(s.financialYear)}</label><input class="input" id="set-year" value="${escapeHtml(ws.financialYear)}" maxlength="4" pattern="[0-9]{4}" ${isOwner ? '' : 'disabled'} required /></div>
        </div>
        ${
          isOwner
            ? `
          <div class="divider"></div>
          <div style="font-weight:700;font-size:13px;color:var(--emerald);">Maklumat Peribadi Pemilik (Sulit — Pemilik Sahaja)</div>
          <div class="field"><label>No. KP / Pengenalan</label><input class="input" id="set-priv-ic" value="${escapeHtml(priv.ownerIdNumber || '')}" /></div>
          <div class="field"><label>No. Telefon</label><input class="input" id="set-priv-phone" value="${escapeHtml(priv.ownerPhone || '')}" /></div>
          <div class="field"><label>E-mel Pemilik</label><input class="input" id="set-priv-email" value="${escapeHtml(priv.ownerEmail || '')}" /></div>
          <button type="button" class="btn btn-outline btn-full" data-action="load-sample-data">Muatkan Rekod Contoh Kedai Kopi (2026)</button>
          <button type="submit" class="btn btn-primary btn-full">${escapeHtml(s.save)}</button>
        `
            : ''
        }
      </form>
      <button type="button" class="btn btn-outline btn-full" style="color:var(--coral);" data-action="sign-out">${escapeHtml(s.signOut)}</button>
    `;
  } else if (m.type === 'team') {
    const uid = state.user?.uid;
    const inviteRole = m.inviteRole || 'STAFF';
    title = s.teamTitle;
    content = `
      <div class="text-muted" style="font-size:13px;">Ahli pasukan (${ws.memberUids.length} / ${ws.maxMembers})</div>
      ${ws.memberUids
        .map((muid) => {
          const r = ws.memberRoles?.[muid] || 'STAFF';
          return `
          <div class="card" style="padding:12px;">
            <div class="row-between">
              <div style="min-width:0;">
                <div style="font-weight:600;font-size:13px;word-break:break-all;">${escapeHtml(muid)}${muid === uid ? ' (Anda)' : ''}</div>
                <div style="font-size:11.5px;color:var(--emerald);font-weight:700;">${escapeHtml(r)}</div>
              </div>
              ${
                isOwner && muid !== ws.ownerId
                  ? `
                <div style="display:flex;align-items:center;gap:4px;">
                  <button class="btn btn-ghost btn-sm" data-action="toggle-member-role" data-uid="${escapeHtml(muid)}" data-next-role="${r === 'STAFF' ? 'ACCOUNTANT' : 'STAFF'}">
                    ${r === 'STAFF' ? 'Jadikan Akauntan' : 'Jadikan Staf'}
                  </button>
                  <button class="icon-btn" style="color:var(--coral);" data-action="remove-member" data-uid="${escapeHtml(muid)}">${ICONS.trash}</button>
                </div>
              `
                  : ''
              }
            </div>
          </div>
        `;
        })
        .join('')}
      ${
        isOwner
          ? `
        <div class="divider"></div>
        <div style="font-weight:700;font-size:13.5px;">Jana Kod Jemputan Sekali Guna (Sah 3 Hari)</div>
        <div class="chip-row">
          <button type="button" class="chip ${inviteRole === 'STAFF' ? 'active' : ''}" data-action="set-invite-role" data-role="STAFF">STAFF</button>
          <button type="button" class="chip ${inviteRole === 'ACCOUNTANT' ? 'active' : ''}" data-action="set-invite-role" data-role="ACCOUNTANT">ACCOUNTANT</button>
        </div>
        <button type="button" class="btn btn-primary btn-full" data-action="generate-invite" data-role="${inviteRole}">Jana Kod Jemputan</button>
        ${
          state.generatedInviteCode
            ? `
          <div class="banner banner-info">
            <div style="min-width:0;">
              <div style="font-size:11px;">Kod Jemputan Baharu:</div>
              <strong style="word-break:break-all;">${escapeHtml(state.generatedInviteCode)}</strong>
            </div>
            <button type="button" class="icon-btn" data-action="copy-text" data-text="${escapeHtml(state.generatedInviteCode)}">${ICONS.copy}</button>
          </div>
        `
            : ''
        }
        <button type="button" class="btn btn-outline btn-full" data-action="sync-team-access">Segerak Akses Rekod Kepada Semua Ahli</button>
      `
          : ''
      }
    `;
  } else if (m.type === 'licence') {
    const lic = state.licence;
    const access = currentAccessStatus();
    const uid = state.user?.uid;
    const expStr =
      lic?.expiryDateMillis > 0
        ? new Date(lic.expiryDateMillis).toLocaleDateString('en-GB', {
            day: '2-digit',
            month: 'short',
            year: 'numeric'
          })
        : '-';
    title = s.licenceTitle;
    content = `
      <div class="card" style="background:var(--surface-alt);">
        <div class="row-between">
          <div style="font-size:13px;word-break:break-all;"><strong>Business ID:</strong> ${escapeHtml(ws.id)}</div>
          <button class="icon-btn" data-action="copy-text" data-text="${escapeHtml(ws.id)}">${ICONS.copy}</button>
        </div>
        ${
          lic
            ? `
          <div class="row-between">
            <div style="font-size:14px;word-break:break-all;"><strong>Licence Key:</strong> ${escapeHtml(lic.licenceKey)}</div>
            <button class="icon-btn" data-action="copy-text" data-text="${escapeHtml(lic.licenceKey)}">${ICONS.copy}</button>
          </div>
          <div style="font-weight:700;font-size:13px;color:${access.isGranted ? 'var(--emerald)' : 'var(--coral)'};">
            Plan: ${escapeHtml(lic.plan)} &bull; Status: ${escapeHtml(lic.status)}
          </div>
          <div class="text-muted" style="font-size:12.5px;">Sah sehingga: ${escapeHtml(expStr)}</div>
          <div class="text-muted" style="font-size:12.5px;">Had Peranti: ${state.devices.length} / ${lic.maxDevices}</div>
        `
            : ''
        }
        <div class="text-muted" style="font-size:11.5px;margin-top:4px;">ID Pelayar Ini: ${escapeHtml(state.installationId.slice(0, 12))}...</div>
      </div>

      <button type="button" class="btn btn-outline btn-full" data-action="reverify-device">Sahkan Semula Peranti &amp; Lesen</button>
      <div class="text-muted" style="font-size:12px;">
        Salin Business ID &amp; Licence Key di atas untuk mengaktifkan telefon Android atau pelayar Web/PWA lain.
      </div>
      <div style="font-weight:700;font-size:15px;">Peranti Berdaftar (${state.devices.length})</div>
      ${state.devices
        .map((dev) => {
          const isCur = dev.installationId === state.installationId;
          const isWeb = String(dev.platform || '').toUpperCase() === 'WEB';
          return `
          <div class="card" style="padding:12px;">
            <div class="row-between">
              <div style="min-width:0;">
                <div style="font-weight:600;font-size:13.5px;">${escapeHtml(dev.deviceName || dev.platform)}${isCur ? ' (Pelayar Ini)' : ''}</div>
                <div class="text-muted" style="font-size:11.5px;">${isWeb ? 'WEB / PWA' : escapeHtml(dev.platform)} &bull; ${escapeHtml(dev.installationId.slice(0, 12))}...</div>
              </div>
              ${
                isOwner || dev.userId === uid
                  ? `<button class="icon-btn" style="color:var(--coral);" data-action="remove-device" data-id="${escapeHtml(dev.installationId)}">${ICONS.trash}</button>`
                  : ''
              }
            </div>
          </div>
        `;
        })
        .join('')}
    `;
  } else if (m.type === 'language') {
    title = 'Bahasa / Language / 语言';
    content = `
      <div style="display:flex;flex-direction:column;gap:8px;">
        ${[
          ['ms', 'Bahasa Melayu'],
          ['en', 'English'],
          ['zh', '中文']
        ]
          .map(
            ([code, name]) => `
          <button type="button" class="btn ${state.language === code ? 'btn-primary' : 'btn-outline'} btn-full" data-action="set-language" data-lang="${code}">
            ${escapeHtml(name)}
          </button>
        `
          )
          .join('')}
      </div>
    `;
  }

  return `
    <div class="modal-backdrop" data-action="backdrop-close">
      <div class="modal-sheet">
        <div class="modal-header">
          <div class="modal-title">${escapeHtml(title)}</div>
          <button class="icon-btn" data-action="close-modal" aria-label="Close">${ICONS.close}</button>
        </div>
        <div class="modal-body">${content}</div>
      </div>
    </div>
  `;
}

// ---- Main Render Loop ----

function render() {
  const appEl = document.getElementById('app');
  const modalRoot = document.getElementById('modal-root');
  if (!appEl || !modalRoot) return;

  if (state.initializing) {
    appEl.innerHTML = `
      <div class="auth-wrap">
        <div class="card auth-card center-hero">
          <img src="./icons/icon-192.png" alt="BukuKira" />
          <div style="font-weight:700;font-size:20px;">BukuKira</div>
          <div class="spinner" aria-label="Loading"></div>
        </div>
      </div>
    `;
    modalRoot.innerHTML = '';
    return;
  }

  if (!state.configReady) {
    appEl.innerHTML = renderUnavailableScreen();
    modalRoot.innerHTML = '';
    return;
  }

  if (!state.user) {
    appEl.innerHTML = renderAuthScreen();
    modalRoot.innerHTML = '';
    return;
  }

  const access = currentAccessStatus();
  if (state.forceOnboarding || !state.activeWorkspace || access.status === 'WorkspaceRequired') {
    appEl.innerHTML = renderOnboardingScreen();
    modalRoot.innerHTML = '';
    return;
  }

  if (access.status === 'LicenseRequired' || access.status === 'LicenseDenied') {
    appEl.innerHTML = renderLicenseGateScreen(access);
    modalRoot.innerHTML = '';
    return;
  }

  appEl.innerHTML = renderWorkspaceShell();
  modalRoot.innerHTML = renderModal();
}

// ---- Event Delegation ----

document.addEventListener('click', async (e) => {
  const target = e.target.closest('[data-action]');
  if (!target) {
    if (e.target.classList.contains('modal-backdrop') && state.modal) {
      state.modal = null;
      render();
    }
    return;
  }

  const action = target.getAttribute('data-action');
  try {
    switch (action) {
      case 'backdrop-close':
        if (e.target === target) {
          state.modal = null;
          render();
        }
        break;
      case 'close-modal':
        state.modal = null;
        render();
        break;
      case 'trigger-pwa-install':
        if (state.deferredInstallPrompt) {
          state.deferredInstallPrompt.prompt();
          await state.deferredInstallPrompt.userChoice;
          state.deferredInstallPrompt = null;
          render();
        }
        break;
      case 'dismiss-ios-hint':
        state.iosHintDismissed = true;
        localStorage.setItem('bukukira_ios_hint_dismissed', '1');
        render();
        break;
      case 'google-signin':
      case 'google-signin-redirect':
        state.authLoading = true;
        state.errorMessage = null;
        render();
        try {
          await repo.signInWithGoogleWeb({ forceRedirect: action === 'google-signin-redirect' });
        } catch (err) {
          state.errorMessage = err?.message || 'Google Sign-In failed.';
        } finally {
          state.authLoading = false;
          render();
        }
        break;
      case 'sign-out':
        state.modal = null;
        state.forceOnboarding = false;
        await repo.signOutWeb();
        break;
      case 'set-onboarding-tab':
        state.onboardingTab = target.getAttribute('data-tab') || 'create';
        state.errorMessage = null;
        render();
        break;
      case 'open-onboarding':
        state.modal = null;
        state.forceOnboarding = true;
        state.errorMessage = null;
        render();
        break;
      case 'cancel-onboarding':
        state.forceOnboarding = false;
        state.errorMessage = null;
        render();
        break;
      case 'select-workspace': {
        const bizId = target.getAttribute('data-id');
        if (bizId && state.user) {
          state.modal = null;
          await repo.setActiveBusinessId(state.user.uid, bizId);
          showToast('Ruang kerja ditukar');
        }
        break;
      }
      case 'switch-tab':
        state.currentTab = target.getAttribute('data-tab') || 'home';
        render();
        break;
      case 'set-records-filter':
        state.recordsFilter = target.getAttribute('data-filter') || 'ALL';
        render();
        break;
      case 'select-year':
        state.selectedYear = target.getAttribute('data-year') || '2026';
        render();
        break;
      case 'set-stmt-tab':
        state.statementTab = target.getAttribute('data-tab') || 'pnl';
        render();
        break;
      case 'open-tx-modal':
        state.modal = {
          type: 'tx',
          txType: target.getAttribute('data-tx-type') || 'IN',
          categoryId:
            target.getAttribute('data-tx-type') === 'OUT' ? OUT_CATEGORIES[0].id : IN_CATEGORIES[0].id,
          method: 'CASH',
          disposalAssetType: 'equipment'
        };
        render();
        break;
      case 'modal-tx-type': {
        const nextType = target.getAttribute('data-tx-type') === 'OUT' ? 'OUT' : 'IN';
        state.modal = {
          ...state.modal,
          txType: nextType,
          categoryId: nextType === 'IN' ? IN_CATEGORIES[0].id : OUT_CATEGORIES[0].id
        };
        render();
        break;
      }
      case 'modal-tx-method':
        state.modal = { ...state.modal, method: target.getAttribute('data-method') || 'CASH' };
        render();
        break;
      case 'modal-tx-cat':
        state.modal = { ...state.modal, categoryId: target.getAttribute('data-cat-id') };
        render();
        break;
      case 'modal-disposal-type':
        state.modal = {
          ...state.modal,
          disposalAssetType: target.getAttribute('data-asset-type') || 'equipment'
        };
        render();
        break;
      case 'open-credit-modal':
        state.modal = {
          type: 'credit',
          txType: target.getAttribute('data-tx-type') || 'CREDIT_SALE',
          purchaseOptId: 'credit_purchase'
        };
        render();
        break;
      case 'modal-credit-opt':
        state.modal = { ...state.modal, purchaseOptId: target.getAttribute('data-opt-id') };
        render();
        break;
      case 'delete-tx': {
        const txId = target.getAttribute('data-id');
        if (txId && state.activeWorkspace) {
          await repo.deleteTransaction(state.activeWorkspace.id, txId);
          showToast('Rekod dipadam');
        }
        break;
      }
      case 'open-closing-stock':
        state.modal = { type: 'closingStock' };
        render();
        break;
      case 'open-beginning-balances':
        state.modal = { type: 'beginningBalances' };
        render();
        break;
      case 'open-settings-modal':
        state.modal = { type: 'settings' };
        render();
        break;
      case 'open-team-modal':
        state.modal = { type: 'team', inviteRole: 'STAFF' };
        render();
        break;
      case 'open-licence-modal':
        state.modal = { type: 'licence' };
        render();
        break;
      case 'open-language-modal':
        state.modal = { type: 'language' };
        render();
        break;
      case 'set-language': {
        const lang = target.getAttribute('data-lang') || 'ms';
        state.language = lang;
        localStorage.setItem('bukukira_web_lang', lang);
        state.modal = null;
        if (state.user) {
          await repo.updatePreferredLanguage(state.user.uid, lang);
        }
        render();
        break;
      }
      case 'set-invite-role':
        state.modal = { ...state.modal, inviteRole: target.getAttribute('data-role') || 'STAFF' };
        render();
        break;
      case 'generate-invite':
        if (state.user && state.activeWorkspace) {
          const role = target.getAttribute('data-role') || 'STAFF';
          const code = await repo.createTeamInvite(
            state.user.uid,
            state.activeWorkspace.id,
            state.activeWorkspace.businessName,
            role
          );
          state.generatedInviteCode = code;
          render();
          showToast('Kod jemputan dijana');
        }
        break;
      case 'toggle-member-role':
        if (state.activeWorkspace) {
          const tUid = target.getAttribute('data-uid');
          const nextRole = target.getAttribute('data-next-role');
          await repo.updateMemberRole(state.activeWorkspace, tUid, nextRole);
          showToast('Peranan ahli dikemas kini');
        }
        break;
      case 'remove-member':
        if (state.user && state.activeWorkspace) {
          const tUid = target.getAttribute('data-uid');
          await repo.removeTeamMember(state.user.uid, state.activeWorkspace, tUid);
          showToast('Ahli dikeluarkan');
        }
        break;
      case 'sync-team-access':
        if (state.user && state.activeWorkspace) {
          await repo.syncMembersToTransactions(state.user.uid, state.activeWorkspace);
          showToast('Akses rekod berjaya disegerakkan');
        }
        break;
      case 'reverify-device':
        if (state.user && state.activeWorkspace) {
          await repo.registerCurrentDevice(state.user.uid, state.activeWorkspace.id);
          showToast('Pelayar & lesen disahkan semula');
        }
        break;
      case 'remove-device':
        if (state.activeWorkspace) {
          await repo.removeRegisteredDevice(state.activeWorkspace.id, target.getAttribute('data-id'));
          showToast('Peranti dipadam');
        }
        break;
      case 'load-sample-data':
        if (state.user && state.activeWorkspace) {
          await repo.loadSampleTransactions(state.user.uid, state.activeWorkspace);
          state.modal = null;
          showToast('10 rekod contoh 2026 dimuatkan');
          render();
        }
        break;
      case 'copy-text':
        await copyText(target.getAttribute('data-text') || '', 'Disalin ke papan klip');
        break;
      case 'export-pdf': {
        const ws = state.activeWorkspace;
        const stats = computeStatements(
          state.transactions,
          ws.beginningBalances,
          ws.closingStockByYear,
          state.selectedYear,
          state.language
        );
        printStatementsPdf(ws, stats, state.language);
        break;
      }
      case 'export-csv': {
        const ws = state.activeWorkspace;
        const stats = computeStatements(
          state.transactions,
          ws.beginningBalances,
          ws.closingStockByYear,
          state.selectedYear,
          state.language
        );
        const csv = buildCsvReport(ws, state.transactions, stats, state.language);
        downloadTextFile(`BukuKira_${state.selectedYear}.csv`, csv, 'text/csv;charset=utf-8');
        showToast('CSV berjaya dimuat turun');
        break;
      }
      case 'backup-json': {
        const ws = state.activeWorkspace;
        const json = buildJsonBackup(ws, state.transactions);
        downloadTextFile(`BukuKira_Backup_${todayIso()}.json`, json, 'application/json');
        showToast('Sandaran JSON berjaya dimuat turun');
        break;
      }
    }
  } catch (err) {
    showToast(err?.message || 'Ralat operasi berlaku.');
  }
});

document.addEventListener('input', (e) => {
  if (e.target.id === 'records-search-input') {
    state.recordsSearch = e.target.value;
    const cursorPos = e.target.selectionStart;
    render();
    const restored = document.getElementById('records-search-input');
    if (restored) {
      restored.focus();
      if (cursorPos != null) restored.setSelectionRange(cursorPos, cursorPos);
    }
  }
});

document.addEventListener('change', async (e) => {
  if (e.target.id === 'restore-json-file') {
    const file = e.target.files?.[0];
    if (!file || !state.user || !state.activeWorkspace) return;
    try {
      const text = await file.text();
      const payload = parseJsonBackup(
        text,
        state.activeWorkspace.id,
        state.user.uid,
        state.activeWorkspace.memberUids
      );
      const count = await repo.restoreBackup(state.user.uid, state.activeWorkspace, payload);
      showToast(`Berjaya memulihkan ${count} transaksi`);
    } catch (err) {
      showToast(err?.message || 'Fail JSON tidak sah.');
    } finally {
      e.target.value = '';
    }
  }
});

document.addEventListener('submit', async (e) => {
  e.preventDefault();
  try {
    if (e.target.id === 'onboard-create-form') {
      await repo.createBusinessWorkspace(state.user.uid, {
        businessName: document.getElementById('ob-biz-name').value,
        businessRegNo: document.getElementById('ob-reg-no').value,
        businessType: document.getElementById('ob-biz-type').value,
        businessAddress: '',
        ownerName: document.getElementById('ob-owner-name').value,
        currency: document.getElementById('ob-currency').value,
        financialYear: document.getElementById('ob-year').value,
        loadSampleData: document.getElementById('ob-load-sample').checked
      });
      state.forceOnboarding = false;
      state.errorMessage = null;
      showToast('Ruang kerja baharu dicipta!');
    } else if (e.target.id === 'onboard-invite-form') {
      await repo.redeemInviteCode(state.user.uid, document.getElementById('ob-invite-code').value);
      state.forceOnboarding = false;
      state.errorMessage = null;
      showToast('Berjaya menyertai ruang kerja!');
    } else if (e.target.id === 'onboard-activate-form') {
      await repo.verifyAndActivateBusinessAccess(
        state.user.uid,
        document.getElementById('ob-act-biz-id').value,
        document.getElementById('ob-act-key').value
      );
      state.forceOnboarding = false;
      state.errorMessage = null;
      showToast('Lesen & pelayar disahkan!');
    } else if (e.target.id === 'gate-activate-form') {
      await repo.verifyAndActivateBusinessAccess(
        state.user.uid,
        document.getElementById('gate-biz-id').value,
        document.getElementById('gate-licence-key').value
      );
      state.errorMessage = null;
      showToast('Pelayar diaktifkan!');
    } else if (e.target.id === 'tx-form') {
      const m = state.modal;
      const isDisposal = m.categoryId === 'asset_disposal';
      const disposalCost = isDisposal
        ? Number.parseFloat(document.getElementById('tx-disposal-cost').value)
        : null;
      await repo.createTransaction(state.user.uid, state.activeWorkspace, {
        date: document.getElementById('tx-date').value,
        type: m.txType,
        categoryId: m.categoryId,
        method: m.method,
        amount: Number.parseFloat(document.getElementById('tx-amount').value),
        note: document.getElementById('tx-note').value,
        disposalAssetType: isDisposal ? m.disposalAssetType : null,
        disposalAssetCost: disposalCost
      });
      state.modal = null;
      render();
      showToast('Transaksi disimpan');
    } else if (e.target.id === 'credit-form') {
      const m = state.modal;
      const categoryId =
        m.txType === 'CREDIT_SALE'
          ? 'credit_sale'
          : m.txType === 'CREDIT_NOTE'
            ? 'sales_return_refund'
            : m.txType === 'DEBIT_NOTE'
              ? 'purchase_return_refund'
              : m.purchaseOptId || 'credit_purchase';
      await repo.createTransaction(state.user.uid, state.activeWorkspace, {
        date: document.getElementById('cr-date').value,
        type: m.txType,
        categoryId,
        method: 'CREDIT',
        amount: Number.parseFloat(document.getElementById('cr-amount').value),
        partyName: document.getElementById('cr-party').value,
        invoiceNo: document.getElementById('cr-invoice').value,
        note: document.getElementById('cr-note').value
      });
      state.modal = null;
      render();
      showToast('Rekod kredit disimpan');
    } else if (e.target.id === 'closing-stock-form') {
      const amt = Number.parseFloat(document.getElementById('cs-amount').value);
      await repo.updateClosingStock(
        state.activeWorkspace.id,
        state.activeWorkspace.closingStockByYear,
        state.selectedYear,
        amt
      );
      state.modal = null;
      render();
      showToast('Stok akhir dikemas kini');
    } else if (e.target.id === 'bb-form') {
      const fd = new FormData(e.target);
      const balances = {};
      for (const [k, v] of fd.entries()) {
        balances[k] = Number.parseFloat(v) || 0;
      }
      await repo.updateBeginningBalances(state.activeWorkspace.id, balances);
      state.modal = null;
      render();
      showToast('Baki awal dikemas kini');
    } else if (e.target.id === 'settings-form') {
      await repo.updateBusinessSettings(state.activeWorkspace.id, {
        businessName: document.getElementById('set-name').value,
        businessRegNo: document.getElementById('set-reg').value,
        businessType: document.getElementById('set-type').value,
        businessAddress: document.getElementById('set-addr').value,
        ownerName: document.getElementById('set-owner').value,
        currency: document.getElementById('set-currency').value,
        financialYear: document.getElementById('set-year').value
      });
      if (currentUserRole() === 'OWNER') {
        await repo.upsertOwnerPrivateInfo(state.user.uid, state.activeWorkspace.id, {
          ownerIdNumber: document.getElementById('set-priv-ic').value,
          ownerPhone: document.getElementById('set-priv-phone').value,
          ownerEmail: document.getElementById('set-priv-email').value
        });
      }
      state.modal = null;
      render();
      showToast('Tetapan perniagaan disimpan');
    }
  } catch (err) {
    showToast(err?.message || 'Gagal menyimpan data.');
  }
});

window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault();
  state.deferredInstallPrompt = e;
  render();
});

window.addEventListener('appinstalled', () => {
  state.deferredInstallPrompt = null;
  showToast('BukuKira berjaya dipasang!');
  render();
});

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('./sw.js').catch((err) => {
      console.warn('Service worker registration failed:', err?.message || err);
    });
  });
}

bootApp();

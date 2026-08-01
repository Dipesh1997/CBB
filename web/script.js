const CLIENT_ID = '812006416646-cd28a14enlpg87ktbeim0l02m6f965q9.apps.googleusercontent.com';
const SCOPES = 'https://www.googleapis.com/auth/spreadsheets https://www.googleapis.com/auth/drive.readonly https://www.googleapis.com/auth/drive.file';

let tokenClient;
let gapiInited = false;
let gisInited = false;
let databaseId = '1tTnbqhjkKLSvQxm3rI-rHCue_oRhWIjgzgZQsySuR58'; // Hardcoded Spreadsheet ID
let currentModalType = null;
let currentEditId = null;
let currentParentId = null;
let activeCustomerId = null;

let appData = { customers: [], transactions: [] };

/* Business Tips Carousel */
const LEDGER_TIPS = [
    "Attach photo receipts for all transactions over ₹1,000 to keep digital proof and avoid balance disputes.",
    "Send monthly PDF statements to customers to encourage timely payment settlements.",
    "Flag defaulting or non-responsive customer accounts as 'Bad Debt' to keep active balance metrics clean.",
    "Use 'Record Part Payment' directly from customer ledgers to keep track of partial settlements against specific bills.",
    "Ensure customer phone numbers are accurate so bills can be easily shared via WhatsApp or SMS."
];
let currentTipIndex = 0;

function renderCurrentTip() {
    const el = document.getElementById('tip-content-text');
    if (el) el.textContent = LEDGER_TIPS[currentTipIndex];
}
function nextTip() { currentTipIndex = (currentTipIndex + 1) % LEDGER_TIPS.length; renderCurrentTip(); }
function prevTip() { currentTipIndex = (currentTipIndex - 1 + LEDGER_TIPS.length) % LEDGER_TIPS.length; renderCurrentTip(); }

/* Toast Notifications */
function showToast(message, type = 'info', title = null) {
    const container = document.getElementById('toast-container');
    if (!container) return;
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    const icons = { success: 'check_circle', warning: 'warning', info: 'lightbulb', error: 'error' };
    const defaultTitles = { success: 'Success', warning: 'Warning Alert', info: 'Ledger Tip', error: 'Action Failed' };
    const icon = icons[type] || 'info';
    const toastTitle = title || defaultTitles[type] || 'Notification';
    
    toast.innerHTML = `
        <span class="material-icons toast-icon">${icon}</span>
        <div class="toast-content">
            <div class="toast-title">${toastTitle}</div>
            <div class="toast-message">${message}</div>
        </div>
        <button class="toast-close" onclick="closeToast(this.parentElement)">&times;</button>
    `;
    container.appendChild(toast);
    setTimeout(() => { closeToast(toast); }, 5000);
}

function closeToast(toast) {
    if (!toast || toast.dataset.closing) return;
    toast.dataset.closing = 'true';
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(50px)';
    setTimeout(() => { if (toast.parentNode) toast.parentNode.removeChild(toast); }, 300);
}

let confirmActionCallback = null;
function showConfirmModal(title, message, onConfirm) {
    document.getElementById('confirm-modal-title').textContent = title;
    document.getElementById('confirm-modal-message').textContent = message;
    confirmActionCallback = onConfirm;
    document.getElementById('confirm-action-btn').onclick = async () => {
        hideConfirmModal();
        if (confirmActionCallback) await confirmActionCallback();
    };
    document.getElementById('confirm-modal-overlay').style.display = 'flex';
}
function hideConfirmModal() {
    document.getElementById('confirm-modal-overlay').style.display = 'none';
    confirmActionCallback = null;
}

function generateUUID() { return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => { const r = Math.random() * 16 | 0; return (c == 'x' ? r : (r & 0x3 | 0x8)).toString(16); }); }
function gapiLoaded() { gapi.load('client', initializeGapiClient); }
async function initializeGapiClient() { await gapi.client.init({ discoveryDocs: ['https://sheets.googleapis.com/$discovery/rest?version=v4', 'https://www.googleapis.com/discovery/v1/apis/drive/v3/rest'] }); gapiInited = true; maybeEnableButtons(); }
function gisLoaded() { tokenClient = google.accounts.oauth2.initTokenClient({ client_id: CLIENT_ID, scope: SCOPES, callback: '' }); gisInited = true; maybeEnableButtons(); }
function maybeEnableButtons() { if (gapiInited && gisInited) console.log("System Ready"); }
window.onload = () => { gapiLoaded(); gisLoaded(); };

function handleCredentialResponse(response) {
    const payload = decodeJwt(response.credential);
    document.getElementById('welcome-screen').style.display = 'none';
    document.getElementById('user-profile').style.display = 'block';
    if (document.querySelector('.g_id_signin')) document.querySelector('.g_id_signin').style.display = 'none';
    document.getElementById('sidebar').style.display = 'flex';
    document.getElementById('user-name').textContent = payload.name;
    document.getElementById('user-pic').src = payload.picture;

    tokenClient.callback = async (resp) => {
        if (resp.error !== undefined) throw (resp);
        await loadDashboardData();
    };

    // Auto-request access token without prompt if possible
    tokenClient.requestAccessToken({prompt: ''});
}

function decodeJwt(t) { var b = t.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'); return JSON.parse(decodeURIComponent(atob(b).split('').map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)).join(''))); }
function handleSignOut() { stopAutoSync(); const t = gapi.client.getToken(); if (t) { google.accounts.oauth2.revoke(t.access_token); gapi.client.setToken(''); window.location.reload(); } }
function getErrorMessage(e) { if (e?.result?.error?.message) return e.result.error.message; if (e?.message) return e.message; return "Unknown Error"; }

let syncTimer = null;
let lastDataSignature = '';
let isSilentSyncing = false;
const SYNC_INTERVAL_MS = 5000;

function computeDataSignature(customers, transactions) {
    return JSON.stringify(customers) + '||' + JSON.stringify(transactions);
}

function updateSyncStatus(status, text) {
    const badge = document.getElementById('sync-status-badge');
    const dot = document.getElementById('sync-dot');
    const textEl = document.getElementById('sync-text');
    if (!badge || !dot || !textEl) return;

    badge.style.display = 'flex';
    dot.className = 'sync-dot ' + status;
    textEl.textContent = text;
}

function startAutoSync() {
    stopAutoSync();
    updateSyncStatus('active', 'Live Auto-Sync');
    syncTimer = setInterval(() => {
        if (document.visibilityState === 'visible' && !isSilentSyncing) {
            silentSyncData();
        }
    }, SYNC_INTERVAL_MS);
}

function stopAutoSync() {
    if (syncTimer) {
        clearInterval(syncTimer);
        syncTimer = null;
    }
}

async function triggerManualSync() {
    if (isSilentSyncing) return;
    updateSyncStatus('syncing', 'Syncing...');
    await silentSyncData(true);
}

async function silentSyncData(isManual = false) {
    if (!gapi.client || !gapi.client.sheets || !databaseId) return;
    isSilentSyncing = true;
    try {
        const data = await gapi.client.sheets.spreadsheets.values.batchGet({
            spreadsheetId: databaseId,
            ranges: ['Customers!A2:I', 'Transactions!A2:M']
        });

        const newCustomers = data.result.valueRanges[0].values || [];
        const newTransactions = data.result.valueRanges[1].values || [];
        const newSignature = computeDataSignature(newCustomers, newTransactions);

        if (lastDataSignature && newSignature !== lastDataSignature) {
            appData.customers = newCustomers;
            appData.transactions = newTransactions;
            renderAll();
            if (activeCustomerId) showCustomerLedger(activeCustomerId);
            showToast("Remote changes synced across devices", "info", "Live Auto-Sync");
            const nowTime = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
            updateSyncStatus('active', `Synced (${nowTime})`);
        } else if (isManual) {
            showToast("Data is up to date", "success", "Live Auto-Sync");
            updateSyncStatus('active', 'Live Auto-Sync');
        } else {
            updateSyncStatus('active', 'Live Auto-Sync');
        }
        lastDataSignature = newSignature;
    } catch (err) {
        console.warn("Silent sync warning:", err);
        updateSyncStatus('error', 'Sync Paused');
    } finally {
        isSilentSyncing = false;
    }
}

document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') {
        updateSyncStatus('active', 'Resuming Sync...');
        silentSyncData();
        startAutoSync();
    } else {
        stopAutoSync();
        updateSyncStatus('paused', 'Sync Paused (Background)');
    }
});

async function loadDashboardData() {
    showLoader(true);
    try {
        const data = await gapi.client.sheets.spreadsheets.values.batchGet({
            spreadsheetId: databaseId,
            ranges: ['Customers!A2:I', 'Transactions!A2:M']
        });

        appData.customers = data.result.valueRanges[0].values || [];
        appData.transactions = data.result.valueRanges[1].values || [];
        lastDataSignature = computeDataSignature(appData.customers, appData.transactions);

        renderAll();
        if (activeCustomerId) showCustomerLedger(activeCustomerId);
        else showSection('home');

        startAutoSync();
    } catch (err) {
        console.error(err);
        if (err.status === 404) {
            showToast("Database not found! Ensure the spreadsheet ID is correct and you have access.", "error");
        } else {
            showToast("Error loading data: " + getErrorMessage(err), "error");
        }
    }
    showLoader(false);
}

function showSection(id, param = null) {
    document.querySelectorAll('.section').forEach(s => s.classList.remove('active'));
    document.querySelectorAll('nav button').forEach(b => b.classList.remove('active'));
    document.getElementById(id).classList.add('active');
    const navId = (id === 'customer-ledger' ? 'nav-home' : 'nav-' + id);
    if (document.getElementById(navId)) document.getElementById(navId).classList.add('active');

    const fab = document.getElementById('global-fab');
    if (id === 'home') { fab.style.display='flex'; document.getElementById('fab-icon').textContent='person_add'; fab.onclick=()=>showModal('customer'); }
    else if (id === 'customer-ledger') { fab.style.display='flex'; document.getElementById('fab-icon').textContent='add'; fab.onclick=()=>showModal('transaction', param); }
    else if (id === 'transactions') { fab.style.display='flex'; document.getElementById('fab-icon').textContent='add'; fab.onclick=()=>showModal('transaction'); }
    else fab.style.display='none';
}

function renderAll() {
    let tr = 0, ta = 0, badDebtCount = 0, highOverdueCount = 0;
    appData.customers.forEach(row => {
        const b = parseFloat(row[4] || 0);
        if (b > 0) tr += b; else ta += Math.abs(b);
        if (row[5] === 'TRUE') badDebtCount++;
        if (b >= 10000) highOverdueCount++;
    });
    document.getElementById('total-receivable').textContent = `₹ ${tr.toLocaleString('en-IN')}`;
    document.getElementById('total-advance').textContent = `₹ ${ta.toLocaleString('en-IN')}`;

    // Render Risk Warning Banner on Home
    const warningBanner = document.getElementById('home-warning-banner');
    if (warningBanner) {
        if (badDebtCount > 0 || highOverdueCount > 0) {
            warningBanner.innerHTML = `
                <div class="alert-box alert-warning">
                    <span class="material-icons">warning</span>
                    <div>
                        <div class="alert-title">Account Risk Alert</div>
                        <div>
                            ${badDebtCount > 0 ? `<b>${badDebtCount}</b> account(s) flagged as Bad Debt. ` : ''}
                            ${highOverdueCount > 0 ? `<b>${highOverdueCount}</b> account(s) have overdue balances exceeding ₹10,000.` : ''}
                        </div>
                    </div>
                </div>`;
        } else {
            warningBanner.innerHTML = '';
        }
    }

    const cbody = document.querySelector('#customers-table tbody'); cbody.innerHTML = '';
    appData.customers.forEach(r => {
        const bal = parseFloat(r[4] || 0), sid = r[8];
        const isBadDebt = r[5] === 'TRUE';
        let statusBadge = '';
        if (isBadDebt) {
            statusBadge = '<span class="badge badge-danger"><span class="material-icons" style="font-size:12px;vertical-align:middle;">warning</span> Bad Debt</span>';
        } else if (bal >= 10000) {
            statusBadge = '<span class="badge badge-warning">High Overdue</span>';
        } else {
            statusBadge = '<span class="badge badge-success">Active</span>';
        }
        const tr = document.createElement('tr');
        tr.innerHTML = `<td><span class="clickable-name" onclick="showCustomerLedger('${sid}')">${r[1]}</span></td><td>${r[2]}</td><td style="color:${bal >= 0 ? '#B71C1C' : '#1B5E20'}; font-weight:700">₹ ${bal.toLocaleString('en-IN')}</td><td>${statusBadge}</td><td style="text-align:right;"><span class="material-icons action-icon" onclick="showModal('customer', '${sid}')">edit</span><span class="material-icons action-icon delete" onclick="deleteItem('Customers', '${sid}')">delete</span></td>`;
        cbody.appendChild(tr);
    });

    const tbody = document.querySelector('#transactions-table tbody'); tbody.innerHTML = '';
    [...appData.transactions].sort((a, b) => parseInt(b[4]) - parseInt(a[4])).slice(0, 50).forEach(r => {
        const cust = appData.customers.find(c => c[8] === r[1]);
        const did = (r[8] || '').trim(), tid = r[12];
        const tr = document.createElement('tr');
        tr.className = 'clickable-row';
        tr.onclick = (e) => { if (!e.target.classList.contains('material-icons') && !e.target.classList.contains('thumbnail')) showTransactionDetails(tid); };

        const thumbUrl = did ? `https://drive.google.com/thumbnail?id=${did}&sz=w200` : '';

        tr.innerHTML = `<td>${new Date(parseInt(r[4])).toLocaleDateString()}</td><td>${cust ? cust[1] : 'Unknown'}</td><td style="color:${r[3] === 'DEBIT' ? 'red' : 'green'}">₹ ${parseFloat(r[2]).toLocaleString('en-IN')}</td><td>${r[3]}</td><td>${r[5] || ''}</td><td style="text-align:right;"><span class="material-icons action-icon" onclick="showModal('transaction', '${tid}')">edit</span><span class="material-icons action-icon" onclick="shareTransaction('${tid}')">share</span>${did ? `<img src="${thumbUrl}" class="thumbnail" onclick="event.stopPropagation(); viewFullscreen('${did}')" alt="Receipt">` : ''}<span class="material-icons action-icon delete" onclick="deleteItem('Transactions', '${tid}')">delete</span></td>`;
        tbody.appendChild(tr);
    });
}

function showCustomerLedger(sid) {
    activeCustomerId = sid;
    const cust = appData.customers.find(c => c[8]?.trim() === sid.trim());
    if (!cust) return;
    showSection('customer-ledger', sid);
    document.getElementById('ledger-title').textContent = `${cust[1]}'s Ledger`;
    const bal = parseFloat(cust[4] || 0);
    const isBadDebt = cust[5] === 'TRUE';

    // Render Ledger Warning Box
    const warningBox = document.getElementById('ledger-warning-box');
    if (warningBox) {
        if (isBadDebt) {
            warningBox.innerHTML = `
                <div class="alert-box alert-danger">
                    <span class="material-icons">error_outline</span>
                    <div>
                        <div class="alert-title">Bad Debt Account Warning</div>
                        <div>This customer has been flagged for Bad Debt default risk. Exercise caution before extending additional credit.</div>
                    </div>
                </div>`;
        } else if (bal >= 10000) {
            warningBox.innerHTML = `
                <div class="alert-box alert-warning">
                    <span class="material-icons">warning</span>
                    <div>
                        <div class="alert-title">High Overdue Balance</div>
                        <div>Customer balance is unusually high (₹ ${bal.toLocaleString('en-IN')}). Consider requesting a payment before extending more credit.</div>
                    </div>
                </div>`;
        } else {
            warningBox.innerHTML = '';
        }
    }

    document.getElementById('ledger-customer-info').innerHTML = `<div><span class="label">Phone</span><span class="value">${cust[2]}</span></div><div><span class="label">Address</span><span class="value">${cust[3] || 'N/A'}</span></div><div><span class="label">Balance</span><span class="value" style="color:${bal >= 0 ? '#B71C1C' : '#1B5E20'}">₹ ${bal.toLocaleString('en-IN')}</span></div>`;
    document.getElementById('export-pdf-btn').onclick = () => showExportModal();

    const tbody = document.querySelector('#ledger-table tbody'); tbody.innerHTML = '';
    const txs = appData.transactions.filter(t => t[1]?.trim() === sid.trim()).sort((a, b) => parseInt(b[4]) - parseInt(a[4]));
    if (txs.length === 0) { tbody.innerHTML = '<tr><td colspan="5" style="text-align:center; padding:40px; color:var(--outline);">No transactions found.</td></tr>'; return; }
    txs.forEach(r => {
        const did = (r[8] || '').trim(), tid = r[12];
        const tr = document.createElement('tr');
        tr.className = 'clickable-row';
        tr.onclick = (e) => { if (!e.target.classList.contains('material-icons') && !e.target.classList.contains('thumbnail')) showTransactionDetails(tid); };

        const thumbUrl = did ? `https://drive.google.com/thumbnail?id=${did}&sz=w200` : '';

        tr.innerHTML = `<td>${new Date(parseInt(r[4])).toLocaleDateString()}</td><td>${r[3]}</td><td style="color:${r[3] === 'DEBIT' ? 'red' : 'green'}">₹ ${parseFloat(r[2]).toLocaleString('en-IN')}</td><td>${r[5] || ''}</td><td style="text-align:right;">${r[3] === 'DEBIT' ? `<span class="material-icons action-icon pay" title="Record Payment" onclick="event.stopPropagation(); recordPartPayment('${tid}')">payments</span>` : ''}<span class="material-icons action-icon" onclick="event.stopPropagation(); showModal('transaction', '${tid}')">edit</span><span class="material-icons action-icon" onclick="event.stopPropagation(); shareTransaction('${tid}')">share</span>${did ? `<img src="${thumbUrl}" class="thumbnail" onclick="event.stopPropagation(); viewFullscreen('${did}')" alt="Receipt">` : ''}<span class="material-icons action-icon delete" onclick="event.stopPropagation(); deleteItem('Transactions', '${tid}')">delete</span></td>`;
        tbody.appendChild(tr);
    });
}

function showModal(type, id = null) {
    currentModalType = type; currentEditId = id;
    const modal = document.getElementById('modal-overlay');
    const fields = document.getElementById('form-fields'); fields.innerHTML = '';
    document.getElementById('image-preview-container').style.display = 'none';
    document.getElementById('confirm-replace-text').style.display = 'none';
    document.getElementById('modal-warning-box').innerHTML = '';

    const data = id && findRecord(type, id) ? findRecord(type, id) : null;
    const now = new Date();
    const dateStr = now.toISOString().split('T')[0];
    const timeStr = now.toTimeString().slice(0, 5);

    if (type === 'customer') {
        document.getElementById('modal-title').textContent = data ? 'Edit Customer' : 'Add Customer';
        document.getElementById('save-btn').textContent = data ? 'Update Customer' : 'Save';
        fields.innerHTML = `
            <div class="form-group">
                <label>Name</label>
                <input type="text" id="cust-name" value="${data ? data[1] : ''}" required>
                <div class="field-tip"><span class="material-icons">lightbulb</span> Full name of the customer for PDF statements</div>
            </div>
            <div class="form-group">
                <label>Phone</label>
                <input type="text" id="cust-phone" value="${data ? data[2] : ''}" required>
                <div class="field-tip"><span class="material-icons">lightbulb</span> Phone number for sharing bill details</div>
            </div>
            <div class="form-group">
                <label>Address</label>
                <input type="text" id="cust-address" value="${data ? data[3] : ''}">
            </div>`;
    } else if (type === 'transaction') {
        document.getElementById('modal-title').textContent = data ? 'Edit Existing Bill' : (currentParentId ? 'Record Part Payment' : 'Add New Transaction');
        document.getElementById('save-btn').textContent = data ? 'Update Bill' : 'Save Transaction';

        let pc = '';
        if (data) pc = data[1];
        else if (currentParentId) { const p = appData.transactions.find(t => t[12] === currentParentId); pc = p ? p[1] : ''; }
        else if (document.getElementById('customer-ledger').classList.contains('active')) pc = activeCustomerId;

        const opts = appData.customers.map(c => `<option value="${c[8]}" ${c[8] === pc ? 'selected' : ''}>${c[1]}</option>`).join('');
        const dVal = data ? new Date(parseInt(data[4])).toISOString().split('T')[0] : dateStr;
        const tVal = data ? new Date(parseInt(data[4])).toTimeString().slice(0, 5) : timeStr;

        fields.innerHTML = `
            <div class="form-group">
                <label>Customer</label>
                <select id="tx-cust" required ${currentParentId ? 'disabled' : ''} onchange="checkTransactionWarnings()">${opts}</select>
            </div>
            <div style="display:flex; gap:12px">
                <div class="form-group" style="flex:1"><label>Date</label><input type="date" id="tx-date" value="${dVal}" required></div>
                <div class="form-group" style="flex:1"><label>Time</label><input type="time" id="tx-time" value="${tVal}" required></div>
            </div>
            <div class="form-group">
                <label>Amount</label>
                <input type="number" id="tx-amount" step="0.01" value="${data ? data[2] : ''}" required oninput="checkTransactionWarnings()">
                <div id="amount-field-warning"></div>
            </div>
            <div class="form-group">
                <label>Type</label>
                <select id="tx-type" ${currentParentId ? 'disabled' : ''} onchange="checkTransactionWarnings()">
                    <option value="DEBIT" ${data && data[3] === 'DEBIT' ? 'selected' : ''}>YOU GAVE (Debit)</option>
                    <option value="CREDIT" ${(data && data[3] === 'CREDIT') || currentParentId ? 'selected' : ''}>YOU GOT (Credit)</option>
                </select>
                <div class="field-tip"><span class="material-icons">lightbulb</span> <b>DEBIT</b> increases customer debt. <b>CREDIT</b> records payment received.</div>
            </div>
            <div class="form-group"><label>Note</label><input type="text" id="tx-note" value="${data ? data[5] : (currentParentId ? 'Part Payment' : '')}"></div>
            <div class="form-group">
                <label>Attach Photo (Optional)</label>
                <input type="file" id="tx-photo" accept="image/*" onchange="previewImage(this)">
                <div class="field-tip"><span class="material-icons">lightbulb</span> Receipt photos uploaded here are stored in your Google Drive.</div>
            </div>`;

        if (data && data[8]) {
            document.getElementById('img-preview').src = `https://drive.google.com/thumbnail?id=${data[8]}&sz=w200`;
            document.getElementById('image-preview-container').style.display = 'block';
        }
        setTimeout(() => checkTransactionWarnings(), 50);
    }
    modal.style.display = 'flex';
}

function checkTransactionWarnings() {
    const custEl = document.getElementById('tx-cust');
    const amtEl = document.getElementById('tx-amount');
    const typeEl = document.getElementById('tx-type');
    const warningBox = document.getElementById('modal-warning-box');
    const amtWarning = document.getElementById('amount-field-warning');
    if (!custEl || !amtEl || !typeEl) return;

    const cid = custEl.value;
    const amt = parseFloat(amtEl.value || 0);
    const type = typeEl.value;
    const cust = appData.customers.find(c => c[8] === cid);

    if (cust && cust[5] === 'TRUE') {
        warningBox.innerHTML = `
            <div class="alert-box alert-danger" style="margin-bottom: 16px;">
                <span class="material-icons">warning</span>
                <div>
                    <div class="alert-title">Bad Debt Customer Warning</div>
                    <div>This customer is marked as Bad Debt! Exercise extreme caution before extending further credit.</div>
                </div>
            </div>`;
    } else {
        warningBox.innerHTML = '';
    }

    if (amtWarning) {
        if (amt >= 50000) {
            amtWarning.innerHTML = `<div class="field-warning"><span class="material-icons">warning</span> High transaction amount (₹ ${amt.toLocaleString('en-IN')}). Please double-check the digits entered.</div>`;
        } else if (type === 'CREDIT' && cust) {
            const bal = parseFloat(cust[4] || 0);
            if (bal > 0 && amt > bal) {
                amtWarning.innerHTML = `<div class="field-tip"><span class="material-icons">info</span> Notice: Payment of ₹${amt.toLocaleString('en-IN')} exceeds pending balance (₹${bal.toLocaleString('en-IN')}). Customer will have an advance credit balance.</div>`;
            } else {
                amtWarning.innerHTML = '';
            }
        } else {
            amtWarning.innerHTML = '';
        }
    }
}

function showTransactionDetails(tid) {
    const tx = appData.transactions.find(t => t[12] === tid); if (!tx) return;
    const cust = appData.customers.find(c => c[8] === tx[1]);
    const linked = appData.transactions.filter(t => t[9] === tid);
    const linkedRows = linked.map(l => `<tr><td>${new Date(parseInt(l[4])).toLocaleDateString()}</td><td>₹ ${l[2]}</td><td>${l[5] || ''}</td></tr>`).join('');
    document.getElementById('details-content').innerHTML = `
        <div class="detail-header"><span class="detail-amount" style="color:${tx[3]==='DEBIT'?'red':'green'}">₹ ${tx[2]}</span><span class="chip">${tx[3]}</span></div>
        <div class="detail-meta"><div><span class="label">Customer</span><span class="value">${cust?cust[1]:'Unknown'}</span></div><div><span class="label">Date</span><span class="value">${new Date(parseInt(tx[4])).toLocaleString()}</span></div></div>
        ${tx[5] ? `<div class="detail-note">"${tx[5]}"</div>` : ''}
        ${tx[8] ? `<img src="https://drive.google.com/thumbnail?id=${tx[8]}&sz=w1000" style="width:100%; border-radius:12px; cursor:pointer;" onclick="viewFullscreen('${tx[8]}')">` : ''}
        ${linked.length > 0 ? `<div class="linked-payments"><h4>Linked Part Payments</h4><table class="mini-table"><thead><tr><th>Date</th><th>Amount</th><th>Note</th></tr></thead><tbody>${linkedRows}</tbody></table></div>` : ''}`;
    document.getElementById('details-edit-btn').onclick = () => { hideDetailsModal(); showModal('transaction', tid); };
    document.getElementById('details-share-btn').onclick = () => shareTransaction(tid);
    document.getElementById('details-modal-overlay').style.display = 'flex';
}

function hideDetailsModal() { document.getElementById('details-modal-overlay').style.display = 'none'; }
function recordPartPayment(billId) { currentParentId = billId; const b = appData.transactions.find(t => t[12] === billId); showModal('transaction', b[1]); }

async function previewImage(i) {
    const f = i.files[0]; if (!f) return;
    const compressed = await compressImage(f, 1200, 0.7);
    const reader = new FileReader(); reader.onload = (e) => {
        const p = document.getElementById('img-preview'); p.src = e.target.result;
        document.getElementById('image-preview-container').style.display = 'block';
        p.onclick = () => { document.getElementById('fs-img').src = e.target.result; document.getElementById('fs-viewer').style.display = 'flex'; };
        const ext = currentEditId ? findRecord('transaction', currentEditId) : null;
        if (ext && ext[8]) document.getElementById('confirm-replace-text').style.display = 'block';
    };
    reader.readAsDataURL(compressed);
}

function viewFullscreenFromPreview() { const s = document.getElementById('img-preview').src; if (s) { document.getElementById('fs-img').src = s; document.getElementById('fs-viewer').style.display = 'flex'; } }

async function handleFormSubmit(e) {
    e.preventDefault(); if (!databaseId) return;
    showLoader(true);
    try {
        if (currentModalType === 'customer') await saveCustomer();
        else if (currentModalType === 'transaction') await saveTransaction();
        await loadDashboardData();
        hideModal(); currentParentId = null;
        showToast("Record saved successfully!", "success");
    } catch (err) { showToast("Error saving record: " + getErrorMessage(err), "error"); }
    showLoader(false);
}

async function saveCustomer() {
    const n = document.getElementById('cust-name').value, p = document.getElementById('cust-phone').value, a = document.getElementById('cust-address').value;
    const sid = currentEditId || generateUUID(), ext = findRecord('customer', currentEditId);
    const row = [ ext ? ext[0] : '0', n, p, a, ext ? ext[4] : '0.0', ext ? ext[5] : 'FALSE', ext ? ext[6] : 'web-user', Date.now().toString(), sid ];
    await updateOrAppendRow('Customers', sid, row, 8);
    await logHistory(`${ext ? 'Updated' : 'Added'} Customer: ${n}`);
}

async function saveTransaction() {
    const cid = document.getElementById('tx-cust').value, amt = document.getElementById('tx-amount').value, type = document.getElementById('tx-type').value, note = document.getElementById('tx-note').value, f = document.getElementById('tx-photo').files[0];
    const ts = new Date(`${document.getElementById('tx-date').value}T${document.getElementById('tx-time').value}`).getTime();
    const ext = findRecord('transaction', currentEditId), sid = ext ? currentEditId : generateUUID();
    let did = ext ? ext[8] : '', prev = ext ? ext[6] : '', view = ext ? ext[7] : '';
    if (f) {
        const comp = await compressImage(f, 1200, 0.7);
        did = await uploadToDrive(comp);
        prev = `=IMAGE("https://drive.google.com/thumbnail?id=${did}")`;
        view = `=HYPERLINK("https://drive.google.com/file/d/${did}/view", "View")`;
    }
    const row = [ ext ? ext[0] : '0', cid, amt, type, ts.toString(), note, prev, view, did, currentParentId || (ext ? ext[9] : ''), ext ? ext[10] : 'web-user', Date.now().toString(), sid ];
    await updateOrAppendRow('Transactions', sid, row, 12);
    await updateCustomerBalance(cid);
    const c = appData.customers.find(x => x[8] === cid);
    await logHistory(`${ext ? 'Updated' : 'Added'} ${type} of ₹${amt} for ${c ? c[1] : 'Unknown'}`);
    return sid;
}

async function logHistory(a) { const r = [ '0', Date.now().toString(), a, 'TRUE', generateUUID() ]; await gapi.client.sheets.spreadsheets.values.append({ spreadsheetId: databaseId, range: 'History!A1', valueInputOption: 'USER_ENTERED', resource: { values: [r] } }); }

async function compressImage(f, mw, q) {
    return new Promise((res) => {
        const r = new FileReader(); r.readAsDataURL(f);
        r.onload = (e) => {
            const img = new Image(); img.src = e.target.result;
            img.onload = () => {
                const can = document.createElement('canvas');
                let w = img.width, h = img.height;
                if (w > mw) { h *= mw / w; w = mw; }
                can.width = w; can.height = h;
                const ctx = can.getContext('2d'); ctx.drawImage(img, 0, 0, w, h);
                can.toBlob((b) => res(b), 'image/jpeg', q);
            };
        };
    });
}

async function imageToBase64(did) {
    if (!did || !did.trim()) return null;
    const cleanDid = did.trim();

    // Method 1: Google Drive API v3 binary media download with OAuth Bearer token
    try {
        const token = gapi.client?.getToken()?.access_token;
        if (token) {
            const resp = await fetch(`https://www.googleapis.com/drive/v3/files/${cleanDid}?alt=media`, {
                headers: { Authorization: 'Bearer ' + token }
            });
            if (resp.ok) {
                const blob = await resp.blob();
                return await blobToBase64(blob);
            }
        }
    } catch (err) {
        console.warn("Drive API alt=media fetch failed:", cleanDid, err);
    }

    // Method 2: Google Drive Thumbnail endpoint via Image Canvas
    try {
        const b64 = await loadImageAsBase64ViaCanvas(`https://drive.google.com/thumbnail?id=${cleanDid}&sz=w800`);
        if (b64) return b64;
    } catch (err) {
        console.warn("Thumbnail canvas fetch failed:", cleanDid, err);
    }

    // Method 3: Direct UserContent URL via Image Canvas
    try {
        const b64 = await loadImageAsBase64ViaCanvas(`https://lh3.googleusercontent.com/d/${cleanDid}=w800`);
        if (b64) return b64;
    } catch (err) {
        console.warn("Direct Google Content fetch failed:", cleanDid, err);
    }

    return null;
}

function blobToBase64(blob) {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onloadend = () => resolve(reader.result);
        reader.onerror = reject;
        reader.readAsDataURL(blob);
    });
}

function loadImageAsBase64ViaCanvas(url) {
    return new Promise((resolve) => {
        const img = new Image();
        img.crossOrigin = 'Anonymous';
        img.src = url;
        img.onload = () => {
            try {
                const canvas = document.createElement('canvas');
                canvas.width = img.naturalWidth || img.width || 400;
                canvas.height = img.naturalHeight || img.height || 400;
                const ctx = canvas.getContext('2d');
                ctx.drawImage(img, 0, 0);
                resolve(canvas.toDataURL('image/jpeg', 0.85));
            } catch (e) { resolve(null); }
        };
        img.onerror = () => resolve(null);
    });
}

async function exportStatement(type) {
    const sid = activeCustomerId;
    const cust = appData.customers.find(c => c[8] === sid);
    if (!cust) {
        showToast("No active customer selected", "error");
        return;
    }

    let txs = appData.transactions.filter(t => t[1] === sid).sort((a, b) => parseInt(a[4]) - parseInt(b[4]));

    if (type === 'range') {
        const startVal = document.getElementById('export-start-date').value;
        const endVal = document.getElementById('export-end-date').value;
        if (!startVal || !endVal) {
            showToast("Please select a valid start and end date", "warning");
            return;
        }
        const start = new Date(startVal).getTime();
        const end = new Date(endVal).getTime() + 86400000;
        if (start > end) {
            showToast("Start date cannot be after End date", "warning");
            return;
        }
        txs = txs.filter(t => parseInt(t[4]) >= start && parseInt(t[4]) <= end);
    }

    if (txs.length === 0) {
        showToast("No transactions found for statement export", "warning");
        return;
    }

    const includeImages = (type === 'full' || type === 'range');
    const txsWithImages = txs.filter(t => (t[8] || '').trim() !== '');

    showLoader(true);
    document.getElementById('loader-text').textContent = (includeImages && txsWithImages.length > 0)
        ? "Generating Statement with Receipt Photos..."
        : "Generating Statement PDF...";

    const progressContainer = document.getElementById('pdf-progress-container');
    const progressBar = document.getElementById('pdf-progress-bar');
    const progressText = document.getElementById('pdf-progress-text');

    if (includeImages && txsWithImages.length > 0) {
        progressContainer.style.display = 'block';
        progressBar.style.width = '0%';
        progressText.textContent = `Processing receipt images (0/${txsWithImages.length})...`;
    } else {
        progressContainer.style.display = 'none';
    }

    try {
        const { jsPDF } = window.jspdf;
        const doc = new jsPDF();

        // Header Title
        doc.setFontSize(22);
        doc.text("Customer Statement", 14, 20);
        doc.setFontSize(10);
        doc.text(`Generated: ${new Date().toLocaleString()}`, 14, 27);
        doc.line(14, 30, 196, 30);

        // Customer Details
        doc.setFontSize(11);
        doc.text(`Customer: ${cust[1]}`, 14, 40);
        doc.text(`Phone: ${cust[2]}`, 14, 46);
        doc.text(`Balance: Rs. ${parseFloat(cust[4] || 0).toLocaleString('en-IN')}`, 14, 52);

        let y = 60;
        let processedImgCount = 0;

        for (let i = 0; i < txs.length; i++) {
            const t = txs[i];
            const linked = appData.transactions.filter(x => x[9] === t[12]);
            const head = [['Date', 'Type', 'Amount', 'Note']];
            const body = [[
                new Date(parseInt(t[4])).toLocaleDateString(),
                t[3],
                `Rs. ${parseFloat(t[2]).toLocaleString('en-IN')}`,
                t[5] || ''
            ]];

            doc.autoTable({
                startY: y,
                head: head,
                body: body,
                theme: 'grid',
                headStyles: { fillColor: [103, 80, 164] }
            });
            y = doc.lastAutoTable.finalY + 5;

            if (linked.length > 0) {
                doc.setFontSize(9);
                doc.text("Part Payments Received:", 20, y);
                y += 5;
                const lRows = linked.map(l => [
                    new Date(parseInt(l[4])).toLocaleDateString(),
                    `Rs. ${parseFloat(l[2]).toLocaleString('en-IN')}`,
                    l[5] || ''
                ]);
                doc.autoTable({
                    startY: y,
                    head: [['Date', 'Amount', 'Note']],
                    body: lRows,
                    theme: 'plain',
                    margin: { left: 20 }
                });
                y = doc.lastAutoTable.finalY + 6;
            }

            // Export receipt images for full or custom date range export
            const driveFileId = (t[8] || '').trim();
            if (includeImages && driveFileId) {
                processedImgCount++;
                const prog = Math.round((processedImgCount / txsWithImages.length) * 100);
                progressBar.style.width = prog + '%';
                progressText.textContent = `Processing receipt image (${processedImgCount}/${txsWithImages.length})...`;

                try {
                    const base64 = await imageToBase64(driveFileId);
                    if (base64) {
                        if (y > 210) {
                            doc.addPage();
                            y = 20;
                        }
                        const imgFormat = base64.includes('image/png') ? 'PNG' : 'JPEG';
                        doc.setFontSize(9);
                        doc.text("Attached Receipt Photo:", 14, y);
                        y += 4;
                        doc.addImage(base64, imgFormat, 14, y, 60, 60);
                        y += 68;
                    }
                } catch (e) {
                    console.error("Image processing error for transaction:", t[12], e);
                }
            }

            if (y > 260) {
                doc.addPage();
                y = 20;
            }
        }

        doc.save(`Statement_${cust[1].replace(/[^a-zA-Z0-9]/g, '_')}.pdf`);
        showToast(`PDF Statement for ${cust[1]} generated with receipt images!`, "success");
    } catch (err) {
        console.error("PDF generation failed:", err);
        showToast("Failed to generate PDF statement: " + getErrorMessage(err), "error");
    } finally {
        hideExportModal();
        showLoader(false);
        progressContainer.style.display = 'none';
    }
}

async function uploadToDrive(f) {
    const m = { name: `Udaari_${Date.now()}_${f.name}`, mimeType: 'image/jpeg' };
    const fd = new FormData();
    fd.append('metadata', new Blob([JSON.stringify(m)], { type: 'application/json' }));
    fd.append('file', f);
    const r = await fetch('https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart', { method: 'POST', headers: { Authorization: 'Bearer ' + gapi.client.getToken().access_token }, body: fd });
    const res = await r.json();
    if (!r.ok) throw new Error(res.error?.message || 'Upload Failed');
    return res.id;
}

async function updateOrAppendRow(s, sid, r, c) {
    const v = (await gapi.client.sheets.spreadsheets.values.get({ spreadsheetId: databaseId, range: `${s}!A:Z` })).result.values;
    let i = -1;
    const targetSid = (sid || '').trim();
    if (v) {
        for (let x = 0; x < v.length; x++) {
            if (v[x].length > c && (v[x][c] || '').trim() === targetSid) {
                i = x + 1; break;
            }
        }
    }
    if (i !== -1) await gapi.client.sheets.spreadsheets.values.update({ spreadsheetId: databaseId, range: `${s}!A${i}`, valueInputOption: 'USER_ENTERED', resource: { values: [r] } });
    else await gapi.client.sheets.spreadsheets.values.append({ spreadsheetId: databaseId, range: `${s}!A1`, valueInputOption: 'USER_ENTERED', resource: { values: [r] } });
}

function deleteItem(s, sid) {
    const label = s === 'Customers' ? 'Customer' : 'Transaction';
    showConfirmModal(
        `Delete ${label} Record`,
        `Are you sure you want to delete this ${label.toLowerCase()} record? It will be archived to Trash and customer balances will be updated.`,
        async () => {
            showLoader(true);
            try {
                const c = { 'Customers': 8, 'Transactions': 12 }[s];
                const v = (await gapi.client.sheets.spreadsheets.values.get({ spreadsheetId: databaseId, range: `${s}!A:Z` })).result.values;
                if (v) { for (let x = 0; x < v.length; x++) { if (v[x][c] === sid) {
                    const rd = v[x];
                    await gapi.client.sheets.spreadsheets.values.append({ spreadsheetId: databaseId, range: 'Trash!A1', valueInputOption: 'USER_ENTERED', resource: { values: [[`Deleted from Web: ${s}`, s.toLowerCase(), sid, Date.now().toString(), JSON.stringify(rd)]] } });
                    await gapi.client.sheets.spreadsheets.values.clear({ spreadsheetId: databaseId, range: `${s}!A${x+1}:Z${x+1}` });
                    if (s === 'Transactions') await updateCustomerBalance(rd[1]);
                    await logHistory(`Deleted ${s} record: ${sid}`);
                    break;
                } } }
                await loadDashboardData();
                showToast(`${label} record deleted.`, "info");
            } catch (err) { showToast("Delete failed: " + getErrorMessage(err), "error"); }
            showLoader(false);
        }
    );
}

async function updateCustomerBalance(cid) {
    const txs = (await gapi.client.sheets.spreadsheets.values.get({ spreadsheetId: databaseId, range: 'Transactions!A2:M' })).result.values || [];
    let bal = 0; txs.forEach(t => { if (t[1]?.trim() === cid.trim()) { const a = parseFloat(t[2] || 0); if (t[3] === 'DEBIT') bal += a; else bal -= a; } });
    const custs = (await gapi.client.sheets.spreadsheets.values.get({ spreadsheetId: databaseId, range: 'Customers!A:I' })).result.values;
    for (let x = 0; x < custs.length; x++) { if (custs[x][8]?.trim() === cid.trim()) {
        const r = [...custs[x]]; r[4] = bal.toString(); r[7] = Date.now().toString();
        await gapi.client.sheets.spreadsheets.values.update({ spreadsheetId: databaseId, range: `Customers!A${x+1}`, valueInputOption: 'USER_ENTERED', resource: { values: [r] } });
        break;
    } }
}

function shareTransaction(sid) {
    const tx = appData.transactions.find(t => t[12] === sid), c = appData.customers.find(x => x[8] === tx[1]);
    const linked = appData.transactions.filter(t => t[9] === sid);
    const received = linked.reduce((acc, l) => acc + parseFloat(l[2] || 0), 0);
    const remaining = parseFloat(tx[2]) - received;

    let txt = `📜 Udaari Ledger Bill\nCustomer: ${c[1]}\nTotal Bill: ₹${tx[2]}\nDate: ${new Date(parseInt(tx[4])).toLocaleDateString()}\nNote: ${tx[5] || 'N/A'}\nReceived: ₹${received}\nRemaining: ₹${remaining.toFixed(2)}`;
    if (navigator.share) navigator.share({ title: 'Bill', text: txt }); else { navigator.clipboard.writeText(txt); showToast("Bill copied to clipboard!", "success"); }
}

function viewFullscreen(did) {
    document.getElementById('fs-img').src = `https://drive.google.com/thumbnail?id=${did}&sz=w2000`;
    document.getElementById('fs-viewer').style.display = 'flex';
}

function showLoader(s) { document.getElementById('loader').style.display = s ? 'flex' : 'none'; }
function hideModal() { document.getElementById('modal-overlay').style.display = 'none'; }
function findRecord(t, id) { if (t === 'customer') return appData.customers.find(c => c[8] === id); if (t === 'transaction') return appData.transactions.find(x => x[12] === id); return null; }
function showExportModal() { document.getElementById('export-modal-overlay').style.display = 'flex'; }
function hideExportModal() { document.getElementById('export-modal-overlay').style.display = 'none'; }

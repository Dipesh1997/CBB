const CLIENT_ID = '812006416646-cd28a14enlpg87ktbeim0l02m6f965q9.apps.googleusercontent.com';
const SCOPES = 'https://www.googleapis.com/auth/spreadsheets https://www.googleapis.com/auth/drive.metadata.readonly https://www.googleapis.com/auth/drive.file';

let tokenClient;
let gapiInited = false;
let gisInited = false;
let databaseId = null;
let currentModalType = null;
let currentEditId = null; // Can be ServerID of record or CustomerServerID if adding new bill for specific customer

let appData = {
    customers: [],
    transactions: []
};

// UUID Helper
function generateUUID() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        var r = Math.random() * 16 | 0, v = c == 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
}

// Initialize GAPI and GIS
function gapiLoaded() { gapi.load('client', initializeGapiClient); }
async function initializeGapiClient() {
    await gapi.client.init({ discoveryDocs: ['https://sheets.googleapis.com/$discovery/rest?version=v4', 'https://www.googleapis.com/discovery/v1/apis/drive/v3/rest'] });
    gapiInited = true;
    maybeEnableButtons();
}
function gisLoaded() {
    tokenClient = google.accounts.oauth2.initTokenClient({ client_id: CLIENT_ID, scope: SCOPES, callback: '' });
    gisInited = true;
    maybeEnableButtons();
}
function maybeEnableButtons() { if (gapiInited && gisInited) console.log("System Ready"); }

window.onload = () => { gapiLoaded(); gisLoaded(); };

// Auth Handlers
function handleCredentialResponse(response) {
    const payload = decodeJwt(response.credential);
    document.getElementById('welcome-screen').style.display = 'none';
    document.getElementById('user-profile').style.display = 'block';
    document.getElementById('sidebar').style.display = 'flex';
    document.getElementById('user-name').textContent = payload.name;
    document.getElementById('user-pic').src = payload.picture;

    tokenClient.callback = async (resp) => {
        if (resp.error !== undefined) throw (resp);
        await loadDashboardData();
    };

    if (gapi.client.getToken() === null) tokenClient.requestAccessToken({prompt: 'consent'});
    else tokenClient.requestAccessToken({prompt: ''});
}

function decodeJwt(token) {
    var base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    var jsonPayload = decodeURIComponent(atob(base64).split('').map(function(c) {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
    }).join(''));
    return JSON.parse(jsonPayload);
}

function handleSignOut() {
    const token = gapi.client.getToken();
    if (token !== null) {
        google.accounts.oauth2.revoke(token.access_token);
        gapi.client.setToken('');
        window.location.reload();
    }
}

function getErrorMessage(err) {
    if (err && err.result && err.result.error && err.result.error.message) return err.result.error.message;
    if (err && err.message) return err.message;
    return "Unknown Error";
}

// Data Fetching
async function loadDashboardData() {
    showLoader(true);
    try {
        const response = await gapi.client.drive.files.list({
            q: "name = 'Udaari_Database' and mimeType = 'application/vnd.google-apps.spreadsheet' and trashed = false",
            fields: 'files(id, name)',
        });
        if (response.result.files.length === 0) {
            alert("Spreadsheet 'Udaari_Database' not found. Please sync from Android app first.");
            showLoader(false); return;
        }
        databaseId = response.result.files[0].id;
        const ranges = ['Customers!A2:I', 'Transactions!A2:M'];
        const dataResponse = await gapi.client.sheets.spreadsheets.values.batchGet({ spreadsheetId: databaseId, ranges: ranges });
        const valueRanges = dataResponse.result.valueRanges;
        appData.customers = valueRanges[0].values || [];
        appData.transactions = valueRanges[1].values || [];

        renderAll();
        if (!document.getElementById('customer-ledger').classList.contains('active')) showSection('home');
    } catch (err) { alert("Error fetching data: " + getErrorMessage(err)); }
    showLoader(false);
}

// UI Rendering
function showSection(sectionId, param = null) {
    document.querySelectorAll('.section').forEach(s => s.classList.remove('active'));
    document.querySelectorAll('nav button').forEach(b => b.classList.remove('active'));
    const target = document.getElementById(sectionId);
    if (target) target.classList.add('active');
    const navBtn = document.getElementById('nav-' + (sectionId === 'customer-ledger' ? 'home' : sectionId));
    if (navBtn) navBtn.classList.add('active');

    const fab = document.getElementById('global-fab');
    const fabIcon = document.getElementById('fab-icon');
    if (sectionId === 'home') {
        fab.style.display = 'flex'; fabIcon.textContent = 'person_add';
        fab.onclick = () => showModal('customer');
    } else if (sectionId === 'customer-ledger') {
        fab.style.display = 'flex'; fabIcon.textContent = 'add';
        fab.onclick = () => showModal('transaction', param);
    } else if (sectionId === 'transactions') {
        fab.style.display = 'flex'; fabIcon.textContent = 'add';
        fab.onclick = () => showModal('transaction');
    } else { fab.style.display = 'none'; }
}

function renderAll() {
    renderOverview();
    renderCustomers();
    renderTransactions();
}

function renderOverview() {
    let totalRec = 0, totalAdv = 0;
    appData.customers.forEach(row => {
        const bal = parseFloat(row[4] || 0);
        if (bal > 0) totalRec += bal; else totalAdv += Math.abs(bal);
    });
    document.getElementById('total-receivable').textContent = `₹ ${totalRec.toLocaleString('en-IN')}`;
    document.getElementById('total-advance').textContent = `₹ ${totalAdv.toLocaleString('en-IN')}`;
}

function renderCustomers(filter = '') {
    const tbody = document.querySelector('#customers-table tbody');
    tbody.innerHTML = '';
    appData.customers.filter(row => row[1].toLowerCase().includes(filter.toLowerCase())).forEach(row => {
        const bal = parseFloat(row[4] || 0);
        const sid = row[8];
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><span class="clickable-name" onclick="showCustomerLedger('${sid}')">${row[1]}</span></td>
            <td>${row[2]}</td>
            <td style="color: ${bal >= 0 ? '#B71C1C' : '#1B5E20'}; font-weight: 700">₹ ${bal}</td>
            <td>${row[5] === 'TRUE' ? '<span style="color:red">Bad Debt</span>' : 'Active'}</td>
            <td style="text-align: right;">
                <span class="material-icons action-icon" onclick="showModal('customer', '${sid}')">edit</span>
                <span class="material-icons action-icon delete" onclick="deleteItem('Customers', '${sid}')">delete</span>
            </td>`;
        tbody.appendChild(tr);
    });
}

function filterCustomers() { renderCustomers(document.getElementById('customer-search').value); }

function showCustomerLedger(sid) {
    const customer = appData.customers.find(c => c[8] && c[8].trim() === sid.trim());
    if (!customer) return;
    showSection('customer-ledger', sid);
    document.getElementById('ledger-title').textContent = `${customer[1]}'s Ledger`;
    const info = document.getElementById('ledger-customer-info');
    const bal = parseFloat(customer[4] || 0);
    info.innerHTML = `
        <div><span class="label">Phone</span><span class="value">${customer[2]}</span></div>
        <div><span class="label">Address</span><span class="value">${customer[3] || 'N/A'}</span></div>
        <div><span class="label">Balance</span><span class="value" style="color: ${bal >= 0 ? '#B71C1C' : '#1B5E20'}">₹ ${bal}</span></div>
    `;
    document.getElementById('export-pdf-btn').onclick = () => exportCustomerPdf(sid);
    renderCustomerLedger(sid);
}

function renderCustomerLedger(sid) {
    const tbody = document.querySelector('#ledger-table tbody');
    tbody.innerHTML = '';
    const targetId = sid.trim();
    const txs = appData.transactions.filter(tx => tx[1] && tx[1].trim() === targetId).sort((a, b) => parseInt(b[4]) - parseInt(a[4]));
    if (txs.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" style="text-align:center; padding: 40px; color: var(--outline);">No transactions found.</td></tr>';
        return;
    }
    txs.forEach(row => {
        const tr = document.createElement('tr');
        const driveId = (row[8] || '').trim();
        const tid = row[12];
        tr.innerHTML = `
            <td>${new Date(parseInt(row[4])).toLocaleDateString()}</td>
            <td>${row[3]}</td>
            <td style="color: ${row[3] === 'DEBIT' ? 'red' : 'green'}">₹ ${row[2]}</td>
            <td>${row[5] || ''}</td>
            <td style="text-align: right;">
                <span class="material-icons action-icon" onclick="showModal('transaction', '${tid}')">edit</span>
                <span class="material-icons action-icon" onclick="shareTransaction('${tid}')">share</span>
                ${driveId ? `<img src="https://drive.google.com/thumbnail?id=${driveId}" class="thumbnail" onclick="viewFullscreen('${driveId}')">` : ''}
                <span class="material-icons action-icon delete" onclick="deleteItem('Transactions', '${tid}')">delete</span>
            </td>`;
        tbody.appendChild(tr);
    });
}

function renderTransactions() {
    const tbody = document.querySelector('#transactions-table tbody');
    tbody.innerHTML = '';
    const sorted = [...appData.transactions].sort((a, b) => parseInt(b[4]) - parseInt(a[4]));
    sorted.slice(0, 50).forEach(row => {
        const cust = appData.customers.find(c => c[8] === row[1]);
        const driveId = (row[8] || '').trim();
        const tid = row[12];
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>${new Date(parseInt(row[4])).toLocaleDateString()}</td>
            <td>${cust ? cust[1] : 'Unknown'}</td>
            <td style="color: ${row[3] === 'DEBIT' ? 'red' : 'green'}">₹ ${row[2]}</td>
            <td>${row[3]}</td>
            <td>${row[5] || ''}</td>
            <td style="text-align: right;">
                <span class="material-icons action-icon" onclick="showModal('transaction', '${tid}')">edit</span>
                <span class="material-icons action-icon" onclick="shareTransaction('${tid}')">share</span>
                ${driveId ? `<img src="https://drive.google.com/thumbnail?id=${driveId}" class="thumbnail" onclick="viewFullscreen('${driveId}')">` : ''}
                <span class="material-icons action-icon delete" onclick="deleteItem('Transactions', '${tid}')">delete</span>
            </td>`;
        tbody.appendChild(tr);
    });
}

// Modal Logic
function showModal(type, id = null) {
    currentModalType = type; currentEditId = id;
    const modal = document.getElementById('modal-overlay');
    const title = document.getElementById('modal-title');
    const fields = document.getElementById('form-fields');
    fields.innerHTML = '';
    document.getElementById('image-preview-container').style.display = 'none';
    document.getElementById('confirm-replace-text').style.display = 'none';

    const data = id ? findRecord(type, id) : null;

    if (type === 'customer') {
        title.textContent = data ? 'Edit Customer' : 'Add Customer';
        fields.innerHTML = `
            <div class="form-group"><label>Name</label><input type="text" id="cust-name" value="${data ? data[1] : ''}" required></div>
            <div class="form-group"><label>Phone</label><input type="text" id="cust-phone" value="${data ? data[2] : ''}" required></div>
            <div class="form-group"><label>Address</label><input type="text" id="cust-address" value="${data ? data[3] : ''}"></div>`;
    } else if (type === 'transaction') {
        title.textContent = data ? 'Edit Transaction' : 'Add Transaction';
        let preselectedCust = data ? data[1] : (document.getElementById('customer-ledger').classList.contains('active') ? id : '');
        const options = appData.customers.map(c => `<option value="${c[8]}" ${c[8] === preselectedCust ? 'selected' : ''}>${c[1]}</option>`).join('');
        fields.innerHTML = `
            <div class="form-group"><label>Customer</label><select id="tx-cust" required>${options}</select></div>
            <div class="form-group"><label>Amount</label><input type="number" id="tx-amount" step="0.01" value="${data ? data[2] : ''}" required></div>
            <div class="form-group"><label>Type</label><select id="tx-type">
                <option value="DEBIT" ${data && data[3] === 'DEBIT' ? 'selected' : ''}>YOU GAVE (Debit)</option>
                <option value="CREDIT" ${data && data[3] === 'CREDIT' ? 'selected' : ''}>YOU GOT (Credit)</option>
            </select></div>
            <div class="form-group"><label>Note</label><input type="text" id="tx-note" value="${data ? data[5] : ''}"></div>
            <div class="form-group"><label>Attach Photo (Optional)</label><input type="file" id="tx-photo" accept="image/*" onchange="previewImage(this)"></div>`;
        if (data && data[8]) {
            document.getElementById('img-preview').src = `https://drive.google.com/thumbnail?id=${data[8]}`;
            document.getElementById('image-preview-container').style.display = 'block';
        }
    }
    modal.style.display = 'flex';
}

function previewImage(input) {
    const reader = new FileReader();
    reader.onload = (e) => {
        document.getElementById('img-preview').src = e.target.result;
        document.getElementById('image-preview-container').style.display = 'block';
        if (currentEditId && findRecord(currentModalType, currentEditId)[8]) {
            document.getElementById('confirm-replace-text').style.display = 'block';
        }
    };
    if (input.files[0]) reader.readAsDataURL(input.files[0]);
}

function hideModal() { document.getElementById('modal-overlay').style.display = 'none'; }

function findRecord(type, id) {
    if (type === 'customer') return appData.customers.find(c => c[8] === id);
    if (type === 'transaction') return appData.transactions.find(t => t[12] === id);
    return null;
}

// Form Submission
async function handleFormSubmit(e) {
    e.preventDefault();
    if (!databaseId) return;
    const onLedger = document.getElementById('customer-ledger').classList.contains('active');
    const ledgerCustId = document.getElementById('global-fab').onclick.toString().includes('transaction') ? currentEditId : null;

    showLoader(true);
    try {
        let lastId = currentEditId;
        if (currentModalType === 'customer') await saveCustomer();
        else if (currentModalType === 'transaction') lastId = await saveTransaction();
        await loadDashboardData();
        if (onLedger) {
            const tx = appData.transactions.find(t => t[12] === lastId);
            showCustomerLedger(tx ? tx[1] : (ledgerCustId || ''));
        }
        hideModal();
    } catch (err) { alert("Error saving: " + getErrorMessage(err)); }
    showLoader(false);
}

async function saveCustomer() {
    const name = document.getElementById('cust-name').value;
    const phone = document.getElementById('cust-phone').value;
    const address = document.getElementById('cust-address').value;
    const sid = currentEditId || generateUUID();
    const existing = findRecord('customer', currentEditId);
    const row = [ existing ? existing[0] : '0', name, phone, address, existing ? existing[4] : '0.0', existing ? existing[5] : 'FALSE', existing ? existing[6] : 'web-user', Date.now().toString(), sid ];
    await updateOrAppendRow('Customers', sid, row, 8);
    await logHistory(`${existing ? 'Updated' : 'Added'} Customer: ${name}`);
}

async function saveTransaction() {
    const cid = document.getElementById('tx-cust').value;
    const amt = document.getElementById('tx-amount').value;
    const type = document.getElementById('tx-type').value;
    const note = document.getElementById('tx-note').value;
    const file = document.getElementById('tx-photo').files[0];
    const existing = findRecord('transaction', currentEditId);
    const sid = existing ? currentEditId : generateUUID();

    let did = existing ? existing[8] : '', prev = existing ? existing[6] : '', view = existing ? existing[7] : '';
    if (file) {
        did = await uploadToDrive(file);
        prev = `=IMAGE("https://drive.google.com/thumbnail?id=${did}")`;
        view = `=HYPERLINK("https://drive.google.com/file/d/${did}/view", "View")`;
    }
    const row = [ existing ? existing[0] : '0', cid, amt, type, existing ? existing[4] : Date.now().toString(), note, prev, view, did, existing ? existing[9] : '', existing ? existing[10] : 'web-user', Date.now().toString(), sid ];
    await updateOrAppendRow('Transactions', sid, row, 12);
    await updateCustomerBalance(cid);
    const cust = appData.customers.find(c => c[8] === cid);
    await logHistory(`${existing ? 'Updated' : 'Added'} ${type} of ₹${amt} for ${cust ? cust[1] : 'Unknown'}`);
    return sid;
}

async function logHistory(action) {
    const row = [ '0', Date.now().toString(), action, 'TRUE', generateUUID() ];
    await gapi.client.sheets.spreadsheets.values.append({ spreadsheetId: databaseId, range: 'History!A1', valueInputOption: 'USER_ENTERED', resource: { values: [row] } });
}

async function uploadToDrive(file) {
    const meta = { name: `Udaari_${Date.now()}_${file.name}`, mimeType: file.type };
    const form = new FormData();
    form.append('metadata', new Blob([JSON.stringify(meta)], { type: 'application/json' }));
    form.append('file', file);
    const resp = await fetch('https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart', { method: 'POST', headers: { Authorization: 'Bearer ' + gapi.client.getToken().access_token }, body: form });
    const res = await resp.json();
    if (!resp.ok) throw new Error(res.error ? res.error.message : 'Drive Upload Failed');
    return res.id;
}

async function updateOrAppendRow(sheet, sid, row, col) {
    const vals = (await gapi.client.sheets.spreadsheets.values.get({ spreadsheetId: databaseId, range: `${sheet}!A:Z` })).result.values;
    let idx = -1;
    if (vals) { for (let i = 0; i < vals.length; i++) { if (vals[i].length > col && vals[i][col] === sid) { idx = i + 1; break; } } }
    if (idx !== -1) await gapi.client.sheets.spreadsheets.values.update({ spreadsheetId: databaseId, range: `${sheet}!A${idx}`, valueInputOption: 'USER_ENTERED', resource: { values: [row] } });
    else await gapi.client.sheets.spreadsheets.values.append({ spreadsheetId: databaseId, range: `${sheet}!A1`, valueInputOption: 'USER_ENTERED', resource: { values: [row] } });
}

async function deleteItem(sheet, sid) {
    if (!confirm("Are you sure?")) return;
    showLoader(true);
    try {
        const col = { 'Customers': 8, 'Transactions': 12 }[sheet];
        const vals = (await gapi.client.sheets.spreadsheets.values.get({ spreadsheetId: databaseId, range: `${sheet}!A:Z` })).result.values;
        if (vals) {
            for (let i = 0; i < vals.length; i++) {
                if (vals[i][col] === sid) {
                    const rowData = vals[i];
                    await gapi.client.sheets.spreadsheets.values.append({ spreadsheetId: databaseId, range: 'Trash!A1', valueInputOption: 'USER_ENTERED', resource: { values: [[`Deleted from Web: ${sheet}`, sheet.toLowerCase(), sid, Date.now().toString(), JSON.stringify(rowData)]] } });
                    await gapi.client.sheets.spreadsheets.values.clear({ spreadsheetId: databaseId, range: `${sheet}!A${i+1}:Z${i+1}` });
                    if (sheet === 'Transactions') await updateCustomerBalance(rowData[1]);
                    await logHistory(`Deleted ${sheet} record: ${sid}`);
                    break;
                }
            }
        }
        await loadDashboardData();
    } catch (err) { alert("Delete failed: " + getErrorMessage(err)); }
    showLoader(false);
}

async function updateCustomerBalance(cid) {
    const txs = (await gapi.client.sheets.spreadsheets.values.get({ spreadsheetId: databaseId, range: 'Transactions!A2:M' })).result.values || [];
    let bal = 0;
    txs.forEach(tx => { if (tx[1] && tx[1].trim() === cid.trim()) { const a = parseFloat(tx[2] || 0); if (tx[3] === 'DEBIT') bal += a; else bal -= a; } });
    const custs = (await gapi.client.sheets.spreadsheets.values.get({ spreadsheetId: databaseId, range: 'Customers!A:I' })).result.values;
    for (let i = 0; i < custs.length; i++) { if (custs[i][8] && custs[i][8].trim() === cid.trim()) {
        const row = [...custs[i]]; row[4] = bal.toString(); row[7] = Date.now().toString();
        await gapi.client.sheets.spreadsheets.values.update({ spreadsheetId: databaseId, range: `Customers!A${i+1}`, valueInputOption: 'USER_ENTERED', resource: { values: [row] } });
        break;
    } }
}

function shareTransaction(sid) {
    const tx = appData.transactions.find(t => t[12] === sid);
    const cust = appData.customers.find(c => c[8] === tx[1]);
    const text = `📜 Udaari Ledger Bill\nCustomer: ${cust[1]}\nAmount: ₹${tx[2]}\nType: ${tx[3]}\nDate: ${new Date(parseInt(tx[4])).toLocaleDateString()}\nNote: ${tx[5] || 'N/A'}`;
    if (navigator.share) navigator.share({ title: 'Bill', text: text });
    else { navigator.clipboard.writeText(text); alert("Copied to clipboard!"); }
}

function viewFullscreen(did) {
    document.getElementById('fs-img').src = `https://drive.google.com/file/d/${did}/view?usp=sharing`.replace('/view', '/uc');
    document.getElementById('fs-viewer').style.display = 'flex';
}

function showLoader(show) { document.getElementById('loader').style.display = show ? 'flex' : 'none'; }

function exportCustomerPdf(sid) {
    const customer = appData.customers.find(c => c[8] === sid);
    const { jsPDF } = window.jspdf;
    const doc = new jsPDF();
    doc.text("Customer Statement", 14, 20);
    doc.text(`Name: ${customer[1]}`, 14, 30);
    doc.text(`Balance: Rs. ${customer[4]}`, 14, 40);
    const data = appData.transactions.filter(tx => tx[1] === sid).map(tx => [new Date(parseInt(tx[4])).toLocaleDateString(), tx[3], `Rs. ${tx[2]}`, tx[5] || '']);
    doc.autoTable({ startY: 50, head: [['Date', 'Type', 'Amount', 'Note']], body: data });
    doc.save(`Statement_${customer[1]}.pdf`);
}

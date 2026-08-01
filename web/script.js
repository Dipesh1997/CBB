const CLIENT_ID = '812006416646-cd28a14enlpg87ktbeim0l02m6f965q9.apps.googleusercontent.com';
const SCOPES = 'https://www.googleapis.com/auth/spreadsheets https://www.googleapis.com/auth/drive.metadata.readonly https://www.googleapis.com/auth/drive.file';

let tokenClient;
let gapiInited = false;
let gisInited = false;
let databaseId = null;
let currentModalType = null;
let currentEditId = null;

let appData = {
    customers: [],
    transactions: [],
    catalog: [],
    billItems: []
};

// UUID Helper
function generateUUID() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        var r = Math.random() * 16 | 0, v = c == 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
}

// Initialize GAPI and GIS
function gapiLoaded() {
    gapi.load('client', initializeGapiClient);
}

async function initializeGapiClient() {
    await gapi.client.init({
        discoveryDocs: ['https://sheets.googleapis.com/$discovery/rest?version=v4', 'https://www.googleapis.com/discovery/v1/apis/drive/v3/rest'],
    });
    gapiInited = true;
    maybeEnableButtons();
}

function gisLoaded() {
    tokenClient = google.accounts.oauth2.initTokenClient({
        client_id: CLIENT_ID,
        scope: SCOPES,
        callback: '', // defined later
    });
    gisInited = true;
    maybeEnableButtons();
}

function maybeEnableButtons() {
    if (gapiInited && gisInited) {
        console.log("System Ready");
    }
}

// Window load listeners
window.onload = () => {
    gapiLoaded();
    gisLoaded();
};

// Auth Handlers
function handleCredentialResponse(response) {
    const payload = decodeJwt(response.credential);
    document.getElementById('welcome-screen').style.display = 'none';
    document.getElementById('user-profile').style.display = 'block';
    document.getElementById('sidebar').style.display = 'flex';
    document.getElementById('user-name').textContent = payload.name;
    document.getElementById('user-pic').src = payload.picture;

    // Request access token for Sheets API
    tokenClient.callback = async (resp) => {
        if (resp.error !== undefined) {
            throw (resp);
        }
        await loadDashboardData();
    };

    if (gapi.client.getToken() === null) {
        tokenClient.requestAccessToken({prompt: 'consent'});
    } else {
        tokenClient.requestAccessToken({prompt: ''});
    }
}

function decodeJwt(token) {
    var base64Url = token.split('.')[1];
    var base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
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
    if (err && err.result && err.result.error && err.result.error.message) {
        return err.result.error.message;
    }
    if (err && err.message) return err.message;
    return "Unknown Error";
}

// Data Fetching
async function loadDashboardData() {
    showLoader(true);
    try {
        // 1. Find the spreadsheet ID
        const response = await gapi.client.drive.files.list({
            q: "name = 'Udaari_Database' and mimeType = 'application/vnd.google-apps.spreadsheet'",
            fields: 'files(id, name)',
        });

        if (response.result.files.length === 0) {
            alert("Could not find 'Udaari_Database' spreadsheet in your Google Drive. Please ensure the Android app has synced at least once.");
            showLoader(false);
            return;
        }

        databaseId = response.result.files[0].id;

        // 2. Fetch all ranges
        const ranges = ['Customers!A2:I', 'Transactions!A2:L', 'Catalog!A2:H', 'BillItems!A2:F'];
        const dataResponse = await gapi.client.sheets.spreadsheets.values.batchGet({
            spreadsheetId: databaseId,
            ranges: ranges,
        });

        const valueRanges = dataResponse.result.valueRanges;
        appData.customers = valueRanges[0].values || [];
        appData.transactions = valueRanges[1].values || [];
        appData.catalog = valueRanges[2].values || [];
        appData.billItems = valueRanges[3].values || [];

        renderAll();
        showSection('overview');
    } catch (err) {
        console.error(err);
        alert("Error fetching data: " + getErrorMessage(err));
    }
    showLoader(false);
}

// UI Rendering
function showSection(sectionId, param = null) {
    document.querySelectorAll('.section').forEach(s => s.classList.remove('active'));
    document.querySelectorAll('nav button').forEach(b => b.classList.remove('active'));

    document.getElementById(sectionId).classList.add('active');
    const navBtn = document.getElementById('nav-' + sectionId);
    if (navBtn) navBtn.classList.add('active');

    // Handle Global FAB
    const fab = document.getElementById('global-fab');
    const fabIcon = document.getElementById('fab-icon');

    if (sectionId === 'customers') {
        fab.style.display = 'flex';
        fabIcon.textContent = 'person_add';
        fab.onclick = () => showModal('customer');
    } else if (sectionId === 'customer-ledger') {
        fab.style.display = 'flex';
        fabIcon.textContent = 'add';
        fab.onclick = () => showModal('transaction', param); // param is serverId
    } else if (sectionId === 'transactions') {
        fab.style.display = 'flex';
        fabIcon.textContent = 'add';
        fab.onclick = () => showModal('transaction');
    } else if (sectionId === 'catalog') {
        fab.style.display = 'flex';
        fabIcon.textContent = 'add';
        fab.onclick = () => showModal('catalog');
    } else {
        fab.style.display = 'none';
    }
}

function renderAll() {
    renderOverview();
    renderCustomers();
    renderTransactions();
    renderCatalog();
}

function renderOverview() {
    let totalReceivable = 0;
    let totalAdvance = 0;

    appData.customers.forEach(row => {
        const balance = parseFloat(row[4] || 0);
        if (balance > 0) totalReceivable += balance;
        else totalAdvance += Math.abs(balance);
    });

    document.getElementById('total-receivable').textContent = `₹ ${totalReceivable.toLocaleString('en-IN')}`;
    document.getElementById('total-advance').textContent = `₹ ${totalAdvance.toLocaleString('en-IN')}`;
    document.getElementById('count-customers').textContent = appData.customers.length;
    document.getElementById('count-transactions').textContent = appData.transactions.length;
}

function renderCustomers(filter = '') {
    const tbody = document.querySelector('#customers-table tbody');
    tbody.innerHTML = '';

    appData.customers
        .filter(row => row[1].toLowerCase().includes(filter.toLowerCase()))
        .forEach(row => {
            const tr = document.createElement('tr');
            const balance = parseFloat(row[4] || 0);
            const serverId = row[8];
            tr.innerHTML = `
                <td><span class="clickable-name" onclick="showCustomerLedger('${serverId}')">${row[1]}</span></td>
                <td>${row[2]}</td>
                <td style="color: ${balance >= 0 ? 'green' : 'red'}">₹ ${balance}</td>
                <td>${row[5] === 'TRUE' ? '<span style="color:red">Bad Debt</span>' : 'Active'}</td>
                <td style="text-align: right;">
                    <span class="material-icons action-icon" onclick="showModal('customer', '${serverId}')">edit</span>
                    <span class="material-icons action-icon delete" onclick="deleteItem('Customers', '${serverId}')">delete</span>
                </td>
            `;
            tbody.appendChild(tr);
        });
}

function showCustomerLedger(serverId) {
    const customer = appData.customers.find(c => c[8] && c[8].trim() === serverId.trim());
    if (!customer) {
        console.error("Customer not found for ID:", serverId);
        return;
    }

    showSection('customer-ledger', serverId);
    document.getElementById('ledger-title').textContent = `${customer[1]}'s Ledger`;

    const info = document.getElementById('ledger-customer-info');
    const balance = parseFloat(customer[4] || 0);
    info.innerHTML = `
        <div><span class="label">Phone</span><span class="value">${customer[2]}</span></div>
        <div><span class="label">Address</span><span class="value">${customer[3] || 'N/A'}</span></div>
        <div><span class="label">Current Balance</span><span class="value" style="color: ${balance >= 0 ? 'green' : 'red'}">₹ ${balance}</span></div>
    `;

    document.getElementById('export-pdf-btn').onclick = () => exportCustomerPdf(serverId);
    renderCustomerLedger(serverId);
}

function renderCustomerLedger(serverId) {
    const tbody = document.querySelector('#ledger-table tbody');
    tbody.innerHTML = '';

    const targetId = serverId.trim();
    const transactions = appData.transactions
        .filter(tx => tx[1] && tx[1].trim() === targetId)
        .sort((a, b) => {
            const timeA = parseInt(a[4]) || 0;
            const timeB = parseInt(b[4]) || 0;
            return timeB - timeA;
        });

    console.log(`Rendering ledger for ${targetId}. Found ${transactions.length} transactions.`);

    if (transactions.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" style="text-align:center; padding: 40px; color: var(--outline);">No transactions found for this customer.</td></tr>';
        return;
    }

    transactions.forEach(row => {
        const tr = document.createElement('tr');
        const driveId = (row[8] || '').trim();
        const hasPhoto = driveId !== '';
        tr.innerHTML = `
            <td>${new Date(parseInt(row[4])).toLocaleDateString()}</td>
            <td>${row[3]}</td>
            <td style="color: ${row[3] === 'DEBIT' ? 'red' : 'green'}">₹ ${row[2]}</td>
            <td>${row[5] || ''}</td>
            <td style="text-align: right;">
                ${hasPhoto ? `<a href="https://drive.google.com/file/d/${driveId}/view" target="_blank" class="material-icons action-icon">image</a>` : '-'}
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function exportCustomerPdf(serverId) {
    const customer = appData.customers.find(c => c[8] === serverId);
    if (!customer) return;

    const { jsPDF } = window.jspdf;
    const doc = new jsPDF();

    // Header
    doc.setFontSize(22);
    doc.text("Customer Statement", 14, 20);
    doc.setFontSize(12);
    doc.text(`Udaari Ledger - Generated on ${new Date().toLocaleDateString()}`, 14, 28);

    // Customer Info
    doc.line(14, 32, 196, 32);
    doc.setFont("helvetica", "bold");
    doc.text("Customer Details:", 14, 42);
    doc.setFont("helvetica", "normal");
    doc.text(`Name: ${customer[1]}`, 14, 50);
    doc.text(`Phone: ${customer[2]}`, 14, 58);
    doc.text(`Current Balance: Rs. ${customer[4]}`, 14, 66);
    doc.line(14, 72, 196, 72);

    // Table
    const transactions = appData.transactions
        .filter(tx => tx[1] === serverId)
        .sort((a, b) => parseInt(a[4]) - parseInt(b[4])) // Ascending for report
        .map(tx => [
            new Date(parseInt(tx[4])).toLocaleDateString(),
            tx[3],
            `Rs. ${tx[2]}`,
            tx[5] || ''
        ]);

    doc.autoTable({
        startY: 80,
        head: [['Date', 'Type', 'Amount', 'Note']],
        body: transactions,
        theme: 'striped',
        headStyles: { fillColor: [103, 80, 164] }
    });

    doc.save(`Statement_${customer[1]}_${Date.now()}.pdf`);
}

function filterCustomers() {
    const query = document.getElementById('customer-search').value;
    renderCustomers(query);
}

function renderTransactions() {
    const tbody = document.querySelector('#transactions-table tbody');
    tbody.innerHTML = '';

    // Sort by timestamp descending
    const sorted = [...appData.transactions].sort((a, b) => parseInt(b[4]) - parseInt(a[4]));

    sorted.slice(0, 50).forEach(row => {
        const tr = document.createElement('tr');
        const customer = appData.customers.find(c => c[8] === row[1]);
        const customerName = customer ? customer[1] : 'Unknown';
        const serverId = row[11];

        tr.innerHTML = `
            <td>${new Date(parseInt(row[4])).toLocaleDateString()}</td>
            <td>${customerName}</td>
            <td style="color: ${row[3] === 'DEBIT' ? 'red' : 'green'}">₹ ${row[2]}</td>
            <td>${row[3]}</td>
            <td>${row[5] || ''}</td>
            <td style="text-align: right;">
                <span class="material-icons action-icon delete" onclick="deleteItem('Transactions', '${serverId}')">delete</span>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function renderCatalog() {
    const tbody = document.querySelector('#catalog-table tbody');
    tbody.innerHTML = '';

    appData.catalog.forEach(row => {
        const tr = document.createElement('tr');
        const serverId = row[7];
        tr.innerHTML = `
            <td>${row[1]}</td>
            <td>₹ ${row[2]}</td>
            <td>${row[4]}</td>
            <td style="text-align: right;">
                <span class="material-icons action-icon" onclick="showModal('catalog', '${serverId}')">edit</span>
                <span class="material-icons action-icon delete" onclick="deleteItem('Catalog', '${serverId}')">delete</span>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

// Modal Logic
function showModal(type, serverId = null) {
    currentModalType = type;
    currentEditId = serverId;
    const modal = document.getElementById('modal-overlay');
    const title = document.getElementById('modal-title');
    const fields = document.getElementById('form-fields');
    fields.innerHTML = '';

    const data = serverId ? findRecord(type, serverId) : null;

    if (type === 'customer') {
        title.textContent = data ? 'Edit Customer' : 'Add Customer';
        fields.innerHTML = `
            <div class="form-group"><label>Name</label><input type="text" id="cust-name" value="${data ? data[1] : ''}" required></div>
            <div class="form-group"><label>Phone</label><input type="text" id="cust-phone" value="${data ? data[2] : ''}" required></div>
            <div class="form-group"><label>Address</label><input type="text" id="cust-address" value="${data ? data[3] : ''}"></div>
        `;
    } else if (type === 'transaction') {
        title.textContent = 'Add Transaction';
        const customerOptions = appData.customers.map(c =>
            `<option value="${c[8]}" ${c[8] === serverId ? 'selected' : ''}>${c[1]}</option>`
        ).join('');
        fields.innerHTML = `
            <div class="form-group"><label>Customer</label><select id="tx-cust" required>${customerOptions}</select></div>
            <div class="form-group"><label>Amount</label><input type="number" id="tx-amount" step="0.01" required></div>
            <div class="form-group"><label>Type</label><select id="tx-type"><option value="DEBIT">YOU GAVE (Debit)</option><option value="CREDIT">YOU GOT (Credit)</option></select></div>
            <div class="form-group"><label>Note</label><input type="text" id="tx-note"></div>
            <div class="form-group"><label>Attach Photo (Optional)</label><input type="file" id="tx-photo" accept="image/*"></div>
        `;
    } else if (type === 'catalog') {
        title.textContent = data ? 'Edit Catalog Item' : 'Add Product';
        fields.innerHTML = `
            <div class="form-group"><label>Name</label><input type="text" id="cat-name" value="${data ? data[1] : ''}" required></div>
            <div class="form-group"><label>Price</label><input type="number" id="cat-price" step="0.01" value="${data ? data[2] : ''}" required></div>
            <div class="form-group"><label>Units</label><input type="text" id="cat-units" value="${data ? data[4] : ''}"></div>
        `;
    }

    modal.style.display = 'flex';
}

function hideModal() {
    document.getElementById('modal-overlay').style.display = 'none';
}

function findRecord(type, serverId) {
    if (type === 'customer') return appData.customers.find(c => c[8] === serverId);
    if (type === 'catalog') return appData.catalog.find(c => c[7] === serverId);
    return null;
}

// Form Submission
async function handleFormSubmit(e) {
    e.preventDefault();
    if (!databaseId) {
        alert("Spreadsheet not loaded. Please sign in or refresh.");
        return;
    }
    showLoader(true);
    try {
        if (currentModalType === 'customer') await saveCustomer();
        else if (currentModalType === 'transaction') await saveTransaction();
        else if (currentModalType === 'catalog') await saveCatalogItem();

        await loadDashboardData();
        hideModal();
    } catch (err) {
        console.error(err);
        alert("Error saving: " + getErrorMessage(err));
    }
    showLoader(false);
}

async function saveCustomer() {
    const name = document.getElementById('cust-name').value;
    const phone = document.getElementById('cust-phone').value;
    const address = document.getElementById('cust-address').value;
    const serverId = currentEditId || generateUUID();
    const existing = findRecord('customer', currentEditId);

    const row = [
        existing ? existing[0] : '0',
        name, phone, address,
        existing ? existing[4] : '0.0',
        existing ? existing[5] : 'FALSE',
        existing ? existing[6] : 'web-user',
        Date.now().toString(),
        serverId
    ];

    await updateOrAppendRow('Customers', serverId, row, 8);
}

async function saveTransaction() {
    const custServerId = document.getElementById('tx-cust').value;
    const amount = parseFloat(document.getElementById('tx-amount').value);
    const type = document.getElementById('tx-type').value;
    const note = document.getElementById('tx-note').value;
    const photoFile = document.getElementById('tx-photo').files[0];
    const serverId = generateUUID();

    let driveFileId = '';
    let imagePreview = '';
    let viewLink = '';

    if (photoFile) {
        driveFileId = await uploadToDrive(photoFile);
        imagePreview = `=IMAGE("https://drive.google.com/thumbnail?id=${driveFileId}")`;
        viewLink = `=HYPERLINK("https://drive.google.com/file/d/${driveFileId}/view", "View Attachment")`;
    }

    const row = [
        '0', custServerId, amount.toString(), type,
        Date.now().toString(), note,
        imagePreview, viewLink, driveFileId, 'web-user',
        Date.now().toString(), serverId
    ];

    // 1. Append transaction
    await gapi.client.sheets.spreadsheets.values.append({
        spreadsheetId: databaseId,
        range: 'Transactions!A1',
        valueInputOption: 'USER_ENTERED',
        resource: { values: [row] }
    });

    // 2. Recalculate balance
    await updateCustomerBalance(custServerId);
}

async function uploadToDrive(file) {
    const metadata = {
        name: `Udaari_${Date.now()}_${file.name}`,
        mimeType: file.type,
    };

    const form = new FormData();
    form.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }));
    form.append('file', file);

    const response = await fetch('https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart', {
        method: 'POST',
        headers: {
            Authorization: 'Bearer ' + gapi.client.getToken().access_token,
        },
        body: form,
    });

    const result = await response.json();
    if (!response.ok) throw new Error(result.error ? result.error.message : 'Drive Upload Failed');

    // Set permission to anyone with link (like Android app) or at least ensure it's viewable
    // Note: To match Android app perfectly, we'd need to set permissions,
    // but drive.file scope only allows the app to see files it created.
    return result.id;
}

async function saveCatalogItem() {
    const name = document.getElementById('cat-name').value;
    const price = document.getElementById('cat-price').value;
    const units = document.getElementById('cat-units').value;
    const serverId = currentEditId || generateUUID();
    const existing = findRecord('catalog', currentEditId);

    const row = [
        existing ? existing[0] : '0',
        name, price, '', units,
        'web-user', Date.now().toString(), serverId
    ];

    await updateOrAppendRow('Catalog', serverId, row, 7);
}

// Google Sheets Helpers
async function updateOrAppendRow(sheetName, serverId, row, serverIdColIndex) {
    const range = `${sheetName}!A:Z`;
    const response = await gapi.client.sheets.spreadsheets.values.get({
        spreadsheetId: databaseId,
        range: range,
    });
    const values = response.result.values;
    let rowIndex = -1;

    if (values) {
        for (let i = 0; i < values.length; i++) {
            if (values[i].length > serverIdColIndex && values[i][serverIdColIndex] === serverId) {
                rowIndex = i + 1;
                break;
            }
        }
    }

    if (rowIndex !== -1) {
        await gapi.client.sheets.spreadsheets.values.update({
            spreadsheetId: databaseId,
            range: `${sheetName}!A${rowIndex}`,
            valueInputOption: 'USER_ENTERED',
            resource: { values: [row] }
        });
    } else {
        await gapi.client.sheets.spreadsheets.values.append({
            spreadsheetId: databaseId,
            range: `${sheetName}!A1`,
            valueInputOption: 'USER_ENTERED',
            resource: { values: [row] }
        });
    }
}

async function deleteItem(sheetName, serverId) {
    if (!confirm("Are you sure you want to delete this?")) return;
    if (!databaseId) {
        alert("Spreadsheet not loaded.");
        return;
    }
    showLoader(true);
    try {
        const serverIdColMap = { 'Customers': 8, 'Transactions': 11, 'Catalog': 7 };
        const colIndex = serverIdColMap[sheetName];

        const response = await gapi.client.sheets.spreadsheets.values.get({
            spreadsheetId: databaseId,
            range: `${sheetName}!A:Z`,
        });
        const values = response.result.values;
        let rowIndex = -1;

        if (values) {
            for (let i = 0; i < values.length; i++) {
                if (values[i].length > colIndex && values[i][colIndex] === serverId) {
                    rowIndex = i + 1;
                    const rowData = values[i];

                    // 1. Move to Trash
                    const trashRow = [
                        `${sheetName} record deleted from Web`,
                        sheetName.toLowerCase(),
                        serverId,
                        Date.now().toString(),
                        JSON.stringify(rowData)
                    ];
                    await gapi.client.sheets.spreadsheets.values.append({
                        spreadsheetId: databaseId,
                        range: 'Trash!A1',
                        valueInputOption: 'USER_ENTERED',
                        resource: { values: [trashRow] }
                    });

                    // 2. Clear from main sheet
                    await gapi.client.sheets.spreadsheets.values.clear({
                        spreadsheetId: databaseId,
                        range: `${sheetName}!A${rowIndex}:Z${rowIndex}`
                    });

                    // 3. If transaction, update balance
                    if (sheetName === 'Transactions') {
                        await updateCustomerBalance(rowData[1]);
                    }
                    break;
                }
            }
        }
        await loadDashboardData();
    } catch (err) {
        alert("Delete failed: " + getErrorMessage(err));
    }
    showLoader(false);
}

async function updateCustomerBalance(custServerId) {
    // Reload all transactions for this customer
    const response = await gapi.client.sheets.spreadsheets.values.get({
        spreadsheetId: databaseId,
        range: 'Transactions!A2:L',
    });
    const txs = response.result.values || [];
    let balance = 0;

    txs.forEach(tx => {
        if (tx[1] === custServerId) {
            const amt = parseFloat(tx[2] || 0);
            if (tx[3] === 'DEBIT') balance += amt;
            else balance -= amt;
        }
    });

    // Update customer row
    const custResponse = await gapi.client.sheets.spreadsheets.values.get({
        spreadsheetId: databaseId,
        range: 'Customers!A:I',
    });
    const customers = custResponse.result.values;
    for (let i = 0; i < customers.length; i++) {
        if (customers[i][8] === custServerId) {
            const rowIndex = i + 1;
            const updatedRow = [...customers[i]];
            updatedRow[4] = balance.toString();
            updatedRow[7] = Date.now().toString();

            await gapi.client.sheets.spreadsheets.values.update({
                spreadsheetId: databaseId,
                range: `Customers!A${rowIndex}`,
                valueInputOption: 'USER_ENTERED',
                resource: { values: [updatedRow] }
            });
            break;
        }
    }
}

function showLoader(show) {
    document.getElementById('loader').style.display = show ? 'flex' : 'none';
}

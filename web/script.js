const CLIENT_ID = '812006416646-cd28a14enlpg87ktbeim0l02m6f965q9.apps.googleusercontent.com';
const SCOPES = 'https://www.googleapis.com/auth/spreadsheets https://www.googleapis.com/auth/drive.metadata.readonly';

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
    gapi.load('client', intializeGapiClient);
}

async function intializeGapiClient() {
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
            alert("Could not find 'Udaari_Database' spreadsheet in your Google Drive.");
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
        alert("Error fetching data: " + err.message);
    }
    showLoader(false);
}

// UI Rendering
function showSection(sectionId) {
    document.querySelectorAll('.section').forEach(s => s.classList.remove('active'));
    document.querySelectorAll('nav button').forEach(b => b.classList.remove('active'));

    document.getElementById(sectionId).classList.add('active');
    document.getElementById('nav-' + sectionId).classList.add('active');
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
                <td>${row[1]}</td>
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
        const customerOptions = appData.customers.map(c => `<option value="${c[8]}">${c[1]}</option>`).join('');
        fields.innerHTML = `
            <div class="form-group"><label>Customer</label><select id="tx-cust" required>${customerOptions}</select></div>
            <div class="form-group"><label>Amount</label><input type="number" id="tx-amount" step="0.01" required></div>
            <div class="form-group"><label>Type</label><select id="tx-type"><option value="DEBIT">YOU GAVE (Debit)</option><option value="CREDIT">YOU GOT (Credit)</option></select></div>
            <div class="form-group"><label>Note</label><input type="text" id="tx-note"></div>
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
    showLoader(true);
    try {
        if (currentModalType === 'customer') await saveCustomer();
        else if (currentModalType === 'transaction') await saveTransaction();
        else if (currentModalType === 'catalog') await saveCatalogItem();

        await loadDashboardData();
        hideModal();
    } catch (err) {
        alert("Error saving: " + err.message);
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
    const serverId = generateUUID();

    const row = [
        '0', custServerId, amount.toString(), type,
        Date.now().toString(), note,
        '', '', '', 'web-user',
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

        for (let i = 0; i < values.length; i++) {
            if (values[i][colIndex] === serverId) {
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
        await loadDashboardData();
    } catch (err) {
        alert("Delete failed: " + err.message);
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

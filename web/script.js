const CLIENT_ID = '812006416646-cd28a14enlpg87ktbeim0l02m6f965q9.apps.googleusercontent.com';
const SCOPES = 'https://www.googleapis.com/auth/spreadsheets.readonly https://www.googleapis.com/auth/drive.metadata.readonly';

let tokenClient;
let gapiInited = false;
let gisInited = false;
let databaseId = null;

let appData = {
    customers: [],
    transactions: [],
    catalog: [],
    billItems: []
};

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
            tr.innerHTML = `
                <td>${row[1]}</td>
                <td>${row[2]}</td>
                <td style="color: ${balance >= 0 ? 'green' : 'red'}">₹ ${balance}</td>
                <td>${row[5] === 'TRUE' ? '<span style="color:red">Bad Debt</span>' : 'Active'}</td>
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

    // Sort by timestamp descending (assuming column index 4 is timestamp)
    const sorted = [...appData.transactions].sort((a, b) => new Date(b[4]) - new Date(a[4]));

    sorted.slice(0, 50).forEach(row => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>${new Date(row[4]).toLocaleDateString()}</td>
            <td>${row[1]}</td>
            <td>₹ ${row[2]}</td>
            <td>${row[3]}</td>
            <td>${row[5] || ''}</td>
        `;
        tbody.appendChild(tr);
    });
}

function renderCatalog() {
    const tbody = document.querySelector('#catalog-table tbody');
    tbody.innerHTML = '';

    appData.catalog.forEach(row => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td>${row[1]}</td>
            <td>₹ ${row[2]}</td>
            <td>${row[4]}</td>
        `;
        tbody.appendChild(tr);
    });
}

function showLoader(show) {
    document.getElementById('loader').style.display = show ? 'flex' : 'none';
}

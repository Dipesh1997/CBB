# Udaari Ledger Web Dashboard

A standalone web dashboard for the Udaari Ledger project, built with HTML, CSS, and vanilla JavaScript.

## Features
- Google Sign-In (Google Identity Services)
- Google Sheets API Integration (Udaari_Database)
- Overview of Receivables and Advances
- Customer Ledger Management
- Transaction History
- Product Catalog

## Setup Instructions

### 1. Google Cloud Console Setup
1. Go to the [Google Cloud Console](https://console.cloud.google.com/).
2. Create a new project or select an existing one.
3. Enable the **Google Sheets API** and **Google Drive API**.
4. Go to **APIs & Services > OAuth consent screen**:
   - Select "External" and click "Create".
   - Fill in the required app information.
   - Add scopes: `https://www.googleapis.com/auth/spreadsheets.readonly` and `https://www.googleapis.com/auth/drive.metadata.readonly`.
5. Go to **APIs & Services > Credentials**:
   - Click "Create Credentials" > "OAuth client ID".
   - Application type: "Web application".
   - Name: "Udaari Ledger Web".
   - **Authorized JavaScript origins**: Add `http://localhost:8080` (for local testing) and your GitHub Pages URL (e.g., `https://username.github.io`).
   - Click "Create".
   - Copy the **Client ID** and update it in `web/script.js` if it differs from the pre-configured one.

### 2. Local Testing
To test locally, you need a local web server because Google Identity Services requires a web origin.
You can use Python:
```bash
cd web
python -m http.server 8080
```
Then open `http://localhost:8080` in your browser.

### 3. GitHub Pages Deployment
1. Push your code to a GitHub repository.
2. Go to the repository settings > **Pages**.
3. Under "Build and deployment", select the branch (e.g., `main`) and folder (e.g., `/web`).
4. Click "Save".
5. Once deployed, ensure the deployment URL is added to the "Authorized JavaScript origins" in the Google Cloud Console.

## Spreadsheet Requirement
The dashboard expects a spreadsheet named **'Udaari_Database'** in your Google Drive with the following sheets:
- **Customers**: ID, Name, Phone, Address, Balance, IsBadDebt, CreatedBy, LastUpdated, ServerID
- **Transactions**: ID, CustomerServerID, Amount, Type, Timestamp, Note, Image Preview, View Link, DriveFileID, CreatedBy, LastUpdated, ServerID
- **Catalog**: ID, Name, Price, Shortcut, Units, CreatedBy, LastUpdated, ServerID
- **BillItems**: ID, TransactionServerID, ProductName, Price, LastUpdated, ServerID

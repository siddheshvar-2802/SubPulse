# =============================================================================
# SubPulse — Interactive API Automated Test Script
# =============================================================================

$baseUrl = "http://localhost:8080/api/v1"
$testEmail = "test.user@subpulse.io"
$testPassword = "Password123!"
$testFullName = "SubPulse Tester"

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "[*] Starting SubPulse End-to-End API Test Suite" -ForegroundColor Cyan
Write-Host "Base URL: $baseUrl" -ForegroundColor Gray
Write-Host "========================================================" -ForegroundColor Cyan

# ── 1. Register or Login ─────────────────────────────────────────────────────
Write-Host "`n[1/6] Authenticating user ($testEmail)..." -ForegroundColor Yellow

$authPayload = @{
    email    = $testEmail
    password = $testPassword
} | ConvertTo-Json

$token = $null

try {
    # Attempt login first
    $loginResp = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $authPayload -ContentType "application/json" -ErrorAction Stop
    $token = $loginResp.accessToken
    Write-Host "[+] Logged in successfully!" -ForegroundColor Green
} catch {
    # If login fails (user doesn't exist yet), register
    Write-Host "User not found, registering new account..." -ForegroundColor Gray
    $registerPayload = @{
        fullName = $testFullName
        email    = $testEmail
        password = $testPassword
    } | ConvertTo-Json

    try {
        $regResp = Invoke-RestMethod -Uri "$baseUrl/auth/register" -Method Post -Body $registerPayload -ContentType "application/json" -ErrorAction Stop
        $token = $regResp.accessToken
        Write-Host "[+] Registered and authenticated successfully!" -ForegroundColor Green
    } catch {
        Write-Host "[-] Authentication failed: $($_.Exception.Message)" -ForegroundColor Red
        exit 1
    }
}

$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type"  = "application/json"
}

# ── 2. Create Sample Subscriptions ──────────────────────────────────────────
Write-Host "`n[2/6] Creating sample subscriptions..." -ForegroundColor Yellow

$today = (Get-Date).ToString("yyyy-MM-dd")
$in7Days = (Get-Date).AddDays(7).ToString("yyyy-MM-dd")
$in20Days = (Get-Date).AddDays(20).ToString("yyyy-MM-dd")

$sub1Payload = @{
    serviceName     = "GitHub Copilot"
    description     = "AI pair programmer subscription"
    websiteUrl      = "https://github.com"
    amount          = 10.00
    currency        = "USD"
    billingCycle    = "MONTHLY"
    startDate       = "2026-01-01"
    nextBillingDate = $in7Days
    autoRenew       = $true
    category        = "DEVELOPER_TOOLS"
} | ConvertTo-Json

$sub2Payload = @{
    serviceName     = "Netflix Premium"
    description     = "4K UHD Family Plan"
    websiteUrl      = "https://netflix.com"
    amount          = 22.99
    currency        = "USD"
    billingCycle    = "MONTHLY"
    startDate       = "2026-01-15"
    nextBillingDate = $in20Days
    autoRenew       = $true
    category        = "ENTERTAINMENT"
} | ConvertTo-Json

$sub1 = Invoke-RestMethod -Uri "$baseUrl/subscriptions" -Method Post -Headers $headers -Body $sub1Payload
Write-Host "[+] Created Subscription: '$($sub1.serviceName)' (ID: $($sub1.id)) due on $($sub1.nextBillingDate)" -ForegroundColor Green

$sub2 = Invoke-RestMethod -Uri "$baseUrl/subscriptions" -Method Post -Headers $headers -Body $sub2Payload
Write-Host "[+] Created Subscription: '$($sub2.serviceName)' (ID: $($sub2.id)) due on $($sub2.nextBillingDate)" -ForegroundColor Green

# ── 3. Add Alert Configurations ──────────────────────────────────────────────
Write-Host "`n[3/6] Adding renewal alert rules..." -ForegroundColor Yellow

$alert1Payload = @{
    daysBefore  = 7
    channel     = "EMAIL"
    isEnabled   = $true
    destination = $testEmail
} | ConvertTo-Json

$alert1 = Invoke-RestMethod -Uri "$baseUrl/subscriptions/$($sub1.id)/alerts" -Method Post -Headers $headers -Body $alert1Payload
Write-Host "[+] Added Alert: Notify via $($alert1.channel) $($alert1.daysBefore) days before for Sub ID $($sub1.id)" -ForegroundColor Green

# ── 4. Retrieve All Subscriptions ────────────────────────────────────────────
Write-Host "`n[4/6] Fetching all subscriptions..." -ForegroundColor Yellow
$allSubs = Invoke-RestMethod -Uri "$baseUrl/subscriptions" -Method Get -Headers $headers
Write-Host "[+] Found $($allSubs.Count) active subscription(s):" -ForegroundColor Green
foreach ($s in $allSubs) {
    Write-Host "   - [$($s.category)] $($s.serviceName): $($s.currency) $($s.amount) ($($s.billingCycle)) | Next Renewal: $($s.nextBillingDate)" -ForegroundColor Gray
}

# ── 5. Get Upcoming Renewals ─────────────────────────────────────────────────
Write-Host "`n[5/6] Querying upcoming renewals (next 30 days)..." -ForegroundColor Yellow
$upcoming = Invoke-RestMethod -Uri "$baseUrl/subscriptions/upcoming?days=30" -Method Get -Headers $headers
Write-Host "[+] Found $($upcoming.Count) upcoming renewal(s) in next 30 days." -ForegroundColor Green

# ── 6. Spend Analytics ───────────────────────────────────────────────────────
Write-Host "`n[6/6] Fetching Spend Analytics..." -ForegroundColor Yellow
$analytics = Invoke-RestMethod -Uri "$baseUrl/subscriptions/analytics" -Method Get -Headers $headers

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host " SubPulse Spend Analytics" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host " Total Active Subscriptions : $($analytics.totalActiveSubscriptions)" -ForegroundColor White
Write-Host " Renewing in next 7 days    : $($analytics.renewingInNextSevenDays)" -ForegroundColor White
Write-Host " Renewing in next 30 days   : $($analytics.renewingInNextThirtyDays)" -ForegroundColor White
Write-Host " Total Monthly Spend        : $($analytics.currency) $($analytics.monthlySpend)" -ForegroundColor White
Write-Host " Projected Annual Spend     : $($analytics.currency) $($analytics.annualSpend)" -ForegroundColor White
Write-Host " Category Breakdown:" -ForegroundColor White
foreach ($prop in $analytics.spendByCategory.PSObject.Properties) {
    Write-Host "   - $($prop.Name): $($analytics.currency) $($prop.Value)" -ForegroundColor Gray
}
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "[SUCCESS] All tests executed successfully!" -ForegroundColor Green

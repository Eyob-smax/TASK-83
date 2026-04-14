param(
    [string]$BaseUrl = "http://localhost:8443",
    [string]$DbContainer = "eventops-mysql"
)

$ErrorActionPreference = "Stop"

$stamp = Get-Date -Format "yyyyMMddHHmmss"
$tmpDir = Join-Path $PSScriptRoot ("tmp\curl-smoke-" + $stamp)
New-Item -ItemType Directory -Path $tmpDir -Force | Out-Null

$cookieFile = Join-Path $tmpDir "session.cookie"
$results = New-Object System.Collections.Generic.List[Object]

function Invoke-Api {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Path,
        [int[]]$Expected,
        [string]$Body = "",
        [switch]$UseAuth
    )

    $url = $BaseUrl.TrimEnd("/") + $Path
    $outFile = Join-Path $tmpDir ([Guid]::NewGuid().ToString() + ".out")

    $args = @(
        "-sS",
        "-o", $outFile,
        "-w", "%{http_code}",
        "-X", $Method,
        $url,
        "-H", "Accept: application/json"
    )

    if ($Body) {
        $args += @("-H", "Content-Type: application/json", "--data-raw", $Body)
    }

    if ($UseAuth) {
        $args += @("-b", $cookieFile, "-c", $cookieFile)
    }

    $statusText = ""
    $curlExit = 0
    try {
        $statusText = & curl.exe @args
        $curlExit = $LASTEXITCODE
    } catch {
        $statusText = "0"
        $curlExit = 1
    }

    [int]$status = 0
    if ($statusText -match "^\d+$") {
        $status = [int]$statusText
    }

    $raw = ""
    if (Test-Path $outFile) {
        $raw = Get-Content $outFile -Raw
    }

    $parsed = $null
    try {
        if ($raw) {
            $parsed = $raw | ConvertFrom-Json -Depth 20
        }
    } catch {
        $parsed = $null
    }

    $message = ""
    if ($parsed -and $parsed.PSObject.Properties.Name -contains "message") {
        $message = [string]$parsed.message
    } elseif ($raw) {
        $oneLine = ($raw -replace "\s+", " ").Trim()
        if ($oneLine.Length -gt 180) {
            $message = $oneLine.Substring(0, 180) + "..."
        } else {
            $message = $oneLine
        }
    }

    $pass = ($Expected -contains $status)
    $results.Add([pscustomobject]@{
            Name     = $Name
            Method   = $Method
            Path     = $Path
            Status   = $status
            Expected = ($Expected -join ",")
            Pass     = $pass
            CurlExit = $curlExit
            Message  = $message
        })

    return [pscustomobject]@{
        Status = $status
        Json   = $parsed
        Raw    = $raw
        Pass   = $pass
    }
}

function Promote-ToSystemAdmin {
    param([string]$Username)
    $sql = "UPDATE users SET role_type='SYSTEM_ADMIN' WHERE username='$Username';"
    & docker exec $DbContainer mysql -uroot -peventops_root eventops -e $sql | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to promote user '$Username' to SYSTEM_ADMIN."
    }
}

$username = "curl_admin_$stamp"
$password = "Pass12345!"
$displayName = "Curl Admin $stamp"

$fakeSessionId = "11111111-1111-1111-1111-111111111111"
$fakeRegistrationId = "22222222-2222-2222-2222-222222222222"
$fakeNotificationId = "33333333-3333-3333-3333-333333333333"
$fakeImportJobId = "44444444-4444-4444-4444-444444444444"
$fakeSourceId = "55555555-5555-5555-5555-555555555555"
$fakeExportId = "66666666-6666-6666-6666-666666666666"
$fakeAuditId = "77777777-7777-7777-7777-777777777777"
$fakePolicyId = "88888888-8888-8888-8888-888888888888"

$registerBody = @{
    username    = $username
    password    = $password
    displayName = $displayName
    contactInfo = "curl-admin@example.local"
} | ConvertTo-Json -Compress

Invoke-Api -Name "Auth Register" -Method "POST" -Path "/api/auth/register" -Expected @(201) -Body $registerBody | Out-Null

try {
    Promote-ToSystemAdmin -Username $username
    $results.Add([pscustomobject]@{
            Name     = "DB Promote Role"
            Method   = "SQL"
            Path     = "users.role_type"
            Status   = 200
            Expected = "200"
            Pass     = $true
            CurlExit = 0
            Message  = "Promoted test user to SYSTEM_ADMIN"
        })
} catch {
    $results.Add([pscustomobject]@{
            Name     = "DB Promote Role"
            Method   = "SQL"
            Path     = "users.role_type"
            Status   = 500
            Expected = "200"
            Pass     = $false
            CurlExit = 1
            Message  = $_.Exception.Message
        })
}

$loginBody = @{
    username = $username
    password = $password
} | ConvertTo-Json -Compress

Invoke-Api -Name "Auth Login" -Method "POST" -Path "/api/auth/login" -Expected @(200) -Body $loginBody -UseAuth | Out-Null
Invoke-Api -Name "Auth Me" -Method "GET" -Path "/api/auth/me" -Expected @(200) -UseAuth | Out-Null

# Events
Invoke-Api -Name "Events List" -Method "GET" -Path "/api/events?page=0&size=5" -Expected @(200) -UseAuth | Out-Null
Invoke-Api -Name "Events Get By ID" -Method "GET" -Path "/api/events/$fakeSessionId" -Expected @(404) -UseAuth | Out-Null
Invoke-Api -Name "Events Availability" -Method "GET" -Path "/api/events/$fakeSessionId/availability" -Expected @(404) -UseAuth | Out-Null

# Registrations
$registrationBody = @{ sessionId = $fakeSessionId } | ConvertTo-Json -Compress
Invoke-Api -Name "Registrations Create" -Method "POST" -Path "/api/registrations" -Expected @(404) -Body $registrationBody -UseAuth | Out-Null
Invoke-Api -Name "Registrations List" -Method "GET" -Path "/api/registrations" -Expected @(200) -UseAuth | Out-Null
Invoke-Api -Name "Registrations Get By ID" -Method "GET" -Path "/api/registrations/$fakeRegistrationId" -Expected @(404) -UseAuth | Out-Null
Invoke-Api -Name "Registrations Delete" -Method "DELETE" -Path "/api/registrations/$fakeRegistrationId" -Expected @(404) -UseAuth | Out-Null
Invoke-Api -Name "Registrations Waitlist" -Method "GET" -Path "/api/registrations/waitlist" -Expected @(200) -UseAuth | Out-Null

# Check-In
$checkInBody = @{
    userId   = "99999999-9999-9999-9999-999999999999"
    passcode = "123456"
} | ConvertTo-Json -Compress
Invoke-Api -Name "CheckIn Create" -Method "POST" -Path "/api/checkin/sessions/$fakeSessionId" -Expected @(404) -Body $checkInBody -UseAuth | Out-Null
Invoke-Api -Name "CheckIn Passcode" -Method "GET" -Path "/api/checkin/sessions/$fakeSessionId/passcode" -Expected @(404) -UseAuth | Out-Null
Invoke-Api -Name "CheckIn Roster" -Method "GET" -Path "/api/checkin/sessions/$fakeSessionId/roster" -Expected @(200) -UseAuth | Out-Null
Invoke-Api -Name "CheckIn Conflicts" -Method "GET" -Path "/api/checkin/sessions/$fakeSessionId/conflicts" -Expected @(200) -UseAuth | Out-Null

# Notifications
Invoke-Api -Name "Notifications List" -Method "GET" -Path "/api/notifications?page=0&size=5" -Expected @(200) -UseAuth | Out-Null
Invoke-Api -Name "Notifications Unread Count" -Method "GET" -Path "/api/notifications/unread-count" -Expected @(200) -UseAuth | Out-Null
Invoke-Api -Name "Notifications Mark Read" -Method "PATCH" -Path "/api/notifications/$fakeNotificationId/read" -Expected @(404) -UseAuth | Out-Null
Invoke-Api -Name "Notifications Subscriptions Get" -Method "GET" -Path "/api/notifications/subscriptions" -Expected @(200) -UseAuth | Out-Null
$subsBody = '[{"notificationType":"REGISTRATION_CONFIRMATION","enabled":true}]'
Invoke-Api -Name "Notifications Subscriptions Put" -Method "PUT" -Path "/api/notifications/subscriptions" -Expected @(200) -Body $subsBody -UseAuth | Out-Null
Invoke-Api -Name "Notifications DND Get" -Method "GET" -Path "/api/notifications/dnd" -Expected @(200) -UseAuth | Out-Null
$dndBody = '{"startTime":"21:00","endTime":"07:00","enabled":true}'
Invoke-Api -Name "Notifications DND Put" -Method "PUT" -Path "/api/notifications/dnd" -Expected @(200) -Body $dndBody -UseAuth | Out-Null

# Finance (data setup + endpoint checks)
$periodAId = $fakeRegistrationId
$periodBId = $fakeImportJobId
$accountId = $fakeNotificationId
$costCenterId = $fakeAuditId
$ruleId = $fakePolicyId
$postingId = $fakeExportId

Invoke-Api -Name "Finance Periods List" -Method "GET" -Path "/api/finance/periods" -Expected @(200) -UseAuth | Out-Null

$periodABody = @{
    name      = "CURL-PERIOD-A-$stamp"
    startDate = "2026-01-01"
    endDate   = "2026-01-31"
} | ConvertTo-Json -Compress
$periodAResp = Invoke-Api -Name "Finance Periods Create A" -Method "POST" -Path "/api/finance/periods" -Expected @(201) -Body $periodABody -UseAuth
if ($periodAResp.Status -eq 201 -and $periodAResp.Json -and $periodAResp.Json.data -and $periodAResp.Json.data.id) {
    $periodAId = [string]$periodAResp.Json.data.id
}

Invoke-Api -Name "Finance Periods Close" -Method "PUT" -Path "/api/finance/periods/$periodAId/close" -Expected @(200) -UseAuth | Out-Null

$periodBBody = @{
    name      = "CURL-PERIOD-B-$stamp"
    startDate = "2026-02-01"
    endDate   = "2026-02-28"
} | ConvertTo-Json -Compress
$periodBResp = Invoke-Api -Name "Finance Periods Create B" -Method "POST" -Path "/api/finance/periods" -Expected @(201) -Body $periodBBody -UseAuth
if ($periodBResp.Status -eq 201 -and $periodBResp.Json -and $periodBResp.Json.data -and $periodBResp.Json.data.id) {
    $periodBId = [string]$periodBResp.Json.data.id
}

Invoke-Api -Name "Finance Accounts List" -Method "GET" -Path "/api/finance/accounts" -Expected @(200) -UseAuth | Out-Null
$accountBody = @{
    accountCode = "CURL-$stamp"
    name        = "Curl Revenue Account"
    description = "Created by curl sweep"
    accountType = "REVENUE"
} | ConvertTo-Json -Compress
$accountResp = Invoke-Api -Name "Finance Accounts Create" -Method "POST" -Path "/api/finance/accounts" -Expected @(201) -Body $accountBody -UseAuth
if ($accountResp.Status -eq 201 -and $accountResp.Json -and $accountResp.Json.data -and $accountResp.Json.data.id) {
    $accountId = [string]$accountResp.Json.data.id
}

Invoke-Api -Name "Finance CostCenters List" -Method "GET" -Path "/api/finance/cost-centers" -Expected @(200) -UseAuth | Out-Null
$costCenterBody = @{
    code        = "CC-$stamp"
    name        = "Curl Cost Center"
    description = "Created by curl sweep"
    centerType  = "OPERATIONS"
} | ConvertTo-Json -Compress
$costCenterResp = Invoke-Api -Name "Finance CostCenters Create" -Method "POST" -Path "/api/finance/cost-centers" -Expected @(201) -Body $costCenterBody -UseAuth
if ($costCenterResp.Status -eq 201 -and $costCenterResp.Json -and $costCenterResp.Json.data -and $costCenterResp.Json.data.id) {
    $costCenterId = [string]$costCenterResp.Json.data.id
}

Invoke-Api -Name "Finance Rules List" -Method "GET" -Path "/api/finance/rules" -Expected @(200) -UseAuth | Out-Null
$ruleBody = @{
    name              = "Curl Rule $stamp"
    allocationMethod  = "PROPORTIONAL"
    recognitionMethod = "IMMEDIATE"
    accountId         = $accountId
    costCenterId      = $costCenterId
    ruleConfig        = ""
} | ConvertTo-Json -Compress
$ruleResp = Invoke-Api -Name "Finance Rules Create" -Method "POST" -Path "/api/finance/rules" -Expected @(201) -Body $ruleBody -UseAuth
if ($ruleResp.Status -eq 201 -and $ruleResp.Json -and $ruleResp.Json.data -and $ruleResp.Json.data.id) {
    $ruleId = [string]$ruleResp.Json.data.id
}

$ruleUpdateBody = @{
    name              = "Curl Rule Updated $stamp"
    allocationMethod  = "PROPORTIONAL"
    recognitionMethod = "IMMEDIATE"
    accountId         = $accountId
    costCenterId      = $costCenterId
    ruleConfig        = ""
} | ConvertTo-Json -Compress
Invoke-Api -Name "Finance Rules Update" -Method "PUT" -Path "/api/finance/rules/$ruleId" -Expected @(200) -Body $ruleUpdateBody -UseAuth | Out-Null

$postingBody = @{
    periodId    = $periodBId
    sessionId   = $null
    ruleId      = $ruleId
    totalAmount = 100.00
    description = "Curl posting"
} | ConvertTo-Json -Compress
$postingResp = Invoke-Api -Name "Finance Postings Create" -Method "POST" -Path "/api/finance/postings" -Expected @(201) -Body $postingBody -UseAuth
if ($postingResp.Status -eq 201 -and $postingResp.Json -and $postingResp.Json.data -and $postingResp.Json.data.id) {
    $postingId = [string]$postingResp.Json.data.id
}

Invoke-Api -Name "Finance Postings List" -Method "GET" -Path "/api/finance/postings" -Expected @(200) -UseAuth | Out-Null
Invoke-Api -Name "Finance Postings LineItems" -Method "GET" -Path "/api/finance/postings/$postingId/line-items" -Expected @(200) -UseAuth | Out-Null
Invoke-Api -Name "Finance Postings Reverse" -Method "POST" -Path "/api/finance/postings/$postingId/reverse" -Expected @(200) -UseAuth | Out-Null

# Imports
Invoke-Api -Name "Imports Sources" -Method "GET" -Path "/api/imports/sources" -Expected @(200) -UseAuth | Out-Null
Invoke-Api -Name "Imports Jobs List" -Method "GET" -Path "/api/imports/jobs?page=0&size=5" -Expected @(200) -UseAuth | Out-Null
Invoke-Api -Name "Imports Job By ID" -Method "GET" -Path "/api/imports/jobs/$fakeImportJobId" -Expected @(404) -UseAuth | Out-Null
$importTriggerBody = @{ sourceId = $fakeSourceId; mode = "INCREMENTAL" } | ConvertTo-Json -Compress
Invoke-Api -Name "Imports Trigger" -Method "POST" -Path "/api/imports/jobs/trigger" -Expected @(404) -Body $importTriggerBody -UseAuth | Out-Null
Invoke-Api -Name "Imports CircuitBreaker" -Method "GET" -Path "/api/imports/circuit-breaker" -Expected @(200) -UseAuth | Out-Null

# Exports
$rosterExportBody = @{ sessionId = $fakeSessionId } | ConvertTo-Json -Compress
$rosterExportResp = Invoke-Api -Name "Exports Roster Create" -Method "POST" -Path "/api/exports/rosters" -Expected @(201) -Body $rosterExportBody -UseAuth
$rosterExportId = $fakeExportId
if ($rosterExportResp.Status -eq 201 -and $rosterExportResp.Json -and $rosterExportResp.Json.data -and $rosterExportResp.Json.data.id) {
    $rosterExportId = [string]$rosterExportResp.Json.data.id
}

$financeExportBody = @{ periodId = $periodBId } | ConvertTo-Json -Compress
Invoke-Api -Name "Exports FinanceReport Create" -Method "POST" -Path "/api/exports/finance-reports" -Expected @(201) -Body $financeExportBody -UseAuth | Out-Null
Invoke-Api -Name "Exports Download" -Method "GET" -Path "/api/exports/$rosterExportId/download" -Expected @(422) -UseAuth | Out-Null

$policiesResp = Invoke-Api -Name "Exports Policies List" -Method "GET" -Path "/api/exports/policies" -Expected @(200) -UseAuth
$policyId = $fakePolicyId
$updatePolicyExpected = @(404)
if ($policiesResp.Status -eq 200 -and $policiesResp.Json -and $policiesResp.Json.data -and $policiesResp.Json.data.Count -gt 0) {
    $policyId = [string]$policiesResp.Json.data[0].id
    $updatePolicyExpected = @(200)
}
$policyBody = @{ downloadAllowed = $true; watermarkTemplate = "Curl watermark" } | ConvertTo-Json -Compress
Invoke-Api -Name "Exports Policies Update" -Method "PUT" -Path "/api/exports/policies/$policyId" -Expected $updatePolicyExpected -Body $policyBody -UseAuth | Out-Null

# Audit
$auditLogsResp = Invoke-Api -Name "Audit Logs List" -Method "GET" -Path "/api/audit/logs?page=0&size=5" -Expected @(200) -UseAuth
$auditLogId = $fakeAuditId
$auditLogExpected = @(404)
if ($auditLogsResp.Status -eq 200 -and $auditLogsResp.Json -and $auditLogsResp.Json.data -and
    $auditLogsResp.Json.data.content -and $auditLogsResp.Json.data.content.Count -gt 0) {
    $auditLogId = [string]$auditLogsResp.Json.data.content[0].id
    $auditLogExpected = @(200)
}
Invoke-Api -Name "Audit Log By ID" -Method "GET" -Path "/api/audit/logs/$auditLogId" -Expected $auditLogExpected -UseAuth | Out-Null

$auditExportResp = Invoke-Api -Name "Audit Logs Export" -Method "POST" -Path "/api/audit/logs/export" -Expected @(201) -Body "{}" -UseAuth
$auditExportId = $fakeExportId
if ($auditExportResp.Status -eq 201 -and $auditExportResp.Json -and $auditExportResp.Json.data -and $auditExportResp.Json.data.id) {
    $auditExportId = [string]$auditExportResp.Json.data.id
}
Invoke-Api -Name "Audit Export Download" -Method "GET" -Path "/api/audit/exports/$auditExportId/download" -Expected @(422) -UseAuth | Out-Null

# Backups
$backupTriggerResp = Invoke-Api -Name "Backups Trigger" -Method "POST" -Path "/api/admin/backups/trigger" -Expected @(201) -UseAuth
$backupId = $fakeImportJobId
if ($backupTriggerResp.Status -eq 201 -and $backupTriggerResp.Json -and $backupTriggerResp.Json.data -and $backupTriggerResp.Json.data.id) {
    $backupId = [string]$backupTriggerResp.Json.data.id
}
Invoke-Api -Name "Backups List" -Method "GET" -Path "/api/admin/backups" -Expected @(200) -UseAuth | Out-Null
Invoke-Api -Name "Backups Get By ID" -Method "GET" -Path "/api/admin/backups/$backupId" -Expected @(200) -UseAuth | Out-Null
Invoke-Api -Name "Backups Retention" -Method "GET" -Path "/api/admin/backups/retention" -Expected @(200) -UseAuth | Out-Null

# Auth logout at end
Invoke-Api -Name "Auth Logout" -Method "POST" -Path "/api/auth/logout" -Expected @(200) -UseAuth | Out-Null

$reportJson = Join-Path $tmpDir "endpoint-results.json"
$reportCsv = Join-Path $tmpDir "endpoint-results.csv"

$results | ConvertTo-Json -Depth 6 | Out-File -LiteralPath $reportJson -Encoding utf8
$results | Export-Csv -LiteralPath $reportCsv -NoTypeInformation -Encoding utf8

$results | Format-Table Name, Method, Path, Status, Expected, Pass -AutoSize

$total = $results.Count
$passed = ($results | Where-Object { $_.Pass }).Count
$failed = $total - $passed

Write-Host ""
Write-Host "Total checks: $total"
Write-Host "Passed: $passed"
Write-Host "Failed: $failed"
Write-Host "JSON report: $reportJson"
Write-Host "CSV report:  $reportCsv"

if ($failed -gt 0) {
    exit 1
}

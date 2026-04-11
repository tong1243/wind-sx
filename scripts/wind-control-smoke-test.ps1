param(
    [string]$BaseUrl = "http://localhost:12700",
    [string]$Token = "",
    [long]$Timestamp = 0
)

if ($Timestamp -eq 0) {
    $Timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
}

$headers = @{}
if (![string]::IsNullOrWhiteSpace($Token)) {
    $headers["Authorization"] = "Bearer $Token"
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        $Body = $null
    )
    $url = "$BaseUrl$Path"
    Write-Host "`n[$Method] $url"
    if ($Body -ne $null) {
        $json = $Body | ConvertTo-Json -Depth 10
        Write-Host "Body: $json"
        return Invoke-RestMethod -Method $Method -Uri $url -Headers $headers -ContentType "application/json" -Body $json
    }
    return Invoke-RestMethod -Method $Method -Uri $url -Headers $headers
}

try {
    $r1 = Invoke-Api -Method "GET" -Path "/api/v1/road-status/overview?timestamp=$Timestamp"
    $r2 = Invoke-Api -Method "GET" -Path "/api/v1/wind-impact/visualization?timestamp=$Timestamp&mode=real"
    $r3 = Invoke-Api -Method "PUT" -Path "/api/v1/wind-impact/speed-thresholds" -Body @{
        windLevel = 9
        passengerSpeedLimit = 55
        freightSpeedLimit = 45
        dangerousGoodsSpeedLimit = 35
    }
    $r4 = Invoke-Api -Method "GET" -Path "/api/v1/resource-library/staff"
    $r5 = Invoke-Api -Method "GET" -Path "/api/v1/control-plan-library/plans"
    $r6 = Invoke-Api -Method "POST" -Path "/api/v1/control-execution/generate" -Body @{
        timestamp = $Timestamp
        segment = "HS-G30 K3020-K3030"
        realtimeWindLevel = 9
        forecastMaxWindLevel = 10
    }

    $planId = $r6.data.planId
    if ($planId) {
        $r7 = Invoke-Api -Method "POST" -Path "/api/v1/control-execution/publish/$planId"
    }

    $r8 = Invoke-Api -Method "GET" -Path "/api/v1/control-execution/event-records"
    Write-Host "`nSmoke test completed."
}
catch {
    Write-Host "`nSmoke test failed: $($_.Exception.Message)"
    throw
}

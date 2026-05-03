param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Title = "真实大模型联调-订单履约需求",
    [string]$ReportDir = "docs/reports",
    [switch]$AllowMock
)

$ErrorActionPreference = "Stop"

function Invoke-AiTestOpsApi {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body = $null
    )

    $uri = "$BaseUrl$Path"
    $parameters = @{
        Method = $Method
        Uri = $uri
        ContentType = "application/json; charset=utf-8"
    }
    if ($null -ne $Body) {
        $parameters.Body = ($Body | ConvertTo-Json -Depth 50)
    }

    $response = Invoke-RestMethod @parameters
    if ($response.code -ne 0) {
        throw "API failed: $Method $Path, code=$($response.code), message=$($response.message)"
    }
    return $response.data
}

$requirementText = @"
订单履约系统需要支持用户提交订单、库存校验、优惠券抵扣和超时取消。
1. 用户已登录且收货地址有效时，可以提交订单。
2. 商品库存必须大于等于购买数量，库存不足时提交失败并提示库存不足。
3. 优惠券只能在有效期内使用，且订单金额必须满足优惠券门槛。
4. 订单提交成功后状态为 CREATED，15 分钟未支付自动取消并释放库存。
5. 已取消订单不允许再次支付。
6. 同一用户对同一购物车请求重复提交时，系统必须保证幂等，不能生成重复订单。
"@

$startedAt = Get-Date
Write-Host "Checking AI provider..."
$health = Invoke-AiTestOpsApi -Method "Post" -Path "/api/ai-testops/ai/health/check"
if ($health.mockMode -and -not $AllowMock) {
    throw "Current AI_PROVIDER is MOCK. Configure real AI_PROVIDER/AI_API_BASE/AI_API_KEY/AI_MODEL_NAME, or rerun with -AllowMock."
}
if (-not $health.reachable) {
    throw "AI provider is not reachable: $($health.message)"
}

Write-Host "Creating text document..."
$document = Invoke-AiTestOpsApi -Method "Post" -Path "/api/ai-testops/documents/text" -Body @{
    title = $Title
    content = $requirementText
}

Write-Host "Parsing document $($document.documentId)..."
$parse = Invoke-AiTestOpsApi -Method "Post" -Path "/api/ai-testops/documents/$($document.documentId)/parse"

Write-Host "Extracting requirements..."
$extract = Invoke-AiTestOpsApi -Method "Post" -Path "/api/ai-testops/ai/requirements/extract" -Body @{
    documentId = $document.documentId
    modelCode = "default"
    promptTemplateCode = "REQUIREMENT_EXTRACT"
}
$extractRecord = Invoke-AiTestOpsApi -Method "Get" -Path "/api/ai-testops/generations/$($extract.generationId)"

Write-Host "Generating test cases..."
$generation = Invoke-AiTestOpsApi -Method "Post" -Path "/api/ai-testops/testcases/generate" -Body @{
    documentId = $document.documentId
    requirementExtractId = $extract.requirementExtractId
    modelCode = "default"
    promptTemplateCode = "TEST_CASE_GENERATE"
}
$caseRecord = Invoke-AiTestOpsApi -Method "Get" -Path "/api/ai-testops/generations/$($generation.generationId)"
$validations = Invoke-AiTestOpsApi -Method "Get" -Path "/api/ai-testops/validations/$($generation.generationId)"
$drafts = Invoke-AiTestOpsApi -Method "Get" -Path "/api/ai-testops/testcases/drafts?generationId=$($generation.generationId)"

$failedValidationCount = @($validations | Where-Object { $_.status -eq "FAILED" }).Count
$report = [ordered]@{
    startedAt = $startedAt.ToString("s")
    finishedAt = (Get-Date).ToString("s")
    modelName = $health.modelName
    provider = $health.provider
    health = $health
    documentId = $document.documentId
    inputTextLength = $requirementText.Length
    parseStatus = $parse.parseStatus
    requirementExtractId = $extract.requirementExtractId
    requirementGenerationId = $extract.generationId
    requirementTokenInput = $extractRecord.tokenInput
    requirementTokenOutput = $extractRecord.tokenOutput
    testCaseGenerationId = $generation.generationId
    testCaseTokenInput = $caseRecord.tokenInput
    testCaseTokenOutput = $caseRecord.tokenOutput
    draftCount = @($drafts).Count
    validationCount = @($validations).Count
    failedValidationCount = $failedValidationCount
    validations = $validations
}

New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null
$reportPath = Join-Path $ReportDir ("real-llm-smoke-{0}.json" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
$report | ConvertTo-Json -Depth 50 | Set-Content -Path $reportPath -Encoding utf8

Write-Host "Real LLM smoke finished."
Write-Host "Report: $reportPath"
Write-Host "Draft count: $($report.draftCount), failed validations: $failedValidationCount"

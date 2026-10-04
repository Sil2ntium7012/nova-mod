# Nova-Mod 빌드 스크립트 (49-40차)
#   .\build.cmd            → 40개 버전 전체 빌드
#   .\build.cmd 1.20.1     → 한 버전만 (:v1_20_1:build)
#   .\build.cmd 1.20.1 1.21.11 → 여러 버전
# 시작할 때 이전 로그(build*.log, *_build*.txt 등)를 전부 지우고, 이번 결과만 build.log 하나에 남긴다.
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Versions = @())

Set-Location $PSScriptRoot

# 1. 옛 로그 정리 - 이 폴더에 쌓인 빌드 로그/점검 파일만(소스·그래들 파일은 건드리지 않음)
$oldLogs = Get-ChildItem -Path $PSScriptRoot -File | Where-Object {
    $_.Name -match '^build.*\.log$' -or
    $_.Name -match '^build-log\.txt$' -or
    $_.Name -match '^(fix|input|lock|map)_build\.txt$' -or
    $_.Name -match '^full_build_check\d*\.txt$'
}
foreach ($f in $oldLogs) {
    Remove-Item -LiteralPath $f.FullName -Force -ErrorAction SilentlyContinue
}
if ($oldLogs.Count -gt 0) { Write-Host ("이전 로그 " + $oldLogs.Count + "개 삭제") -ForegroundColor DarkGray }

# 1-1. 49-147차: 안 쓰는 소스 지우기 - obsolete-files.txt 에 적힌 파일/폴더를 빌드 전에 지운다.
#      (한 줄에 하나, Nova-Mod 폴더 기준 경로, # 뒤는 설명. 이미 없으면 그냥 넘어감)
#      Claude가 파일을 빼야 할 때는 이 목록에 한 줄 더하고, 다음 빌드 때 자동으로 지워진다.
$obsList = Join-Path $PSScriptRoot "obsolete-files.txt"
if (Test-Path -LiteralPath $obsList) {
    $removed = 0
    foreach ($line in Get-Content -LiteralPath $obsList -Encoding UTF8) {
        $rel = ($line -replace '#.*$', '').Trim()
        if ($rel -eq '' -or $rel.Contains('..')) { continue }
        $full = Join-Path $PSScriptRoot $rel
        if (Test-Path -LiteralPath $full) {
            Remove-Item -LiteralPath $full -Recurse -Force -ErrorAction SilentlyContinue
            Write-Host ("안 쓰는 파일 삭제: " + $rel) -ForegroundColor DarkGray
            $removed++
        }
    }
    if ($removed -gt 0) { Write-Host ("안 쓰는 파일 " + $removed + "개 삭제") -ForegroundColor DarkGray }
}

# 2. 태스크 결정
if ($Versions.Count -eq 0) {
    $tasks = @("build")
    $label = "전체(40개 버전)"
} else {
    # 49-164차: 버전 하나면 ForEach-Object 결과가 배열이 아니라 문자열 하나가 되고, 그걸 @tasks로 펼쳐 넘기니
    # gradle에 ":"만 들어갔다("Cannot locate tasks that match ':'"). 늘 배열로 만들고 splat 없이 그대로 넘긴다.
    $tasks = @($Versions | ForEach-Object { ":v" + ($_ -replace '\.', '_') + ":build" })
    $label = ($Versions -join ", ")
}
Write-Host ("gradle 태스크: " + ($tasks -join " ")) -ForegroundColor DarkGray
$log = Join-Path $PSScriptRoot "build.log"
Write-Host ("빌드 시작: " + $label + "  →  build.log") -ForegroundColor Cyan

# 3. 빌드 (UTF-8 로그 하나)
#    ⚠️ 49-51차: --continue 를 붙인다. 이게 없으면 한 버전이 깨지는 순간 Gradle이 멈춰서
#    그 뒤 버전들은 아예 시도조차 안 한다(49-50차 빌드가 1.18 근처에서 멈춰 40개 중 12개만 돌았고,
#    나머지 28개에 오류가 있는지 없는지 알 수 없는 상태로 끝났다). --continue 면 한 번 돌려서
#    "깨지는 것 전부"를 한 로그에 모아 볼 수 있다.
$sw = [System.Diagnostics.Stopwatch]::StartNew()
& .\gradlew.bat $tasks --console=plain --continue *>&1 | Out-File -Encoding utf8 $log
$sw.Stop()

# 4. 결과 요약
$ok = Select-String -Path $log -Pattern 'BUILD SUCCESSFUL' -Quiet
$mins = [math]::Round($sw.Elapsed.TotalMinutes, 1)
if ($ok) {
    Write-Host ("BUILD SUCCESSFUL (" + $mins + "분) - jar는 런처 novamod 폴더에 자동 복사됨") -ForegroundColor Green
} else {
    Write-Host ("BUILD FAILED (" + $mins + "분) - build.log 를 그대로 보여 주세요.") -ForegroundColor Red
    # 49-51차: 어느 버전이 깨졌는지부터(--continue 라 여러 개일 수 있다)
    $failed = Select-String -Path $log -Pattern "Execution failed for task '(:[^']+)'" |
        ForEach-Object { $_.Matches[0].Groups[1].Value } | Select-Object -Unique
    if ($failed) { Write-Host ("실패한 태스크: " + ($failed -join ", ")) -ForegroundColor Yellow }
    Write-Host "아래는 오류 부분:" -ForegroundColor Red
    Select-String -Path $log -Pattern 'error:|FAILED|What went wrong|Exception' -Context 0, 4 |
        Select-Object -First 25 | ForEach-Object { $_.Line; $_.Context.PostContext }
}
exit ($(if ($ok) { 0 } else { 1 }))

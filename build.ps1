# Nova-Mod 빌드 스크립트 (49-40차, 49-369차 바뀐 버전만)
#   .\build.cmd                → 지난 빌드 뒤 바뀐 파일이 닿는 버전만 빌드(49-369차)
#   .\build.cmd all            → 40개 버전 전체 빌드
#   .\build.cmd 1.20.1         → 한 버전만 (:v1_20_1:build)
#   .\build.cmd 1.20.1 1.21.11 → 여러 버전
# 시작할 때 이전 로그(build*.log, *_build*.txt 등)를 전부 지우고, 이번 결과만 build.log 하나에 남긴다.
#
# 49-369차(사용자: "패치된 부분만 따로 확인해서 그 버전들만 하는 방법은 안 되나?"): 버전마다 마지막으로 빌드에 성공한 시각을
# .build-stamps.json 에 적어 두고, 그 뒤에 바뀐 파일이 그 버전이 쓰는 곳에 있으면 그 버전만 빌드한다.
#   - 공통(전부): build.gradle, settings.gradle, gradle.properties, gradle/, libs/, src/main/
#   - 1.14.4~1.21.11: src/client/, compat/, loom-common.gradle
#   - 26.x: src-26x/, loom-common-modern.gradle
#   - 그 버전 폴더(versions/<버전>)의 build.gradle, gradle.properties
#   - 런처 novamod 폴더에 그 버전 jar가 없어도 빌드
# 일부만 빌드할 때는 --configure-on-demand로 그 버전들만 설정한다(40개 Loom 설정을 건너뜀). 설정 단계에서 실패하면 그 옵션 없이 한 번 더.
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

# 2. 버전 목록(versions/<숫자로 시작하는 폴더> 중 build.gradle 있는 것)과 26.x 구분
$allVersions = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot "versions") -Directory |
    Where-Object { $_.Name -match '^\d' -and (Test-Path -LiteralPath (Join-Path $_.FullName "build.gradle")) } |
    ForEach-Object { $_.Name })
function Test-Modern([string]$v) {
    $bg = Join-Path $PSScriptRoot ("versions\" + $v + "\build.gradle")
    return [bool](Select-String -LiteralPath $bg -Pattern 'loom-common-modern' -Quiet)
}
function Safe([string]$v) { return "v" + ($v -replace '\.', '_') }

# 마지막 성공 시각(.build-stamps.json: { "1.20.1": "2026-10-16T12:00:00.0000000Z", ... })
$stampFile = Join-Path $PSScriptRoot ".build-stamps.json"
$stamps = @{}
if (Test-Path -LiteralPath $stampFile) {
    try {
        $obj = Get-Content -LiteralPath $stampFile -Raw -Encoding UTF8 | ConvertFrom-Json
        foreach ($p in $obj.PSObject.Properties) {
            $d = if ($p.Value -is [datetime]) { $p.Value } else { [datetime]::Parse([string]$p.Value, [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::RoundtripKind) }
            $stamps[$p.Name] = $d.ToUniversalTime()
        }
    } catch { $stamps = @{} }
}

# 폴더/파일들 중 가장 최근에 바뀐 시각(UTC). build/.gradle 같은 출력 폴더는 안 본다.
# 폴더 시각도 본다 - 파일을 지우거나 이름을 바꾸면 그 폴더 시각이 바뀌어서, 지운 것도 잡힌다.
function Get-Newest([string[]]$rels) {
    $newest = [datetime]::MinValue
    foreach ($rel in $rels) {
        $full = Join-Path $PSScriptRoot $rel
        if (-not (Test-Path -LiteralPath $full)) { continue }
        $item = Get-Item -LiteralPath $full
        if ($item.PSIsContainer) {
            if ($item.LastWriteTimeUtc -gt $newest) { $newest = $item.LastWriteTimeUtc }
            $files = Get-ChildItem -LiteralPath $full -Recurse -ErrorAction SilentlyContinue |
                Where-Object { $_.FullName -notmatch '\\(build|\.gradle|run|out|bin)\\' }
            foreach ($f in $files) { if ($f.LastWriteTimeUtc -gt $newest) { $newest = $f.LastWriteTimeUtc } }
        } elseif ($item.LastWriteTimeUtc -gt $newest) {
            $newest = $item.LastWriteTimeUtc
        }
    }
    return $newest
}

$partial = $true
if ($Versions.Count -eq 0) {
    # 49-369차: 자동 - 바뀐 곳이 닿는 버전만
    $tCommon = Get-Newest @("build.gradle", "settings.gradle", "gradle.properties", "gradle", "libs", "src\main")
    $tMain = Get-Newest @("src\client", "compat", "loom-common.gradle")
    $tModern = Get-Newest @("src-26x", "loom-common-modern.gradle")
    $jarDir = Join-Path $env:APPDATA "NovaClient\novamod"
    $pick = @()
    foreach ($v in $allVersions) {
        $t = $tCommon
        $tree = if (Test-Modern $v) { $tModern } else { $tMain }
        if ($tree -gt $t) { $t = $tree }
        $tv = Get-Newest @(("versions\" + $v + "\build.gradle"), ("versions\" + $v + "\gradle.properties"))
        if ($tv -gt $t) { $t = $tv }
        $hasJar = (Test-Path -LiteralPath $jarDir) -and (@(Get-ChildItem -LiteralPath $jarDir -Filter ("builtin-mod-" + $v + "-*.jar") -ErrorAction SilentlyContinue).Count -gt 0)
        if (-not $stamps.ContainsKey($v) -or $t -gt $stamps[$v] -or -not $hasJar) { $pick += $v }
    }
    if ($pick.Count -eq 0) {
        Write-Host "지난 빌드 뒤 바뀐 파일이 없어 빌드할 버전이 없습니다 (전부 다시: .\build.cmd all)" -ForegroundColor Green
        exit 0
    }
    if ($pick.Count -eq $allVersions.Count) {
        $partial = $false
        $tasks = @("build")
        $label = "바뀐 버전: 전체(" + $pick.Count + "개)"
    } else {
        $tasks = @($pick | ForEach-Object { ":" + (Safe $_) + ":build" })
        $label = "바뀐 버전 " + $pick.Count + "개: " + ($pick -join ", ")
    }
    $built = $pick
} elseif ($Versions.Count -eq 1 -and ($Versions[0] -eq "all" -or $Versions[0] -eq "전체")) {
    $partial = $false
    $tasks = @("build")
    $label = "전체(" + $allVersions.Count + "개 버전)"
    $built = $allVersions
} else {
    # 49-164차: 버전 하나면 ForEach-Object 결과가 배열이 아니라 문자열 하나가 되고, 그걸 @tasks로 펼쳐 넘기니
    # gradle에 ":"만 들어갔다("Cannot locate tasks that match ':'"). 늘 배열로 만들고 splat 없이 그대로 넘긴다.
    $tasks = @($Versions | ForEach-Object { ":" + (Safe $_) + ":build" })
    $label = ($Versions -join ", ")
    $built = @($Versions)
}
Write-Host ("gradle 태스크: " + ($tasks -join " ")) -ForegroundColor DarkGray
$log = Join-Path $PSScriptRoot "build.log"
Write-Host ("빌드 시작: " + $label + "  →  build.log") -ForegroundColor Cyan

# 3. 빌드 (UTF-8 로그 하나)
#    ⚠️ 49-51차: --continue 를 붙인다. 이게 없으면 한 버전이 깨지는 순간 Gradle이 멈춰서
#    그 뒤 버전들은 아예 시도조차 안 한다(49-50차 빌드가 1.18 근처에서 멈춰 40개 중 12개만 돌았고,
#    나머지 28개에 오류가 있는지 없는지 알 수 없는 상태로 끝났다). --continue 면 한 번 돌려서
#    "깨지는 것 전부"를 한 로그에 모아 볼 수 있다.
$started = (Get-Date).ToUniversalTime()   # 빌드 중에 고친 파일은 다음 빌드에 잡히도록 시작 시각을 적는다
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$gradleArgs = @($tasks) + @("--console=plain", "--continue")
if ($partial) { $gradleArgs += "--configure-on-demand" }
& .\gradlew.bat $gradleArgs *>&1 | Out-File -Encoding utf8 $log
$ok = Select-String -Path $log -Pattern 'BUILD SUCCESSFUL' -Quiet
if (-not $ok -and $partial -and -not (Select-String -Path $log -Pattern "Execution failed for task ':v" -Quiet)) {
    # 49-369차: 버전 태스크까지 못 가고 설정 단계에서 깨졌다 - --configure-on-demand 없이 한 번 더
    Write-Host "설정 단계 실패 - 일부만 설정하는 옵션 없이 다시 빌드합니다" -ForegroundColor Yellow
    & .\gradlew.bat $tasks --console=plain --continue *>&1 | Out-File -Encoding utf8 $log
    $ok = Select-String -Path $log -Pattern 'BUILD SUCCESSFUL' -Quiet
}
$sw.Stop()

# 3-1. 49-369차: 성공한 버전만 시각을 적는다(실패한 버전은 다음 빌드에 다시 잡힌다)
$failedVersions = @{}
Select-String -Path $log -Pattern "Execution failed for task ':(v[0-9_]+):" | ForEach-Object {
    $failedVersions[$_.Matches[0].Groups[1].Value] = $true
}
$configFailed = (-not $ok) -and ($failedVersions.Count -eq 0)
if (-not $configFailed) {
    foreach ($v in $built) {
        if (-not $failedVersions.ContainsKey((Safe $v))) { $stamps[$v] = $started }
    }
    $out = [ordered]@{}
    foreach ($k in ($stamps.Keys | Sort-Object)) { $out[$k] = $stamps[$k].ToString("o") }
    ($out | ConvertTo-Json) | Set-Content -LiteralPath $stampFile -Encoding UTF8
}

# 4. 결과 요약
$mins = [math]::Round($sw.Elapsed.TotalMinutes, 1)
if ($ok) {
    Write-Host ("BUILD SUCCESSFUL (" + $mins + "분, " + $built.Count + "개 버전) - jar는 런처 novamod 폴더에 자동 복사됨") -ForegroundColor Green
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

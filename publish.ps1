# Nova-Mod 올리기(49-249차): 소스를 GitHub(Sil2ntium7012/nova-mod)에 올리고, 빌드한 jar를 릴리스 "latest"에 붙인다.
#   .\publish.cmd                 → 소스 커밋 + 푸시 + 새로 빌드된 jar 올리기
#   .\publish.cmd "고친 내용"      → 커밋 메시지를 직접 적기
#   .\publish.cmd -SkipJars       → 소스만 올리기
# 처음 한 번: 이 폴더를 git 저장소로 만들고 origin을 nova-mod로 잡는다(이미 있으면 그대로).
# jar 올리기는 GitHub CLI(gh)가 있어야 한다. 없으면 소스만 올리고 설치 방법을 알려 준다.
param(
    [Parameter(Position = 0)][string]$Message = "",
    [switch]$SkipJars
)

$ErrorActionPreference = "Continue"   # git은 진행 상황을 stderr로 내보내서 Stop이면 PowerShell 5가 오류로 멈춘다 - 종료 코드로만 판단
Set-Location $PSScriptRoot
$RepoUrl = "https://github.com/Sil2ntium7012/nova-mod.git"
$RepoName = "Sil2ntium7012/nova-mod"
$Branch = "main"

function Run([string]$exe, [string[]]$argv) {
    & $exe @argv
    if ($LASTEXITCODE -ne 0) { throw ("실패: " + $exe + " " + ($argv -join " ")) }
}

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Write-Host "git이 없습니다. https://git-scm.com 에서 설치한 뒤 다시 실행하세요." -ForegroundColor Red
    exit 1
}

# 1. 저장소 준비
if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot ".git"))) {
    Write-Host "처음 실행: git 저장소를 만듭니다" -ForegroundColor Cyan
    Run git @("init", "-b", $Branch)
}
$origin = (& git remote get-url origin 2>$null)
if (-not $origin) {
    Run git @("remote", "add", "origin", $RepoUrl)
} elseif ($origin -ne $RepoUrl) {
    Run git @("remote", "set-url", "origin", $RepoUrl)
}
& git config core.quotepath false | Out-Null
& git config core.autocrlf false | Out-Null

# 2. 원격에 이미 있는 것(README 등) 먼저 받기 - 처음엔 서로 다른 기록이라 허용
& git fetch origin $Branch 2>$null
$hasRemote = ($LASTEXITCODE -eq 0)
$hasLocal = $true
& git rev-parse --verify HEAD 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) { $hasLocal = $false }

# 3. 커밋
Run git @("add", "-A")
& git diff --cached --quiet
$changed = ($LASTEXITCODE -ne 0)
if ($changed) {
    if ($Message -eq "") { $Message = "Nova-Mod 업데이트 " + (Get-Date -Format "yyyy-MM-dd HH:mm") }
    Run git @("commit", "-m", $Message)
    Write-Host ("커밋: " + $Message) -ForegroundColor Green
} else {
    Write-Host "바뀐 소스 없음 - 커밋 건너뜀" -ForegroundColor DarkGray
}

if ($hasRemote) {
    if (-not $hasLocal) {
        Run git @("pull", "--no-rebase", "--allow-unrelated-histories", "-X", "ours", "--no-edit", "origin", $Branch)
    } else {
        Run git @("pull", "--no-rebase", "-X", "ours", "--no-edit", "origin", $Branch)
    }
}

# 4. 푸시
Run git @("push", "-u", "origin", $Branch)
Write-Host ("소스 올림: https://github.com/" + $RepoName) -ForegroundColor Green

# 5. jar 올리기(릴리스 latest) - 지난번 올린 뒤 새로 빌드된 것만
if ($SkipJars) { exit 0 }
$jarDir = Join-Path $env:APPDATA "NovaClient\novamod"
if (-not (Test-Path -LiteralPath $jarDir)) {
    Write-Host ("jar 폴더가 없습니다: " + $jarDir + " (먼저 build.cmd)") -ForegroundColor Yellow
    exit 0
}
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Host "GitHub CLI(gh)가 없어 jar는 안 올렸습니다." -ForegroundColor Yellow
    Write-Host "  설치: winget install GitHub.cli   그다음 한 번: gh auth login" -ForegroundColor Yellow
    exit 0
}
$stampFile = Join-Path $PSScriptRoot ".publish-stamp"
$since = [datetime]::MinValue
if (Test-Path -LiteralPath $stampFile) {
    try { $since = [datetime]::Parse((Get-Content -LiteralPath $stampFile -Raw).Trim()) } catch { }
}
$jars = @(Get-ChildItem -LiteralPath $jarDir -Filter "builtin-mod-*.jar" | Where-Object { $_.LastWriteTime -gt $since })
if ($jars.Count -eq 0) {
    Write-Host "새로 빌드된 jar 없음 - jar 건너뜀" -ForegroundColor DarkGray
    exit 0
}
& gh release view latest --repo $RepoName *> $null
if ($LASTEXITCODE -ne 0) {
    Run gh @("release", "create", "latest", "--repo", $RepoName, "--title", "최신 빌드", "--notes", "build.cmd로 빌드한 버전별 jar. publish.cmd가 새로 빌드된 것만 덮어씁니다.")
}
Write-Host ("jar " + $jars.Count + "개 올리는 중...") -ForegroundColor Cyan
$paths = @($jars | ForEach-Object { $_.FullName })
Run gh (@("release", "upload", "latest", "--repo", $RepoName, "--clobber") + $paths)
(Get-Date).ToString("o") | Set-Content -LiteralPath $stampFile -Encoding ascii
Write-Host ("jar 올림: https://github.com/" + $RepoName + "/releases/tag/latest") -ForegroundColor Green

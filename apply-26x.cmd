@echo off
rem 49-43차: src-26x(26.x 전용 트리)를 통째로 새 이식본으로 교체합니다.
rem   1) 기존 src-26x\client\java\kr 와 src-26x\client\resources 를 지우고
rem   2) src-26x.zip 을 src-26x\ 아래에 풉니다 (client\java\kr\..., client\resources\lunaslight\yarnmap.txt)
cd /d "%~dp0"
if not exist src-26x.zip (
  echo src-26x.zip 이 없습니다.
  exit /b 1
)
if exist src-26x\client\java\kr rmdir /s /q src-26x\client\java\kr
if exist src-26x\client\resources rmdir /s /q src-26x\client\resources
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force -LiteralPath 'src-26x.zip' -DestinationPath 'src-26x'"
if errorlevel 1 (
  echo 압축 해제 실패
  exit /b 1
)
echo src-26x 교체 완료 - 이제 .\build.cmd 26.2 (또는 .\build.cmd 26.1 26.1.1 26.1.2 26.2) 로 빌드하세요.

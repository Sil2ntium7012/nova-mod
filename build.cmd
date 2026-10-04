@echo off
rem Nova-Mod 빌드: 옛 로그 지우고 build.log 하나만 남김.  사용법: build.cmd [1.20.1 [1.21.11 ...]]
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1" %*

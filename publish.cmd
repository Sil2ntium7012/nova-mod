@echo off
rem Nova-Mod publish: source to GitHub nova-mod + built jars to release "latest". usage: publish.cmd ["message"] [-SkipJars]
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0publish.ps1" %*

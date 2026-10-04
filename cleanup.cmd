@echo off
rem ============================================================================
rem 49-44차: Nova-Mod 폴더의 죽은 파일·찌꺼기 정리
rem   여기 적힌 것들은 전부 "어디서도 참조하지 않는" 것만 골랐습니다(빌드에 영향 없음).
rem   지우기 전에 무엇을 지울지 보여주고, 엔터를 눌러야 실제로 지웁니다.
rem   되돌릴 수 없으니 필요 없다고 판단되는 줄은 미리 rem 으로 주석 처리하세요.
rem ============================================================================
cd /d "%~dp0"
echo.
echo [Nova-Mod 정리] 다음을 지웁니다:
echo.
echo   1) 참조가 끊긴 소스 6개
echo      - module\impl\render\GuiBlurModule.java        (49-33차부터 빈 껍데기)
echo      - module\impl\misc\VoiceChatIntegrationModule.java (49-44차 등록 해제)
echo      - gui\NovaRecipeScreen.java                     (49-39차부터 미참조)
echo      - module\impl\inventory\InventoryAnimationsModule.java (49-47차 제거)
echo      - util\ScreenAnimHook.java                      (49-47차 제거)
echo      - mixin\HandledScreenAnimMixin.java             (49-47차 제거)
echo   2) 쓰지 않는 리소스
echo      - src\main\resources\nova_modern\              (폴더)
echo      - assets\novaclient\font\body.ttf              (nova_modern 전용이었음)
echo      - assets\novaclient\font\ui.json               (배율별 ui1~ui6만 씀)
echo   3) 옛 작업 폴더
echo      - versions\_reverse_1_21_11\                   (폴더)
echo      - _recovered-yarn-src\                         (폴더)
echo      - migrated-26x\                                (폴더, 비어 있음)
echo   4) 빌드 로그·중간 덤프 9개 (build-*.log, *_build.txt 등)
echo.
pause

set SRC=src\client\java\kr\novaclient\mod
if exist "%SRC%\module\impl\render\GuiBlurModule.java" del /q "%SRC%\module\impl\render\GuiBlurModule.java"
if exist "%SRC%\module\impl\misc\VoiceChatIntegrationModule.java" del /q "%SRC%\module\impl\misc\VoiceChatIntegrationModule.java"
if exist "%SRC%\gui\NovaRecipeScreen.java" del /q "%SRC%\gui\NovaRecipeScreen.java"
if exist "%SRC%\module\impl\inventory\InventoryAnimationsModule.java" del /q "%SRC%\module\impl\inventory\InventoryAnimationsModule.java"
if exist "%SRC%\util\ScreenAnimHook.java" del /q "%SRC%\util\ScreenAnimHook.java"
if exist "%SRC%\mixin\HandledScreenAnimMixin.java" del /q "%SRC%\mixin\HandledScreenAnimMixin.java"

set RES=src\main\resources
if exist "%RES%\nova_modern" rmdir /s /q "%RES%\nova_modern"
if exist "%RES%\assets\novaclient\font\body.ttf" del /q "%RES%\assets\novaclient\font\body.ttf"
if exist "%RES%\assets\novaclient\font\ui.json" del /q "%RES%\assets\novaclient\font\ui.json"

if exist "versions\_reverse_1_21_11" rmdir /s /q "versions\_reverse_1_21_11"
if exist "_recovered-yarn-src" rmdir /s /q "_recovered-yarn-src"
if exist "migrated-26x" rmdir /s /q "migrated-26x"
if exist "ImplTmp" rmdir /s /q "ImplTmp"
if exist "src\ImplTmp" rmdir /s /q "src\ImplTmp"

for %%F in (build-1211.log build-26x.log build-all.log build-log.txt fix_build.txt full_build_check5.txt input_build.txt lock_build.txt map_build.txt) do (
  if exist "%%F" del /q "%%F"
)

echo.
echo 정리 완료. 이어서 .\build.cmd 로 한 버전만 빌드해 이상 없는지 확인하세요.
pause

# Nova Client 내장 전용 모드 (novaclient-mod)

Nova Client 런처가 조립하는 게임 인스턴스에만 들어가는 "숨은" Fabric 모드입니다.
Mod Menu의 기본 모드 목록에는 `library` 배지로 접혀서 안 보이도록 되어 있습니다
(`fabric.mod.json`의 `custom.modmenu.badge`). 완전히 안 보이게 하려면 애초에
Mod Menu 자체를 런처가 노출 안 시키는 게 제일 확실합니다(런처가 이미 자체 설정
UI를 갖고 있으니 자연스러움).

설정 화면은 게임 안에서 **Right Shift** 키로 엽니다(카테고리 탭 + 기능 온/오프
스위치 + 세부 설정, `kr.novaclient.mod.gui.NovaClientScreen`).

## ⚠️ 가장 먼저 읽어야 할 것 - 이 코드의 검증 상태

이 프로젝트는 **외부 네트워크가 완전히 막힌 샌드박스 환경**에서 작성되었습니다.
Mojang/Fabric 서버(Minecraft 라이브러리, Yarn 매핑, Fabric API 등)에 전혀 접근할
수 없어서, **이 세션에서는 단 한 번도 실제로 컴파일/실행해보지 못했습니다.**
(참고로 같은 이유로 Nova Client 런처 쪽 Electron 코드도 지금까지 항상 "실제 실행은
못 해보고 코드만 전달" 패턴이었던 것과 동일한 제약입니다.)

즉:
- 문법 오류(중괄호 짝, 패키지 선언 등)는 스크립트로 기계적으로 검사해서 문제 없음을 확인했습니다.
- 하지만 **Minecraft/Fabric API의 정확한 메서드 이름·시그니처**는 제 지식(1.21.1 정식
  버전 기준, 다만 실시간 검증은 불가)에 의존했고, 실제로는 조금 다를 수 있습니다.
- 본인 PC(IntelliJ + Fabric 플러그인, 인터넷 연결됨)에서 처음 열면 Gradle sync가
  실패하거나, 빌드 시 `cannot find symbol` 에러가 몇 군데 날 수 있습니다. **이건
  정상입니다** - Fabric 모드 개발은 원래 Yarn 매핑 이름을 IDE 자동완성으로 확인하며
  고쳐나가는 과정이 있고, 이번엔 그 "확인" 단계를 거치지 못한 채 전달하는 것뿐입니다.
- 각 파일 맨 위에 `⚠️ 컴파일 확인 필요` 주석이 있는 곳이 그 확률이 높은 지점입니다.
  아래 "기능별 구현 상태" 표에도 정리해뒀습니다.

## 처음 여는 방법 (본인 PC)

1. IntelliJ IDEA + [Fabric 개발 플러그인](https://plugins.jetbrains.com/plugin/25390) 설치
   (또는 플러그인 없이도 Gradle 프로젝트로 바로 열립니다)
2. 이 폴더를 IntelliJ에서 "Open"으로 열기 → Gradle sync 자동 진행(인터넷 필요,
   Minecraft/Yarn/Fabric API를 실제로 받아옴)
3. `gradle.properties`에 적어둔 버전 조합이 최신인지 먼저
   https://fabricmc.net/develop/ 에서 한 번 확인 권장(이 세션에서 확인한 값이지만
   시간이 지나면 최신 패치로 올려주는 게 좋음)
4. sync가 끝나면 `genSources` 관련 Gradle 태스크(Fabric Loom이 자동으로 걸어줌)로
   실제 Minecraft 소스에 IDE에서 점프해볼 수 있음 → 위 "확인 필요" 주석이 달린 지점들을
   실제 이름과 대조
5. `./gradlew build` (또는 IntelliJ의 Gradle 패널에서 build) → 에러 나는 지점부터
   하나씩 실제 이름으로 교정
6. `./gradlew runClient`로 테스트용 클라이언트 실행 가능

> gradlew 래퍼 스크립트/jar는 이 세션에 없습니다(다운로드가 막혀서). 로컬에 Gradle이
> 설치되어 있다면 `gradle wrapper --gradle-version 8.10.2` 한 번 실행해서 만들면 됩니다.

## Nova Client 런처와 합치는 법

이 모드는 런처가 만드는 게임 인스턴스의 `mods/` 폴더에 빌드된 jar
(`build/libs/novaclient-mod-<version>.jar`)를 넣고, Fabric Loader + Fabric API를
같이 설치해두면 됩니다. 런처가 이미 버전별 모드 목록을 관리하고 있다면, 이 jar를
"항상 강제 포함되는 내장 모드" 취급으로 넣는 걸 추천합니다(사용자가 껐다 켰다 못하게).

## 기능별 구현 상태

범례: ✅ 사실상 완성(Fabric 공식 API만 사용해서 신뢰도 높음) / 🟡 부분 구현(핵심 로직은
있지만 실제 렌더/후킹 지점이 mixin 확인 필요) / ⚪ 설정 뼈대만(정직하게 TODO로 남김,
가짜 동작 없음)

| # | 요청 기능 | 클래스 | 상태 |
|---|---|---|---|
| 1 | 키바인드→명령어 10슬롯 | `misc.CustomKeybindsModule` | ✅ |
| 2 | 크로스헤어 대상 테두리 | `render.CrosshairOutlineModule` | 🟡 (WorldRenderContext API명 확인 필요) |
| 3 | 감마 조절 | `render.GammaControlModule` | 🟡 (바닐라 옵션과 동기화만, 커스텀 셰이더 아님) |
| 4 | 색상 그레이딩(밝기/채도/대비/색조 프리셋) | `render.ColorGradingModule` | ⚪ (설정값만, 실제 후처리 셰이더는 다음 라운드) |
| 5 | 좌표/바이옴 HUD | `hud.CoordsBiomeHudModule` | ✅ |
| 6 | CPS | `hud.CpsHudModule` | ✅ |
| 7 | Keystroke | `hud.KeystrokesHudModule` | ✅ |
| 8 | 커스텀 크로스헤어 | `render.CustomCrosshairModule` | 🟡 (바닐라 크로스헤어를 안 숨기고 위에 덧그림) |
| 9 | 채팅 시간/검색/더 많이 남기기 | `chat.ChatEnhancementsModule` + `gui.ChatSearchScreen` | 🟡 (자체 히스토리는 완성, 바닐라 채팅창 자체 확장은 아님) |
| 10 | 바라보는 엔티티 정보 | `render.EntityLookOverlayModule` | ✅ |
| 11 | 아이템 버리기 제한 | `inventory.DropProtectionModule` | ✅ |
| 12 | FPS | `hud.FpsHudModule` | 🟡 (`getCurrentFps()` 존재 여부 확인 필요, 폴백 로직 있음) |
| 13 | 체력 숫자/바 대체 | `hud.SimpleHealthHudModule` | 🟡 (추가 오버레이 방식, 바닐라 하트 대체 아님) |
| 14-1 | 핫바 아이템 개수 | `inventory.HeldItemCounterModule` | ✅ |
| 14-2 | 자동 리필 | `inventory.AutoRefillModule` | 🟡 (clickSlot 기반, 안티치트 서버 주의) |
| 14-3 | 방금 먹은 아이템 하이라이트 | `inventory.RecentlyEatenModule` | ✅ |
| 14-4 | 희귀도 테두리 | `inventory.RarityOutlineModule` | ⚪ (렌더 훅 TODO) |
| 14-5 | 아이템 이동 애니메이션 | `inventory.InventoryAnimationsModule` | ⚪ (보간 유틸만, 렌더 연결 TODO) |
| 14-6 | 인벤토리/갑옷 상시 표시 | `inventory.ArmorHudModule` | ✅ |
| 14-7 | 섭취 토스트 | `inventory.ItemUsageToastModule` | ✅ |
| 14-8 | 특정 키로 전부 버리기/옮기기 | `inventory.DropDumpModule` | 🟡 |
| 14-9 | 인벤토리 연 채 이동 | `inventory.InventoryMoveModule` | ⚪ (mixin 필요, TODO) |
| 15 | 아이템 정보(인챈트 등) 툴팁 | `inventory.ItemTooltipInfoModule` | 🟡 |
| 16 | 아이템 빛기둥 | `render.ItemLightBeamModule` | 🟡 |
| 17 | 이름표 항상/안보임 | `render.NametagVisibilityModule` + `mixin.EntityRendererLabelMixin` | 🟡 |
| 18 | 핑 표시 | `hud.PingHudModule` | ✅ |
| 19 | 업타임 | `hud.UptimeHudModule` | ✅ |
| 20 | 포션 효과 표시 | `hud.PotionEffectsHudModule` | ✅ |
| 21 | 튕겼을 때 자동 재접속 | `misc.AutoReconnectModule` | 🟡 (ConnectScreen 오버로드 확인 필요) |
| 22 | 사거리 표시 | `hud.ReachDistanceHudModule` | ✅ |
| 23 | Apple Skin류 포만감 표시 | `hud.AppleSkinHudModule` | 🟡 (현재값 표시는 완성, 회복량 예측은 TODO) |
| 24 | 스코어보드 커스텀 | `chat.ScoreboardTweaksModule` + `mixin.ScoreboardDisplayMixin` | 🟡 (숨기기만, 위치 이동 TODO) |
| 25 | 스크린샷 도구 | `misc.ScreenshotToolModule` | 🟡 |
| 26 | 셜커박스 내용물 미리보기 | `inventory.ShulkerPeekModule` | 🟡 (1.21 컴포넌트 API 확인 필요) |
| 27 | 키 누르는 동안 시점 유지 | `render.FreeLookModule` | 🟡 |
| 28 | 달리는 속도 표시 | `hud.SpeedometerHudModule` | ✅ |
| 29 | 초당 블록 파괴 수 | `hud.BlocksPerSecondHudModule` | ✅ (공식 `PlayerBlockBreakEvents` 사용) |
| 30 | 타이머/스톱워치/시계 | `hud.ClockHudModule` | ✅ |
| 31 | 채팅 몹 분류 색상 | `chat.MessageColorizerModule` | ⚪ (감지만, 실제 재색칠은 이벤트 한계로 TODO) |
| 32 | 메모리 사용량 | `hud.MemoryUsageHudModule` | ✅ |
| 33 | 탭 목록 인원 제한 | `misc.TabListLimitModule` + `mixin.PlayerListHudMixin` | ⚪ (mixin 훅만, 실제 자르기 TODO) |
| 34 | 클라이언트 전용 시간 오버라이드 | `misc.ClientTimeModule` | ⚪ (설정만) |
| 35 | TNT 타이머 | `render.TntTimerModule` + `mixin.TntEntityAccessor` | 🟡 |
| 36 | TPS 표시 | `hud.TpsHudModule` | ⚪→🟡 (서버 협조 없이는 원리적 한계, 채팅 파싱 근사치) |
| 37 | Simple Voice Chat 연동 | `misc.VoiceChatIntegrationModule` | ⚪ (설치 감지만, addon API 미연동) |
| 38-39 | 웨이포인트/나침반 | `waypoint.WaypointModule`, `waypoint.CompassModule` | ✅ |
| 40 | 시점 스냅(yaw/pitch) | `waypoint.ViewSnapModule` | ✅ |
| 41 | 줌 | `render.ZoomModule` | 🟡 (진행도 계산은 완성, 실제 FOV 반영 mixin TODO) |
| 42 | 핑 마크 | `waypoint.PingMarkModule` + `network.PingPayload` | 🟡 (로컬만 확실, 멀티유저는 서버 필요) |
| 43 | GUI 블러 | `render.GuiBlurModule` | 🟡 (진짜 블러 아닌 반투명 근사) |
| 44 | 인챈트/포션 반짝임 설정 | `render.GlintControlModule` | ⚪ |
| 45 | 바라보는 방향(yaw/pitch) | `hud.DirectionHudModule` | ✅ |
| 46 | 겉날개/갑옷 스위치(하이픽셀 차단) | `misc.ElytraSwapModule` | 🟡 |

## 다음 라운드에서 우선 손볼 곳 (권장 순서)

1. `gradlew build` 한 번 돌려서 실제 컴파일 에러 목록 받기 - 이게 제일 정확한 다음 할 일 목록입니다.
2. mixin 4개(`TntEntityAccessor`, `EntityRendererLabelMixin`, `PlayerListHudMixin`,
   `ScoreboardDisplayMixin`)의 대상 메서드/필드명을 Yarn genSources로 실제 확인.
3. ⚪ 표시된 항목들(색상 그레이딩 셰이더, 인벤토리 애니메이션 렌더 연결, GUI 블러 진짜
   구현, 탭 목록 자르기, 클라이언트 시간 오버라이드) - 전부 "설정 뼈대는 있고 렌더
   연결만 남음" 상태라 순서대로 채우면 됩니다.
4. 실제 게임에서 Right Shift로 설정 화면을 열어서 레이아웃/스크롤/색상 확인(다크 +
   보라 포인트 팔레트는 `util/NovaTheme.java` 한 파일에 몰아뒀으니 런처의 실제 색상
   코드를 알려주시면 그 파일만 바꾸면 전체 반영됨).

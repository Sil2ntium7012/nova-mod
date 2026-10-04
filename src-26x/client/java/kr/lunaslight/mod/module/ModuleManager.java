package kr.lunaslight.mod.module;

import kr.lunaslight.mod.module.impl.chat.ScoreboardTweaksModule;
import kr.lunaslight.mod.module.impl.chat.WhisperAlertModule;
import kr.lunaslight.mod.module.impl.combat.*;
import kr.lunaslight.mod.module.impl.hud.*;
import kr.lunaslight.mod.module.impl.inventory.*;
import kr.lunaslight.mod.module.impl.misc.*;
import kr.lunaslight.mod.module.impl.render.*;
import kr.lunaslight.mod.module.impl.waypoint.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import kr.lunaslight.mod.util.LunaCompat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 모든 모듈이 등록되는 중앙 레지스트리이자 이벤트 라우터.
 * 새 기능을 추가할 때: 모듈 클래스 하나 만들고 register() 목록에 한 줄 추가하면 끝
 * (GUI/저장/틱/렌더 전부 여기 순회로 자동 연결됨).
 */
public final class ModuleManager {
	private static final ModuleManager INSTANCE = new ModuleManager();

	private final Map<String, Module> byId = new LinkedHashMap<>();

	public static ModuleManager get() {
		return INSTANCE;
	}

	private ModuleManager() {
	}

	public void init() {
		// ---- HUD ----
		register(new CoordsBiomeHudModule());
		register(new CpsHudModule());
		register(new KeystrokesHudModule());
		register(new FpsHudModule());
		register(new PingHudModule());
		register(new UptimeHudModule());
		register(new PotionEffectsHudModule());
		register(new AimDistanceHudModule());
		register(new SimpleHealthHudModule());
		registerReflectively("kr.lunaslight.mod.module.impl.hud.ArmorBarColorModule"); // 49-39차: 갑옷 줄 색
		register(new MoveRateHudModule());
		register(new BlocksPerSecondHudModule());
		register(new ClockHudModule());
		register(new MemoryUsageHudModule());
		register(new DirectionHudModule());
		// 35차: TpsHudModule은 ClientReceiveMessageEvents(1.19.3 미만 Fabric API엔 없음)에 의존해서
		// no_message_events가 켜진 서브프로젝트(1.16~1.19.2)에서는 컴파일 자체에서 제외됨
		// (loom-common.gradle) - 직접 new 대신 리플렉션 등록으로 이관(자세한 배경은
		// claude/nova-mod-todo.md 33~35차 참고).
		registerReflectively("kr.lunaslight.mod.module.impl.hud.TpsHudModule");
		register(new AppleSkinHudModule());
		register(new KeyItemCountHudModule());   // 49-65차(4-2): 폭죽·토템·화살 남은 수
		register(new kr.lunaslight.mod.module.impl.inventory.HeldItemCounterModule());   // 49-179차: 손 아이템 개수 되살림
		register(new BlockInfoHudModule());      // 49-66차(4-3): 보고 있는 블록의 이름·좌표

		// ---- 전투(49-23차) ----
		register(new ComboCounterModule());
		register(new ShieldCooldownModule());
		register(new CritIndicatorModule());
		register(new HitboxModule());   // 49-66차(4-50): 실제로 맞는 범위를 선으로
		register(new PvpAnalyzeModule());   // 49-71차(4-43): 상대가 어떻게 치는지 판별

		// ---- 렌더/월드 ----
		register(new CrosshairOutlineModule());
		register(new CustomCrosshairModule());
		register(new EntityLookOverlayModule());
		register(new TntTimerModule());
		register(new ItemLightBeamModule());
		register(new DroppedItemInfoModule());   // 49-66차(4-51): 떨어진 아이템 이름·개수(겹치면 묶어서)
		register(new NametagVisibilityModule());
		register(new GammaControlModule());
		register(new DebugHudStyleModule()); // 49-22차: F3 꾸미기
		// 49-6차: 실동작 없는 스텁이라 목록에서 임시 제외(가짜 기능 노출 방지) - 구현 완성 시 재등록
		register(new ColorGradingModule()); // 49-42차: 스텁 → 블렌딩 사각형으로 실제 구현
		// 49-22차: 메뉴 흐림(가짜 어둡기)은 사용자 요청으로 삭제 - 등록 해제.
		// 49-6차: 실동작 없는 스텁이라 목록에서 임시 제외(가짜 기능 노출 방지) - 구현 완성 시 재등록
		register(new GlintControlModule()); // 49-42차: 스텁 → hasGlint 믹스인 + 바닐라 세기/속도 옵션
		register(new ZoomModule());
		register(new FovLockModule());   // 49-53차(3-8): 속도로 시야가 출렁이는 것 끄기(1.17+)
		register(new ParticleFilterModule());   // 49-53차(3-2·3-7): 포션·방울 입자 끄기
		register(new BeaconBeamModule());   // 49-55차(3-3): 신호기 빛기둥 끄기
		register(new kr.lunaslight.mod.module.impl.render.VignetteModule());   // 49-195차: 비네팅 끄기([그래픽])
		register(new kr.lunaslight.mod.module.impl.render.CapeSmoothModule());   // 49-201차: 망토 흔들림 부드럽게
		register(new kr.lunaslight.mod.module.impl.render.WingsModule());   // 49-270차: 노바 날개(산 사람만, 애니메이션)
		register(new kr.lunaslight.mod.module.impl.misc.CosmeticsModule());   // 49-271차: [코스메틱] 착용한 것 보기(바꾸기는 런처)
		register(new EntityHideModule());   // 49-59차(3-4): 이름으로 엔티티 가리기
		register(new EntityCullModule());   // 49-60차(3-11·3-6): 겹친·먼 엔티티 안 그리기
		// 49-76차(6-16, 사용자: "상자 화로 줄이기는 왜 있는건지 모르겠음 나는 렉을 줄여달라한 거지 줄여달라고는 안함"):
		// BlockEntityCullModule(거리로 안 그리기) 등록 해제·삭제. 훅과 믹스인은 AfkModule(3-9)이 같이 쓰므로 남긴다.
		register(new OcclusionCullModule());     // 49-76차(6-16): 벽 뒤에 가려진 엔티티·상자·화로 건너뛰기
		register(new AfkModule());               // 49-63차(3-9): 자리 비움일 때 그리는 양 줄이기
		register(new BorderlessWindowModule());   // 49-63차(3-10): 테두리 없는 창
		register(new FireOverlayModule());        // 49-69차(2-3 앞 절반): 타는 중 불꽃 높이
		register(new ShieldOffsetModule());       // 49-75차(2-3 뒤 절반): 1인칭 방패 높이
		register(new FreeLookModule());

		// ---- 인벤토리 ----
		// 49-157차: [인벤토리 > 아이템 개수](손에 든 것)는 [HUD > 아이템 개수]의 [손에 든 아이템]으로 합쳤다.
		// 39차: AutoRefillModule은 net.minecraft.container.Container/SlotActionType(1.15.2 전용
		// 패키지/클래스명)에 의존해서 no_container_modules가 켜진 서브프로젝트(1.15.2)에서는
		// 컴파일 자체에서 제외됨(loom-common.gradle) - TpsHudModule과 동일한 이유로 직접 new 대신
		// 리플렉션 등록으로 이관(claude/nova-mod-todo.md 39차 참고).
		registerReflectively("kr.lunaslight.mod.module.impl.inventory.AutoRefillModule");
		// 49-21차/49-32차: 안 쓰는 기능 정리(RecentlyEatenModule · ItemUsageToastModule · GuiBlurModule 삭제).
		// 49-6차: 실동작 없는 스텁이라 목록에서 임시 제외(가짜 기능 노출 방지) - 구현 완성 시 재등록
		registerReflectively("kr.lunaslight.mod.module.impl.inventory.RarityOutlineModule"); // 49-42차: 스텁 → drawSlot 믹스인(SlotDrawHook) · 1.15.2 제외(리플렉션 등록)
		// 49-47차(사용자: "인벤토리 애니메이션 없애줘 다시"): 등록 해제. 모듈·훅·믹스인 전부 제거했다.
		register(new ArmorHudModule());
		// 49-21차: "아이템 먹었을 때 뜨는 거" = 아이템을 **주웠을 때**(획득) 표시였음(게이머 은어 '먹다'를
		// 섭취로 잘못 해석해 48~49-19차 내내 먹기 감지를 고치고 있었음) → 획득 표시 모듈로 교체.
		register(new ItemPickupToastModule());
		// 39차: DropDumpModule도 AutoRefillModule과 동일한 이유(Container/SlotActionType 의존)로
		// 1.15.2에서 컴파일 제외 - 리플렉션 등록으로 이관.
		registerReflectively("kr.lunaslight.mod.module.impl.inventory.DropDumpModule");
		// 49-6차: 실동작 없는 스텁이라 목록에서 임시 제외(가짜 기능 노출 방지) - 구현 완성 시 재등록
		registerReflectively("kr.lunaslight.mod.module.impl.inventory.InventoryMoveModule"); // 49-42차: 스텁 → 이동 키 폴링(Inventory Walk 방식) · 1.15.2 제외(리플렉션 등록)
		register(new ItemTooltipInfoModule());
		register(new ShulkerPeekModule());
		register(new DropProtectionModule());
		// 49-23차: 인벤토리 표시 HUD, Alt+휠 핫바 줄 교체(SlotActionType 의존 → 1.15.2 컴파일 제외, 리플렉션 등록)
		register(new InventoryHudModule());
		registerReflectively("kr.lunaslight.mod.module.impl.inventory.HotbarRowSwapModule");
		// 49-27차: 부드러운 마우스(끌어서 여러 칸 옮기기 · 휠로 옮기기) - 같은 이유로 리플렉션 등록
		registerReflectively("kr.lunaslight.mod.module.impl.inventory.MouseTweaksModule");
		// 49-111차: 인벤토리 정리(키 하나로 상자·인벤토리 정렬) - Slot/SlotActionType 의존, 리플렉션 등록
		registerReflectively("kr.lunaslight.mod.module.impl.inventory.InventorySortModule");
		// 49-27차: 아이템 찾기(열어 본 상자 기록 - Slot/Container 의존)
		registerReflectively("kr.lunaslight.mod.module.impl.inventory.ItemFinderModule");
		// 49-125차: 인벤토리 탭(상자 화면에서 근처 상자 바로 열기) - HandledScreen 의존, 1.15.2 제외(리플렉션 등록)
		registerReflectively("kr.lunaslight.mod.module.impl.inventory.ContainerTabsModule");
		// 49-33차: 제작 도우미(조합법 검색 · 만드는 순서) - 역시 Slot 의존이라 리플렉션 등록
		registerReflectively("kr.lunaslight.mod.module.impl.inventory.CraftingHelperModule");
		// 49-133차: 너굴마을 - 아이템 출처 창(R 키) + 추천/핫타임 HUD
		registerReflectively("kr.lunaslight.mod.module.impl.misc.NeogulItemsModule");
		registerReflectively("kr.lunaslight.mod.module.impl.misc.NeogulDailyModule");
		// 49-148차: 너굴마을 허수아비 알림(채팅 감지 → 소리 + 타이틀)
		registerReflectively("kr.lunaslight.mod.module.impl.misc.NeogulScarecrowModule");
		// 49-149차: 너굴마을 레벨 계산기(입력 > 결과 화면, 늘 켜짐)
		registerReflectively("kr.lunaslight.mod.module.impl.misc.NeogulLevelModule");
		// 49-144차: 리소스팩 기억(서버 모드로 켜면 그 서버 팩 미리 적용)
		registerReflectively("kr.lunaslight.mod.module.impl.misc.ServerPackMemoryModule");

		// ---- 채팅 ----
		// 35차: ChatEnhancementsModule도 TpsHudModule과 동일한 이유로 1.16~1.19.2에서 컴파일 제외
		// - 리플렉션 등록으로 이관.
		// 49-121차(사용자: "셀 다 '채팅' 하나로 합치고 개별 삭제"): 채팅 강조(MessageColorizer)·채팅 숨기기
		// (ChatFilter)·채팅 정리(ChatTidy)를 전부 이 "채팅"(ChatEnhancementsModule) 하나로 합치고 개별
		// 모듈은 삭제했다. [일반] 설정에는 이제 채팅 + 유저 차단만 남는다. (합친 기능은 1.16~1.19.2에서는
		// ChatEnhancements가 컴파일 제외라 함께 빠진다 - 그 버전엔 원래 "채팅" 모듈이 없었으니 새 손실은 없음.)
		registerReflectively("kr.lunaslight.mod.module.impl.chat.ChatEnhancementsModule");
		register(new ScoreboardTweaksModule());
		register(new ToastFilterModule());   // 49-54차(1-3): 토스트 끄기
		// 49-157차: [일반 > 유저 차단]은 [기능 > 채팅]의 [차단] 묶음으로 합쳤다.
		register(new WhisperAlertModule());  // 49-67차(5-5)·49-121차: 귓속말 오면 소리만
		register(new kr.lunaslight.mod.module.impl.misc.FishingAlertModule());   // 49-125차: 낚시 미끼 물면 소리
		register(new kr.lunaslight.mod.module.impl.misc.DiscordStatusModule());  // 49-125차: 디스코드에 플레이 중 표시
		register(new kr.lunaslight.mod.module.impl.chat.ChatFaceModule());   // 49-81차(4-48): 채팅 줄 앞에 보낸 사람 얼굴(1.20+)
		register(new BackgroundSoundModule());   // 49-55차(1-2): 창이 뒤로 가면 소리 줄이기

		// ---- 웨이포인트/나침반/시야 ----
		register(new WaypointModule());
		register(new CompassModule());
		register(new ViewSnapModule());
		register(new PingMarkModule());
		registerReflectively("kr.lunaslight.mod.module.impl.waypoint.MeasureModule"); // 49-41차: 거리 재기(막대기)
		register(new kr.lunaslight.mod.module.impl.waypoint.BlueprintModule());   // 49-253차: 설계도(나무 도끼 구역 → 저장, 불러와 홀로그램)
		register(new kr.lunaslight.mod.module.impl.hud.BlueprintHudModule());     // 49-253차: 설계도 HUD

		// ---- 기타/시스템 ----
		register(new InterfaceStyleModule()); // 49-13차: 글꼴 선택(마크 기본/마크+한글 픽셀/모던)
		register(new HudBackgroundModule());  // 49-63차(2-1): HUD 배경 모양(둥근/네모난/없음)
		register(new HudHideModule());        // 49-63차(4-1): 키 하나로 Luna HUD만 숨기기
		register(new kr.lunaslight.mod.module.impl.misc.ShortcutsModule());   // 49-89차(8-6·8-8·8-17): 내 키+명령어 키 합침, [키] 탭, 마크 보조 키
		register(new RecorderModule());       // 49-72차(4-11): 짧은 순간을 움직이는 그림으로
		register(new PackPriorityModule());   // 49-73차(1-7): 서버 팩보다 내 팩을 위에
		register(new FriendAlertModule());    // 49-74차(1-4): 루나 친구가 들어오면 채팅에 한 줄
		register(new kr.lunaslight.mod.module.impl.misc.PatchNoteAlertModule());   // 49-268차: 런처 새 패치노트를 화면 위 가운데에(항상 켜짐)
		register(new NowPlayingModule());
		register(new kr.lunaslight.mod.module.impl.misc.VideoPipModule());   // 49-177차: 보고 있는 영상(런처가 찍은 창)     // 49-74차(4-5): 지금 듣고 있는 노래를 HUD에
		// 49-102차(사용자: "PIP 기능 삭제해줘"): PIP 창(YoutubeWindowModule) + 런처 mirror 창 전부 제거.
		register(new SoundFilterModule());    // 49-68차(4-9): 거슬리는 소리만 골라 끄기
		// 49-61차(사용자: "테마는 설정할 수 있으면 안 된다고"): ThemeModule 등록 해제.
		// 테마는 런처에서 사고 장착하는 것이고, 게임 안에는 그걸 "따라가는 쪽"만 있으면 된다.
		// 따라가는 일은 LunaSocial이 .luna-launch.json을 읽어 LunaTheme.setLauncherBase/
		// setLauncherAccent를 부르는 것으로 이미 끝나고, LunaTheme의 기본값이 자동(=런처 값)이라
		// 이 모듈이 실제로 하던 일은 없었다. 인게임에 고르는 자리를 두면 오히려 런처에서 장착한
		// 것과 어긋난다("장착했는데 적용이 안 됐어" - 49-40차에 실제로 겪은 그 문제).
		register(new SprintToggleModule());
		register(new ScreenshotToolModule());
		register(new AutoReconnectModule());
		register(new kr.lunaslight.mod.module.impl.misc.SessionFixModule());   // 49-106차: 세션 만료 화면에서 바로 세션 새로 받아 재접속
		// 49-6차: 실동작 없는 스텁이라 목록에서 임시 제외(가짜 기능 노출 방지) - 구현 완성 시 재등록
		register(new TabListLimitModule()); // 49-42차: 스텁 → collectPlayerEntries 믹스인
		register(new ClientTimeModule());
		// 49-48차 신규
		register(new kr.lunaslight.mod.module.impl.hud.ServerAddressHudModule());
		register(new kr.lunaslight.mod.module.impl.hud.ItemInfoHudModule());
		register(new kr.lunaslight.mod.module.impl.hud.MousestrokesHudModule());
		register(new kr.lunaslight.mod.module.impl.render.WeatherChangerModule());
		register(new kr.lunaslight.mod.module.impl.render.SubtitleStyleModule());
		// 49-61차(사용자: "다크모드는 마크 기본 UI에 포함되는 기능인데 바뀌지도 않고"): 다크 모드 삭제.
		// 실제로 <b>한 번도 작동한 적이 없었다</b> - 믹스인이 HandledScreen#drawBackground를 노렸는데
		// 그 메서드는 40개 버전 전부에서 abstract다(javap 실측: 1.16.5·1.19.2·1.20.4·1.21.8·1.21.11 모두
		// "protected abstract void drawBackground(...)"). 본문이 없는 메서드에는 @Inject가 붙을 자리가
		// 없고, require = 0이라 조용히 넘어가서 "설정은 있는데 아무 일도 안 나는" 상태였다(4-26 계열의
		// 가짜 기능). 고치려면 버전마다 다른 자리를 새로 잡아야 하는데, 바닐라 UI를 덮는 기능이라
		// 만들 이유가 없어 통째로 뺐다(모듈·훅·믹스인 삭제).
		register(new HarvestTrackerModule());
		register(new DeathInfoModule());
		register(new SmoothScrollModule()); // 49-23차: 부드러운 휠
		// 49-44차에 "껍데기"라 뺐던 보이스챗을 49-50차에 진짜 애드온으로 다시 넣었다 - SVC가 공개한
		// voicechat-api를 compileOnly로 붙이고 fabric.mod.json의 "voicechat" 진입점에
		// kr.lunaslight.mod.integration.LunaVoicechatPlugin을 등록해서, SVC가 실제로 주고받는
		// "누가 말하는 중인지"를 우리 HUD로 그린다. SVC가 안 깔렸으면 카드가 잠긴 채로 뜬다(requiresMod).
		register(new kr.lunaslight.mod.module.impl.hud.VoiceHudModule());
		// 39차: ElytraSwapModule도 AutoRefillModule/DropDumpModule과 동일한 이유(Container/
		// SlotActionType 의존)로 1.15.2에서 컴파일 제외 - 리플렉션 등록으로 이관.
		registerReflectively("kr.lunaslight.mod.module.impl.misc.ElytraSwapModule");

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			kr.lunaslight.mod.util.LunaPerf.tick(client); // 49-7차: 성능 진단 지표 수집(1초 단위 샘플)
			for (Module module : byId.values()) {
				if (module.isEnabled()) {
					try {
						module.onTick();
					} catch (Exception e) {
						kr.lunaslight.mod.LunaClientMod.LOGGER.error("[Nova] 모듈 tick 오류: " + module.getId(), e);
					}
				} else if (module instanceof BackgroundTick bt) {
					// 49-194차: 꺼져 있어도 다른 기능을 위해 돌아야 하는 일(아이템 찾기의 상자 기록 → 블록 정보 내용물)
					try {
						bt.backgroundTick();
					} catch (Exception e) {
						kr.lunaslight.mod.LunaClientMod.LOGGER.error("[Nova] 모듈 background tick 오류: " + module.getId(), e);
					}
				}
			}
		});

		// 19차: HudRenderCallback의 두 번째 인자 타입이 신형(RenderTickCounter)/구형(1.20.x, float)로
		// 갈려서 정적으로 등록하면 한쪽 버전이 깨짐 - LunaCompat이 리플렉션으로 흡수.
		LunaCompat.registerHudRenderCallback((drawContext, tickCounter) -> {
			kr.lunaslight.mod.util.LunaPerf.frame(); // 49-7차: 프레임 간격/스파이크 측정
			kr.lunaslight.mod.gui.LunaDraw.setAlpha(1f); // 49-12차: 화면 페이드가 남긴 전역 알파가 HUD 상자에 섞이지 않게
			// 49-21차: F1(HUD 숨김)이나 F3(디버그 화면)일 땐 우리 HUD도 전부 숨김(사용자 요청).
			net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
			kr.lunaslight.mod.util.LunaProjection.beginFrame(); // 49-22차: 투영기 프레임 캐시 갱신(모듈들이 같은 카메라를 공유)
			boolean debugHud = LunaCompat.isDebugHudShown(mc);
			if (LunaCompat.isHudHidden(mc)) {
				return;
			}
			if (!kr.lunaslight.mod.LunaClientMod.launchOk()) {
				return;   // 49-259차: 실행 확인이 깨지면 HUD를 안 그린다
			}
			// 49-63차(4-1): Luna HUD만 따로 숨기는 키. 켜져 있으면 모듈을 하나도 안 돈다.
			if (kr.lunaslight.mod.module.impl.misc.HudHideModule.hidden) {
				return;
			}
			// 49-24차: HUD 편집기가 열려 있으면 편집기가 직접(샘플 포함) 그리므로 여기선 건너뜀(이중 그리기 방지)
			if (kr.lunaslight.mod.util.HudEditorState.active) {
				return;
			}
			for (Module module : hudOrder()) {
				if (module.isEnabled()) {
					// 49-22차: F3 화면에서는 F3 꾸미기 모듈만 그리고 나머지 HUD는 전부 숨김
					if (debugHud && !module.rendersOnDebugHud()) {
						continue;
					}
					try {
						module.renderHud(drawContext, tickCounter); // 49-24차: 편집기 배율 적용
					} catch (Exception e) {
						kr.lunaslight.mod.LunaClientMod.LOGGER.error("[Nova] 모듈 HUD 렌더 오류: " + module.getId(), e);
					}
				}
			}
		});

		// 19차: WorldRenderEvents/WorldRenderContext가 1.21.9+에서 업스트림 자체에서 제거됨
		// (Fabric API 이슈 #4902, 미재구현) - LunaCompat이 클래스 존재 여부를 먼저 확인하고,
		// 없는 버전(현재 1.21.11)에서는 등록 자체를 건너뛰어 onWorldRender가 호출되지 않음
		// (크로스헤어 아웃라인/아이템 빛기둥 등 월드 렌더 의존 모듈은 그 버전에서만 비활성).
		LunaCompat.registerWorldRenderLast(context -> {
			for (Module module : byId.values()) {
				if (module.isEnabled()) {
					try {
						module.onWorldRender(context);
					} catch (Exception e) {
						kr.lunaslight.mod.LunaClientMod.LOGGER.error("[Nova] 모듈 월드 렌더 오류: " + module.getId(), e);
					}
				}
			}
		});
	}

	private void register(Module module) {
		byId.put(module.getId(), module);
		hudOrderCache = null;
	}

	private List<Module> hudOrderCache;

	/** 49-23차: HUD 그리기 순서 - renderOrder() 오름차순(같으면 등록 순서). 엔티티 정보가 나침반 위에 덮이게. */
	private List<Module> hudOrder() {
		List<Module> cached = hudOrderCache;
		if (cached == null) {
			cached = new ArrayList<>(byId.values());
			cached.sort(java.util.Comparator.comparingInt(Module::renderOrder));
			hudOrderCache = cached;
		}
		return cached;
	}

	/**
	 * 35차: className이 이 버전의 컴파일에서 아예 빠져있을 수 있는 모듈(예: ClientReceiveMessageEvents에
	 * 의존해 1.16~1.19.2에서 제외된 채팅/HUD 모듈 3개)을 직접 new 대신 리플렉션으로 생성/등록.
	 * gui/ 패키지의 openScreenReflectively와 동일한 패턴 - 클래스가 없는 버전에서는 조용히 스킵.
	 */
	private void registerReflectively(String className) {
		try {
			Class<?> cls = Class.forName(className);
			Module module = (Module) cls.getDeclaredConstructor().newInstance();
			register(module);
		} catch (ClassNotFoundException notSupportedOnThisVersion) {
			// 채팅 메시지 이벤트가 없는 구버전 - 조용히 스킵
		} catch (Throwable t) {
			kr.lunaslight.mod.LunaClientMod.LOGGER.warn("[Nova] 모듈 등록 실패: " + className, t);
		}
	}

	public List<Module> all() {
		return new ArrayList<>(byId.values());
	}

	/** 49-53차(4-47): {@code category}가 null이면 <b>[전체]</b> 탭 - 카테고리를 가리지 않고 전부 돌려준다. */
	public List<Module> byCategory(ModuleCategory category) {
		List<Module> result = new ArrayList<>();
		for (Module module : byId.values()) {
			// 49-32차: 목록에서 숨긴 것(글꼴 등)은 격자에 안 넣는다 - 설정 화면 아래 버튼으로만.
			// 49-56차: [일반]·[UI]·[그래픽] 설정 페이지로 간 것도 기능 격자에서 뺀다(두 군데 나오면 안 됨).
			if ((category == null || module.getCategory() == category)
				&& !module.hiddenInList() && module.getPage() == null) {
				result.add(module);
			}
		}
		return result;
	}

	/**
	 * 49-56차: 설정 페이지([일반]·[UI]·[그래픽])에 실리는 모듈들. 등록 순서를 그대로 유지한다 -
	 * 설정 화면에서 줄 순서가 프레임마다 흔들리면 안 되고, 등록 순서가 곧 우리가 정한 배치다.
	 */
	public List<Module> byPage(SettingsPage page) {
		List<Module> result = new ArrayList<>();
		for (Module module : byId.values()) {
			if (module.getPage() == page) {
				result.add(module);
			}
		}
		return result;
	}

	public Optional<Module> find(String id) {
		return Optional.ofNullable(byId.get(id));
	}

	/**
	 * GUI에서 사용자가 모듈을 켤 때 호출. 그 모듈이 선언한 conflictsWith에 있는,
	 * 지금 켜져있는 모듈들을 자동으로 꺼줌 ("비슷한 기능의 모드를 넣으면... 따로 켠 게
	 * 아니면 꺼줘" 요청 반영 - 크로스헤어 아웃라인/커스텀 크로스헤어, 여러 줌 모드 등).
	 */
	public void enableWithConflictResolution(Module module) {
		for (String conflictId : module.getConflicts()) {
			find(conflictId).ifPresent(other -> {
				if (other.isEnabled()) {
					other.setEnabled(false);
				}
			});
		}
		module.setEnabled(true);
	}

	/** GUI의 토글 스위치 클릭 한 번으로 켜기/끄기 - 켜질 때만 충돌 모듈 자동 정리. */
	public void toggleWithConflictResolution(Module module) {
		if (module.isEnabled()) {
			module.setEnabled(false);
		} else {
			enableWithConflictResolution(module);
		}
	}
}

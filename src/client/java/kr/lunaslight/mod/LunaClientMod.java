package kr.lunaslight.mod;

import kr.lunaslight.mod.config.LunaClientConfig;
import kr.lunaslight.mod.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Luna's Light 내장 전용 모드의 진입점.
 *
 * 이 모드는 런처(Luna's Light, Electron 앱)가 조립해서 배포하는 게임 인스턴스에만 들어가는
 * "숨은" 모드입니다 - 독립적으로 Modrinth/CurseForge에 올려서 다른 런처 사용자가 설치하는
 * 용도가 아니라서, fabric.mod.json에서 Mod Menu에 "library" 배지로 표시되게 해서 기본
 * 모드 목록 화면에서는 접혀서 안 보이게 해뒀습니다(요청하신 "모드리스트에 안 뜨는" 부분).
 * 완전히 안 보이게 하려면 전용 런처 쪽에서 애초에 Mod Menu 자체를 노출 안 시키는 게
 * 가장 확실합니다(런처가 이미 자체 UI로 설정을 노출하므로 자연스러움).
 */
public class LunaClientMod implements ClientModInitializer {
	public static final String MOD_ID = "lunaslight";   // 49-206차: 모드 이름 Luna's Light(옛 id lunaslight, 49-205차 lunaslight)
	public static final Logger LOGGER = LoggerFactory.getLogger("LunaClient");
	private static boolean modulesResumed;

	private static Object openMenuKey;

	// ==================== 45차 → 49-259차: 런처 전용 잠금 ====================
	// 전용 런처가 실행 직전에 게임 폴더에 써 주는 토큰 파일(.luna-launch.json)을 확인해서, 런처 밖에서 실행되면(jar만 복사해
	// 다른 런처에 넣은 경우 등) 모든 기능을 스스로 끈다. 개발 환경(gradlew runClient)은 토큰 없이도 통과.
	//
	// 49-259차(사용자: "우리 모드 복사해 가도 우리 클라이언트 아니면 작동 안 하게"): 예전엔 HMAC 비밀값이 이 jar 안에 글자
	// 그대로 있어서(공개 저장소에도 올라감) 누구나 토큰을 만들 수 있었다. 이제 런처가 Ed25519 <b>개인 키</b>로 서명(sig2)하고
	// 모드에는 <b>공개 키</b>만 둔다 - 공개 키로는 확인만 되고 서명은 못 만든다. 서명하는 글자에 파일 내용(상점 해금 목록,
	// 새 세션의 계정)도 넣어서, 서명은 그대로 두고 내용만 바꿔치기할 수도 없다. 예전 sig(HMAC)는 더 보지 않는다.
	// 확인 결과는 시작할 때 한 번으로 끝내지 않고 HUD 그리기·설정 화면·틱에서도 다시 본다(한 곳만 고쳐서 풀리지 않게).
	// (한계: 클라이언트 측 보호라 바이트코드를 직접 고치면 우회 가능 - 복사해 가서 그냥 쓰는 것을 막는 목적)
	private static final long LAUNCH_TOKEN_MAX_AGE_MS = 15L * 60L * 1000L;
	private static final String[] PK = {"MCowBQYDK2Vw", "AyEAkCw7ktw9FyB22eGuM+9I", "/TBMmdLZNyd7N28BXXMe1co="};
	private static volatile java.security.PublicKey pubKey;
	/** 시작할 때 확인한 실행 토큰(다시 확인용). */
	private static volatile long proofTs;
	private static volatile String proofSig;
	private static volatile int proofState;   // 0 = 아직, 0x5A17 = 통과, 그 밖 = 실패
	private static long proofCheckedAt;

	private static java.security.PublicKey pub() throws Exception {
		java.security.PublicKey k = pubKey;
		if (k == null) {
			byte[] der = java.util.Base64.getDecoder().decode(PK[0] + PK[1] + PK[2]);
			k = java.security.KeyFactory.getInstance("Ed25519").generatePublic(new java.security.spec.X509EncodedKeySpec(der));
			pubKey = k;
		}
		return k;
	}

	/**
	 * 49-259차: 런처가 Ed25519로 서명한 글자 "NovaSig|v1|kind|ts|body"가 맞는지. checkAge면 15분보다 오래된 서명은 거절.
	 * kind: launch(실행 토큰), unlocks(상점 해금), relogin(세션 새로 받기).
	 */
	public static boolean verifySig2(String kind, long ts, String body, String sigB64, boolean checkAge) {
		try {
			if (sigB64 == null || sigB64.isEmpty() || kind == null) {
				return false;
			}
			if (checkAge && Math.abs(System.currentTimeMillis() - ts) > LAUNCH_TOKEN_MAX_AGE_MS) {
				return false;
			}
			java.security.Signature v = java.security.Signature.getInstance("Ed25519");
			v.initVerify(pub());
			v.update(("NovaSig|v1|" + kind + "|" + ts + "|" + (body == null ? "" : body)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
			return v.verify(java.util.Base64.getDecoder().decode(sigB64.trim()));
		} catch (Throwable t) {
			return false;
		}
	}

	public static boolean verifySig2(String kind, long ts, String body, String sigB64) {
		return verifySig2(kind, ts, body, sigB64, true);
	}

	/** 문자열의 SHA-256 16진수(세션 토큰을 서명 글자에 넣을 때 - 토큰 자체는 서명 글자에 안 넣는다). */
	public static String sha256Hex(String s) {
		try {
			byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();
			for (byte b : d) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (Throwable t) {
			return "";
		}
	}

	private static boolean isDev() {
		try {
			return net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment();
		} catch (Throwable t) {
			return false;
		}
	}

	private static boolean isLaunchedByLunaClient() {
		try {
			if (isDev()) {
				proofState = 0x5A17;
				return true; // gradlew runClient 등 개발/테스트 실행은 항상 허용
			}
			net.fabricmc.loader.api.FabricLoader loader = net.fabricmc.loader.api.FabricLoader.getInstance();
			java.nio.file.Path tokenPath = loader.getGameDir().resolve(".luna-launch.json");
			if (!java.nio.file.Files.exists(tokenPath)) {
				return false;
			}
			String raw = new String(java.nio.file.Files.readAllBytes(tokenPath), java.nio.charset.StandardCharsets.UTF_8);
			java.util.regex.Matcher tsMatcher = java.util.regex.Pattern.compile("\"ts\"\\s*:\\s*(\\d+)").matcher(raw);
			java.util.regex.Matcher sigMatcher = java.util.regex.Pattern.compile("\"sig2\"\\s*:\\s*\"([A-Za-z0-9+/=]+)\"").matcher(raw);
			if (!tsMatcher.find() || !sigMatcher.find()) {
				return false;
			}
			long ts = Long.parseLong(tsMatcher.group(1));
			String sig = sigMatcher.group(1);
			if (!verifySig2("launch", ts, "", sig, true)) {
				return false;
			}
			proofTs = ts;
			proofSig = sig;
			proofState = 0x5A17;
			proofCheckedAt = System.currentTimeMillis();
			return true;
		} catch (Throwable t) {
			return false; // 어떤 이유로든 검증에 실패하면 안전하게 "런처 아님" 취급
		}
	}

	/**
	 * 49-259차: 시작할 때 통과한 실행 토큰이 여전히 맞는지(HUD 그리기, 설정 화면, 틱이 부른다). 5분마다 서명을 다시 확인하고
	 * 그 사이엔 기억한 값. 시작 확인을 건너뛰게 고친 jar에서는 기억한 서명이 없어 여기서 막힌다.
	 */
	public static boolean launchOk() {
		int st = proofState;
		if (st != 0x5A17) {
			return false;
		}
		if (isDev()) {
			return true;
		}
		long now = System.currentTimeMillis();
		if (now - proofCheckedAt > 300_000L) {
			proofCheckedAt = now;
			if (!verifySig2("launch", proofTs, "", proofSig, false)) {
				proofState = 0x0BAD;
				LOGGER.info("[Nova] 실행 확인을 다시 통과하지 못해 기능을 끕니다.");
				return false;
			}
		}
		return true;
	}

	@Override
	public void onInitializeClient() {
		// 45차: 런처 전용 잠금 - 전용 런처로 실행된 게 아니면 아무 기능도 등록하지 않음
		// (모듈/키바인드/설정 화면 전부 비활성. 게임 자체는 정상 실행됨).
		if (!isLaunchedByLunaClient()) {
			LOGGER.info("[Nova] Nova Client 런처를 통해 실행되지 않아 내장 기능을 비활성화합니다.");
			return;
		}

		LOGGER.info("[Nova] Nova Client 내장 모드 초기화 시작");

		// 49-23차: 런처가 실어 보낸 소셜 정보(계정 토큰 포함)를 지금 메모리로 읽고, 파일(.luna-launch.json)은
		// 바로 지운다 - 토큰이 게임 폴더에 남지 않게(사용자 요청). 위 검증은 이미 끝났고 런처가 다음 실행 때 다시 씀.
		kr.lunaslight.mod.util.LunaSocial.load();

		ModuleManager.get().init();
		LunaClientConfig.load();
		// 49-144차: 리소스팩 기억 - 옵션 파일(options.txt)을 읽기 전인 지금, 서버 모드로 켜졌으면 그 서버의 기억된 팩을
		// resourcepacks에 넣고 켠다(꺼져 있으면 지난번에 넣은 것만 정리).
		kr.lunaslight.mod.util.ServerPackMemory.startup(ModuleManager.get().find("server_pack_memory")
			.map(kr.lunaslight.mod.module.Module::isEnabled).orElse(false));
		// 49-151차: 바닐라 타이틀이 그려지는 순간에도 Luna 타이틀로 바꾼다(틱만으로는 놓치는 경우가 있었다)
		kr.lunaslight.mod.util.LunaCompat.installTitleSwapHook();

		// 설정 화면을 여는 키바인드. 기본값 Right Shift - 게임플레이 키와 거의 안 겹치고
		// 페더/러너/랩모드 등 다른 유틸 클라이언트들이 흔히 쓰는 자리라 사용자에게 익숙함.
		// 35차: KeyBinding 생성자/카테고리 API가 버전마다(1.16~1.21.11) 크게 갈려서 LunaCompat의
		// 범용 리플렉션 헬퍼로 이관(자세한 배경은 claude/nova-mod-todo.md 33~35차 참고).
		openMenuKey = kr.lunaslight.mod.util.LunaCompat.createAndRegisterKeyBinding(
				"key.lunaslight.open_menu", GLFW.GLFW_KEY_RIGHT_SHIFT);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!launchOk()) {
				return;   // 49-259차: 실행 확인이 깨지면 틱 일(메뉴 키, 화면 바꾸기 …)을 안 한다
			}
			// 49-154차: 화면 멈춤 기록(렌더 스레드가 10초 넘게 멈추면 어디서 멈췄는지 로그에 남김)
			kr.lunaslight.mod.util.StallWatch.beat();
			// 49-156차: 설정 파일에서 켜짐으로 불러온 기능은 onEnable이 안 불렸다(탭리스트 먹통 원인) - 첫 틱에 한 번 준비
			if (!modulesResumed) {
				modulesResumed = true;
				for (kr.lunaslight.mod.module.Module m : ModuleManager.get().all()) {
					m.resumeAfterLoad();
				}
			}
			// 49차: ESC 일시정지 메뉴를 Luna 스타일(LunaPauseScreen)로 교체
			kr.lunaslight.mod.util.LunaCompat.maybeSwapPauseMenu(client);
			// 49-7차: 바닐라 타이틀 화면도 페더식 메인 화면(LunaTitleScreen)으로 교체
			kr.lunaslight.mod.util.LunaCompat.maybeSwapTitleScreen(client);
			// 49-213차: 처음 켰으면 취향 설정부터(서버로 켰어도 이게 먼저 - 마치면 그 서버로 들어간다)
			kr.lunaslight.mod.util.FirstSetup.tick(client);
			// 49-27차: 통계 수집 + 내 접속 정보(친구에게 "어느 서버에 있는지") 갱신
			kr.lunaslight.mod.util.LunaStats.tick(client);
			kr.lunaslight.mod.util.LunaSocial.publishPresence(client);
			// 49-124차(사용자: "탭 핑 앞에 루나 별 - 페더처럼"): 같은 서버 루나 유저 목록 갱신.
			kr.lunaslight.mod.util.LunaSocial.pollServerBadges(client);
			while (kr.lunaslight.mod.util.LunaCompat.wasKeyBindingPressed(openMenuKey)) {
				if (client.currentScreen == null) {
					// 28차: 1.16~1.19.4 서브프로젝트에서는 gui 패키지 자체가 컴파일에서 빠져있음
					// (Screen 상속 문제로 아직 이식 안 됨) - 직접 new LunaClientScreen(...) 대신
					// 리플렉션으로 클래스 존재 여부부터 확인해서, 없는 버전에서도 컴파일이 깨지지
					// 않고 그냥 "미지원" 로그만 남기고 넘어가게 함.
					kr.lunaslight.mod.util.LunaCompat.openScreenReflectively("kr.lunaslight.mod.gui.LunaClientScreen", null);
				}
			}
		});

		// 게임 종료 시 설정 저장이 안 남는 일이 없도록 종료 시점에도 한 번 더 저장.
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			LunaClientConfig.save();
			// 49-27차: 아이템 찾기용 상자 기록 · 통계도 종료할 때 한 번 더 저장
			kr.lunaslight.mod.util.ContainerIndex.save();
			kr.lunaslight.mod.util.LunaStats.saveNow();
		});
		// 49-23차: 게임 창 아이콘을 런처와 같은 Luna 아이콘으로(바닐라 잔디 블록 아이콘 덮어쓰기)
		ClientLifecycleEvents.CLIENT_STARTED.register(kr.lunaslight.mod.util.WindowIcon::apply);

		LOGGER.info("[Nova] 초기화 완료 - " + ModuleManager.get().all().size() + "개 기능 등록됨"
			+ " (감지된 마인크래프트 버전: " + kr.lunaslight.mod.util.LunaVersion.current() + ", Right Shift로 설정 열기)");
	}
}

package kr.lunaslight.mod.util;

import kr.lunaslight.mod.LunaClientMod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

/**
 * 49-645차(사용자: "노바 쓰던 유저들은 노바 모드 설정 그대로 가져오기"): 너굴라이언트로 처음 켰을 때(모드 설정 파일이 아직 없음),
 * 같은 PC에 Nova Client가 있으면 그 프로필의 모드 설정 폴더(config/lunaslight - 기능 켜짐/설정, HUD 자리, 웨이포인트, 통계, 창고 기억 등)를
 * 그대로 복사해 온다. 프로필이 여럿이면 너굴마을 기록이 있는 것, 그다음 가장 최근에 쓴 것. 한 번만(표시 파일), 이미 있는 파일은 덮지 않는다.
 * 모듈과 설정을 읽기 전에(LunaClientMod: LunaSocial.load 바로 뒤) 부른다.
 */
public final class NovaSettingsImport {
	private NovaSettingsImport() {
	}

	private static final String MARK = ".nova-import-done";
	/** 49-652차: 이번 실행에서 Nova Client 설정을 가져왔나(그럼 처음 온 사람 - 메인 화면 고르기를 묻는다). */
	public static volatile boolean imported;

	public static void run() {
		if (!LunaSocial.nugulient) {
			return;
		}
		try {
			Path dst = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("lunaslight");
			if (Files.exists(dst.resolve(MARK))) {
				return;
			}
			if (Files.exists(dst.resolve("modules.json"))) {
				mark(dst, "이미 너굴라이언트 설정이 있어 가져오지 않음");
				return;
			}
			String appdata = System.getenv("APPDATA");
			if (appdata == null || appdata.isEmpty()) {
				return;
			}
			Path profiles = Paths.get(appdata, "NovaClient", "profiles");
			if (!Files.isDirectory(profiles)) {
				mark(dst, "Nova Client 없음");
				return;
			}
			Path best = null;
			long bestScore = Long.MIN_VALUE;
			try (Stream<Path> ps = Files.list(profiles)) {
				for (Path p : ps.toList()) {
					Path src = p.resolve("config").resolve("lunaslight");
					Path mod = src.resolve("modules.json");
					if (!Files.isRegularFile(mod)) {
						continue;
					}
					long t = Files.getLastModifiedTime(mod).toMillis();
					boolean neogul = Files.exists(src.resolve("neogul-storage.json")) || Files.exists(src.resolve("neogul-calendar.json"))
							|| p.getFileName().toString().contains("너굴");
					long score = (neogul ? Long.MAX_VALUE / 2 : 0) + t / 1000;
					if (score > bestScore) {
						bestScore = score;
						best = src;
					}
				}
			}
			if (best == null) {
				mark(dst, "Nova Client 모드 설정 없음");
				return;
			}
			int[] n = {0};
			Path from = best;
			try (Stream<Path> all = Files.walk(from)) {
				List<Path> files = all.filter(Files::isRegularFile).toList();
				for (Path f : files) {
					Path to = dst.resolve(from.relativize(f).toString());
					if (Files.exists(to)) {
						continue;
					}
					try {
						Files.createDirectories(to.getParent());
						Files.copy(f, to);
						n[0]++;
					} catch (Throwable t) {
						LunaCompat.warnOnce("novaImport:copy", t);
					}
				}
			}
			mark(dst, "Nova Client에서 " + n[0] + "개 가져옴: " + from);
			imported = n[0] > 0;
			LunaClientMod.LOGGER.info("[Nova] Nova Client 모드 설정을 가져옴({}개, {})", n[0], from);
		} catch (Throwable t) {
			LunaCompat.warnOnce("novaImport", t);
		}
	}

	private static void mark(Path dst, String why) {
		try {
			Files.createDirectories(dst);
			Files.writeString(dst.resolve(MARK), why + "\n", java.nio.charset.StandardCharsets.UTF_8);
		} catch (Throwable ignored) {
		}
	}
}

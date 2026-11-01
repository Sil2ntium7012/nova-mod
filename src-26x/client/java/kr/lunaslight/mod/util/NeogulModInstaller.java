package kr.lunaslight.mod.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 49-454차(사용자: "보이스모드랑 키 모드 내장한 거 아니었어? … 노바 안에 넣어 달라고, 아니면 너굴마을 프로필에 자동으로 넣거나"):
 * <b>너굴마을 모드 자동 설치</b>(26.1.2만). 너굴마을 위키가 배포하는 두 묶음을 너굴마을 서버에서 직접 받아 이 게임의 mods 폴더에 넣는다.
 * <ul>
 *   <li>https://mcng.kr/mods/ng-voiceaddon.zip - ng-voiceaddon-*.jar(보이스챗 한국어 화면) + voicechat-fabric-*.jar(Simple Voice Chat)</li>
 *   <li>https://mcng.kr/mods/ng-skillkeydown.zip - ng-skillkeydown-*.jar(스킬 키 세팅)</li>
 * </ul>
 * 언제: 게임을 켤 때 이 게임 폴더가 너굴마을 프로필(폴더 이름에 "너굴" 또는 mods에 ng- 모드)이면, 아니면 너굴마을 서버에 처음 들어갈 때 - 한 번 실행에 한 번.
 * 이미 있는 것(같은 이름으로 시작하는 jar, 또는 이미 켜진 voicechat 모드)은 건너뛴다. 받는 건 백그라운드, 실패하면 로그만.
 * 모드는 게임을 켤 때만 읽히므로 넣은 뒤 "다시 켜면 적용"이라고 채팅에 알린다.
 */
public final class NeogulModInstaller {
	private NeogulModInstaller() {
	}

	private static final String VOICE_URL = "https://mcng.kr/mods/ng-voiceaddon.zip";
	private static final String SKILL_URL = "https://mcng.kr/mods/ng-skillkeydown.zip";

	private static volatile boolean started;
	private static volatile String pendingNotice;

	private static Path modsDir() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("mods");
	}

	/** 게임을 켤 때(26.1.2): 너굴마을 프로필이면 바로. */
	public static void startup() {
		try {
			Path game = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
			String name = game.getFileName() == null ? "" : game.getFileName().toString();
			if (name.contains("너굴") || hasJar("ng-")) {
				start();
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulInstall:startup", t);
		}
	}

	/** 매 틱: 너굴마을 서버에 들어가면 한 번, 끝났으면 채팅으로 알림. */
	public static void tick(Minecraft mc) {
		if (!started && mc.player != null && NeogulData.onNeogul(mc)) {
			start();
		}
		String n = pendingNotice;
		if (n != null && mc.player != null) {
			pendingNotice = null;
			try {
				mc.player.sendSystemMessage(Component.literal(n));
			} catch (Throwable t) {
				LunaCompat.sendActionBar(mc, n);
			}
		}
	}

	private static synchronized void start() {
		if (started) {
			return;
		}
		started = true;
		Thread th = new Thread(NeogulModInstaller::run, "Nova neogul mods");
		th.setDaemon(true);
		th.start();
	}

	private static boolean hasJar(String prefix) {
		Path dir = modsDir();
		if (!Files.isDirectory(dir)) {
			return false;
		}
		String p = prefix.toLowerCase(Locale.ROOT);
		try (Stream<Path> s = Files.list(dir)) {
			return s.anyMatch(f -> {
				String n = f.getFileName().toString().toLowerCase(Locale.ROOT);
				return n.startsWith(p) && n.endsWith(".jar");
			});
		} catch (Throwable t) {
			return false;
		}
	}

	private static void run() {
		List<String> added = new ArrayList<>();
		try {
			Files.createDirectories(modsDir());
			boolean needVoiceAddon = !hasJar("ng-voiceaddon");
			boolean needSvc = !net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("voicechat") && !hasJar("voicechat");
			boolean needSkill = !net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("ng_skillkeydown") && !hasJar("ng-skillkeydown");
			// 49-473차(사용자: "V가 보이스 설정이던데 안 켜져"): 49-454차에 넣은 voicechat-fabric jar가 런처 프로필 휴지통(.trash/mods)으로
			// 옮겨져 있었다(그러면 너굴 보이스는 Simple Voice Chat이 없어 아무것도 안 함). 휴지통에 있으면 받지 않고 그걸 되돌린다.
			if (needSvc && restoreFromTrash("voicechat", added)) {
				needSvc = false;
			}
			if (needVoiceAddon && restoreFromTrash("ng-voiceaddon", added)) {
				needVoiceAddon = false;
			}
			HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
				.followRedirects(HttpClient.Redirect.NORMAL).build();
			if (needVoiceAddon || needSvc) {
				boolean wantAddon = needVoiceAddon;
				boolean wantSvc = needSvc;
				install(http, VOICE_URL, name -> (wantAddon && name.startsWith("ng-voiceaddon")) || (wantSvc && name.startsWith("voicechat")), added);
			}
			if (needSkill) {
				install(http, SKILL_URL, name -> name.startsWith("ng-skillkeydown"), added);
			}
		} catch (Throwable t) {
			kr.lunaslight.mod.LunaClientMod.LOGGER.warn("[Nova] 너굴마을 모드 자동 설치 실패: " + t);
		}
		if (!added.isEmpty()) {
			kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 너굴마을 모드 넣음: " + String.join(", ", added));
			pendingNotice = "§e[Nova] §f너굴마을 보이스챗, 스킬 키 모드를 넣었어요. §7게임을 다시 켜면 적용돼요. (" + String.join(", ", added) + ")";
		}
	}

	/** 런처 프로필 휴지통(게임 폴더/.trash/mods)에 이 이름으로 시작하는 jar가 있으면 mods로 되돌린다. */
	private static boolean restoreFromTrash(String prefix, List<String> added) {
		Path trash = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve(".trash").resolve("mods");
		if (!Files.isDirectory(trash)) {
			return false;
		}
		String p = prefix.toLowerCase(Locale.ROOT);
		try (Stream<Path> s = Files.list(trash)) {
			for (Path f : (Iterable<Path>) s::iterator) {
				String n = f.getFileName().toString();
				String low = n.toLowerCase(Locale.ROOT);
				if (low.startsWith(p) && low.endsWith(".jar")) {
					Path dst = modsDir().resolve(n);
					if (!Files.exists(dst)) {
						Files.move(f, dst);
						added.add(n);
						kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 휴지통에 있던 " + n + " 을(를) mods로 되돌림");
						return true;
					}
				}
			}
		} catch (Throwable t) {
			kr.lunaslight.mod.LunaClientMod.LOGGER.warn("[Nova] 휴지통 되돌리기 실패: " + t);
		}
		return false;
	}

	private static void install(HttpClient http, String url, java.util.function.Predicate<String> want, List<String> added) throws Exception {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
			.header("User-Agent", "NovaClient").GET().build();
		HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
		if (res.statusCode() != 200) {
			res.body().close();
			throw new java.io.IOException(url + " HTTP " + res.statusCode());
		}
		try (ZipInputStream zin = new ZipInputStream(res.body())) {
			ZipEntry e;
			while ((e = zin.getNextEntry()) != null) {
				if (e.isDirectory()) {
					continue;
				}
				String name = e.getName();
				int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
				name = slash >= 0 ? name.substring(slash + 1) : name;
				String low = name.toLowerCase(Locale.ROOT);
				if (!low.endsWith(".jar") || !want.test(low) || name.contains("..")) {
					continue;
				}
				Path dst = modsDir().resolve(name);
				if (Files.exists(dst)) {
					continue;
				}
				Path tmp = modsDir().resolve(name + ".nova-tmp");
				Files.copy(zin, tmp, StandardCopyOption.REPLACE_EXISTING);
				Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING);
				added.add(name);
			}
		}
	}
}

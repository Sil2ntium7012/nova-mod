package kr.lunaslight.mod.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 49-448차: <b>리소스팩 기억 기능 삭제</b>(사용자: "그러면 그냥 리소스팩 기억 기능 없애자" - 49-447차 서버 들어갈 때 로딩창 무한 제보 뒤).
 * 예전(49-144차) 기능은 서버 팩을 기억해 두었다가 게임을 켤 때 미리 켜고, 접속 때 서버가 같은 팩을 보내면 네티 통로에서 가로채
 * 바닐라 대신 "적용 완료"를 답했다. 이제 서버 팩은 바닐라가 평소대로 받는다.
 * 남은 건 <b>뒷정리 한 번</b>뿐: 옵션 파일을 읽기 전에(LunaClientMod) 예전 기능이 남긴 것을 지운다.
 * <ul>
 *   <li>resourcepacks/luna-server-*.zip (미리 넣은 복사본)</li>
 *   <li>options.txt resourcePacks / incompatibleResourcePacks의 "file/luna-server-…"</li>
 *   <li>config/lunaslight/server-packs/ (기억 저장소)</li>
 * </ul>
 * 아무것도 없으면 아무 일도 안 한다.
 */
public final class ServerPackMemory {
	private ServerPackMemory() {
	}

	private static final String PREFIX = "luna-server-";

	public static void cleanup() {
		try {
			Path game = FabricLoader.getInstance().getGameDir();
			int n = 0;
			Path packsDir = game.resolve("resourcepacks");
			if (Files.isDirectory(packsDir)) {
				try (Stream<Path> s = Files.list(packsDir)) {
					for (Path p : (Iterable<Path>) s::iterator) {
						if (p.getFileName().toString().startsWith(PREFIX)) {
							try {
								Files.deleteIfExists(p);
								n++;
							} catch (Throwable ignored) {
							}
						}
					}
				}
			}
			boolean opt = cleanOptions(game.resolve("options.txt"));
			Path store = game.resolve("config").resolve("lunaslight").resolve("server-packs");
			boolean st = Files.exists(store);
			if (st) {
				try (Stream<Path> s = Files.walk(store)) {
					s.sorted(Comparator.reverseOrder()).forEach(p -> {
						try {
							Files.deleteIfExists(p);
						} catch (Throwable ignored) {
						}
					});
				}
			}
			if (n > 0 || opt || st) {
				kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 리소스팩 기억(삭제된 기능) 뒷정리: 복사본 " + n + "개"
					+ (opt ? ", 옵션 목록" : "") + (st ? ", 저장소" : ""));
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("packmem:cleanup", t);
		}
	}

	/** options.txt의 팩 목록에서 "file/luna-server-…"를 뺀다. 바꿨으면 true. */
	@SuppressWarnings("deprecation")
	private static boolean cleanOptions(Path opt) throws java.io.IOException {
		if (!Files.exists(opt)) {
			return false;
		}
		List<String> lines = Files.readAllLines(opt, StandardCharsets.UTF_8);
		boolean changed = false;
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			String key = line.startsWith("resourcePacks:") ? "resourcePacks:"
				: line.startsWith("incompatibleResourcePacks:") ? "incompatibleResourcePacks:" : null;
			if (key == null || !line.contains(PREFIX)) {
				continue;
			}
			JsonArray arr;
			try {
				JsonElement el = new JsonParser().parse(line.substring(key.length()));
				if (!el.isJsonArray()) {
					continue;
				}
				arr = el.getAsJsonArray();
			} catch (Throwable t) {
				continue;
			}
			JsonArray out = new JsonArray();
			for (JsonElement e : arr) {
				String v = e.getAsString();
				if (!v.startsWith("file/" + PREFIX)) {
					out.add(v);
				}
			}
			lines.set(i, key + out);
			changed = true;
		}
		if (changed) {
			Files.write(opt, lines, StandardCharsets.UTF_8);
		}
		return changed;
	}
}

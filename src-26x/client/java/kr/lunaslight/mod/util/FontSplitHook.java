package kr.lunaslight.mod.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

/**
 * 49-157차(사용자: "내 리소스팩 우선은 특수문자 다 빼 - 글이랑 숫자만 변하게, 서버 기호는 서버 것 그대로"):
 * [내 리소스팩 우선]으로 내 팩이 서버 팩 위에 올라가면, 내 글꼴 팩이 서버가 기호 자리에 넣어 둔 아이콘 글자
 * (사용 영역 글자, ★ → ■ 같은 기호)까지 덮어 버린다. 그래서 <b>내 팩(resourcepacks 폴더, id "file/…")의 글꼴 JSON</b>을
 * 읽을 때 기호를 빼 준다.
 * <ul>
 *   <li>ttf 프로바이더: {@code skip}에 기호를 전부 더한다(그 글자는 이 글꼴이 안 그린다).</li>
 *   <li>bitmap 프로바이더: {@code chars} 표에서 기호 칸을 빈 칸(\u0000)으로 바꾼다(줄 길이는 그대로).</li>
 *   <li>바닐라 글꼴을 끌어오는 reference(minecraft:include/space, include/default, include/unifont)는 뺀다(49-269차).</li>
 *   <li>나머지(다른 reference, space, unihex)는 그대로.</li>
 * </ul>
 * 49-269차(사용자: "특수문자만 적용 안 되게 해 주는 거 아직 안 됐어"): ttf에서 기호를 빼도, 런처가 만든 글꼴 팩(그리고 흔한 글꼴 팩)은
 * 같은 JSON 안에 바닐라 글꼴 reference(include/default, include/unifont)를 이어 붙여 둔다. 글꼴 찾기는 위 팩부터 프로바이더를
 * 차례로 보므로, 내 ttf가 비운 ★ 같은 기호를 <b>내 팩 안의 바닐라 reference</b>가 먼저 그려 버려서 서버 팩까지 내려가지 않았다.
 * 그 reference를 빼면 내 글꼴에 없는 글자는 서버 팩 → 맨 아래 바닐라 팩(같은 reference를 늘 갖고 있다) 순으로 내려간다.
 * 빠진 기호는 그 아래 팩(서버 팩, 없으면 바닐라)이 그린다 - 마인크래프트 글꼴은 팩마다 프로바이더를 이어 붙이고
 * 위 팩부터 찾기 때문이다. 글자, 숫자, 빈칸은 그대로 내 팩 글꼴이다.
 *
 * <p>글꼴 JSON을 한 번에 읽는 FontManager의 자리(1.20+ loadFontProviders, 26.x loadResourceStack)에서만 동작한다
 * (FontSplitMixin). 켜고 끈 뒤에는 리소스를 다시 읽어야(서버 재접속, F3+T) 반영된다.
 */
public final class FontSplitHook {
	private FontSplitHook() {
	}

	/**
	 * PackPriorityModule이 끼운다(내 리소스팩 우선 켜짐 + [기호는 서버 것]). 값이 아니라 조건을 넘기는 이유:
	 * 게임을 켤 때의 첫 리소스 로딩은 설정 파일을 읽은 뒤지만 첫 틱보다 앞이라, 그때도 맞는 값을 봐야 한다.
	 */
	public static volatile java.util.function.BooleanSupplier enabled;

	private static boolean active() {
		java.util.function.BooleanSupplier e = enabled;
		try {
			return e != null && e.getAsBoolean();
		} catch (Throwable t) {
			return false;
		}
	}

	private static String symbols;

	/** 글자, 숫자, 빈칸, 결합 부호가 아닌 글자 = 기호(사용 영역 포함). BMP만. */
	static boolean isSymbol(int cp) {
		if (cp < 0x21 || cp > 0xFFFF || Character.isSurrogate((char) cp)) {
			return false;
		}
		if (Character.isLetterOrDigit(cp) || Character.isWhitespace(cp) || Character.isISOControl(cp) || Character.isSpaceChar(cp)) {
			return false;
		}
		int t = Character.getType(cp);
		return t != Character.NON_SPACING_MARK && t != Character.ENCLOSING_MARK && t != Character.COMBINING_SPACING_MARK
			&& t != Character.UNASSIGNED && t != Character.FORMAT;
	}

	private static synchronized String symbols() {
		if (symbols == null) {
			StringBuilder sb = new StringBuilder(12000);
			for (int cp = 0x21; cp <= 0xFFFF; cp++) {
				if (isSymbol(cp)) {
					sb.append((char) cp);
				}
			}
			symbols = sb.toString();
		}
		return symbols;
	}

	/** 리소스가 들어 있는 팩의 id(1.21 getPackId / 26.x sourcePackId / 팩 객체의 getId·getName·packId). */
	public static String packIdOf(Object resource) {
		for (String n : new String[]{"getPackId", "sourcePackId", "getResourcePackName"}) {
			Object v = LunaCompat.callNoArg(resource, n);
			if (v instanceof String s) {
				return s;
			}
		}
		Object pack = LunaCompat.callNoArg(resource, "getPack");
		if (pack == null) {
			pack = LunaCompat.callNoArg(resource, "source");
		}
		if (pack != null) {
			for (String n : new String[]{"getId", "getName", "packId"}) {
				Object v = LunaCompat.callNoArg(pack, n);
				if (v instanceof String s) {
					return s;
				}
			}
		}
		return null;
	}

	/** main 트리용: 리소스의 getReader()를 리플렉션으로 부르고 거른다(옛 버전 컴파일을 위해 직접 안 부름). */
	public static BufferedReader reader(Object resource, Object fontId) throws IOException {
		Object r = LunaCompat.callNoArg(resource, "getReader");
		if (!(r instanceof BufferedReader br)) {
			throw new IOException("[Nova] 글꼴 리소스를 못 읽음: " + fontId);
		}
		return filter(br, resource, fontId);
	}

	/** 켜져 있고 내 팩 글꼴이면 기호를 뺀 JSON으로 바꿔 돌려준다. 아니면(또는 조금이라도 이상하면) 원래 그대로. */
	public static BufferedReader filter(BufferedReader original, Object resource, Object fontId) throws IOException {
		if (original == null || !active()) {
			return original;
		}
		String id = String.valueOf(fontId);
		if (id.startsWith("lunaslight:")) {
			return original;
		}
		String pack = packIdOf(resource);
		if (pack == null || !pack.startsWith("file/")) {
			return original;
		}
		StringBuilder text = new StringBuilder();
		try (BufferedReader in = original) {
			char[] buf = new char[8192];
			int n;
			while ((n = in.read(buf)) > 0) {
				text.append(buf, 0, n);
			}
		}
		String src = text.toString();
		try {
			String out = transform(src);
			return new BufferedReader(new StringReader(out));
		} catch (Throwable t) {
			LunaCompat.warnOnce("fontSplit", t);
			return new BufferedReader(new StringReader(src));
		}
	}

	@SuppressWarnings("deprecation")
	static String transform(String json) {
		JsonElement root = new JsonParser().parse(json);
		if (!root.isJsonObject() || !root.getAsJsonObject().has("providers")) {
			return json;
		}
		JsonArray providers = root.getAsJsonObject().getAsJsonArray("providers");
		boolean changed = false;
		JsonArray kept = new JsonArray();
		for (JsonElement e : providers) {
			if (!e.isJsonObject()) {
				kept.add(e);
				continue;
			}
			JsonObject p = e.getAsJsonObject();
			String type = p.has("type") ? p.get("type").getAsString() : "";
			if (type.startsWith("minecraft:")) {
				type = type.substring("minecraft:".length());
			}
			if (type.equals("reference") && p.has("id") && isVanillaInclude(p.get("id").getAsString())) {
				changed = true;   // 49-269차: 내 팩 안의 바닐라 글꼴 reference는 뺀다
				continue;
			}
			kept.add(e);
			if (type.equals("ttf")) {
				StringBuilder skip = new StringBuilder();
				if (p.has("skip")) {
					JsonElement s = p.get("skip");
					if (s.isJsonArray()) {
						for (JsonElement x : s.getAsJsonArray()) {
							skip.append(x.getAsString());
						}
					} else {
						skip.append(s.getAsString());
					}
				}
				skip.append(symbols());
				p.add("skip", new JsonPrimitive(skip.toString()));
				changed = true;
			} else if (type.equals("bitmap") && p.has("chars") && p.get("chars").isJsonArray()) {
				JsonArray rows = p.getAsJsonArray("chars");
				JsonArray out = new JsonArray();
				for (JsonElement r : rows) {
					String row = r.getAsString();
					StringBuilder sb = new StringBuilder(row.length());
					for (int i = 0; i < row.length(); i++) {
						char c = row.charAt(i);
						sb.append(isSymbol(c) ? '\u0000' : c);
					}
					out.add(new JsonPrimitive(sb.toString()));
				}
				p.add("chars", out);
				changed = true;
			}
		}
		if (changed) {
			root.getAsJsonObject().add("providers", kept);
		}
		return changed ? root.toString() : json;
	}

	/** minecraft:include/space, include/default, include/unifont(바닐라가 default 글꼴을 쪼개 둔 것). */
	static boolean isVanillaInclude(String id) {
		String s = id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
		return s.startsWith("include/");
	}
}

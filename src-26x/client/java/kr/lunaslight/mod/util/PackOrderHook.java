package kr.lunaslight.mod.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 49-73차(1-7): <b>리소스팩 우선순위 강제</b> - 서버가 준 팩을 <b>내 팩 아래로</b> 내린다.
 *
 * <p>사용자 요청 1-7: "리소스팩 우선순위 강제". 서버에 들어가면 서버 팩이 <b>내 팩보다 위</b>에
 * 얹혀서 내가 쓰던 글꼴·아이콘·UI가 서버 것으로 덮인다. 그걸 뒤집는다.
 *
 * <h3>⚠️ 여기가 왜 조심스러운 자리인가</h3>
 * 팩 목록은 <b>리소스 로딩 전체가 지나가는 길목</b>이다. 순서를 잘못 바꾸거나 목록을 망가뜨리면
 * 텍스처·글꼴·소리가 통째로 안 읽히고, 최악에는 <b>클라이언트가 안 켜진다</b>.
 * 그래서 다음 규칙을 못 박았다.
 *
 * <ul>
 *   <li><b>개수를 바꾸지 않는다.</b> 오직 <b>자리만</b> 옮긴다(빼서 다른 칸에 끼워 넣기).</li>
 *   <li><b>옮기는 것은 id가 정확히 {@code server}(또는 {@code world})인 팩 하나뿐</b>이다.
 *       나머지는 원래 순서 그대로 둔다.</li>
 *   <li><b>내 팩이 하나도 없으면 아무것도 안 한다.</b> 옮길 이유가 없다.</li>
 *   <li>어디서든 조금이라도 이상하면 <b>원래 목록을 그대로 돌려준다</b>(손대지 않는 쪽이 항상 안전하다).</li>
 * </ul>
 *
 * <p><b>어디로 옮기나</b>: <b>맨 아래가 아니라</b> "내가 켠 팩 중 가장 아래" 바로 앞이다.
 * 맨 아래로 내리면 <b>바닐라 기본 팩보다도 아래</b>가 되어 서버 팩이 아예 안 먹는다 -
 * 우리가 원하는 건 "서버 팩을 끄기"가 아니라 <b>"내 팩이 이기게"</b>다.
 *
 * <p><b>언제 반영되나</b>: 팩 목록은 <b>리소스를 새로 읽을 때</b>만 만들어진다. 켠 뒤 서버에 다시
 * 들어가거나 F3+T를 눌러야 바뀐다 - 설정 설명에도 그렇게 적었다.
 */
public final class PackOrderHook {
	private PackOrderHook() {
	}

	/** 꺼져 있으면 믹스인이 이 한 줄만 읽고 나간다. */
	public static volatile boolean off = true;
	/** 월드에 딸려 오는 팩({@code world})도 같이 내릴지. */
	public static volatile boolean includeWorld;

	/** 서버 팩의 id. 바닐라가 {@code ServerResourcePackProvider}에서 이 이름으로 만든다. */
	private static final String SERVER = "server";
	private static final String WORLD = "world";
	/** 사용자가 resourcepacks 폴더에 넣은 팩의 id 접두사. */
	private static final String USER_PREFIX = "file/";

	/**
	 * 순서를 고친 새 목록. <b>바꿀 것이 없거나 조금이라도 이상하면 null</b>(그러면 믹스인이
	 * 원래 목록을 그대로 쓴다).
	 */
	public static List<Object> reorder(List<?> packs) {
		if (off || packs == null || packs.size() < 2) {
			return null;
		}
		try {
			List<Object> list = new ArrayList<>(packs);
			// 내 팩(resourcepacks 폴더 = "file/") 중 가장 위(가장 앞) 위치.
			int firstUser = -1;
			for (int i = 0; i < list.size(); i++) {
				String id = idOf(list.get(i));
				if (id != null && id.startsWith(USER_PREFIX)) {
					firstUser = i;
					break;
				}
			}
			if (firstUser < 0) {
				return null;                       // 내 팩이 없다 - 옮길 이유가 없다
			}
			// 49-107차: 내 팩보다 뒤(= 더 높은 우선순위)에 있는 서버/월드 팩을 <b>전부</b> 뽑아 내 팩 앞으로 내린다.
			// 예전엔 정확히 "server" 하나만, 그것도 단 하나만 옮겨서 (1) id가 "server/uuid"면 매칭 실패,
			// (2) 서버 팩이 여러 개면 하나만 내려가 나머지가 내 팩을 계속 덮었다. 이제 둘 다 처리한다.
			List<Object> servers = new ArrayList<>();
			for (int i = list.size() - 1; i > firstUser; i--) {
				if (isServerPack(idOf(list.get(i)))) {
					servers.add(0, list.remove(i));   // 원래 상대 순서 유지
				}
			}
			if (servers.isEmpty()) {
				return null;                       // 내 팩보다 위에 있는 서버 팩이 없다 - 이미 내 팩이 이긴다
			}
			list.addAll(firstUser, servers);       // 내 팩 바로 앞(= 낮은 우선순위)에 서버 팩들을 끼워 넣는다
			if (list.size() != packs.size()) {
				return null;                       // 있을 수 없는 일이지만, 그러면 손대지 않는다
			}
			return list;
		} catch (Throwable t) {
			LunaCompat.warnOnce("packOrder", t);
			return null;
		}
	}

	/** id가 서버(또는 월드) 팩인가 - "server" / "server/uuid" / "server:..." / "world" 등. 대소문자 무시. */
	private static boolean isServerPack(String id) {
		if (id == null) {
			return false;
		}
		String s = id.toLowerCase(java.util.Locale.ROOT);
		if (s.equals(SERVER) || s.startsWith(SERVER + "/") || s.startsWith(SERVER + ":") || s.startsWith(SERVER + "_")) {
			return true;
		}
		return includeWorld && (s.equals(WORLD) || s.startsWith(WORLD + "/") || s.startsWith(WORLD + ":") || s.startsWith(WORLD + "_"));
	}

	/**
	 * 팩의 id. <b>이름이 시대마다 갈린다</b> - {@code getName()}(~1.21.x) → {@code getId()}(1.21.11).
	 * 둘 다 없으면 null(그 팩은 판단에서 빠진다).
	 */
	private static String idOf(Object pack) {
		Object v = LunaCompat.callNoArg(pack, "getName");
		if (!(v instanceof String)) {
			v = LunaCompat.callNoArg(pack, "getId");
		}
		return v instanceof String s ? s : null;
	}
}

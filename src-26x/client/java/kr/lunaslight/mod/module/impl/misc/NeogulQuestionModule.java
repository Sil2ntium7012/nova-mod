package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.StringSetting;
import kr.lunaslight.mod.util.ChatState;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.NeogulData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 49-589차(사용자: 채팅 "[질문] cowin0301: 룰렛 쿠폰은 어디서 쓰나요?" 사진 - "질문 나오면 나한테만 화면에 질문 알림, 질문과 답변이 같이 나오는 줄은
 * 아니고 질문만, 내가 질문을 클릭해서 한 번 더 뜨는 건 알림 없이, 클릭했을 때 키워드별로 설정해 둔 답변 목록을 나한테만 보여 줘서 골라 붙여 넣게,
 * 설정은 너굴마을 기능에서 나만"): <b>너굴 질문 도우미</b> - 관리자(LNR_Sil2ntium) 전용.
 *
 * <ul>
 *   <li>알림: 채팅 "[질문] 이름: 내용"(같은 줄에 "[답변]"이 없는 것)이 처음 오면 화면 위 가운데에 4.5초 띄우고 딩 소리.</li>
 *   <li>같은 질문이 다시 오면(내가 그 질문을 눌러 서버가 한 번 더 보여 준 것) 알림 없이 "방금 누른 질문"으로 기억한다.</li>
 *   <li>답변 고르기: 채팅창이 열려 있고 입력칸이 "/답변"으로 시작하거나 방금(30초 안) 질문을 눌렀으면, 입력칸 위 오른쪽에 답변 목록.
 *       질문 내용에 키워드가 든 답변이 위로(초록 점). 누르면 입력칸에 붙는다(보내기는 직접).</li>
 *   <li>답변 목록 설정: "키워드/키워드 = 답변" 한 줄씩(쉼표는 쓸 수 없다 - 목록 칸이 쉼표로 나뉜다).</li>
 * </ul>
 * 로그 실측(11-08): 질문 "ꀀꀐꀁꑋ[질문] 이름: 내용\nꀀꀐꀁꑋ이어진 줄", 누르면 같은 줄이 다시 옴, 답이 달리면 "[질문] …\n…[답변] 이름: 답".
 */
public class NeogulQuestionModule extends Module {

	private final BooleanSetting alert = register(new BooleanSetting(
			"alert", "질문 알림", "새 질문이 올라오면 화면 위 가운데에 띄웁니다.", true));
	private final BooleanSetting sound = register(new BooleanSetting(
			"sound", "알림 소리", "새 질문이 올라오면 소리를 냅니다.", true));
	// 49-590차(사용자가 준 자주 묻는 질문 30개, 초월은 위키 기준으로 자세히): 목록 칸이 쉼표로 줄을 나눠서 답변 안 쉼표는 전각 쉼표(，)로 적고,
	// 붙여 넣을 때 보통 쉼표로 되돌린다. 기본값을 바꿀 때마다 설정 이름도 바꾼다(49-591차 answers_v3: 미끼 추가, 도토리 문구 / 49-592차 answers_v4: 곡괭이 획득처, 강화 장비 제작 장소 / 49-593차 answers_v5: 도토리 판매, 유저상점 4개 / 49-594차 answers_v6: 청크로더 범위 / answers_v7: 토벌 피로도 / 49-595차 answers_v8: 룰렛 / 49-600차 answers_v9: 내 정보).
	private final StringSetting answers = register(new StringSetting(
			"answers_v9", "답변 목록", "키워드/키워드 = 답변 형식으로 한 줄씩 넣습니다. 답변 안 쉼표는 ，(전각)로 써 주세요.",
			"기반암/제거기 = 기반암은 상점 2층 캐시상점에서 제거기를 구매하실 수 있습니다." + ", "
					+ "미끼/진주 = 낚시하다보면 진주가 나오는데 거기서 나옵니다." + ", "
					+ "곡괭이/곡/도구 어디/도구 획득 = 곡괭이 획득처는 제작 혹은 상점 2층 캐시상점 혹은 유저상점입니다!" + ", "
					+ "강화/초월/장비 제작/장비제작/대장간 = 워프 2페이지에서 대장간에 가시면 됩니다!" + ", "
					+ "도토리 판매/도토리판매/도토리 팔/도토리를 팔/도토리 거래/거래소 = /도토리거래소 등록 [수량] [비율] 로 판매 등록하실 수 있습니다. (비율 1000~1400) 취소는 /도토리거래소 일괄취소 입니다." + ", "
					+ "유저상점/유상 = /유저상점 으로 유저상점을 열 수 있습니다." + ", "
					+ "유상 판매/유저상점 판매/유상에 올/유저상점에 올/올리/판매 등록 = 손에 아이템을 들고 /유저상점 판매 (가격) [갯수] 로 등록합니다. 갯수를 안 쓰시면 손에 든 전량이 등록됩니다." + ", "
					+ "회수/일괄취소/판매 취소/내리 = /유저상점 일괄취소 로 판매중인 모든 아이템을 회수할 수 있습니다." + ", "
					+ "청크로더 범위/청크로더/몇칸/몇 칸 = 청크로더는 한번에 48x48입니다" + ", "
					+ "토벌 피로도/토벌피로도/피로도 쌓/피로도 다/피로도가 가득/피로도 생기/피로도 차 = 피로도가 가득차면 이후 잡는 몹의 전리품 및 경험치가 25%로 감소합니다" + ", "
					+ "룰렛/룰렛쿠폰/룰렛 쿠폰 = 스폰 우측에서 사용가능합니다" + ", "
					+ "내 정보/내정보/정보/프로필/스탯 = /프로필 해보세요!" + ", "
					+ "시세/얼마 = 손에 들고 /시세 를 입력하시면 최근 30일 유저상점 거래가를 보실 수 있습니다. 시세표 품목은 시세 범위 안 가격으로만 등록됩니다." + ", "
					+ "셋홈/스폰 위치/스폰위치/스폰설정/스폰 설정 = /섬 스폰설정 입니다" + ", "
					+ "하트/멤버십 = 멤버십 표시입니다. (/멤버십 혜택)" + ", "
					+ "고추 = 네더와트 기반이라 영혼모래에 심으시면 됩니다! 고추 자체가 씨앗입니다." + ", "
					+ "도토리/캐시 = /도토리거래소 /후원 혹은 도토리복권 등으로 획득 가능합니다." + ", "
					+ "치장 도구/치장도구/치장곡/치장 곡 = 다이아몬드 곡보다 1강 높게 시작하며 강화횟수가 같아 더 높은 스팩을 보여줍니다" + ", "
					+ "인챈트/인첸트/강화 = /인챈트 를 입력하시면 보실 수 있습니다. /워프 - 대장간 이동 후 주문의 흔적으로 강화 가능해요 https://mcng.kr/wiki#강화-일반강화" + ", "
					+ "마이크/보이스/음성 = 너굴마을 위키 서버정보에 보이스 모드가 있으며 V로 설정 가능합니다." + ", "
					+ "한글챗/한글 챗/한글채팅/한글 채팅 = 너굴마을 위키 서버정보에 한글챗 모드가 있습니다." + ", "
					+ "길라잡이 = 길라잡이는 모두 깨는걸 추천드리지만 /길라잡이로 끌 수 있습니다. 진행도는 길라잡이 아래쪽에 (0/30)과 같이 나옵니다." + ", "
					+ "후원/안들어/안 들어/지급 = 너굴마을은 자동 캐시 지급을 하고 있기 때문에 제대로된 입력을 하셨다면 보통 1분~30분 사이로는 받을 수 있습니다!" + ", "
					+ "도박/가위바위보 = /가위바위보 혹은 사설 도박장을 /확성기를 통해 찾아주세요" + ", "
					+ "초월 = 대장간 NPC 탱구에게서 초월 스크롤 1장으로 합니다. 괭이， 도끼， 삽은 단계마다 추가드롭 3~21%， 5단계 내구도 무한， 8단계 자동줍기 / 곡괭이 8단계는 태양열 + 연쇄채굴 / 무기는 최종데미지와 드랍률， 8단계 큐브 각인 https://mcng.kr/wiki#강화-초월강화" + ", "
					+ "잠수 포인트/잠수포인트/핫타임 코인/핫타임코인/추천 코인/추천코인/코인 = 상점 2층 우측에 모두 NPC가 있습니다." + ", "
					+ "어디서 팔/어디에 팔/팔아/판매/팔수/팔 수 = 보통 물고기를 제외한 대부분의 아이템은 상점 1층에서 모두 판매가 가능합니다. 물고기는 낚시터 NPC에게 판매하시면 됩니다." + ", "
					+ "브금/소리/음악/노래 = /브금 으로 설정 가능합니다!" + ", "
					+ "비행티켓/비행 티켓 = /비행티켓 입력시 획득처 확인 가능합니다." + ", "
					+ "광물창고/인벤에 안/인벤에안/광물 = 광물창고에 보관됩니다! /광물창고 혹은 /ㄱㅊ 으로도 열 수 있어요" + ", "
					+ "기본도구/기본 도구/기본템/깨졌/깨짐 = /기본템 으로 1회에 한해 받을 수 있어요. 혹은 광산 광물로 새로 만드시면 됩니다! 상점 중앙 요미 가챠 도구는 1강이 붙어 있어 일반 도구보다 1강 더 할 수 있어요!" + ", "
					+ "주문의 흔적/주문의흔적/주흔/흔적 = 던전파밍이나 유저거래， 도토리상점， 추천상점， 핫타임상점에서 구매가능합니다." + ", "
					+ "일퀘 작물/일일퀘스트 작물/퀘스트 작물/일퀘 = 밀， 감자， 당근， 벼， 고추， 호박， 수박 있습니다. https://mcng.kr/wiki#서브컨텐츠-일일퀘스트" + ", "
					+ "채집섬/도구를 안/도구 안/도구를 놓/보내기 = /보내기 닉네임 으로 도구를 받을 수 있어요! 지인이나 /확성기 로 부탁해보세요!" + ", "
					+ "엘리베이터/엘베 = 도토리상점이나 추천， 핫타임코인으로도 구매 가능합니다. 고급 엘리베이터는 무역순위 보상으로 획득 가능합니다." + ", "
					+ "엘리베이터/엘베/어떻게 쓰 = 같은 좌표， 다른 높이에 2개 이상 설치하시면 됩니다! 일반(차광유리)은 20칸， 고급(네더라이트 블럭)은 150칸까지 작동합니다! https://mcng.kr/wiki#편의기능-엘리베이터" + ", "
					+ "요리용 석탄/석탄/울타리 = 아니요! 요리용 석탄은 광산에서만 드랍됩니다!" + ", "
					+ "청크/청크로딩 = 플레이어 기준 9*9청크까지 활성화됩니다!" + ", "
					+ "자동줍기/자동 줍기 = 도끼， 괭이， 삽 초월 8단계 달성시 자동줍기가 적용됩니다! https://mcng.kr/wiki#강화-초월강화" + ", "
					+ "높이/건축 = -63 ~ 319까지 가능합니다!" + ", "
					+ "자라/성장/청크로더 = 네! 사람이 있거나 도토리 상점 청크로더 아이템 설치하셔야합니다!" + ", "
					+ "잠광/잠수 광질/잠수광질 = 잠수 광질로 섬에서만 가능합니다. 광물 캐는 상태에서 F3+T 누르고 마우스 떼면 리소스팩 리로딩되면서 계속 캐집니다! F3+P로 꺼짐 하면 다른 창으로 바꿔도 유지됩니다!").list());

	private static final Pattern Q = Pattern.compile("\\[질문\\]\\s*([^:\\[\\]\\n]{1,24}?)\\s*:\\s*(.+)", Pattern.DOTALL);
	private static final long DUP_MS = 30 * 60_000L;

	private static NeogulQuestionModule instance;
	private final Map<String, Long> seen = new LinkedHashMap<>();
	private String alertName = "", alertText = "";
	private long alertAt;
	/** 지금 답하려는 질문(누른 것, 없으면 마지막 새 질문). */
	private String focusText = "";
	private long clickedAt, lastQuestionAt;
	/** 이번 프레임에 그린 답변 줄 자리 {x, y, w, h} - 클릭 판정용. */
	private final List<int[]> rows = new ArrayList<>();
	private final List<String> rowAnswers = new ArrayList<>();
	/** 답변 목록 스크롤(맨 위에서 몇 줄 내렸는지)과 이번 프레임 목록 자리 {x, y, w, h, 전체 줄 수}. */
	private int pickerScroll;
	private int[] pickerBox;

	public NeogulQuestionModule() {
		super("neogul_question", "너굴 질문 도우미", ModuleCategory.SERVER, "새 질문 알림, 질문을 누르면 키워드 답변 고르기(관리자 전용)");
		serverGroup("너굴마을");
		newFeature("49-589");
		instance = this;
		try {
			ChatState.addPlainListener(this::onChat);
			net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
				if (!(screen instanceof ChatScreen)) {
					return;
				}
				net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterExtract(screen).register((s, g, mx, my, d) -> drawPicker(s, g, mx, my));
				net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseClick(screen)
						.register((s, e) -> !clickPicker(s, e.x(), e.y(), e.button()));
				net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents.allowKeyPress(screen)
						.register((s, e) -> !keyPicker(s, e.key()));
				net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseScroll(screen)
						.register((s, mx, my, ha, va) -> !scrollPicker(mx, my, va));
				pickerScroll = 0;
			});
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulQuestion:init", t);
		}
	}

	private boolean admin() {
		return NeogulData.isAdmin(client);
	}

	/** 관리자가 아니면 기능 목록에 안 보인다. */
	@Override
	public boolean hiddenInList() {
		return !admin();
	}

	/** 관리자가 아니면 새 기능 창에서도 묻지 않는다. */
	@Override
	public boolean newFeatureAsked() {
		return !admin();
	}

	private boolean active() {
		return isEnabled() && admin() && client.player != null && NeogulData.onNeogul(client);
	}

	private static String plain(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		s.codePoints().filter(cp -> !((cp >= 0xE000 && cp <= 0xF8FF) || cp >= 0xF0000 || (cp >= 0xA000 && cp <= 0xA4CF)))
				.forEach(sb::appendCodePoint);
		return sb.toString();
	}

	private void onChat(String line) {
		try {
			if (line == null || !active()) {
				return;
			}
			// 49-600차(사용자 사진: 질문을 누르면 "└ 채팅으로 답변을 입력하세요. (60초…)"): 그 안내가 오면 그때부터 60초 동안 답변 고르기를 띄운다
			if (line.contains("채팅으로 답변을 입력")) {
				clickedAt = System.currentTimeMillis();
				return;
			}
			if (!line.contains("[질문]")) {
				return;
			}
			String p = plain(line);
			if (p.contains("[답변]")) {
				return;   // 답이 달린 질문(질문 + 답변이 같이 온 줄)은 알리지 않는다
			}
			Matcher m = Q.matcher(p);
			if (!m.find()) {
				return;
			}
			String name = m.group(1).trim();
			String text = m.group(2).replace("\r", "").replaceAll("\\s*\\n\\s*", " ").trim();
			String key = name + "|" + text;
			long now = System.currentTimeMillis();
			synchronized (seen) {
				seen.values().removeIf(t -> now - t > DUP_MS);
				Long first = seen.get(key);
				if (first != null) {
					// 같은 질문이 또 왔다 = 내가 그 질문을 눌렀다 - 알림 없이 답변 고르기 대상으로
					focusText = text;
					clickedAt = now;
					return;
				}
				seen.put(key, now);
				while (seen.size() > 300) {
					seen.remove(seen.keySet().iterator().next());
				}
			}
			focusText = text;
			lastQuestionAt = now;
			if (alert.get()) {
				alertName = name;
				alertText = text;
				alertAt = now;
			}
			if (sound.get()) {
				LunaCompat.playUiSound(client, "EXPERIENCE_ORB_PICKUP", 1.6f);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulQuestion:chat", t);
		}
	}

	// ==================== 알림 ====================

	@Override
	public void onHudRender(GuiGraphicsExtractor ctx, DeltaTracker tick) {
		boolean preview = isPreview();
		long age = preview ? 1000 : System.currentTimeMillis() - alertAt;
		if (!preview && (alertAt == 0 || age > 4500 || !active())) {
			return;
		}
		String name = preview ? "cowin0301" : alertName;
		String text = preview ? "룰렛 쿠폰은 어디서 쓰나요?" : alertText;
		float a = age < 180 ? age / 180f : age > 3900 ? Math.max(0f, (4500 - age) / 600f) : 1f;
		if (preview) {
			drawAlert(ctx, previewCenterX(), previewCenterY() - 14, name, text, 1f);
			return;
		}
		drawAlert(ctx, client.getWindow().getGuiScaledWidth() / 2, 28 - Math.round(6 * (1 - Math.min(1f, age / 180f))), name, text, a);
	}

	private void drawAlert(GuiGraphicsExtractor ctx, int cx, int y, String name, String text, float alpha) {
		var font = client.font;
		int maxW = Math.min(260, client.getWindow().getGuiScaledWidth() - 40);
		List<net.minecraft.util.FormattedCharSequence> ls = font.split(LunaCompat.textLiteral(text), maxW - 16);
		int lines = Math.min(2, ls.size());
		int tw = 0;
		for (int i = 0; i < lines; i++) {
			tw = Math.max(tw, font.width(ls.get(i)));
		}
		String head = "질문  " + name;
		tw = Math.max(tw, font.width(head) + 4);
		int w = tw + 16, h = 14 + lines * 10 + 6;
		int x = cx - w / 2;
		float base = LunaDraw.alpha();
		LunaDraw.setAlpha(base * alpha);
		try {
			LunaDraw.shadow(ctx, x, y, w, h, 6);
			LunaDraw.roundRectBordered(ctx, x, y, w, h, 6, 0xE8121820, 0x66FFD36A);
			LunaDraw.roundRect(ctx, x + 6, y + 5, 3, 8, 1, 0xFFFFD36A);
			int ai = Math.max(4, Math.round(255 * alpha)) << 24;
			ctx.text(font, "질문", x + 12, y + 5, ai | 0xFFD36A, false);
			ctx.text(font, name, x + 12 + font.width("질문  "), y + 5, ai | 0xB8C2CF, false);
			for (int i = 0; i < lines; i++) {
				ctx.text(font, ls.get(i), x + 8, y + 17 + i * 10, ai | 0xFFFFFF, true);
			}
		} finally {
			LunaDraw.setAlpha(base);
		}
	}

	@Override
	public boolean hasPreview() {
		return true;
	}

	// ==================== 답변 고르기 ====================

	private static EditBox input(Object screen) {
		try {
			java.lang.reflect.Field f = LunaCompat.findField(ChatScreen.class, "input");
			if (f == null) {
				return null;
			}
			f.setAccessible(true);
			Object v = f.get(screen);
			return v instanceof EditBox e ? e : null;
		} catch (Throwable t) {
			return null;
		}
	}

	private boolean pickerWanted(Object screen) {
		if (!active()) {
			return false;
		}
		EditBox box = input(screen);
		String v = box == null ? "" : box.getValue();
		return v.startsWith("/답변") || System.currentTimeMillis() - clickedAt < 60_000L;
	}

	/** {키워드들, 답변}. */
	private List<String[]> parsedAnswers() {
		List<String[]> out = new ArrayList<>();
		for (String item : answers.items()) {
			int eq = item.indexOf('=');
			if (eq <= 0 || eq >= item.length() - 1) {
				continue;
			}
			out.add(new String[]{item.substring(0, eq).trim(), item.substring(eq + 1).trim().replace('，', ',')});
		}
		return out;
	}

	/** 답변 후보: 키워드(또는 명령어), 답변, 점수, 명령어에서 만든 것인지. */
	private record Cand(String keys, String answer, int score, boolean command) {
	}

	/**
	 * 질문 글을 낱말로: 원래 낱말(2글자 이상)과 뒤에 붙은 조사, 어미를 뗀 줄기를 둘 다 넣는다("플라이"가 "플라"로만 되지 않게).
	 * 1글자 줄기("돈", "섬", "밥")도 넣는다 - 글 안에 들었는지 볼 때는 2글자 이상만 쓰고, 명령어 이름과 똑같은지 볼 때만 쓴다.
	 */
	private static List<String> stems(String q) {
		List<String> out = new ArrayList<>();
		for (String w : q.split("[^가-힣A-Za-z0-9]+")) {
			if (w.isEmpty()) {
				continue;
			}
			if (w.length() >= 2 && !STOP.contains(w) && !out.contains(w)) {
				out.add(w);
			}
			String t = w;
			boolean cut = true;
			while (cut && t.length() >= 2) {
				cut = false;
				for (String suf : SUFFIX) {
					if (t.length() > suf.length() && t.endsWith(suf)) {
						t = t.substring(0, t.length() - suf.length());
						cut = true;
						break;
					}
				}
			}
			if (!t.isEmpty() && !STOP.contains(t) && !out.contains(t)) {
				out.add(t);
			}
		}
		return out;
	}

	private static final String[][] ALIAS = {{"귓말", "귓속말"}, {"귓", "귓속말"}, {"전챗", "전체채팅"}, {"섬챗", "섬채팅"}, {"동챗", "동네채팅"},
			{"유상", "유저상점"}, {"엘베", "엘리베이터"}, {"텔레포트", "티피"}, {"tp", "티피"}, {"주흔", "주문의 흔적"},
			{"일퀘", "퀘스트"}, {"멤버쉽", "멤버십"}, {"인첸", "인챈트"}, {"닉변", "한글닉네임"}, {"닉네임", "한글닉네임"}};

	private static final String[] SUFFIX = {"하는법", "하는방법", "하나요", "되나요", "인가요", "있나요", "없나요", "할까요", "하려면", "이에요", "이랑",
			"하면", "해요", "에서는", "에서", "으로", "에게", "한테", "까지", "부터", "나요", "가요", "예요", "에요", "인데", "는데", "하고",
			"랑", "은", "는", "이", "가", "을", "를", "에", "로", "도", "요", "죠", "서", "고"};
	private static final java.util.Set<String> STOP = java.util.Set.of("어디", "어디서", "어떻게", "어케", "뭐", "무엇", "무슨", "방법", "혹시", "그럼",
			"이거", "저거", "그거", "있어", "없어", "하는", "되는", "안되", "안돼", "궁금", "질문", "어떤", "언제", "누가", "얼마나",
			"해요", "가요", "하나요", "되나요", "있나요", "나요", "하면", "해", "가", "하", "안", "할", "되", "돼");

	/** 글 두 개가 2글자 묶음을 얼마나 같이 갖나(0~1, 짧은 쪽 기준). 키워드가 질문과 정확히 같지 않아도 비슷하면 잡으려고. */
	private static double bigram(String a, String b) {
		if (a.length() < 2 || b.length() < 2) {
			return 0;
		}
		java.util.Set<String> x = new java.util.HashSet<>(), y = new java.util.HashSet<>();
		for (int i = 0; i + 1 < a.length(); i++) {
			x.add(a.substring(i, i + 2));
		}
		for (int i = 0; i + 1 < b.length(); i++) {
			y.add(b.substring(i, i + 2));
		}
		int both = 0;
		for (String s : x) {
			if (y.contains(s)) {
				both++;
			}
		}
		return both / (double) Math.min(x.size(), y.size());
	}

	/**
	 * 후보 점수: 등록 답변은 키워드가 질문에 그대로 있으면 10, 질문 낱말과 비슷하면(2글자 묶음 60% 이상) 5, 질문 낱말이 답변 글에 있으면 낱말마다 2.
	 * 명령어 답변(위키 명령어 목록 NeogulCommands)은 명령어 이름이 질문에 있으면 10, 질문 낱말이 설명에 있으면 낱말마다 3.
	 * 점수 높은 순, 같으면 등록 답변 먼저. 질문이 없으면 등록 답변만(원래 순서).
	 */
	private List<Cand> rank(String q, List<String[]> all) {
		List<Cand> out = new ArrayList<>();
		// 줄임말을 원래 말로도 본다(질문 뒤에 덧붙임 - 원래 말 키워드도 그대로 맞게)
		for (String[] al : ALIAS) {
			if (q.contains(al[0]) && !q.contains(al[1])) {
				q = q + " " + al[1];
			}
		}
		String qn = q.replace(" ", "");
		List<String> st = stems(q);
		for (String[] a : all) {
			int sc = 0;
			if (!qn.isEmpty()) {
				boolean fuzzy = false;
				for (String k : a[0].split("[/|]")) {
					String t = k.trim().replace(" ", "");
					if (t.isEmpty()) {
						continue;
					}
					if (qn.contains(t)) {
						sc += 10;
					} else if (!fuzzy) {
						for (String w : st) {
							if (w.length() >= 2 && t.length() >= 2 && bigram(w, t) >= 0.6) {
								sc += 5;   // 비슷한 키워드는 답변 하나에 한 번만
								fuzzy = true;
								break;
							}
						}
					}
				}
				String an = a[1].replace(" ", "");
				for (String w : st) {
					if (w.length() >= 2 && an.contains(w)) {
						sc += 2;
					}
				}
			}
			out.add(new Cand(a[0], a[1], sc, false));
		}
		if (!st.isEmpty()) {
			for (kr.lunaslight.mod.util.NeogulCommands.Command c : kr.lunaslight.mod.util.NeogulCommands.ALL) {
				// 명령어 낱말("/섬 초대 <닉네임>" → 섬, 초대)이 모두 질문에 있으면 10, 첫 낱말만 있으면 4.
				String[] words = c.prefill().trim().substring(1).split(" ");
				String desc = c.desc().replace(" ", "");
				int got = 0;
				for (String wd : words) {
					if (wordIn(wd, qn, st)) {
						got++;
					}
				}
				int sc = got == words.length ? 10 : (got > 0 && wordIn(words[0], qn, st) ? 4 : 0);
				for (String w : st) {
					if (w.length() >= 2 && desc.contains(w)) {
						sc += 3;
					}
				}
				if (sc >= 3) {
					out.add(new Cand(c.usage(), commandAnswer(c), sc, true));
				}
			}
		}
		out.sort((x, y) -> Integer.compare(y.score, x.score));
		return out;
	}

	/**
	 * 49-596차(사용자: "질문에 닉네임을 그대로 박으면 어케"): 위키 모양 "/돈 보내기 (닉네임) (금액)"을 그대로 붙이지 않고 말로 풀어 쓴다 -
	 * "/돈 보내기 뒤에 닉네임, 금액을 쓰시면 됩니다! (다른 유저에게 돈을 보냅니다)". [ ]로 묶인 것은 "(내용은 빼도 됩니다)".
	 * 인자가 없으면 "/칭호 를 입력하시면 됩니다! (…)".
	 */
	private static String commandAnswer(kr.lunaslight.mod.util.NeogulCommands.Command c) {
		String u = c.usage();
		String base = c.prefill().trim();
		List<String> need = new ArrayList<>(), opt = new ArrayList<>();
		Matcher m = ARG.matcher(u);
		while (m.find()) {
			String a = m.group(2).trim();
			if (a.isEmpty()) {
				continue;
			}
			if ("[".equals(m.group(1))) {
				opt.add(a);
			} else {
				need.add(a);
			}
		}
		String desc = c.desc().replaceAll("\\.$", "");
		if (need.isEmpty() && opt.isEmpty()) {
			return base + " 를 입력하시면 됩니다! (" + desc + ")";
		}
		List<String> all = new ArrayList<>(need);
		all.addAll(opt);
		String list = String.join(", ", all);
		String out = base + " 뒤에 " + list + josa(all.get(all.size() - 1), "을", "를") + " 쓰시면 됩니다!";
		if (!opt.isEmpty() && !need.isEmpty()) {
			out += " (" + String.join(", ", opt) + josa(opt.get(opt.size() - 1), "은", "는") + " 빼도 됩니다)";
		}
		return out + " (" + desc + ")";
	}

	private static final Pattern ARG = Pattern.compile("([<(\\[])([^>)\\]]*)[>)\\]]");

	/** 받침이 있으면 a, 없으면 b(한글이 아니면 b). */
	private static String josa(String word, String a, String b) {
		if (word.isEmpty()) {
			return b;
		}
		char ch = word.charAt(word.length() - 1);
		if (ch >= 0xAC00 && ch <= 0xD7A3) {
			return (ch - 0xAC00) % 28 != 0 ? a : b;
		}
		return b;
	}

	/** 명령어 낱말이 질문에 있나: 그대로 들었거나, 질문 줄기와 같거나, 2글자 이상 줄기가 앞부분이 같으면("보내" ↔ "보내기"). */
	private static boolean wordIn(String wd, String qn, List<String> st) {
		if (wd.isEmpty()) {
			return false;
		}
		if ((wd.length() >= 2 && qn.contains(wd)) || st.contains(wd)) {
			return true;
		}
		for (String w : st) {
			if (w.length() >= 2 && (wd.startsWith(w) || w.startsWith(wd) && wd.length() >= 2)) {
				return true;
			}
		}
		return false;
	}

	private static boolean matches(String keys, String question) {
		String q = question.replace(" ", "");
		for (String k : keys.split("[/|]")) {
			String t = k.trim().replace(" ", "");
			if (!t.isEmpty() && q.contains(t)) {
				return true;
			}
		}
		return false;
	}

	private void drawPicker(Object screen, GuiGraphicsExtractor g, int mx, int my) {
		rows.clear();
		rowAnswers.clear();
		pickerBox = null;
		try {
			if (!pickerWanted(screen)) {
				return;
			}
			var font = client.font;
			List<String[]> all = parsedAnswers();
			if (all.isEmpty()) {
				return;
			}
			String q = focusText == null ? "" : focusText;
			// 49-595차(사용자: "위키 명령어로 '이거 어떻게 해요?'에 '이 명령어로 할 수 있습니다' 답변도, 키워드가 여러 개거나 정확하지 않을 수 있으니
			// 여러 형식으로 4가지"): 등록 답변 + 명령어 답변을 점수로 줄 세운다. 점수가 있는 위 4개가 추천(초록 점, 명령어 답변은 파란 점).
			List<Cand> cands = rank(q, all);
			int hit = 0;
			for (Cand c : cands) {
				if (c.score > 0 && hit < 4) {
					hit++;
				}
			}
			List<String[]> sorted = new ArrayList<>();
			for (Cand c : cands) {
				sorted.add(new String[]{c.keys, c.answer, c.command ? "c" : ""});
			}
			int total = sorted.size();
			int n = Math.min(8, total);
			pickerScroll = Math.max(0, Math.min(pickerScroll, total - n));
			int sw = client.getWindow().getGuiScaledWidth(), sh = client.getWindow().getGuiScaledHeight();
			// 49-601차(사용자: "리스트가 너무 복사하기 어려운 위치"): 오른쪽 아래 구석 → 화면 가운데(입력칸 위), 넓고 줄 높이도 크게.
			// Ctrl + 1~8로 키보드로도 고른다(줄 앞 번호).
			int w = Math.min(340, sw - 20);
			int rowH = 15;
			int h = 18 + n * rowH + 4;
			int x = (sw - w) / 2, y = Math.max(4, (sh - 16 - h) / 2 + 10);
			LunaDraw.roundRectBordered(g, x, y, w, h, 5, 0xE8121820, 0x4DFFFFFF);
			pickerBox = new int[]{x, y, w, h, total};
			String head = (q.isEmpty() ? "답변 고르기" : "답변 고르기 | " + q);
			if (total > n) {
				String pg = (pickerScroll + 1) + "-" + (pickerScroll + n) + " / " + total;
				g.text(font, pg, x + w - 6 - font.width(pg), y + 5, 0xFF8A93A0, false);
				head = LunaDraw.ellipsize(font, head, w - 18 - font.width(pg));
			}
			g.text(font, LunaDraw.ellipsize(font, head, w - 12), x + 6, y + 5, 0xFFFFD36A, false);
			for (int i = 0; i < n; i++) {
				int si = i + pickerScroll;
				String[] a = sorted.get(si);
				int ry = y + 17 + i * rowH;
				int ry2 = ry + 2;
				boolean hov = LunaDraw.in(mx, my, x + 2, ry, w - 4, rowH - 1);
				LunaDraw.roundRect(g, x + 2, ry, w - 4, rowH - 1, 3, hov ? 0x40FFFFFF : 0x12FFFFFF);
				String num = Integer.toString(i + 1);
				g.text(font, num, x + 7, ry2 + 1, 0xFF8A93A0, false);
				LunaDraw.roundRect(g, x + 15, ry2 + 3, 4, 4, 2, si < hit ? ("c".equals(a[2]) ? 0xFF7EC8F0 : 0xFF7EE08A) : 0x40FFFFFF);
				String kw = a[0] + "  ";
				int kwMax = Math.min(font.width(kw), w / 3);
				g.text(font, LunaDraw.ellipsize(font, kw, kwMax), x + 23, ry2 + 1, 0xFF8A93A0, false);
				int ax = x + 23 + kwMax;
				g.text(font, LunaDraw.ellipsize(font, a[1], x + w - 6 - ax), ax, ry2 + 1, 0xFFFFFFFF, false);
				rows.add(new int[]{x + 2, ry, w - 4, rowH - 1});
				rowAnswers.add(a[1]);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulQuestion:picker", t);
		}
	}

	/** 목록 위에서 휠이면 목록을 넘기고 true. */
	private boolean scrollPicker(double mx, double my, double va) {
		int[] b = pickerBox;
		if (b == null || rows.isEmpty() || !LunaDraw.in(mx, my, b[0], b[1], b[2], b[3])) {
			return false;
		}
		pickerScroll = Math.max(0, Math.min(b[4] - 8, pickerScroll + (va > 0 ? -1 : 1)));
		return true;
	}

	/** 답변 줄을 눌렀으면 입력칸에 붙이고 true(채팅창 클릭은 막음). */
	private boolean clickPicker(Object screen, double mx, double my, int button) {
		if (button != 0 || rows.isEmpty()) {
			return false;
		}
		for (int i = 0; i < rows.size() && i < rowAnswers.size(); i++) {
			int[] r = rows.get(i);
			if (LunaDraw.in(mx, my, r[0], r[1], r[2], r[3])) {
				pick(screen, i);
				return true;
			}
		}
		return false;
	}

	/** Ctrl + 1~8: 그 번호 답변을 붙인다. */
	private boolean keyPicker(Object screen, int key) {
		if (rows.isEmpty() || key < com.mojang.blaze3d.platform.InputConstants.KEY_1 || key > com.mojang.blaze3d.platform.InputConstants.KEY_8
				|| (LunaCompat.heldModifiersAny(client) & LunaCompat.MOD_CTRL) == 0) {
			return false;
		}
		int i = key - com.mojang.blaze3d.platform.InputConstants.KEY_1;
		if (i >= rowAnswers.size()) {
			return false;
		}
		pick(screen, i);
		return true;
	}

	/** i번째 답변을 입력칸에 붙인다("/답변…"이면 뒤에, 아니면 커서 자리에). */
	private void pick(Object screen, int i) {
		EditBox box = input(screen);
		if (box != null) {
			String v = box.getValue();
			String ans = rowAnswers.get(i);
			if (v.startsWith("/답변")) {
				box.setValue(v.endsWith(" ") ? v + ans : v + " " + ans);
			} else {
				box.insertText(ans);
			}
			box.moveCursorToEnd(false);
		}
		clickedAt = 0;
		LunaCompat.playUiSound(client, "UI_BUTTON_CLICK", 1.2f);
	}

	// ==================== 새 기능 창 미리보기 ====================

	@Override
	public boolean hasNewFeaturePreview() {
		return true;
	}

	@Override
	public void drawNewFeaturePreview(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
		drawAlert(ctx, x + w / 2, y + 6, "cowin0301", "룰렛 쿠폰은 어디서 쓰나요?", 1f);
		var font = client.font;
		int bx = x + 10, by = y + 46, bw = w - 20;
		LunaDraw.roundRectBordered(ctx, bx, by, bw, 40, 5, 0xE8121820, 0x4DFFFFFF);
		ctx.text(font, "답변 고르기 | 룰렛 쿠폰은 어디서…", bx + 6, by + 4, 0xFFFFD36A, false);
		LunaDraw.roundRect(ctx, bx + 6, by + 19, 4, 4, 2, 0xFF7EE08A);
		ctx.text(font, LunaDraw.ellipsize(font, "룰렛  스폰 오른쪽에서 돌릴 수 있어요", bw - 22), bx + 14, by + 17, 0xFFFFFFFF, false);
		LunaDraw.roundRect(ctx, bx + 6, by + 31, 4, 4, 2, 0x40FFFFFF);
		ctx.text(font, LunaDraw.ellipsize(font, "멤버십/하트  멤버십 혜택이에요!", bw - 22), bx + 14, by + 29, 0xFFFFFFFF, false);
	}
}

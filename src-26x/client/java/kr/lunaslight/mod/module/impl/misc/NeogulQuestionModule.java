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
	// 붙여 넣을 때 보통 쉼표로 되돌린다. 기본값을 바꿀 때마다 설정 이름도 바꾼다(49-591차 answers_v3: 미끼 추가, 도토리 문구 / 49-592차 answers_v4: 곡괭이 획득처, 강화 장비 제작 장소 / 49-593차 answers_v5: 도토리 판매, 유저상점 4개 / 49-594차 answers_v6: 청크로더 범위 / answers_v7: 토벌 피로도 / 49-595차 answers_v8: 룰렛 / 49-600차 answers_v9: 내 정보 / 49-607차 answers_v10: 물고기 확률 / 49-632차 answers_v11: 초월, 자동줍기, 인챈트, 일퀘, 엘리베이터 답변 길게 + 링크 대신 /위키, 대장간 줄에서 초월 키워드 뺌 / 49-633차 answers_v12: 유저상점 판매 길게, "올리" "어떻게 쓰" 키워드 뺌(다른 질문에 잘못 걸림), 강화 복구 줄, 채집티켓 / 49-634차 answers_v13: 높이 -64 ~ 320(사용자), Y 몇 / 몇까지 / 농사땅 키워드).
	private final StringSetting answers = register(new StringSetting(
			"answers_v13", "답변 목록", "키워드/키워드 = 답변 형식으로 한 줄씩 넣습니다. 답변 안 쉼표는 ，(전각)로 써 주세요.",
			"기반암/제거기 = 기반암은 상점 2층 캐시상점에서 제거기를 구매하실 수 있습니다." + ", "
					+ "미끼/진주 = 낚시하다보면 진주가 나오는데 거기서 나옵니다." + ", "
					+ "물고기 확률/물고기확률/낚시 확률/낚시확률/물고기 등급/등급 확률/등급별 = 물고기 등급별 확률은 S급 0.78%， A급 8%， B급 57.5%， C급 33.72% 입니다!" + ", "
					+ "S급/범고래/상어/소라고동 = S급 물고기(범고래， 상어， 소라고동)는 전체 0.78% 확률로 낚입니다." + ", "
					+ "A급/연어/장어/해파리/랍스터/참치 = A급 물고기(연어， 장어， 해파리， 랍스터， 참치)는 전체 8% 확률로 낚입니다." + ", "
					+ "B급/열대어/메기/베스/개복치/불가사리/멸치/해마/금붕어/쭈꾸미/개불 = B급 물고기(열대어， 메기， 베스， 개복치， 불가사리， 다시 멸치， 분홍 해마， 금붕어， 쭈꾸미， 개불)는 전체 57.5% 확률로 낚입니다." + ", "
					+ "C급/송사리/청개구리/콜라 캔/빈 깡통/쓰레기 봉투/쓰레기 = C급(송사리， 청개구리， 콜라 캔， 빈 깡통， 쓰레기 봉투)은 전체 33.72% 확률로 낚입니다." + ", "
					+ "곡괭이/곡/도구 어디/도구 획득 = 곡괭이 획득처는 제작 혹은 상점 2층 캐시상점 혹은 유저상점입니다!" + ", "
					+ "강화/장비 제작/장비제작/대장간 = 메뉴(Shift+F) → 워프 2페이지에서 대장간에 가시면 됩니다! 강화와 초월은 대장간 NPC 탱구에게 말을 걸어서 하실 수 있어요." + ", "
					+ "도토리 판매/도토리판매/도토리 팔/도토리를 팔/도토리 거래/거래소 = /도토리거래소 등록 [수량] [비율] 로 판매 등록하실 수 있습니다. (비율 1000~1400) 취소는 /도토리거래소 일괄취소 입니다." + ", "
					+ "유저상점/유상 = /유저상점 으로 유저상점을 열 수 있습니다." + ", "
					+ "유상 판매/유저상점 판매/유상에 올/유저상점에 올/유저상점 올리/판매 등록 = 팔 아이템을 손에 들고 /유저상점 판매 (가격) [갯수] 를 입력하시면 등록됩니다! 갯수를 안 쓰시면 손에 든 전부가 올라가고， 시세표 품목은 시세 범위 안 가격으로만 등록돼요. 회수는 /유저상점 일괄취소 입니다." + ", "
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
					+ "인챈트/인첸트/강화 = /인챈트 를 입력하시면 보실 수 있습니다. 강화는 /워프 대장간의 NPC 탱구에게 주문의 흔적으로 하고， 실패해도 스탯과 단계는 안 떨어지며 실패한 횟수만큼 복구 주문서로 되돌릴 수 있어요. 자세한 건 /위키 에서 볼 수 있어요!" + ", "
					+ "마이크/보이스/음성 = 너굴마을 위키 서버정보에 보이스 모드가 있으며 V로 설정 가능합니다." + ", "
					+ "한글챗/한글 챗/한글채팅/한글 채팅 = 너굴마을 위키 서버정보에 한글챗 모드가 있습니다." + ", "
					+ "길라잡이 = 길라잡이는 모두 깨는걸 추천드리지만 /길라잡이로 끌 수 있습니다. 진행도는 길라잡이 아래쪽에 (0/30)과 같이 나옵니다." + ", "
					+ "후원/안들어/안 들어/지급 = 너굴마을은 자동 캐시 지급을 하고 있기 때문에 제대로된 입력을 하셨다면 보통 1분~30분 사이로는 받을 수 있습니다!" + ", "
					+ "도박/가위바위보 = /가위바위보 혹은 사설 도박장을 /확성기를 통해 찾아주세요" + ", "
					+ "초월/초월강화/초월 강화/초월 효과/초월효과/초월 단계/초월단계/초월하면 = 초월은 대장간 NPC 탱구에게 초월 스크롤 1장으로 합니다(스크롤은 상점 2층 도토리 상점). 괭이， 도끼， 삽， 곡괭이는 단계별 추가 드랍 확률 3~21%， 5단계 내구도 무한이고， 8단계에 괭이， 도끼， 삽은 자동 줍기， 곡괭이는 태양열(광물이 구워져 나옴) + 연쇄 채굴(아래 1칸도 같이 캠)이 붙어요. 무기는 최종 데미지와 드랍률이 오르고 8단계에 큐브 각인이 됩니다. 자세한 건 /위키 에서 볼 수 있어요!" + ", "
					+ "잠수 포인트/잠수포인트/핫타임 코인/핫타임코인/추천 코인/추천코인/코인 = 상점 2층 우측에 모두 NPC가 있습니다." + ", "
					+ "어디서 팔/어디에 팔/팔아/판매/팔수/팔 수 = 보통 물고기를 제외한 대부분의 아이템은 상점 1층에서 모두 판매가 가능합니다. 물고기는 낚시터 NPC에게 판매하시면 됩니다." + ", "
					+ "브금/소리/음악/노래 = /브금 으로 설정 가능합니다!" + ", "
					+ "비행티켓/비행 티켓/채집티켓/채집 티켓 = /비행티켓 입력시 획득처 확인 가능합니다." + ", "
					+ "광물창고/인벤에 안/인벤에안/광물 = 광물창고에 보관됩니다! /광물창고 혹은 /ㄱㅊ 으로도 열 수 있어요" + ", "
					+ "기본도구/기본 도구/기본템/깨졌/깨짐 = /기본템 으로 1회에 한해 받을 수 있어요. 혹은 광산 광물로 새로 만드시면 됩니다! 상점 중앙 요미 가챠 도구는 1강이 붙어 있어 일반 도구보다 1강 더 할 수 있어요!" + ", "
					+ "주문의 흔적/주문의흔적/주흔/흔적 = 던전파밍이나 유저거래， 도토리상점， 추천상점， 핫타임상점에서 구매가능합니다." + ", "
					+ "일퀘 작물/일일퀘스트 작물/퀘스트 작물/일퀘 = 섬에서 밀， 감자， 당근， 벼， 고추， 호박， 수박 중 하나를 30~300개 캐는 퀘스트가 나옵니다. 일일퀘스트는 매일 자정에 바뀌고 /일일퀘스트 로 진행도를 볼 수 있어요. 6개를 다 깨면 합산 레벨에 맞는 보상을 받습니다!" + ", "
					+ "채집섬/도구를 안/도구 안/도구를 놓/보내기 = /보내기 닉네임 으로 도구를 받을 수 있어요! 지인이나 /확성기 로 부탁해보세요!" + ", "
					+ "엘리베이터/엘베 = 도토리상점이나 추천， 핫타임코인으로도 구매 가능합니다. 고급 엘리베이터는 무역순위 보상으로 획득 가능합니다." + ", "
					+ "엘리베이터/엘베/엘리베이터 사용/엘베 사용/엘리베이터 설치/엘베 설치 = 같은 좌표 위아래로 같은 종류 엘리베이터를 2개 이상 설치하고 점프로 올라가고 쉬프트로 내려갑니다! 일반(차광 유리)은 20칸， 고급(네더라이트 블럭)은 150칸까지 되고， 도착할 층 블록 위가 막혀 있으면 이동이 안 돼요." + ", "
					+ "요리용 석탄/석탄/울타리 = 아니요! 요리용 석탄은 광산에서만 드랍됩니다!" + ", "
					+ "청크/청크로딩 = 플레이어 기준 9*9청크까지 활성화됩니다!" + ", "
					+ "강화복구/강화 복구/복구 가능/복구가능/복구 주문서/복구주문서/복구 횟수 = 강화에 실패한 횟수만큼 복구 가능 횟수가 쌓이고， 복구 주문서를 인벤토리에서 장비 위로 끌어다 놓으면 확률(실버 10%， 다이아 30%， 골드 50%， 루비 100%)로 강화 횟수 1회가 돌아와요. 복구에 실패해도 복구 가능 횟수는 남아서 다시 할 수 있어요! 자세한 건 /위키 에서 볼 수 있어요!" + ", "
					+ "자동줍기/자동 줍기 = 괭이， 도끼， 삽을 초월 8단계까지 하면 자동 줍기가 붙습니다! 괭이는 밀， 벼， 고추， 당근， 감자， 사탕수수， 나뭇잎， 도끼는 호박， 수박， 원목， 나무판자， 삽은 흙과 모래 종류가 바로 인벤토리로 들어와요. 자세한 건 /위키 에서 볼 수 있어요!" + ", "
					+ "높이/건축/y 몇/y좌표/y 좌표/몇까지/농사땅/최대 높이/최소 높이 = 섬에서는 Y좌표 -64 ~ 320까지 블럭 설치와 농사가 가능합니다!" + ", "
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
	/** 49-605차: 답을 낸 질문(이름|내용) - 서버가 같은 질문을 또 보여도 답변 고르기를 다시 띄우지 않는다. */
	private final java.util.Set<String> answered = new java.util.HashSet<>();
	private String focusKey = "", pendingKey = "", pendingText = "";
	private long pendingAt;
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
			ChatState.addLoggedListener(this::onChat);   // 49-633차: 채팅창에 들어간 줄 그대로(예전 입구는 [질문]을 자주 놓침)
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

	private long diagAt;

	private void onChat(String line) {
		try {
			if (line == null) {
				return;
			}
			if (!active()) {
				// 49-633차: 질문 줄이 왔는데 꺼져 있으면 왜인지 1분에 한 번 기록(알림이 안 올 때 원인 찾기)
				if (line.contains("[질문]") && System.currentTimeMillis() - diagAt > 60_000) {
					diagAt = System.currentTimeMillis();
					kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 질문 도우미 꺼짐: 켜짐={} 관리자={} 너굴={}", isEnabled(), admin(),
							client.player != null && NeogulData.onNeogul(client));
				}
				return;
			}
			// 49-600차(사용자 사진: 질문을 누르면 "└ 채팅으로 답변을 입력하세요. (60초…)"): 그 안내가 오면 그때부터 60초 동안 답변 고르기를 띄운다
			if (line.contains("채팅으로 답변을 입력")) {
				long now = System.currentTimeMillis();
				clickedAt = now;
				if (now - pendingAt < 5000 && !pendingKey.isEmpty()) {
					// 방금 다시 온 질문이 누른 질문(답한 적 있어도 또 누르면 다시 답할 수 있게)
					focusKey = pendingKey;
					focusText = pendingText;
					synchronized (seen) {
						answered.remove(pendingKey);
					}
				}
				return;
			}
			// 49-605차(사용자 사진: 답을 내고 "답변을 제출하였습니다" 뒤에 같은 질문이 또 와서 고르기 창이 다시 떴다): 답을 내면 닫고 그 질문은 기억
			if (!line.contains("[질문]")) {
				// 49-607차(사용자 사진: 다른 사람이 먼저 답해 "[답변] nmiao: …" 뒤 "해당 질문을 찾을 수 없습니다", 채널이 전체채팅으로 바뀜 - 창이 그대로):
				// 답변 줄(누구 것이든), 질문 없음, 채널 바뀜에도 닫는다
				String pl = plain(line);
				if (line.contains("답변을 제출") || pl.contains("[답변]") || line.contains("질문을 찾을 수 없") || line.contains("채팅 채널을")) {
					clickedAt = 0;
					synchronized (seen) {
						if (!focusKey.isEmpty()) {
							answered.add(focusKey);
						}
					}
				} else if (line.contains("답변") && line.contains("취소")) {
					clickedAt = 0;
				}
				return;
			}
			String p = plain(line);
			int ai = p.indexOf("[답변]");
			if (ai >= 0) {
				// 답이 달린 질문(질문 + 답변이 같이 온 줄)은 알리지 않는다. 49-607차: 그 질문을 보고 있었으면 닫고 답한 것으로
				Matcher am = Q.matcher(p.substring(0, ai));
				if (am.find()) {
					String ak = am.group(1).trim() + "|" + am.group(2).replace("\r", "").replaceAll("\\s*\\n\\s*", " ").trim();
					synchronized (seen) {
						answered.add(ak);
					}
					if (ak.equals(focusKey)) {
						clickedAt = 0;
					}
				}
				return;
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
					// 같은 질문이 또 왔다 = 내가 그 질문을 눌렀거나(그 뒤 "채팅으로 답변을 입력"이 옴) 서버가 다시 보여 준 것
					pendingKey = key;
					pendingText = text;
					pendingAt = now;
					if (!answered.contains(key)) {
						focusKey = key;
						focusText = text;
						clickedAt = now;
					}
					return;
				}
				seen.put(key, now);
				while (seen.size() > 300) {
					seen.remove(seen.keySet().iterator().next());
				}
				answered.retainAll(seen.keySet());
			}
			focusKey = key;
			focusText = text;
			lastQuestionAt = now;
			kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 질문 알림: {} - {}", name, text);   // 49-610차: 알림이 안 보일 때 확인용
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
		if (!preview && (alertAt == 0 || age > 6000 || !active())) {
			return;
		}
		String name = preview ? "cowin0301" : alertName;
		String text = preview ? "룰렛 쿠폰은 어디서 쓰나요?" : alertText;
		float a = age < 180 ? age / 180f : age > 5400 ? Math.max(0f, (6000 - age) / 600f) : 1f;
		if (preview) {
			drawAlert(ctx, previewCenterX(), previewCenterY() - 14, name, text, 1f);
			return;
		}
		// 49-610차(사용자: "질문 올라올 때 알림이 안 와, 화면에"): 서버 HUD, 보스바보다 위 층에, 위쪽 HUD 줄 아래(화면 높이 18%)로 내리고 6초
		try {
			ctx.nextStratum();
		} catch (Throwable ignored) {
		}
		int sh = client.getWindow().getGuiScaledHeight();
		drawAlert(ctx, client.getWindow().getGuiScaledWidth() / 2, Math.max(40, Math.round(sh * 0.18f)) - Math.round(6 * (1 - Math.min(1f, age / 180f))), name, text, a);
	}

	private void drawAlert(GuiGraphicsExtractor ctx, int cx, int y, String name, String text, float alpha) {
		var font = client.font;
		int maxW = Math.min(260, client.getWindow().getGuiScaledWidth() - 40);
		List<String> ls = wrap(font, text, maxW - 16, 2);
		int tw = 0;
		for (String l : ls) {
			tw = Math.max(tw, LunaDraw.width(font, l));
		}
		tw = Math.max(tw, LunaDraw.width(font, "질문  " + name) + 4);
		int w = tw + 16, h = 15 + ls.size() * LH + 5;
		int x = cx - w / 2;
		float base = LunaDraw.alpha();
		LunaDraw.setAlpha(base * alpha);
		try {
			LunaDraw.shadow(ctx, x, y, w, h, 6);
			LunaDraw.roundRectBordered(ctx, x, y, w, h, 6, 0xE8121820, 0x66FFD36A);
			LunaDraw.roundRect(ctx, x + 6, y + 5, 3, 8, 1, 0xFFFFD36A);
			LunaDraw.text(ctx, font, "질문", x + 12, y + 5, 0xFFFFD36A);
			LunaDraw.text(ctx, font, name, x + 12 + LunaDraw.width(font, "질문  "), y + 5, 0xFFB8C2CF);
			for (int i = 0; i < ls.size(); i++) {
				LunaDraw.text(ctx, font, ls.get(i), x + 8, y + 17 + i * LH, 0xFFFFFFFF);
			}
		} finally {
			LunaDraw.setAlpha(base);
		}
	}

	private static final int LH = 11;

	/** 49-605차: 노바 글꼴 폭으로 줄 나누기(빈칸에서 우선 끊음), 넘치면 마지막 줄 "…". */
	private static List<String> wrap(net.minecraft.client.gui.Font f, String s, int maxW, int maxLines) {
		List<String> out = new ArrayList<>();
		String rest = s == null ? "" : s.trim();
		while (!rest.isEmpty() && out.size() < maxLines) {
			if (LunaDraw.width(f, rest) <= maxW) {
				out.add(rest);
				rest = "";
				break;
			}
			if (out.size() == maxLines - 1) {
				out.add(LunaDraw.ellipsize(f, rest, maxW));
				rest = "";
				break;
			}
			int cut = 1;
			while (cut < rest.length() && LunaDraw.width(f, rest.substring(0, cut + 1)) <= maxW) {
				cut++;
			}
			int sp = rest.lastIndexOf(' ', cut);
			if (sp > cut / 2) {
				cut = sp;
			}
			out.add(rest.substring(0, cut).trim());
			rest = rest.substring(cut).trim();
		}
		if (out.isEmpty()) {
			out.add("");
		}
		return out;
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

	/** 49-633차: 키워드 낱말(2글자 이상)이 모두 질문에 있나. 1글자 낱말이 있으면 아니다("팔 수"가 아무 질문에나 걸리지 않게). */
	private static boolean allIn(String[] parts, String qn) {
		for (String p : parts) {
			if (p.length() < 2 || !qn.contains(p)) {
				return false;
			}
		}
		return true;
	}

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
		q = q.toLowerCase(java.util.Locale.ROOT);   // 49-607차: "a급"도 "A급" 키워드에 맞게
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
					String t = k.trim().replace(" ", "").toLowerCase(java.util.Locale.ROOT);
					if (t.isEmpty()) {
						continue;
					}
					String[] kp = k.trim().toLowerCase(java.util.Locale.ROOT).split("\\s+");
					boolean multi = kp.length > 1;
					if (qn.contains(t)) {
						sc += multi ? 13 : 10;   // 49-633차: 여러 낱말 키워드("유저상점 판매")가 그대로 있으면 한 낱말보다 높게
					} else if (multi && allIn(kp, qn)) {
						sc += 12;   // 49-633차(사진: "유저 상점에서 판매는 어떻게 하나요?"): 낱말이 떨어져 있어도 모두 있으면
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
				String an = a[1].replace(" ", "").toLowerCase(java.util.Locale.ROOT);
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
			int sw = client.getWindow().getGuiScaledWidth(), sh = client.getWindow().getGuiScaledHeight();
			// 49-605차(사용자: "가운데라 불편하고 UI가 조금 안 예뻐, 글꼴은 클라 기능 글꼴로, 답글 좀 더 보기 쉽게"): 채팅창 바로 오른쪽,
			// 입력칸 위에 붙인다(채팅을 안 가림). 자리가 모자라면 화면 오른쪽. 답변은 두 줄까지 다 보이게, 글자는 노바 글꼴(LunaDraw).
			int chatRight = 330;
			try {
				var o = client.options;
				chatRight = 4 + (int) Math.ceil(net.minecraft.client.gui.components.ChatComponent.getWidth(o.chatWidth().get()) * o.chatScale().get()) + 10;
			} catch (Throwable ignored) {
				// 기본값
			}
			int avail = sw - chatRight - 6;
			int w, x;
			if (avail >= 200) {
				w = Math.min(300, avail);
				x = chatRight;
			} else {
				w = Math.min(300, sw - 12);
				x = sw - w - 6;
			}
			int maxH = sh - 16 - 8;
			pickerScroll = Math.max(0, Math.min(pickerScroll, Math.max(0, total - 1)));
			List<String[]> vis = new ArrayList<>();
			int used = headH(font, w, q) + 14;
			for (int si = pickerScroll; si < total && vis.size() < 8; si++) {
				int rh = rowH(font, w, sorted.get(si)[1]);
				if (used + rh + 3 > maxH && !vis.isEmpty()) {
					break;
				}
				used += rh + 3;
				vis.add(sorted.get(si));
			}
			int h = used;
			int y = Math.max(4, sh - 16 - h - 2);
			pickerBox = new int[]{x, y, w, h, total, vis.size()};
			drawBox(g, x, y, w, h, q, vis, pickerScroll, hit, total, mx, my, true);
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulQuestion:picker", t);
		}
	}

	private int headH(net.minecraft.client.gui.Font font, int w, String q) {
		return 19 + (q.isEmpty() ? 0 : wrap(font, q, w - 16, 2).size() * LH + 3);
	}

	private int rowH(net.minecraft.client.gui.Font font, int w, String answer) {
		return 17 + wrap(font, answer, w - 4 - 26, 2).size() * LH + 3;
	}

	/** 답변 고르기 상자. items = {키워드, 답변, "c"면 명령어 답변}. record면 클릭 자리를 남긴다. */
	private void drawBox(GuiGraphicsExtractor g, int x, int y, int w, int h, String q, List<String[]> items, int first, int hit, int total,
			int mx, int my, boolean record) {
		var font = client.font;
		LunaDraw.shadow(g, x, y, w, h, 6);
		LunaDraw.roundRectBordered(g, x, y, w, h, 6, 0xF0141A22, 0x40FFFFFF);
		LunaDraw.roundRect(g, x + 7, y + 6, 3, 9, 1, 0xFFFFD36A);
		LunaDraw.text(g, font, "답변 고르기", x + 14, y + 7, 0xFFFFD36A);
		if (total > items.size()) {
			String pg = (first + 1) + "-" + (first + items.size()) + " / " + total;
			LunaDraw.text(g, font, pg, x + w - 8 - LunaDraw.width(font, pg), y + 7, 0xFF7D8794);
		}
		int cy = y + 19;
		if (!q.isEmpty()) {
			List<String> ql = wrap(font, q, w - 16, 2);
			LunaDraw.roundRect(g, x + 6, cy - 1, w - 12, ql.size() * LH + 3, 3, 0x1AFFD36A);
			for (int i = 0; i < ql.size(); i++) {
				LunaDraw.text(g, font, ql.get(i), x + 9, cy + 1 + i * LH, 0xFFE6D3A3);
			}
			cy += ql.size() * LH + 3;
		}
		cy += 3;
		for (int i = 0; i < items.size(); i++) {
			String[] a = items.get(i);
			int si = first + i;
			int rh = rowH(font, w, a[1]);
			int rx = x + 4, rw = w - 8;
			boolean rec = si < hit;
			int accent = "c".equals(a[2]) ? 0xFF7EC8F0 : 0xFF7EE08A;
			boolean hov = LunaDraw.in(mx, my, rx, cy, rw, rh);
			LunaDraw.roundRect(g, rx, cy, rw, rh, 4, hov ? 0x38FFFFFF : rec ? 0x1CFFFFFF : 0x10FFFFFF);
			if (rec) {
				LunaDraw.roundRect(g, rx, cy + 3, 2, rh - 6, 1, accent);
			}
			String num = Integer.toString(i + 1);
			LunaDraw.roundRect(g, rx + 6, cy + 4, 12, 11, 3, rec ? (accent & 0x00FFFFFF) | 0x40000000 : 0x26FFFFFF);
			LunaDraw.text(g, font, num, rx + 12 - LunaDraw.width(font, num) / 2, cy + 6, rec ? accent : 0xFFB8C2CF);
			LunaDraw.text(g, font, LunaDraw.ellipsize(font, a[0].replace("/", ", "), rw - 30), rx + 23, cy + 6, 0xFF7D8794);
			List<String> al = wrap(font, noLink(a[1]), rw - 26, 2);   // 49-632차: 링크 대신 /위키
			for (int k = 0; k < al.size(); k++) {
				LunaDraw.text(g, font, al.get(k), rx + 23, cy + 17 + k * LH, 0xFFF2F4F7);
			}
			if (record) {
				rows.add(new int[]{rx, cy, rw, rh});
				rowAnswers.add(a[1]);
			}
			cy += rh + 3;
		}
		LunaDraw.text(g, font, "누르거나 Ctrl+번호로 붙이기, 휠로 넘기기", x + 8, y + h - 12, 0xFF5F6874);
	}

	/** 목록 위에서 휠이면 목록을 넘기고 true. */
	private boolean scrollPicker(double mx, double my, double va) {
		int[] b = pickerBox;
		if (b == null || rows.isEmpty() || !LunaDraw.in(mx, my, b[0], b[1], b[2], b[3])) {
			return false;
		}
		pickerScroll = Math.max(0, Math.min(b[4] - b[5], pickerScroll + (va > 0 ? -1 : 1)));
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

	/**
	 * 49-632차(사용자: "링크는 주지 마 /위키 라고 해야지"): 답변에 주소(http…)가 있으면 빼고, 대신 "/위키"를 안내한다
	 * (직접 넣은 답변 목록에 링크가 있어도).
	 */
	static String noLink(String ans) {
		if (ans == null || !ans.contains("http")) {
			return ans;
		}
		String a = ans.replaceAll("\\s*(?:https?://|www\\.)\\S+", "").trim();
		if (!a.contains("/위키")) {
			a = a + " 자세한 건 /위키 에서 볼 수 있어요!";
		}
		return a;
	}

	/** i번째 답변을 입력칸에 붙인다("/답변…"이면 뒤에, 아니면 커서 자리에). */
	private void pick(Object screen, int i) {
		EditBox box = input(screen);
		if (box != null) {
			String v = box.getValue();
			String ans = noLink(rowAnswers.get(i));
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
		drawAlert(ctx, x + w / 2, y + 4, "cowin0301", "룰렛 쿠폰은 어디서 쓰나요?", 1f);
		var font = client.font;
		int bw = Math.min(260, w - 16), bx = x + (w - bw) / 2;
		List<String[]> items = List.of(new String[]{"룰렛/룰렛쿠폰", "스폰 우측에서 사용가능합니다", ""},
				new String[]{"/룰렛", "/룰렛 을 입력하시면 됩니다!", "c"});
		String q = "룰렛 쿠폰은 어디서 쓰나요?";
		int bh = headH(font, bw, q) + 14;
		for (String[] a : items) {
			bh += rowH(font, bw, a[1]) + 3;
		}
		drawBox(ctx, bx, Math.max(y + 34, y + h - bh - 4), bw, bh, q, items, 0, 2, 2, -1, -1, false);
	}
}

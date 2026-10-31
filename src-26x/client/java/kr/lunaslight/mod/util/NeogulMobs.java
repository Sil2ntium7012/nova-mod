package kr.lunaslight.mod.util;

import java.util.List;

/**
 * 49-443차(사용자: "던전 몬스터들 정보도 보는 란도 하나 만들어줘"): 너굴마을 던전 몬스터 자료(mcng.kr/wiki 메인컨텐츠 > 던전 > 몬스터, 2026-10-31).
 * 던전 8곳 x 몬스터 31마리. 레벨마다 체력, 공격력("-" = 공격 안 함), 경험치. 전리품은 이름, 확률, 아이콘(서버 팩 nexo 모델 id). 순수 자료.
 */
public final class NeogulMobs {
	private NeogulMobs() {
	}

	public record Drop(String name, String chance, String icon) {
		public String model() {
			return "nexo:" + icon;
		}
	}

	public record Mob(String name, int lvMin, int lvMax, int[] hp, String[] atk, String[] exp, List<Drop> drops) {
		/** 첫 전리품(100%) = 이 몬스터의 대표 아이콘. */
		public Drop signature() {
			return drops.isEmpty() ? null : drops.get(0);
		}

		public boolean passive() {
			for (String a : atk) {
				if (!"-".equals(a)) {
					return false;
				}
			}
			return true;
		}
	}

	public record Dungeon(String id, String name, int lvMin, int lvMax, String reward, List<Mob> mobs) {
	}

	private static Drop d(String n, String c, String i) {
		return new Drop(n, c, i);
	}

	public static final List<Dungeon> ALL = List.of(
		new Dungeon("dg1", "외딴 농장", 1, 10, "1,000", List.of(
			new Mob("닭", 1, 3, new int[]{4360, 4421, 4481}, new String[]{"-", "-", "-"}, new String[]{"12", "12.5", "13"}, List.of(d("깃털", "100%", "dungeon_item_1"), d("청동", "3%", "bronze_ingot"), d("하급 가죽", "2.75%", "leather_1"), d("주문의 흔적", "1.925~1.9372%", "spell_trace"), d("하급 보물상자", "0.275~0.2872%", "treasurebox_iron"))),
			new Mob("돼지", 3, 5, new int[]{4522, 4604, 4685}, new String[]{"-", "-", "-"}, new String[]{"13", "13.5", "14"}, List.of(d("돼지 가죽", "100%", "dungeon_item_5"), d("청동", "3%", "bronze_ingot"), d("하급 가죽", "2.75%", "leather_1"), d("주문의 흔적", "1.9372~1.9494%", "spell_trace"), d("하급 보물상자", "0.2872~0.2994%", "treasurebox_iron"))),
			new Mob("소", 5, 7, new int[]{4745, 4827, 4907}, new String[]{"-", "-", "-"}, new String[]{"14", "14.5", "15"}, List.of(d("소 가죽", "100%", "dungeon_item_14"), d("청동", "3%", "bronze_ingot"), d("하급 가죽", "2.75%", "leather_1"), d("주문의 흔적", "1.9494~1.9617%", "spell_trace"), d("하급 보물상자", "0.2994~0.3117%", "treasurebox_iron"))),
			new Mob("말", 7, 9, new int[]{4988, 5111, 5232}, new String[]{"-", "-", "-"}, new String[]{"15", "15.5", "16"}, List.of(d("갈기", "100%", "dungeon_item_7"), d("청동", "3%", "bronze_ingot"), d("하급 가죽", "2.75%", "leather_1"), d("주문의 흔적", "1.9617~1.9739%", "spell_trace"), d("하급 보물상자", "0.3117~0.3239%", "treasurebox_iron"))))),
		new Dungeon("dg2", "메마른 대지", 11, 20, "1,600", List.of(
			new Mob("너구리", 11, 13, new int[]{9841, 10000, 10161}, new String[]{"5.9", "6.1", "6.1"}, new String[]{"26", "27", "27.5"}, List.of(d("너구리 꼬리", "100%", "dungeon_item_3"), d("청동", "3%", "bronze_ingot"), d("하급 가죽", "2.75%", "leather_1"), d("체력 회복포션 (10)", "2.75%", "potion_10"), d("주문의 흔적", "1.98~1.9922%", "spell_trace"), d("하급 보물상자", "0.33~0.3422%", "treasurebox_iron"))),
			new Mob("사막여우", 13, 15, new int[]{10275, 10481, 10686}, new String[]{"6.1", "6.4", "6.4"}, new String[]{"27.5", "28", "28.5"}, List.of(d("사막여우 털뭉치", "100%", "dungeon_item_17"), d("청동", "3%", "bronze_ingot"), d("하급 가죽", "2.75%", "leather_1"), d("체력 회복포션 (10)", "2.75%", "potion_10"), d("주문의 흔적", "1.9922~2.0044%", "spell_trace"), d("하급 보물상자", "0.3422~0.3544%", "treasurebox_iron"))),
			new Mob("낙타", 15, 17, new int[]{10710, 10938, 11167}, new String[]{"6.4", "6.4", "6.6"}, new String[]{"28.5", "29", "30"}, List.of(d("낙타 발굽", "100%", "dungeon_item_2"), d("청동", "3%", "bronze_ingot"), d("하급 가죽", "2.75%", "leather_1"), d("체력 회복포션 (10)", "2.75%", "potion_10"), d("주문의 흔적", "2.0044~2.0167%", "spell_trace"), d("하급 보물상자", "0.3544~0.3667%", "treasurebox_iron"))),
			new Mob("하이애나", 17, 19, new int[]{11258, 11534, 11807}, new String[]{"6.6", "6.9", "7.1"}, new String[]{"30", "31", "31.5"}, List.of(d("하이애나 송곳니", "100%", "dungeon_item_19"), d("청동", "3%", "bronze_ingot"), d("하급 가죽", "2.75%", "leather_1"), d("체력 회복포션 (10)", "2.75%", "potion_10"), d("주문의 흔적", "2.0167~2.0289%", "spell_trace"), d("하급 보물상자", "0.3667~0.3789%", "treasurebox_iron"))))),
		new Dungeon("dg3", "얼어붙은 설원", 21, 30, "2,250", List.of(
			new Mob("펭귄", 21, 23, new int[]{20600, 20942, 21283}, new String[]{"17.3", "17.6", "17.8"}, new String[]{"47", "48", "49"}, List.of(d("펭귄 부리", "100%", "dungeon_item_18"), d("은 주괴", "3%", "silver_ingot"), d("체력 회복포션 (30)", "2.75%", "potion_30"), d("주문의 흔적", "2.035~2.0472%", "spell_trace"), d("중급 가죽", "1.925%", "leather_2"), d("하급 가죽", "0.825%", "leather_1"), d("하급 보물상자", "0.231%", "treasurebox_iron"), d("중급 보물상자", "0.132~0.1393%", "treasurebox_gold"))),
			new Mob("하프물범", 23, 25, new int[]{21579, 21989, 22398}, new String[]{"17.6", "18.1", "18.4"}, new String[]{"49", "50.5", "52"}, List.of(d("하프물범 수염", "100%", "dungeon_item_9"), d("은 주괴", "3%", "silver_ingot"), d("체력 회복포션 (30)", "2.75%", "potion_30"), d("주문의 흔적", "2.0472~2.0594%", "spell_trace"), d("중급 가죽", "1.925%", "leather_2"), d("하급 가죽", "0.825%", "leather_1"), d("하급 보물상자", "0.231%", "treasurebox_iron"), d("중급 보물상자", "0.1393~0.1467%", "treasurebox_gold"))),
			new Mob("북극곰", 25, 27, new int[]{22535, 23035, 23537}, new String[]{"18.4", "18.7", "19.3"}, new String[]{"52", "53", "54"}, List.of(d("반달곰 발바닥", "100%", "dungeon_item_12"), d("은 주괴", "3%", "silver_ingot"), d("체력 회복포션 (30)", "2.75%", "potion_30"), d("주문의 흔적", "2.0594~2.0717%", "spell_trace"), d("중급 가죽", "1.925%", "leather_2"), d("하급 가죽", "0.825%", "leather_1"), d("하급 보물상자", "0.231%", "treasurebox_iron"), d("중급 보물상자", "0.1467~0.154%", "treasurebox_gold"))),
			new Mob("순록", 27, 29, new int[]{23537, 24129, 24720}, new String[]{"19.6", "19.9", "20.7"}, new String[]{"54", "55.5", "57"}, List.of(d("순록 뿔", "100%", "dungeon_item_15"), d("은 주괴", "3%", "silver_ingot"), d("체력 회복포션 (30)", "2.75%", "potion_30"), d("주문의 흔적", "2.0717~2.0839%", "spell_trace"), d("중급 가죽", "1.925%", "leather_2"), d("하급 가죽", "0.825%", "leather_1"), d("하급 보물상자", "0.231%", "treasurebox_iron"), d("중급 보물상자", "0.154~0.1613%", "treasurebox_gold"))))),
		new Dungeon("dg4", "울창한 숲", 31, 40, "3,000", List.of(
			new Mob("사슴", 31, 33, new int[]{39480, 40125, 40769}, new String[]{"20", "20.3", "20.9"}, new String[]{"76", "77.5", "79.5"}, List.of(d("사슴 뿔", "100%", "dungeon_item_13"), d("은 주괴", "3%", "silver_ingot"), d("체력 회복포션 (50)", "2.75%", "potion_50"), d("주문의 흔적", "2.09~2.1022%", "spell_trace"), d("중급 가죽", "1.925%", "leather_2"), d("하급 가죽", "0.825%", "leather_1"), d("하급 보물상자", "0.231~0.2383%", "treasurebox_iron"), d("중급 보물상자", "0.165~0.1723%", "treasurebox_gold"))),
			new Mob("멧돼지", 33, 35, new int[]{41345, 42150, 42956}, new String[]{"20.9", "21.1", "21.4"}, new String[]{"79.5", "81", "83.5"}, List.of(d("멧돼지 송곳니", "100%", "dungeon_item_8"), d("은 주괴", "3%", "silver_ingot"), d("체력 회복포션 (50)", "2.75%", "potion_50"), d("주문의 흔적", "2.1022~2.1144%", "spell_trace"), d("중급 가죽", "1.925%", "leather_2"), d("하급 가죽", "0.825%", "leather_1"), d("하급 보물상자", "0.2383~0.2457%", "treasurebox_iron"), d("중급 보물상자", "0.1723~0.1797%", "treasurebox_gold"))),
			new Mob("들개", 35, 37, new int[]{43209, 44176, 45143}, new String[]{"21.4", "22", "22.3"}, new String[]{"83.5", "85.5", "88"}, List.of(d("들개 털뭉치", "100%", "dungeon_item_6"), d("은 주괴", "3%", "silver_ingot"), d("체력 회복포션 (50)", "2.75%", "potion_50"), d("주문의 흔적", "2.1144~2.1267%", "spell_trace"), d("중급 가죽", "1.925%", "leather_2"), d("하급 가죽", "0.825%", "leather_1"), d("하급 보물상자", "0.2457~0.253%", "treasurebox_iron"), d("중급 보물상자", "0.1797~0.187%", "treasurebox_gold"))),
			new Mob("반달곰", 37, 39, new int[]{45120, 46248, 47376}, new String[]{"22.9", "23.1", "24"}, new String[]{"88", "90", "91.5"}, List.of(d("반달 곰 발바닥", "100%", "dungeon_item_10"), d("은 주괴", "3%", "silver_ingot"), d("체력 회복포션 (50)", "2.75%", "potion_50"), d("주문의 흔적", "2.1267~2.1389%", "spell_trace"), d("중급 가죽", "1.925%", "leather_2"), d("하급 가죽", "0.825%", "leather_1"), d("하급 보물상자", "0.253~0.2603%", "treasurebox_iron"), d("중급 보물상자", "0.187~0.1943%", "treasurebox_gold"))))),
		new Dungeon("dg5", "안개의 습지", 41, 50, "3,900", List.of(
			new Mob("독개구리", 41, 43, new int[]{66601, 67716, 68831}, new String[]{"44.5", "45.7", "46.6"}, new String[]{"114", "116", "118.5"}, List.of(d("독 주머니", "100%", "dungeon_item_4"), d("은 주괴", "3%", "silver_ingot"), d("체력 회복포션 (50)", "2.75%", "potion_50"), d("주문의 흔적", "2.145~2.1572%", "spell_trace"), d("고급 가죽", "1.925%", "leather_3"), d("중급 가죽", "0.825%", "leather_2"), d("중급 보물상자", "0.33~0.3422%", "treasurebox_gold"))),
			new Mob("거북이", 43, 45, new int[]{69552, 70924, 72295}, new String[]{"45.7", "46.9", "47.8"}, new String[]{"118.5", "121", "124.5"}, List.of(d("거북이 등껍질", "100%", "dungeon_item_20"), d("은 주괴", "3%", "silver_ingot"), d("체력 회복포션 (50)", "2.75%", "potion_50"), d("주문의 흔적", "2.1572~2.1694%", "spell_trace"), d("고급 가죽", "1.925%", "leather_3"), d("중급 가죽", "0.825%", "leather_2"), d("중급 보물상자", "0.3422~0.3544%", "treasurebox_gold"))),
			new Mob("뱀", 45, 47, new int[]{72504, 74131, 75759}, new String[]{"47.5", "48.7", "49.9"}, new String[]{"124.5", "127.5", "131"}, List.of(d("뱀 비늘", "100%", "dungeon_item_11"), d("은 주괴", "3%", "silver_ingot"), d("체력 회복포션 (50)", "2.75%", "potion_50"), d("주문의 흔적", "2.1694~2.1817%", "spell_trace"), d("고급 가죽", "1.925%", "leather_3"), d("중급 가죽", "0.825%", "leather_2"), d("중급 보물상자", "0.3544~0.3667%", "treasurebox_gold"))),
			new Mob("악어", 47, 49, new int[]{76061, 77990, 79919}, new String[]{"50.5", "52", "53.5"}, new String[]{"131", "134", "137"}, List.of(d("악어 가죽", "100%", "dungeon_item_16"), d("은 주괴", "3%", "silver_ingot"), d("체력 회복포션 (50)", "2.75%", "potion_50"), d("주문의 흔적", "2.1817~2.1939%", "spell_trace"), d("고급 가죽", "1.925%", "leather_3"), d("중급 가죽", "0.825%", "leather_2"), d("중급 보물상자", "0.3667~0.3789%", "treasurebox_gold"))))),
		new Dungeon("dg6", "무릉도원", 51, 60, "4,800", List.of(
			new Mob("공작새", 51, 53, new int[]{107000, 108763, 110524}, new String[]{"49.1", "50.3", "51.5"}, new String[]{"163.5", "166", "170"}, List.of(d("공작새 깃털", "100%", "dungeon_item_21"), d("금 주괴", "3%", "gold_ingot"), d("체력 회복포션 (80)", "2.75%", "potion_80"), d("주문의 흔적", "2.2~2.2122%", "spell_trace"), d("고급 가죽", "1.925%", "leather_3"), d("중급 가죽", "0.825%", "leather_2"), d("중급 보물상자", "0.385~0.3972%", "treasurebox_gold"))),
			new Mob("베이비 몽키", 54, 56, new int[]{113562, 115903, 118245}, new String[]{"52.5", "53.7", "55.2"}, new String[]{"174", "179", "183"}, List.of(d("원숭이 꼬리", "100%", "dungeon_item_22"), d("금 주괴", "3%", "gold_ingot"), d("체력 회복포션 (80)", "2.75%", "potion_80"), d("주문의 흔적", "2.2183~2.2306%", "spell_trace"), d("고급 가죽", "1.925%", "leather_3"), d("중급 가죽", "0.825%", "leather_2"), d("중급 보물상자", "0.4033~0.4156%", "treasurebox_gold"))),
			new Mob("판다곰", 57, 59, new int[]{122233, 125316, 128400}, new String[]{"55.8", "57.4", "58.9"}, new String[]{"188", "193", "196"}, List.of(d("판다곰 발바닥", "100%", "dungeon_item_23"), d("금 주괴", "3%", "gold_ingot"), d("체력 회복포션 (80)", "2.75%", "potion_80"), d("주문의 흔적", "2.2367~2.2489%", "spell_trace"), d("고급 가죽", "1.925%", "leather_3"), d("중급 가죽", "0.825%", "leather_2"), d("중급 보물상자", "0.4217~0.4339%", "treasurebox_gold"))))),
		new Dungeon("dg7", "균사지대", 61, 70, "5,800", List.of(
			new Mob("전사 버섯", 61, 63, new int[]{172080, 174906, 177731}, new String[]{"76.4", "78", "79.5"}, new String[]{"227.5", "231", "236.5"}, List.of(d("주황 버섯", "100%", "dungeon_item_24"), d("금 주괴", "3%", "gold_ingot"), d("체력 회복포션 (80)", "2.75%", "potion_80"), d("주문의 흔적", "2.255~2.2672%", "spell_trace"), d("황금 가죽", "1.925%", "leather_4"), d("고급 가죽", "0.825%", "leather_3"), d("중급 보물상자", "0.264%", "treasurebox_gold"), d("고급 보물상자", "0.132~0.1393%", "treasurebox_emerald"))),
			new Mob("고목 버섯", 63, 65, new int[]{178681, 182085, 185490}, new String[]{"79.2", "81.1", "83"}, new String[]{"236.5", "242", "249"}, List.of(d("보라 버섯", "100%", "dungeon_item_25"), d("금 주괴", "3%", "gold_ingot"), d("체력 회복포션 (80)", "2.75%", "potion_80"), d("주문의 흔적", "2.2672~2.2794%", "spell_trace"), d("황금 가죽", "1.925%", "leather_4"), d("고급 가죽", "0.825%", "leather_3"), d("중급 보물상자", "0.264%", "treasurebox_gold"), d("고급 보물상자", "0.1393~0.1467%", "treasurebox_emerald"))),
			new Mob("독버섯", 65, 67, new int[]{186764, 190886, 195008}, new String[]{"82.7", "85", "87.2"}, new String[]{"249", "255", "262"}, List.of(d("독 버섯", "100%", "dungeon_item_26"), d("금 주괴", "3%", "gold_ingot"), d("체력 회복포션 (80)", "2.75%", "potion_80"), d("주문의 흔적", "2.2794~2.2917%", "spell_trace"), d("황금 가죽", "1.925%", "leather_4"), d("고급 가죽", "0.825%", "leather_3"), d("중급 보물상자", "0.264%", "treasurebox_gold"), d("고급 보물상자", "0.1467~0.154%", "treasurebox_emerald"))),
			new Mob("헌터 버섯", 67, 69, new int[]{196583, 201540, 206496}, new String[]{"86.5", "89.1", "91.6"}, new String[]{"262", "268.5", "273"}, List.of(d("버섯 포자", "100%", "dungeon_item_27"), d("금 주괴", "3%", "gold_ingot"), d("체력 회복포션 (80)", "2.75%", "potion_80"), d("주문의 흔적", "2.2917~2.3039%", "spell_trace"), d("황금 가죽", "1.925%", "leather_4"), d("고급 가죽", "0.825%", "leather_3"), d("중급 보물상자", "0.264%", "treasurebox_gold"), d("고급 보물상자", "0.154~0.1613%", "treasurebox_emerald"))))),
		new Dungeon("dg8", "끝없는 분화구", 71, 80, "6,900", List.of(
			new Mob("전사 들쥐", 71, 73, new int[]{367200, 373243, 379286}, new String[]{"219.5", "224.3", "229"}, new String[]{"309.5", "314", "322"}, List.of(d("부러진 검 조각", "100%", "dungeon_item_28"), d("금 주괴", "3%", "gold_ingot"), d("체력 회복포션 (80)", "2.75%", "potion_80"), d("주문의 흔적", "2.31~2.3222%", "spell_trace"), d("황금 가죽", "1.925%", "leather_4"), d("고급 가죽", "0.825%", "leather_3"), d("중급 보물상자", "0.264~0.2713%", "treasurebox_gold"), d("고급 보물상자", "0.165~0.1723%", "treasurebox_emerald"))),
			new Mob("사수 들쥐", 73, 75, new int[]{381512, 388798, 396085}, new String[]{"227.8", "233.4", "238.8"}, new String[]{"322", "330", "339"}, List.of(d("부러진 활 조각", "100%", "dungeon_item_29"), d("금 주괴", "3%", "gold_ingot"), d("체력 회복포션 (80)", "2.75%", "potion_80"), d("주문의 흔적", "2.3222~2.3344%", "spell_trace"), d("황금 가죽", "1.925%", "leather_4"), d("고급 가죽", "0.825%", "leather_3"), d("중급 보물상자", "0.2713~0.2787%", "treasurebox_gold"), d("고급 보물상자", "0.1723~0.1797%", "treasurebox_emerald"))),
			new Mob("마법사 들쥐", 75, 77, new int[]{398686, 407476, 416266}, new String[]{"237.9", "244.2", "250.2"}, new String[]{"339", "347.5", "356"}, List.of(d("마법사 들쥐 후드", "100%", "dungeon_item_30"), d("금 주괴", "3%", "gold_ingot"), d("체력 회복포션 (80)", "2.75%", "potion_80"), d("주문의 흔적", "2.3344~2.3467%", "spell_trace"), d("황금 가죽", "1.925%", "leather_4"), d("고급 가죽", "0.825%", "leather_3"), d("중급 보물상자", "0.2787~0.286%", "treasurebox_gold"), d("고급 보물상자", "0.1797~0.187%", "treasurebox_emerald"))),
			new Mob("대장 들쥐", 77, 79, new int[]{419475, 430058, 440640}, new String[]{"249.2", "256.2", "263.5"}, new String[]{"356", "365", "371"}, List.of(d("대장 들쥐 앞니", "100%", "dungeon_item_31"), d("금 주괴", "3%", "gold_ingot"), d("체력 회복포션 (80)", "2.75%", "potion_80"), d("주문의 흔적", "2.3467~2.3589%", "spell_trace"), d("황금 가죽", "1.925%", "leather_4"), d("고급 가죽", "0.825%", "leather_3"), d("중급 보물상자", "0.286~0.2933%", "treasurebox_gold"), d("고급 보물상자", "0.187~0.1943%", "treasurebox_emerald")))))
	);
}

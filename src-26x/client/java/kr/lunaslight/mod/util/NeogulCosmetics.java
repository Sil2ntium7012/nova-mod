package kr.lunaslight.mod.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 49-431차(사용자: "치장 창도 따로 있으면 좋을 듯, 미리 입어보기랑 위키에 다 있고"): 너굴마을 치장 목록(mcng.kr/wiki 서브컨텐츠 > 치장, 2026-10-30).
 * obtainable = 위키가 "획득 가능"으로 보여 주는 것(위키 기본 화면 43종). 모양은 서버 리소스팩의 Nexo 아이템 모델 nexo:&lt;id&gt;
 * (팩이 켜져 있어야 보임). 순수 자료.
 */
public final class NeogulCosmetics {
	private NeogulCosmetics() {
	}

	public static final String[] GRADES = {"레어", "에픽", "유니크", "전설"};
	public static final int[] GRADE_COLORS = {0xFF5AB4FF, 0xFFB98CFF, 0xFFFFC857, 0xFFFF6B6B};
	/** 부위 코드 → 한국어(위키 필터 이름). */
	public static final String[][] SLOTS = {{"HAT", "모자"}, {"BACK", "등"}, {"OFFHAND", "왼손"}, {"CHEST", "상의"}, {"LEGS", "하의"}, {"BOOTS", "신발"}, {"PARTICLE", "효과"}};

	public record Cosmetic(String id, String name, String grade, String slot, boolean obtainable) {
		public String model() {
			return "nexo:" + id;
		}

		public int gradeColor() {
			for (int i = 0; i < GRADES.length; i++) {
				if (GRADES[i].equals(grade)) {
					return GRADE_COLORS[i];
				}
			}
			return 0xFFB8BEC8;
		}

		public String slotName() {
			for (String[] s : SLOTS) {
				if (s[0].equals(slot)) {
					return s[1];
				}
			}
			return slot;
		}
	}

	public static final List<Cosmetic> ALL;

	static {
		List<Cosmetic> a = new ArrayList<>();
		a.add(new Cosmetic("1_wing_blue", "블루냥이 윙", "레어", "BACK", false));
		a.add(new Cosmetic("1_wing_pink", "핑크냥이 윙", "레어", "BACK", false));
		a.add(new Cosmetic("balloon_cloud", "내 이름 흐림", "레어", "HAT", true));
		a.add(new Cosmetic("balloon_cloud_sun", "내 이름 맑음", "레어", "HAT", true));
		a.add(new Cosmetic("balloon_rain", "내 이름 소나기", "레어", "HAT", true));
		a.add(new Cosmetic("balloon_rainbow", "내 이름 무지개", "레어", "HAT", true));
		a.add(new Cosmetic("balloon_snow", "내 이름 첫눈", "레어", "HAT", true));
		a.add(new Cosmetic("cap_blue", "블루냥이 캡", "레어", "HAT", false));
		a.add(new Cosmetic("cap_pink", "핑크냥이 캡", "레어", "HAT", false));
		a.add(new Cosmetic("chestnut_hat", "군밤 모자(베이지)", "에픽", "HAT", false));
		a.add(new Cosmetic("chestnut_hat_color_1", "군밤 모자(브라운)", "에픽", "HAT", false));
		a.add(new Cosmetic("cow_hat", "젖소 모자", "레어", "HAT", true));
		a.add(new Cosmetic("deer_hat", "사슴 모자", "레어", "HAT", false));
		a.add(new Cosmetic("dolphin_hat", "돌고래 모자", "레어", "HAT", true));
		a.add(new Cosmetic("empty_nipple", "쪽쪽이(블루)", "레어", "HAT", true));
		a.add(new Cosmetic("empty_nipple_red", "쪽쪽이(레드)", "레어", "HAT", true));
		a.add(new Cosmetic("gombangdae", "곰방대", "레어", "HAT", false));
		a.add(new Cosmetic("leaf_baby", "나는 새싹", "레어", "HAT", true));
		a.add(new Cosmetic("liar_1", "코주부 안경", "레어", "HAT", false));
		a.add(new Cosmetic("liar_2", "바보 포스트잇", "레어", "HAT", false));
		a.add(new Cosmetic("monkey_hat", "원숭이 모자", "레어", "HAT", false));
		a.add(new Cosmetic("rabbit_hat", "토끼 모자", "레어", "HAT", true));
		a.add(new Cosmetic("s_hat", "상어 모자", "레어", "HAT", false));
		a.add(new Cosmetic("sheep_hat", "양 모자", "레어", "HAT", true));
		a.add(new Cosmetic("turtle_hat", "거북이 모자", "레어", "HAT", true));
		a.add(new Cosmetic("fireman", "소방관 헬멧", "레어", "HAT", true));
		a.add(new Cosmetic("miner_helmet", "광부 헬멧", "레어", "HAT", true));
		a.add(new Cosmetic("mortarboard", "학사모", "레어", "HAT", true));
		a.add(new Cosmetic("safety_helmet", "안전모", "레어", "HAT", true));
		a.add(new Cosmetic("silk_hat", "실크햇", "레어", "HAT", false));
		a.add(new Cosmetic("snorkel", "스노클", "레어", "HAT", true));
		a.add(new Cosmetic("straw_hat", "밀짚모자", "레어", "HAT", true));
		a.add(new Cosmetic("toque", "요리사 모자", "레어", "HAT", true));
		a.add(new Cosmetic("fish_backpack", "강태공 가방", "에픽", "BACK", true));
		a.add(new Cosmetic("fishing_hat", "강태공 모자", "에픽", "HAT", true));
		a.add(new Cosmetic("move_heart_hat", "빙글빙글 하트(레드)", "에픽", "HAT", false));
		a.add(new Cosmetic("move_heart_hat_b", "빙글빙글 하트(블루)", "에픽", "HAT", false));
		a.add(new Cosmetic("move_heart_hat_p", "빙글빙글 하트(핑크)", "에픽", "HAT", false));
		a.add(new Cosmetic("move_heart_hat_y", "빙글빙글 하트(옐로우)", "에픽", "HAT", false));
		a.add(new Cosmetic("sashimi", "사시미", "에픽", "BACK", true));
		a.add(new Cosmetic("sushi", "모둠 초밥", "에픽", "HAT", true));
		a.add(new Cosmetic("sweetpotatohat", "군고구마 모자", "에픽", "HAT", false));
		a.add(new Cosmetic("tteok_hat", "찹쌀떡 모자", "에픽", "HAT", false));
		a.add(new Cosmetic("enfj", "ENFJ", "에픽", "HAT", false));
		a.add(new Cosmetic("enfp", "ENFP", "에픽", "HAT", false));
		a.add(new Cosmetic("entj", "ENTJ", "에픽", "HAT", false));
		a.add(new Cosmetic("entp", "ENTP", "에픽", "HAT", false));
		a.add(new Cosmetic("esfj", "ESFJ", "에픽", "HAT", false));
		a.add(new Cosmetic("esfp", "ESFP", "에픽", "HAT", false));
		a.add(new Cosmetic("estj", "ESTJ", "에픽", "HAT", false));
		a.add(new Cosmetic("estp", "ESTP", "에픽", "HAT", false));
		a.add(new Cosmetic("infj", "INFJ", "에픽", "HAT", false));
		a.add(new Cosmetic("infp", "INFP", "에픽", "HAT", false));
		a.add(new Cosmetic("intj", "INTJ", "에픽", "HAT", false));
		a.add(new Cosmetic("intp", "INTP", "에픽", "HAT", false));
		a.add(new Cosmetic("isfj", "ISFJ", "에픽", "HAT", false));
		a.add(new Cosmetic("isfp", "ISFP", "에픽", "HAT", false));
		a.add(new Cosmetic("istj", "ISTJ", "에픽", "HAT", false));
		a.add(new Cosmetic("istp", "ISTP", "에픽", "HAT", false));
		a.add(new Cosmetic("m2_mask_rabbit", "토끼 탈", "에픽", "HAT", false));
		a.add(new Cosmetic("m2_mask_penguin", "펭귄 탈", "에픽", "HAT", false));
		a.add(new Cosmetic("m2_mask_bear", "곰돌이 탈", "에픽", "HAT", false));
		a.add(new Cosmetic("m2_mask_cat", "고양이 탈", "에픽", "HAT", false));
		a.add(new Cosmetic("m2_mask_frog", "개구리 탈", "에픽", "HAT", false));
		a.add(new Cosmetic("m2_mask_dog", "강아지 탈", "에픽", "HAT", false));
		a.add(new Cosmetic("m2_patch_rabbit", "토끼 안대", "에픽", "HAT", false));
		a.add(new Cosmetic("m2_patch_cat", "고양이 안대", "에픽", "HAT", false));
		a.add(new Cosmetic("m2_patch_frog", "개구리 안대", "에픽", "HAT", false));
		a.add(new Cosmetic("m2_pin_rabbit", "토끼 머리핀", "에픽", "HAT", false));
		a.add(new Cosmetic("m2_pin_penguin", "펭귄 머리핀", "에픽", "HAT", false));
		a.add(new Cosmetic("m2_pin_bear", "곰돌이 머리핀", "에픽", "HAT", false));
		a.add(new Cosmetic("m2_pin_cat", "고양이 머리핀", "에픽", "HAT", false));
		a.add(new Cosmetic("m2_pin_frog", "개구리 머리핀", "에픽", "HAT", false));
		a.add(new Cosmetic("m2_pin_puppy", "강아지 머리핀", "에픽", "HAT", false));
		a.add(new Cosmetic("m2_alien_glasses", "외계인 안경", "에픽", "HAT", false));
		a.add(new Cosmetic("m2_decora_cat_hat", "데코라 고양이 모자", "에픽", "HAT", false));
		a.add(new Cosmetic("m2_milk_hat", "딸기우유 모자", "에픽", "HAT", false));
		a.add(new Cosmetic("m2_baby_pacifier", "아기토끼 쪽쪽이", "에픽", "HAT", false));
		a.add(new Cosmetic("m2_baby_bib", "아기토끼 턱받이", "에픽", "BACK", false));
		a.add(new Cosmetic("m2_bag_rabbit", "토끼 가방", "에픽", "OFFHAND", false));
		a.add(new Cosmetic("m2_bag_penguin", "펭귄 가방", "에픽", "OFFHAND", false));
		a.add(new Cosmetic("m2_bag_bear", "곰돌이 가방", "에픽", "OFFHAND", false));
		a.add(new Cosmetic("m2_bag_cat", "고양이 가방", "에픽", "OFFHAND", false));
		a.add(new Cosmetic("m2_bag_frog", "개구리 가방", "에픽", "OFFHAND", false));
		a.add(new Cosmetic("m2_bag_puppy", "강아지 가방", "에픽", "OFFHAND", false));
		a.add(new Cosmetic("m2_decora_cat_bag", "데코라 고양이 가방", "에픽", "BACK", false));
		a.add(new Cosmetic("m2_milk_bag", "딸기우유 가방", "에픽", "BACK", false));
		a.add(new Cosmetic("m2_pillow_rabbit", "토끼 베개", "에픽", "BACK", false));
		a.add(new Cosmetic("m2_pillow_cat", "고양이 베개", "에픽", "BACK", false));
		a.add(new Cosmetic("m2_pillow_frog", "개구리 베개", "에픽", "BACK", false));
		a.add(new Cosmetic("m2_marshmallow_stick", "마시멜로우 막대기", "에픽", "OFFHAND", false));
		a.add(new Cosmetic("bear_earmuffs", "곰돌이 귀마개", "유니크", "HAT", false));
		a.add(new Cosmetic("bear_gloves", "곰돌이 벙어리 장갑", "유니크", "OFFHAND", false));
		a.add(new Cosmetic("bee_bag_1", "꿀벌 가방", "유니크", "BACK", true));
		a.add(new Cosmetic("bee_hat", "꿀벌 모자", "유니크", "HAT", true));
		a.add(new Cosmetic("cat_bag_1", "동물유치원 고양이 가방", "유니크", "BACK", false));
		a.add(new Cosmetic("cat_hat_1", "동물유치원 고양이 모자", "유니크", "HAT", false));
		a.add(new Cosmetic("crown_1", "반짝 왕관", "유니크", "HAT", true));
		a.add(new Cosmetic("dog_bag_1", "동물유치원 강아지 가방", "유니크", "BACK", false));
		a.add(new Cosmetic("dog_hat_1", "동물유치원 강아지 모자", "유니크", "HAT", false));
		a.add(new Cosmetic("fish_hat", "붕어빵 모자", "유니크", "HAT", false));
		a.add(new Cosmetic("hanbok_hat_2", "한복 삿갓(레드)", "유니크", "HAT", false));
		a.add(new Cosmetic("hanbok_hat_3", "한복 삿갓(블루)", "유니크", "HAT", false));
		a.add(new Cosmetic("kid_bag_boy", "너굴 유치원 가방(남)", "유니크", "BACK", true));
		a.add(new Cosmetic("kid_bag_girl", "너굴 유치원 가방(여)", "유니크", "BACK", true));
		a.add(new Cosmetic("kid_hat_boy", "너굴 유치원 모자(남)", "유니크", "HAT", true));
		a.add(new Cosmetic("kid_hat_girl", "너굴 유치원 모자(여)", "유니크", "HAT", true));
		a.add(new Cosmetic("penguin_backpack", "펭구 가방", "유니크", "BACK", false));
		a.add(new Cosmetic("penguin_hat", "펭구 모자", "유니크", "HAT", false));
		a.add(new Cosmetic("polar_bear_backpack", "폴라베어 가방", "유니크", "BACK", false));
		a.add(new Cosmetic("polar_bear_hat", "폴라베어 모자", "유니크", "HAT", false));
		a.add(new Cosmetic("rabbit_bag_1", "동물유치원 토끼 가방", "유니크", "BACK", false));
		a.add(new Cosmetic("rabbit_ear", "토끼 귀", "유니크", "HAT", false));
		a.add(new Cosmetic("rabbit_hat_1", "동물유치원 토끼 모자", "유니크", "HAT", false));
		a.add(new Cosmetic("round_glasses", "동글이 안경", "유니크", "HAT", true));
		a.add(new Cosmetic("rudolf_backpack", "루돌프 가방", "에픽", "BACK", false));
		a.add(new Cosmetic("rudolf_hat", "루돌프 모자", "에픽", "HAT", false));
		a.add(new Cosmetic("squirrel_camera", "다람쥐 탐정 카메라", "유니크", "OFFHAND", false));
		a.add(new Cosmetic("squirrel_ear_hat", "동물유치원 다람쥐 귀", "유니크", "HAT", false));
		a.add(new Cosmetic("squirrel_hat", "다람쥐 탐정 모자", "유니크", "HAT", false));
		a.add(new Cosmetic("squirrel_tail", "동물유치원 다람쥐 꼬리", "유니크", "BACK", false));
		a.add(new Cosmetic("umbrella", "눈이 펑펑 우산", "유니크", "OFFHAND", false));
		a.add(new Cosmetic("b_satgat", "동백꽃잎 삿갓", "전설", "HAT", true));
		a.add(new Cosmetic("b_umbrella", "동백꽃잎 양산", "전설", "OFFHAND", true));
		a.add(new Cosmetic("devil_bag", "악마 가방", "전설", "BACK", true));
		a.add(new Cosmetic("devil_hat", "악마 뿔", "전설", "HAT", true));
		a.add(new Cosmetic("fairy_bag", "요정 가방", "전설", "BACK", true));
		a.add(new Cosmetic("fairy_hat", "요정 화관", "전설", "HAT", true));
		a.add(new Cosmetic("galaxy_wing", "심연의 항성 윙", "전설", "BACK", false));
		a.add(new Cosmetic("sunset_wing", "금의 환향", "전설", "BACK", true));
		a.add(new Cosmetic("transparent_hat", "투명 모자", "레어", "HAT", true));
		a.add(new Cosmetic("transparent_chest", "투명 상의", "레어", "CHEST", true));
		a.add(new Cosmetic("transparent_legs", "투명 하의", "레어", "LEGS", true));
		a.add(new Cosmetic("transparent_boots", "투명 신발", "레어", "BOOTS", true));
		a.add(new Cosmetic("youain_efx_foot", "너구리 발걸음", "레어", "PARTICLE", false));
		ALL = Collections.unmodifiableList(a);
	}
}

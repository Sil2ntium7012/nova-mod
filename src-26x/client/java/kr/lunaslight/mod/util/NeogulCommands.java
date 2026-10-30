package kr.lunaslight.mod.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 49-431차(사용자: "명령어 목록도 볼 수 있으면 좋을 거 같고 명령어 검색 기능이랑"): 너굴마을 유저 명령어(mcng.kr/wiki 첫걸음 명령어 +
 * 스킬, 스킬 키 세팅, 치장 문서에 나오는 것, 2026-10-30). 일부는 특정 서버(동네, 광산, 섬)에서만 된다. 화면과 무관한 순수 자료.
 */
public final class NeogulCommands {
	private NeogulCommands() {
	}

	/** usage = 위키에 적힌 모양 그대로(인자 포함), desc = 설명. */
	public record Command(String category, String usage, String desc) {
		/** 채팅창에 넣을 앞부분("/섬 초대 <닉네임>" → "/섬 초대 "). 인자가 없으면 명령어 그대로. */
		public String prefill() {
			String u = usage;
			int cut = u.length();
			for (char c : new char[]{'<', '(', '['}) {
				int i = u.indexOf(c);
				if (i > 0 && i < cut) {
					cut = i;
				}
			}
			return cut < u.length() ? u.substring(0, cut).trim() + " " : u;
		}

		/** 인자가 있나(바로 보내지 않고 채팅창에서 마저 쓰게). */
		public boolean hasArgs() {
			return usage.indexOf('<') > 0 || usage.indexOf('(') > 0 || usage.indexOf('[') > 0;
		}
	}

	public static final List<String> CATEGORIES;
	public static final List<Command> ALL;

	static {
		List<String> c = new ArrayList<>();
		List<Command> a = new ArrayList<>();
		c.add("기본");
		a.add(new Command("기본", "/위키", "공식 위키를 엽니다."));
		a.add(new Command("기본", "/디스코드", "공식 디스코드 초대 링크를 확인합니다."));
		a.add(new Command("기본", "/개인설정", "개인 설정을 변경합니다."));
		a.add(new Command("기본", "/길라잡이", "길라잡이 진행 상황을 확인합니다. (혹은 시작합니다.)"));
		a.add(new Command("기본", "/메뉴", "서버의 메인 메뉴를 엽니다."));
		c.add("정보");
		a.add(new Command("정보", "/정보 <한글닉네임>", "한글 닉네임으로 유저의 영어(계정) 닉네임을 확인합니다."));
		a.add(new Command("정보", "/프로필", "내 프로필을 확인합니다."));
		a.add(new Command("정보", "/프로필 (닉네임)", "다른 유저의 프로필을 확인합니다."));
		a.add(new Command("정보", "/인기도", "자신의 인기도를 확인합니다."));
		a.add(new Command("정보", "/인기도 (닉네임)", "다른 유저의 인기도를 확인합니다."));
		a.add(new Command("정보", "/인기도 올리기 (닉네임)", "다른 유저의 인기도를 올립니다."));
		a.add(new Command("정보", "/레벨", "내 레벨을 확인합니다."));
		a.add(new Command("정보", "/경험치", "보유 경험치를 확인합니다."));
		a.add(new Command("정보", "/스탯", "내 스탯을 확인합니다."));
		a.add(new Command("정보", "/플레이타임", "플레이타임을 확인합니다."));
		a.add(new Command("정보", "/퀘스트", "일일퀘스트를 확인합니다."));
		a.add(new Command("정보", "/피로도", "채집 피로도를 확인합니다."));
		a.add(new Command("정보", "/낚시도감", "낚시도감을 확인하고 보상을 수령합니다."));
		a.add(new Command("정보", "/추천", "서버 추천(마인리스트) 안내를 확인합니다."));
		a.add(new Command("정보", "/추천보상", "서버 추천 보상을 확인합니다."));
		a.add(new Command("정보", "/일일접속보상", "일일 접속 보상을 확인하고 수령합니다."));
		a.add(new Command("정보", "/후원", "후원 안내와 누적 후원 금액을 확인합니다."));
		a.add(new Command("정보", "/누적후원보상", "누적 후원 보상을 확인하고 수령합니다."));
		a.add(new Command("정보", "/멤버십", "멤버십 활성 여부와 남은 기간을 확인합니다."));
		a.add(new Command("정보", "/멤버십 혜택", "멤버십 혜택 목록을 확인합니다."));
		c.add("채팅");
		a.add(new Command("채팅", "/cc", "채팅 채널을 변경합니다."));
		a.add(new Command("채팅", "/전체채팅", "채팅 채널을 전체채팅으로 변경합니다."));
		a.add(new Command("채팅", "/동네채팅", "채팅 채널을 동네채팅으로 변경합니다."));
		a.add(new Command("채팅", "/섬채팅", "채팅 채널을 섬채팅으로 변경합니다."));
		a.add(new Command("채팅", "/귓속말 (닉네임) [내용]", "귓속말을 보냅니다."));
		a.add(new Command("채팅", "/답장 [내용]", "마지막 귓속말 상대에게 답장합니다."));
		a.add(new Command("채팅", "/귓속말토글", "귓속말 수신 알림음을 켜고 끕니다."));
		a.add(new Command("채팅", "/확성기", "확성기로 전체 서버에 메시지를 보냅니다."));
		a.add(new Command("채팅", "/자랑", "손에 든 아이템을 자랑합니다. (쿨타임 10분, 10,000원)"));
		a.add(new Command("채팅", "/질문", "질문을 등록합니다."));
		a.add(new Command("채팅", "/답변", "등록된 질문에 답변합니다."));
		a.add(new Command("채팅", "/차단", "특정 유저의 채팅, 귓속말, 티피/거래 요청을 차단합니다. (최대 10명)"));
		c.add("거래");
		a.add(new Command("거래", "/돈", "보유 중인 돈을 확인합니다."));
		a.add(new Command("거래", "/돈순위", "돈 보유 순위를 확인합니다."));
		a.add(new Command("거래", "/도토리", "보유 중인 도토리 및 캐시를 확인합니다."));
		a.add(new Command("거래", "/캐시", "보유 중인 도토리 및 캐시를 확인합니다."));
		a.add(new Command("거래", "/도토리거래소", "도토리 거래소를 엽니다."));
		a.add(new Command("거래", "/시세", "손에 든 아이템의 최근 30일 유저상점 거래가를 확인합니다."));
		a.add(new Command("거래", "/유저상점", "유저상점을 엽니다."));
		a.add(new Command("거래", "/유저상점 판매 (가격) [갯수]", "손에 든 아이템을 유저상점에 판매 등록합니다. 갯수가 없으면 손에 든 전량."));
		a.add(new Command("거래", "/유저상점 일괄취소", "등록한 아이템을 모두 회수합니다."));
		a.add(new Command("거래", "/거래 (닉네임)", "다른 유저에게 거래를 요청합니다."));
		a.add(new Command("거래", "/무역순위", "무역 순위를 확인합니다."));
		a.add(new Command("거래", "/무역현황", "자신의 섬 개인별 무역 현황을 확인합니다."));
		c.add("편의");
		a.add(new Command("편의", "/앉기", "서 있는 자리에 앉습니다."));
		a.add(new Command("편의", "/모자", "손에 든 아이템을 머리에 착용합니다."));
		a.add(new Command("편의", "/밥", "배고픔을 회복합니다. (스킬 필요, 쿨타임 60초)"));
		a.add(new Command("편의", "/수리", "손에 든 도구를 수리합니다. (스킬 필요, 쿨타임 10분)"));
		a.add(new Command("편의", "/쓰레기통", "아이템을 버릴 수 있는 쓰레기통을 엽니다."));
		a.add(new Command("편의", "/금지아이템", "서버의 금지아이템을 확인합니다."));
		a.add(new Command("편의", "/단축어", "나만의 명령어 단축어를 등록합니다. (5개, 멤버십 15개)"));
		a.add(new Command("편의", "/창고", "개인 창고를 엽니다. /창고1~/창고10 (스킬, 멤버십 권한 필요)"));
		a.add(new Command("편의", "/광물창고", "광물 창고를 엽니다."));
		a.add(new Command("편의", "/귀속보관함", "귀속 아이템 전용 보관함을 엽니다."));
		a.add(new Command("편의", "/우편함", "우편함을 열어 보관된 아이템을 수령합니다."));
		a.add(new Command("편의", "/플라이", "플라이를 끄고 켭니다."));
		a.add(new Command("편의", "/플라이 토글", "액션바 플라이 남은 시간 알림을 끄고 켭니다."));
		a.add(new Command("편의", "/플라이 시간", "남은 플라이 시간을 확인합니다."));
		a.add(new Command("편의", "/자동심기", "자동 심기를 끄고 켭니다."));
		a.add(new Command("편의", "/자동심기 토글", "액션바 자동심기 남은 횟수 알림을 끄고 켭니다."));
		a.add(new Command("편의", "/자동심기 횟수", "남은 자동 심기 횟수를 확인합니다."));
		a.add(new Command("편의", "/줄기보호", "줄기보호를 끄고 켭니다. (스킬 필요)"));
		a.add(new Command("편의", "/스킬", "스킬을 배웁니다. (학당 루리와 같음)"));
		a.add(new Command("편의", "/스킬키세팅", "던전 스킬 1~4번 키를 고릅니다."));
		a.add(new Command("편의", "/신속", "신속한 발걸음 효과를 켜고 끕니다. (스킬 필요)"));
		c.add("이동");
		a.add(new Command("이동", "/스폰", "스폰으로 이동합니다."));
		a.add(new Command("이동", "/워프1", "서버의 다양한 장소로 워프합니다. (/워프2, /워프3)"));
		a.add(new Command("이동", "/티피신청 (닉네임)", "해당 유저에게 티피를 요청합니다."));
		a.add(new Command("이동", "/티피권유 (닉네임)", "해당 유저를 내 위치로 초대합니다."));
		a.add(new Command("이동", "/티피수락", "받은 티피 요청을 수락합니다."));
		a.add(new Command("이동", "/티피거절", "받은 티피 요청을 거절합니다."));
		a.add(new Command("이동", "/티피취소", "보낸 티피 요청을 취소합니다."));
		c.add("섬");
		a.add(new Command("섬", "/섬", "내 섬으로 이동합니다. (섬이 없으면 섬 생성 창)"));
		a.add(new Command("섬", "/섬 생성 <이름>", "섬을 생성합니다."));
		a.add(new Command("섬", "/섬 삭제", "섬을 삭제합니다. (복구 불가)"));
		a.add(new Command("섬", "/섬 이름변경 <이름>", "섬의 이름을 변경합니다."));
		a.add(new Command("섬", "/섬 양도 <닉네임>", "다른 유저에게 섬장을 양도합니다."));
		a.add(new Command("섬", "/섬 스폰설정", "서 있는 자리를 섬 스폰 장소로 설정합니다."));
		a.add(new Command("섬", "/섬 멤버", "내 섬 멤버를 확인합니다."));
		a.add(new Command("섬", "/섬 초대 <닉네임>", "플레이어를 내 섬으로 초대합니다."));
		a.add(new Command("섬", "/섬 수락 <닉네임>", "받은 섬 초대를 수락합니다."));
		a.add(new Command("섬", "/섬 추방 <닉네임>", "멤버를 섬에서 추방합니다."));
		a.add(new Command("섬", "/섬 탈퇴", "섬에서 탈퇴합니다."));
		a.add(new Command("섬", "/섬 알바고용 <닉네임>", "플레이어를 알바로 고용합니다."));
		a.add(new Command("섬", "/섬 알바수락 <닉네임>", "받은 알바 제안을 수락합니다."));
		a.add(new Command("섬", "/섬 알바해고 <닉네임>", "알바를 해고합니다."));
		a.add(new Command("섬", "/섬 알바목록", "고용 중인 알바 목록을 확인합니다."));
		a.add(new Command("섬", "/섬 워프", "다른 섬의 워프로 이동합니다."));
		a.add(new Command("섬", "/섬 워프생성 <이름>", "내 섬에 워프를 만듭니다."));
		a.add(new Command("섬", "/섬 워프설정", "내 섬 워프를 관리합니다."));
		a.add(new Command("섬", "/섬 워프삭제 <이름>", "내 섬 워프를 삭제합니다."));
		a.add(new Command("섬", "/섬 방문 <닉네임/섬이름>", "원하는 섬으로 방문합니다."));
		a.add(new Command("섬", "/섬 알바이동 [섬이름]", "알바로 고용된 섬으로 이동합니다."));
		a.add(new Command("섬", "/섬 방문설정", "내 섬의 방문 허용 여부를 설정합니다."));
		a.add(new Command("섬", "/섬 폐쇄", "다른 플레이어의 섬 방문을 전부 차단합니다."));
		a.add(new Command("섬", "/섬 개방", "폐쇄한 섬을 다시 개방합니다."));
		a.add(new Command("섬", "/섬 내보내기 <닉네임>", "내 섬의 방문자를 내보냅니다."));
		a.add(new Command("섬", "/섬 권한", "섬 직위별 권한을 설정합니다."));
		a.add(new Command("섬", "/섬 설정", "섬의 시간, 날씨 등 각종 설정을 확인합니다."));
		a.add(new Command("섬", "/섬 경계선 [파랑/초록/빨강]", "섬 경계선 표시 여부와 색상을 설정합니다."));
		a.add(new Command("섬", "/섬 pvp", "섬 PVP 여부를 설정합니다."));
		a.add(new Command("섬", "/섬 은행", "섬 은행 잔액을 확인합니다."));
		a.add(new Command("섬", "/섬 입금 <금액>", "섬 은행에 돈을 입금합니다."));
		a.add(new Command("섬", "/섬 출금 <금액>", "섬 은행에서 돈을 출금합니다."));
		a.add(new Command("섬", "/섬 은행로그", "섬 은행 입출금 기록을 확인합니다."));
		a.add(new Command("섬", "/섬 목록", "서버의 모든 섬 목록을 확인합니다."));
		a.add(new Command("섬", "/섬 순위", "서버의 섬 순위를 확인합니다."));
		a.add(new Command("섬", "/섬 업그레이드", "섬 업그레이드 메뉴를 엽니다."));
		a.add(new Command("섬", "/섬 광물창고", "섬 광물창고에서 꺼내거나 판매합니다. (모두판매는 멤버십)"));
		a.add(new Command("섬", "/로그조사", "내 섬의 블럭 설치/파괴, 상자 넣기/빼기 기록을 확인합니다."));
		c.add("치장");
		a.add(new Command("치장", "/치장", "치장 GUI를 열어 치장을 착용하고 해제합니다."));
		a.add(new Command("치장", "/옷장", "치장 아이템을 GUI에서 착용합니다."));
		a.add(new Command("치장", "/스킨", "도구 스킨 GUI를 엽니다."));
		a.add(new Command("치장", "/칭호", "보유한 칭호를 확인하고 장착합니다."));
		a.add(new Command("치장", "/한글닉네임", "한글 닉네임을 설정합니다."));
		a.add(new Command("치장", "/펫", "펫 GUI를 열어 펫을 소환하고 해제합니다."));
		a.add(new Command("치장", "/라이딩", "라이딩 GUI를 열어 탑승하고 해제합니다."));
		a.add(new Command("치장", "/발광", "발광 GUI를 열어 효과를 관리합니다."));
		CATEGORIES = Collections.unmodifiableList(c);
		ALL = Collections.unmodifiableList(a);
	}
}

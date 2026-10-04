package kr.lunaslight.mod.module.impl.chat;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.util.ChatState;

/**
 * 49-69차(4-12): <b>채팅 정리</b> - 읽을 게 없는 줄을 치워 채팅을 조용하게 만든다.
 *
 * <p>사용자 요청 4-12는 "인게임 채팅 정리" 한 줄이라 <b>무엇을 정리한다는 것인지</b>가 없었다.
 * 그래서 <b>뜻이 갈리지 않는 것</b>, 즉 "누가 봐도 읽을 필요가 없는 줄"만 골라 치운다.
 * 폭·투명도·줄 간격 같은 <b>모양</b>은 마인크래프트 설정에 이미 있어 여기서 또 만들지 않았다.
 *
 * <ul>
 *   <li><b>같은 줄 반복</b> - 서버 안내나 봇이 같은 문장을 몇 초마다 다시 보낸다. 49-76차(6-20)부터
 *       두 번째부터는 <b>앞 줄에 "×N"이 붙고</b> 새 줄은 안 보인다. <b>플레이어 채팅은 건드리지 않는다.</b>
 *       도배가 이어지는 동안에는 계속 숨기고, 조용해진 뒤 다시 오면 그때는 보여 준다.</li>
 *   <li><b>빈 줄</b> - 서버가 간격을 띄우려고 넣는 줄. 정보가 0인데 화면은 그대로 먹는다.</li>
 *   <li><b>구분선</b> - {@code ────────} 처럼 같은 문자만 늘어선 줄. 꾸밈이라 기본은 꺼 둔다
 *       (이걸로 문단을 나눠 읽는 사람도 있다).</li>
 * </ul>
 *
 * <p><b>"×3으로 합치기"는 안 한다.</b> 이미 그려진 줄을 고쳐 쓰려면 ChatHud가 들고 있는 줄 목록을
 * 직접 수술해야 하는데, 그건 버전마다 개수도 이름도 다른 private 필드다 - 잘못 건드리면 채팅이
 * 통째로 사라진다. 확실히 되는 것만 하고 설정 이름도 <b>"숨김"</b>라고 그대로 적었다.
 *
 * <p><b>성능</b>: 꺼져 있으면 채팅 한 줄당 비용이 0이다({@link ChatState#tidy} 한 줄).
 * 켜져 있어도 최근 24줄짜리 링 버퍼 비교라 채팅이 아무리 흘러도 메모리가 늘지 않는다.
 */
public class ChatTidyModule extends Module {

	private final BooleanSetting repeat = register(new BooleanSetting(
			"repeat", "반복 줄 병합", "같은 문장이 짧은 사이에 또 오면 앞 줄에 ×N을 붙입니다. 플레이어 채팅은 그대로 둡니다.", true));
	private final IntSetting seconds = register(new IntSetting(
			"seconds", "반복 판정 시간", "이 시간(초) 안에 같은 문장이 또 오면 반복으로 봅니다.", 10, 3, 60, 1).unit("초"));
	private final BooleanSetting blank = register(new BooleanSetting(
			"blank", "빈 줄 숨김", "서버가 간격을 띄우려고 넣는 빈 줄을 치웁니다.", true));
	private final BooleanSetting divider = register(new BooleanSetting(
			"divider", "구분선 숨김", "───── 처럼 같은 문자만 늘어선 줄을 치웁니다.", false));

	public ChatTidyModule() {
		super("chat_tidy", "채팅 정리", ModuleCategory.FEATURE, "반복/빈 줄/구분선 치우기");
		repeat.onChange(this::apply);
		seconds.onChange(this::apply);
		blank.onChange(this::apply);
		divider.onChange(this::apply);
	}

	@Override
	protected void onEnable() {
		ChatState.clearTidy();
		apply();
	}

	@Override
	protected void onDisable() {
		ChatState.tidy = false;
		ChatState.clearTidy();
	}

	private void apply() {
		if (!isEnabled()) {
			onDisable();
			return;
		}
		ChatState.tidyRepeat = repeat.get();
		ChatState.tidyRepeatSeconds = seconds.get();
		ChatState.tidyBlank = blank.get();
		ChatState.tidyDivider = divider.get();
		// 세부 값을 먼저 채우고 마지막에 켠다 - 반대로 하면 한순간 옛 값으로 거르는 줄이 생긴다
		ChatState.tidy = repeat.get() || blank.get() || divider.get();
	}
}

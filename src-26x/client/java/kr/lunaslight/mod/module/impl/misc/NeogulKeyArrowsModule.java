package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.NeogulKeyArrows;

/**
 * 49-503차(사용자: "너굴마을 리소스팩 낚시 미니게임 WASD로 돼 있는 거 화살표로도 보이게, 선택 기능, 리팩 비슷하게 최대한"):
 * <b>낚시 키 화살표</b> - 키캡 W A S D를 같은 키캡 그림의 ↑ ← ↓ →로 보여 준다. 기본 끔. 켜고 끄면 리소스를 다시 불러온다(몇 초).
 */
public class NeogulKeyArrowsModule extends Module {

	public NeogulKeyArrowsModule() {
		super("neogul_key_arrows", "낚시 키 화살표", ModuleCategory.SERVER, "미니게임 W A S D 키캡을 ↑ ← ↓ →로");
		serverGroup("너굴마을");
		defaultEnabled(false);
		NeogulKeyArrows.state = this::isEnabled;   // 시작할 때 첫 리소스 불러오기에도 저장된 켬/끔이 반영되게
	}

	/** 49-535차(사용자: "낚시 키 화살표 확인 메시지 한번 줘야"): 켜고 끌 때 리소스를 다시 불러오므로(몇 초 멈춤) 먼저 묻는다. */
	@Override
	public String[] toggleConfirm(boolean turningOn) {
		return turningOn
			? new String[]{"낚시 키 화살표를 켤까요?", "낚시 미니게임 W A S D 키캡이 ↑ ← ↓ →로 보여요", "리소스를 다시 불러와서 몇 초 멈출 수 있어요"}
			: new String[]{"낚시 키 화살표를 끌까요?", "낚시 미니게임 키캡이 원래 W A S D로 돌아가요", "리소스를 다시 불러와서 몇 초 멈출 수 있어요"};
	}

	@Override
	protected void onEnable() {
		apply(true);
	}

	@Override
	protected void onDisable() {
		apply(false);
	}

	private void apply(boolean on) {
		boolean changed = NeogulKeyArrows.applied != on;
		// 게임이 이미 떠 있을 때(설정에서 켜고 끔)만 다시 불러온다 - 시작할 때 설정을 읽는 순간엔 첫 불러오기가 알아서 반영
		if (changed && client != null && client.level != null) {
			try {
				client.reloadResourcePacks();
				LunaCompat.sendActionBar(client, on ? "§a낚시 키를 화살표로 바꾸는 중…" : "§7낚시 키를 원래대로 되돌리는 중…");
			} catch (Throwable t) {
				LunaCompat.warnOnce("keyArrows:reload", t);
			}
		}
	}
}

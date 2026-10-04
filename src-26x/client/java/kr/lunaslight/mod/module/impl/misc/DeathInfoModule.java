package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.core.BlockPos;

/**
 * 죽은 자리 - 죽는 순간의 좌표를 채팅에 남긴다.
 *
 * 죽음 판정은 이벤트가 아니라 <b>체력이 0 이하로 떨어진 순간</b>(살아 있다가 죽음)을 본다 -
 * 어느 버전/어느 서버에서도 같고, 죽음 화면이 뜨기 전에 좌표를 잡을 수 있다.
 * 채팅 줄은 <b>내 화면에만</b> 찍는다(서버로 아무것도 안 보냄).
 *
 * 49-124차(사용자: "죽은 자리는 채팅에만 나오게 해줘 UI는 노노"): 화면(HUD) 표시와 그 설정을
 * 전부 없애고 채팅 기록만 남긴다.
 */
public class DeathInfoModule extends Module {

	private boolean wasAlive;

	public DeathInfoModule() {
		super("death_info", "죽은 자리", ModuleCategory.HUD, "죽은 좌표를 채팅에 남김");
		defaultEnabled(true);
	}

	@Override
	public void onTick() {
		if (client == null || client.player == null || client.level == null) {
			wasAlive = false;
			return;
		}
		boolean alive = client.player.getHealth() > 0;
		if (wasAlive && !alive) {
			onDeath();
		}
		wasAlive = alive;
	}

	private void onDeath() {
		BlockPos pos = client.player.blockPosition();
		String where = "§7[Nova] §f죽은 자리 §7X §f" + pos.getX()
			+ " §7Y §f" + pos.getY() + " §7Z §f" + pos.getZ();
		LunaCompat.printLocalMessage(client, where);
	}
}

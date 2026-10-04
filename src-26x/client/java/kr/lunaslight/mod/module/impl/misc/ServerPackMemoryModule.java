package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.ActionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.ServerPackMemory;

/**
 * 49-144차: <b>리소스팩 기억</b>. 서버에서 받은 리소스팩을 기억해 두었다가, 런처의 서버 모드로 켜면 게임이 뜰 때
 * 미리 적용한다. 들어갈 때 서버 팩이 바뀌었는지 SHA-1로 확인해서 같으면 다시 받지 않고, 바뀌었으면 새로 받아 기억한다.
 * 동작은 {@link ServerPackMemory} 참고. 미리 적용은 게임을 켤 때(LunaClientMod) 한 번이라, 켜고 끄기는 다음 실행부터.
 */
public class ServerPackMemoryModule extends Module {

	public ServerPackMemoryModule() {
		super("server_pack_memory", "리소스팩 기억", ModuleCategory.FEATURE, "서버로 켜면 그 서버 리소스팩을 미리 적용");
		settingsPage(kr.lunaslight.mod.module.SettingsPage.GENERAL);
		defaultEnabled(true);
		register(new ActionSetting("forget", "기억 삭제", "기억해 둔 서버 리소스팩을 전부 지웁니다.", "삭제", () -> {
			ServerPackMemory.forgetAll();
			LunaCompat.sendActionBar(client, "§7기억한 서버 리소스팩을 지웠습니다");
		}));
	}

	@Override
	protected void onEnable() {
		ServerPackMemory.setEnabled(true);
	}

	@Override
	protected void onDisable() {
		ServerPackMemory.setEnabled(false);
	}

	@Override
	public void onTick() {
		ServerPackMemory.tick(client);
	}
}

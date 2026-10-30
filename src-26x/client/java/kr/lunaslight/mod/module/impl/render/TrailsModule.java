package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.util.AfkWatch;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.NovaTrails;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

/**
 * 49-364차(사용자: "클라이언트에 발자취 추가된 거 있는데 마저 그것도 추가좀"): <b>발자취</b> - 런처 상점에서 산 노바 발자취
 * (별가루 / 벚꽃)를 걸을 때 발밑에 남긴다. 틱마다 주변 플레이어 위치를 NovaTrails에 넘기고, BlueprintWorldMixin(submitEntities 끝)에서
 * 카메라를 향한 작은 그림들로 그린다. 내 것은 런처 서명(kind "trail")이 맞을 때만, 남의 것은 nova_player_tiers.cosmetic_trail.
 */
public class TrailsModule extends Module {

	private static TrailsModule instance;

	private final BooleanSetting others = register(new BooleanSetting(
			"others_trails", "다른 사람 발자취", "다른 Nova 유저의 노바 발자취도 보여줍니다.", true));

	public TrailsModule() {
		super("trails", "발자취", ModuleCategory.VIEW, "런처에서 산 노바 발자취");
		alwaysOn();
		instance = this;
	}

	public static boolean shown() {
		TrailsModule m = instance;
		return m != null && m.isEnabled();
	}

	@Override
	public void onTick() {
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc == null || mc.level == null) {
				NovaTrails.clear();
				return;
			}
			NovaTrails.beginTick();
			for (AbstractClientPlayer p : mc.level.players()) {
				if (p.isInvisible() || p.isSpectator() || p.isSleeping() || p.isVisuallySwimming() || p.isPassenger()) {
					continue;
				}
				if (AfkWatch.afkEntity(p)) {
					continue;
				}
				NovaTrails.player(p.getUUID().toString(), p.getX(), p.getY(), p.getZ(), others.get(), p.getScale(), p.onGround() ? 1 : 0);   // 49-370차: 몸 크기
			}
			NovaTrails.endTick();
		} catch (Throwable t) {
			LunaCompat.warnOnce("trails:tick", t);
		}
	}

	@Override
	public void onWorldRender(Object context) {
		kr.lunaslight.mod.util.NovaWingsRender.drawTrails(context);
	}

	/** 49-430차(사용자: "기능 설정에 날개 이런 게 왜 있어 - 기본이니까 껐다 켰다 안 했으면"): 늘 켜짐, 기능 목록에 안 띄움. */
	@Override
	public boolean hiddenInList() {
		return true;
	}
}

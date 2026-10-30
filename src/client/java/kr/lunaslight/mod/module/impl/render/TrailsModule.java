package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.util.AfkWatch;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.NovaTrails;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * 49-364차(사용자: "클라이언트에 발자취 추가된 거 있는데 마저 그것도 추가좀"): <b>발자취</b> - 런처 상점에서 산 노바 발자취
 * (별가루 / 벚꽃)를 걸을 때 발밑에 남긴다. 틱마다 주변 플레이어 위치를 NovaTrails에 넘기고(발자국 찍기, 입자 움직이기),
 * 월드 그리기에서 카메라를 향한 작은 그림들로 그린다(1.14.4는 BlueprintWorld14Mixin이 NovaWingsGl14.drawTrails로).
 * 내 것은 런처 서명(kind "trail")이 맞을 때만, 남의 것은 Supabase nova_player_tiers.cosmetic_trail. 1인칭에서도 내 발자취는 보인다.
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
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc == null || mc.world == null) {
				NovaTrails.clear();
				return;
			}
			NovaTrails.beginTick();
			for (Object o : mc.world.getPlayers()) {
				if (!(o instanceof Entity p) || p.isInvisible() || flag(p, "isSpectator") || flag(p, "isSleeping")
						|| flag(p, "isInSwimmingPose") || p.hasVehicle()) {
					continue;
				}
				if (AfkWatch.afkEntity(p)) {
					continue;
				}
				Vec3d v = LunaCompat.getPos(p);
				if (v != null) {
					NovaTrails.player(p.getUuid().toString(), v.x, v.y, v.z, others.get(),
							kr.lunaslight.mod.util.NovaWingsRender.bodyScale(p), ground(p));   // 49-370차: 몸 크기, 49-371차: 땅/공중
				}
			}
			NovaTrails.endTick();
		} catch (Throwable t) {
			LunaCompat.warnOnce("trails:tick", t);
		}
	}

	/** 49-371차: 땅을 딛고 있나 - 1.16+ isOnGround(), 그 전은 onGround 필드. 못 읽으면 -1(움직임으로 짐작). */
	private static int ground(Object p) {
		Object v = LunaCompat.callNoArg(p, "isOnGround");
		if (v instanceof Boolean b) {
			return b ? 1 : 0;
		}
		try {
			java.lang.reflect.Field f = LunaCompat.findField(p.getClass(), "onGround");
			if (f != null && f.getType() == boolean.class) {
				return f.getBoolean(p) ? 1 : 0;
			}
		} catch (Throwable ignored) {
		}
		return -1;
	}

	private static boolean flag(Object e, String name) {
		return Boolean.TRUE.equals(LunaCompat.callNoArg(e, name));
	}

	@Override
	public void onWorldRender(Object context) {
		kr.lunaslight.mod.util.NovaWingsRender.drawTrails(context, context instanceof kr.lunaslight.mod.util.BlueprintWorld.Ctx15);
	}

	/** 49-430차(사용자: "기능 설정에 날개 이런 게 왜 있어 - 기본이니까 껐다 켰다 안 했으면"): 늘 켜짐, 기능 목록에 안 띄움. */
	@Override
	public boolean hiddenInList() {
		return true;
	}
}

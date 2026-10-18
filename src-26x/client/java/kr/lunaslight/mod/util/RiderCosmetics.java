package kr.lunaslight.mod.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;

/**
 * 49-406차(사용자: "인게임에서 서버 치장이 시야에 보인대, F5 안 하면 1인칭에선 보이면 안 되는데, 그리고 치장이 막 밝게 보여"):
 * 서버 치장(가방, 모자, 날개 등 - Nexo/ModelEngine류가 플레이어 위에 태운 아이템/블록 디스플레이)은 따로 된 엔티티라
 * 1인칭에서도 그대로 그려져 눈앞을 가렸고, 서버가 밝기를 고정해 두면(brightness) 어두운 곳에서도 빛났다.
 * <ul>
 *   <li>1인칭(카메라 = 나)이면 나에게 타 있는(맨 아래 탈것이 나인) 디스플레이는 안 그린다(EntityRenderer.shouldRender).</li>
 *   <li>플레이어에게 타 있는 디스플레이의 밝기는 고정값 대신 그 플레이어 자리의 밝기(플레이어 몸과 같게, DisplayRenderer).</li>
 * </ul>
 * 플레이어에 안 탄 디스플레이(가구, 간판 글자 등)는 그대로.
 */
public final class RiderCosmetics {
	private RiderCosmetics() {
	}

	/** 이 디스플레이가 타 있는 플레이어(없으면 null). */
	public static Player riderOf(Entity e) {
		if (!(e instanceof Display)) {
			return null;
		}
		Entity root = e.getRootVehicle();
		return root != e && root instanceof Player p ? p : null;
	}

	/** 1인칭에서 내 몸에 탄 치장인가. */
	public static boolean hideInFirstPerson(Entity e) {
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc == null || mc.player == null || mc.getCameraEntity() != mc.player
					|| !mc.options.getCameraType().isFirstPerson()) {
				return false;
			}
			return riderOf(e) == mc.player;
		} catch (Throwable t) {
			return false;
		}
	}

	/** 플레이어에 탄 디스플레이면 그 플레이어 자리의 밝기(-1 = 바닐라대로). */
	public static int light(Entity e, boolean sky) {
		try {
			Player p = riderOf(e);
			if (p == null) {
				return -1;
			}
			if (!sky && p.isOnFire()) {
				return 15;
			}
			BlockPos pos = BlockPos.containing(p.getEyePosition());
			return e.level().getBrightness(sky ? LightLayer.SKY : LightLayer.BLOCK, pos);
		} catch (Throwable t) {
			return -1;
		}
	}
}

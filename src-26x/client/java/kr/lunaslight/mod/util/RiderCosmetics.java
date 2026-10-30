package kr.lunaslight.mod.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;

/**
 * 49-406차(사용자: "인게임에서 서버 치장이 시야에 보인대, F5 안 하면 1인칭에선 보이면 안 되는데, 그리고 치장이 막 밝게 보여"):
 * 서버 치장(가방, 모자, 날개 등 - Nexo/ModelEngine/HMCCosmetics류가 서버 리소스팩 모델로 플레이어에 붙인 엔티티)은 따로 된 엔티티라
 * 1인칭에서도 그대로 그려져 눈앞을 가렸고, 서버가 밝기를 고정해 두면(brightness) 어두운 곳에서도 빛났다.
 * <ul>
 *   <li>1인칭(카메라 = 나)이면 내 치장은 안 그린다(EntityRenderer.shouldRender).</li>
 *   <li>플레이어 치장인 디스플레이의 밝기는 고정값 대신 그 플레이어 자리의 밝기(플레이어 몸과 같게, DisplayRenderer). 글자 디스플레이는 그대로.</li>
 * </ul>
 * 49-430차(사용자: "서버 리소스팩 치장이 1인칭에서 보이고 밝은 오류 여전"): 예전엔 플레이어에 "타 있는" 디스플레이만 봤다. 서버에 따라
 * 치장을 태우지 않고 매 틱 플레이어 자리로 옮기거나(따라다니기), 디스플레이 대신 투명 갑옷 거치대에 씌운다. 이제
 * <ul>
 *   <li>디스플레이, 투명하거나 마커인 갑옷 거치대만 본다(보이는 갑옷 거치대, 몹은 그대로).</li>
 *   <li>타 있으면 맨 아래 탈것인 플레이어, 안 타 있으면 몸 안에 들어와 있는(가로 0.8칸, 발 0.5칸 아래 ~ 머리 1칸 위) 플레이어의 치장으로 본다.</li>
 * </ul>
 */
public final class RiderCosmetics {
	private RiderCosmetics() {
	}

	private static boolean cosmeticKind(Entity e) {
		if (e instanceof Display) {
			return true;
		}
		return e instanceof ArmorStand a && (a.isInvisible() || a.isMarker());
	}

	/** 이 치장 엔티티의 주인 플레이어(없으면 null). */
	public static Player ownerOf(Entity e) {
		if (e == null || !cosmeticKind(e)) {
			return null;
		}
		Entity root = e.getRootVehicle();
		if (root != e) {
			return root instanceof Player p ? p : null;
		}
		Player best = null;
		double bestD = 0.64;
		for (Player p : e.level().players()) {
			if (p.isSpectator()) {
				continue;
			}
			double dx = e.getX() - p.getX();
			double dz = e.getZ() - p.getZ();
			double d = dx * dx + dz * dz;
			if (d > bestD) {
				continue;
			}
			double dy = e.getY() - p.getY();
			if (dy < -0.5 || dy > p.getBbHeight() + 1.0) {
				continue;
			}
			best = p;
			bestD = d;
		}
		return best;
	}

	/** 예전 이름(다른 곳에서 부를 수 있어 남김). */
	public static Player riderOf(Entity e) {
		return ownerOf(e);
	}

	/** 1인칭에서 내 몸의 치장인가. */
	public static boolean hideInFirstPerson(Entity e) {
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc == null || mc.player == null || mc.getCameraEntity() != mc.player
					|| !mc.options.getCameraType().isFirstPerson() || !cosmeticKind(e)) {
				return false;
			}
			return ownerOf(e) == mc.player;
		} catch (Throwable t) {
			return false;
		}
	}

	/** 플레이어 치장 디스플레이면 그 플레이어 자리의 밝기(-1 = 바닐라대로). 글자 디스플레이는 그대로(읽기 쉽게). */
	public static int light(Entity e, boolean sky) {
		try {
			if (e instanceof Display.TextDisplay) {
				return -1;
			}
			Player p = ownerOf(e);
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

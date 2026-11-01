package kr.lunaslight.mod.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 49-456차(사용자: "동물들 그 밭이나 잔디에 겹쳐도 통과되게 해 줄래? 기능에 넣어서, 기본으로"): <b>동물 통과</b>.
 * 조준선이 동물(소, 돼지, 닭, 양 …)에 걸렸는데 그 뒤 손 닿는 거리에 밭/작물/풀/꽃(밟고 지나갈 수 있는 식물, 경작지, 사탕수수, 코코아)이 있으면
 * 동물 대신 그 블록을 조준한 것으로 바꾼다 - 동물이 밭 위에 서 있어도 작물을 캐고 심을 수 있다. 몬스터, 플레이어, 탈것은 그대로.
 * 웅크리면(설정) 원래대로 동물을 조준한다. Minecraft.pick(float) 끝(MinecraftPickMixin)에서 부른다.
 */
public final class AnimalPass {
	private AnimalPass() {
	}

	/** AnimalPassModule이 끼운다: 켜짐 여부, 웅크리면 동물 조준. */
	public static volatile boolean on;
	public static volatile boolean sneakToHit = true;

	public static void apply(Minecraft mc, float partial) {
		if (!on || mc == null || mc.player == null || mc.level == null) {
			return;
		}
		HitResult hr = mc.hitResult;
		if (!(hr instanceof EntityHitResult ehr) || !(ehr.getEntity() instanceof Animal) || ehr.getEntity().isVehicle()) {
			return;
		}
		if (sneakToHit && mc.player.isShiftKeyDown()) {
			return;
		}
		try {
			HitResult block = mc.player.pick(mc.player.blockInteractionRange(), partial, false);
			if (!(block instanceof BlockHitResult bhr) || block.getType() != HitResult.Type.BLOCK) {
				return;
			}
			BlockPos pos = bhr.getBlockPos();
			if (!passable(mc.level.getBlockState(pos), mc, pos)) {
				return;
			}
			mc.hitResult = block;
			mc.crosshairPickEntity = null;
		} catch (Throwable t) {
			LunaCompat.warnOnce("animalPass", t);
		}
	}

	/** 밭 쪽 블록인가: 식물(작물, 풀, 꽃, 덤불, 줄기), 경작지, 사탕수수, 코코아, 그 밖에 몸이 통과하는 블록(서버 커스텀 작물 등). */
	private static boolean passable(BlockState st, Minecraft mc, BlockPos pos) {
		Block b = st.getBlock();
		if (b instanceof VegetationBlock || b instanceof FarmlandBlock || b instanceof SugarCaneBlock || b instanceof CocoaBlock) {
			return true;
		}
		try {
			return !st.isAir() && st.getCollisionShape(mc.level, pos).isEmpty();
		} catch (Throwable t) {
			return false;
		}
	}
}

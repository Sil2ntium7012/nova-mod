package kr.lunaslight.mod.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 49-456차 → 49-459차(사용자 사진: 너굴마을 정육 섬 "정육용 식칼" - "잔디나 작물 뚫고 때리는 거 이거 말하는 거다"): <b>풀 너머 때리기</b>.
 * 조준선이 풀, 꽃, 작물, 덤불 같은 식물(또는 몸이 통과하는 블록)에 걸렸는데 그 너머 손 닿는 거리에 동물이나 몹이 있으면 블록 대신 그 생물을
 * 조준한 것으로 바꾼다 - 풀숲에 있는 동물을 풀을 안 베고 바로 때린다. 그 사이에 단단한 블록(벽)이 있으면 그대로. 플레이어는 대상 아님.
 * 괭이를 들었거나(작물 캐기) 웅크리면(설정) 원래대로 블록을 조준한다. Minecraft.pick(float) 끝(MinecraftPickMixin)에서 부른다.
 */
public final class AnimalPass {
	private AnimalPass() {
	}

	/** AnimalPassModule이 끼운다: 켜짐 여부, 웅크리면 블록 조준. */
	public static volatile boolean on;
	public static volatile boolean sneakToHit = true;

	public static void apply(Minecraft mc, float partial) {
		if (!on || mc == null || mc.player == null || mc.level == null) {
			return;
		}
		if (!(mc.hitResult instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK) {
			return;
		}
		Player p = mc.player;
		if (sneakToHit && p.isShiftKeyDown()) {
			return;
		}
		try {
			// 괭이 = 작물 캐기. 49-462차: 26.3에는 HoeItem 클래스가 없어(도구가 데이터로 바뀜) 아이템 id 끝(_hoe)으로 본다.
			if (String.valueOf(p.getMainHandItem().getItem()).endsWith("_hoe")) {
				return;
			}
			BlockPos pos = bhr.getBlockPos();
			if (!passable(mc.level.getBlockState(pos), mc, pos)) {
				return;
			}
			Vec3 eye = p.getEyePosition(partial);
			Vec3 look = p.getViewVector(partial);
			double r = p.entityInteractionRange();
			Vec3 end = eye.add(look.scale(r));
			AABB box = p.getBoundingBox().expandTowards(look.scale(r)).inflate(1.0);
			EntityHitResult ehr = ProjectileUtil.getEntityHitResult(p, eye, end, box, AnimalPass::target, r * r);
			if (ehr == null) {
				return;
			}
			double ed = eye.distanceToSqr(ehr.getLocation());
			// 식물은 몸이 통과하므로 COLLIDER로 다시 쏘면 건너뛴다 - 그래도 생물보다 앞에 걸리는 블록이 있으면 벽
			BlockHitResult wall = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			if (wall.getType() == HitResult.Type.BLOCK && eye.distanceToSqr(wall.getLocation()) < ed) {
				return;
			}
			mc.hitResult = ehr;
			mc.crosshairPickEntity = ehr.getEntity();
		} catch (Throwable t) {
			LunaCompat.warnOnce("animalPass", t);
		}
	}

	private static boolean target(Entity e) {
		return e instanceof LivingEntity && !(e instanceof Player) && !e.isSpectator() && e.isPickable() && e.isAlive();
	}

	/** 지나갈 블록: 식물(작물, 풀, 꽃, 덤불, 줄기), 사탕수수, 코코아, 그 밖에 몸이 통과하는 블록(서버 커스텀 작물 등). 경작지는 땅이라 아님. */
	private static boolean passable(BlockState st, Minecraft mc, BlockPos pos) {
		Block b = st.getBlock();
		if (b instanceof FarmlandBlock) {
			return false;
		}
		if (b instanceof VegetationBlock || b instanceof SugarCaneBlock || b instanceof CocoaBlock) {
			return true;
		}
		try {
			return !st.isAir() && st.getCollisionShape(mc.level, pos).isEmpty() && st.getFluidState().isEmpty();
		} catch (Throwable t) {
			return false;
		}
	}
}

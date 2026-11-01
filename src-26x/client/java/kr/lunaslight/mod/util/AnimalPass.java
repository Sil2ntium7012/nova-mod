package kr.lunaslight.mod.util;

import java.util.Optional;

import kr.lunaslight.mod.module.impl.misc.BeetHitboxModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 49-456차 → 49-472차(사용자: "감자인데 사탕무가 뭔 소용, 그냥 큰 몹이 앞에 있어도 아래 감자 보고 때리면 다리가 안 맞는다, 블록 히트박스가 아니었는데"): <b>몹 조준 보정</b>.
 * 조준선이 블록에 걸렸을 때 둘 중 하나면 블록 대신 그 몹을 때린다.
 * <ol>
 *   <li>몸이 통과하는 작은 블록(감자, 당근 같은 작물, 풀, 꽃 …)을 보고 있고, 그 칸 위에 몹이 서 있다(몹 발밑이나 다리 쪽이 그 칸에 걸침).
 *       서버 몹은 모델(다리)이 실제 히트박스보다 크거나 아래로 내려와 있어서, 다리를 노리면 조준선이 히트박스를 비켜 발밑 작물에 걸렸다.</li>
 *   <li>몹 히트박스가 블록의 원래 모양(사탕무 크게 선택 전)보다 조준선에서 앞에 있다.</li>
 * </ol>
 * 단단한 블록 뒤에 있는 몹은 그대로 블록. 플레이어는 대상 아님. Minecraft.pick(float) 끝(MinecraftPickMixin)에서 부른다.
 */
public final class AnimalPass {
	private AnimalPass() {
	}

	/** AnimalPassModule이 끼운다. */
	public static volatile boolean on;

	/** 몹 다리, 몸이 히트박스 밖으로 나온 만큼(옆, 아래). */
	private static final double SIDE = 0.6;
	private static final double DOWN = 1.0;

	public static void apply(Minecraft mc, float partial) {
		if (!on || mc == null || mc.player == null || mc.level == null) {
			return;
		}
		if (!(mc.hitResult instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK) {
			return;
		}
		Player p = mc.player;
		try {
			Vec3 eye = p.getEyePosition(partial);
			Vec3 look = p.getViewVector(partial);
			double r = p.entityInteractionRange();
			Vec3 end = eye.add(look.scale(r));
			BlockPos pos = bhr.getBlockPos();
			BlockState st = mc.level.getBlockState(pos);
			boolean plant = passable(mc, st, pos);
			AABB cell = new AABB(pos);
			Vec3 at = bhr.getLocation();
			double blockDist = realBlockDist(mc, p, st, pos, eye, end);
			AABB search = p.getBoundingBox().expandTowards(look.scale(r)).inflate(2.0);
			Entity best = null;
			Vec3 bestHit = null;
			double bestD = Double.MAX_VALUE;
			for (Entity e : mc.level.getEntities(p, search, AnimalPass::target)) {
				AABB eb = e.getBoundingBox();
				AABB hb = eb.inflate(e.getPickRadius());
				Vec3 hit = null;
				double score;
				if (hb.contains(eye)) {
					hit = eye;
				} else {
					Optional<Vec3> o = hb.clip(eye, end);
					if (o.isPresent()) {
						hit = o.get();
					}
				}
				if (hit != null && eye.distanceTo(hit) <= r && eye.distanceTo(hit) < blockDist) {
					score = eye.distanceTo(hit);   // 2: 블록보다 앞
				} else if (plant && new AABB(eb.minX - SIDE, eb.minY - DOWN, eb.minZ - SIDE, eb.maxX + SIDE, eb.maxY, eb.maxZ + SIDE).intersects(cell)) {
					hit = closest(eb, at);   // 1: 보고 있는 작물 칸 위에 서 있는 몹
					if (eye.distanceTo(hit) > r + SIDE) {
						continue;
					}
					score = 100 + hit.distanceTo(at);
				} else {
					continue;
				}
				if (score < bestD) {
					bestD = score;
					best = e;
					bestHit = hit;
				}
			}
			if (best == null) {
				return;
			}
			mc.hitResult = new EntityHitResult(best, bestHit);
			mc.crosshairPickEntity = best;
		} catch (Throwable t) {
			LunaCompat.warnOnce("animalPass", t);
		}
	}

	private static Vec3 closest(AABB b, Vec3 v) {
		return new Vec3(Math.max(b.minX, Math.min(b.maxX, v.x)), Math.max(b.minY, Math.min(b.maxY, v.y)), Math.max(b.minZ, Math.min(b.maxZ, v.z)));
	}

	/** 몸이 통과하는 블록(작물, 풀, 꽃 …). 물, 공기는 아님. */
	private static boolean passable(Minecraft mc, BlockState st, BlockPos pos) {
		try {
			return !st.isAir() && st.getFluidState().isEmpty() && st.getCollisionShape(mc.level, pos).isEmpty();
		} catch (Throwable t) {
			return false;
		}
	}

	/** 조준선이 그 블록의 원래 모양(사탕무 키우기 끈 상태)에 닿는 거리. 원래 모양엔 안 닿으면 무한. */
	private static double realBlockDist(Minecraft mc, Player p, BlockState st, BlockPos pos, Vec3 eye, Vec3 end) {
		boolean beet = BeetHitboxModule.on;
		BeetHitboxModule.on = false;
		try {
			VoxelShape shape = st.getShape(mc.level, pos, CollisionContext.of(p));
			if (shape.isEmpty()) {
				return Double.MAX_VALUE;
			}
			BlockHitResult h = shape.clip(eye, end, pos);
			return h == null ? Double.MAX_VALUE : eye.distanceTo(h.getLocation());
		} finally {
			BeetHitboxModule.on = beet;
		}
	}

	private static boolean target(Entity e) {
		return e instanceof LivingEntity && !(e instanceof Player) && !e.isSpectator() && e.isPickable() && e.isAlive();
	}
}

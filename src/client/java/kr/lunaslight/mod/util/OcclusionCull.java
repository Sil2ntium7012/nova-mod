package kr.lunaslight.mod.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 49-76차(6-16): <b>가려진 것 안 그리기</b> - 벽 뒤에 있어서 어차피 안 보이는 엔티티·블록 엔티티를 건너뛴다.
 *
 * <p>사용자: "상자 화로 줄이기는 왜 있는 건지 모르겠음. 나는 렉을 줄여 달라 한 거지 줄여 달라고는 안 함.
 * 대신 <b>내가 바라보고 있지 않은</b> 엔티티·블록 엔티티는 렉을 줄여 주는 시스템 필요."
 *
 * <h3>"바라보고 있지 않다"의 뜻</h3>
 * 화면 밖(시야 바깥)은 바닐라가 이미 안 그린다(frustum). 남는 건 <b>화면 안이지만 벽 뒤</b>인 것 -
 * 동굴 속 몹, 창고 뒤 상자들, 옆 방의 화로. 바닐라는 이걸 전부 그린다. 여기서 그걸 거른다.
 *
 * <h3>어떻게 - 그리고 왜 프레임에 비용이 없나</h3>
 * <ol>
 *   <li>렌더 관문(엔티티·블록 엔티티가 그려지기 직전)이 <b>"이번 프레임에 그리려던 것"</b>을 적어 둔다.
 *       월드 전체를 훑을 필요가 없다 - 바닐라가 이미 시야 안의 것만 여기까지 보내 준다.</li>
 *   <li><b>다른 스레드</b>가 틱마다 그 목록을 가져가서, 카메라 눈에서 대상까지 <b>광선을 쏜다</b>
 *       (가운데 + 모서리 넷, 다섯 줄). 다섯 줄이 <b>전부</b> 불투명 블록에 막히면 "가려짐".</li>
 *   <li>결과(가려진 것들의 집합)를 통째로 바꿔 끼운다. 관문은 <b>집합에 있나</b>만 본다.</li>
 * </ol>
 * 렌더 스레드에 얹히는 건 <b>집합 조회 한 번</b>뿐이다. 광선 계산은 전부 저쪽 스레드다.
 *
 * <h3>절대 안 숨기는 것</h3>
 * <b>플레이어</b>(남이 안 보이면 그건 성능이 아니라 치트다), 카메라가 붙은 엔티티와 타고 있는 것,
 * <b>3블록 안</b>의 것(벽에 붙어 있으면 광선이 벽에 걸려 깜빡인다), <b>빛나는(glowing)</b> 것(벽 너머로
 * 보이라고 있는 효과다), 광선이 64블록을 넘는 것(멀면 그냥 그린다 - 어차피 작다).
 *
 * <h3>다른 스레드에서 월드를 읽는 것에 대해</h3>
 * 블록 상태를 렌더 스레드 밖에서 읽는다. 청크가 그 순간 바뀌면 예외가 날 수 있고, 그러면 <b>그 대상은
 * 보이는 것으로</b> 친다(숨기는 쪽이 아니라 그리는 쪽으로 틀린다). 널리 쓰이는 컬링 모드들이 같은
 * 방식으로 돌고 있다.
 *
 * <p>한 틱 늦다 - 이번 틱의 판정은 지난 프레임의 목록으로 한다. 벽에서 나오는 몹이 <b>한 틱</b>(50ms)
 * 늦게 나타날 수 있다. 사람이 알아채는 시간은 아니다.
 */
public final class OcclusionCull {
	private OcclusionCull() {
	}

	public static volatile boolean on;

	/** 광선 최대 길이(블록). 이보다 멀면 판정하지 않고 그린다. */
	private static final double MAX_DIST = 64.0;
	/** 이 안은 판정하지 않는다(벽에 붙은 것이 깜빡이는 걸 막는다). */
	private static final double NEAR_SQ = 3.0 * 3.0;

	// ---- 관문이 채우는 "이번 프레임 후보" ----
	private static final Set<Entity> seenEntities = ConcurrentHashMap.newKeySet();
	private static final Set<Long> seenBlocks = ConcurrentHashMap.newKeySet();

	// ---- 스레드가 만든 결과(통째로 바꿔 끼운다) ----
	private static volatile Set<Entity> hiddenEntities = Collections.emptySet();
	private static volatile Set<Long> hiddenBlocks = Collections.emptySet();

	private static Thread worker;
	private static volatile boolean busy;

	// ==================== 관문에서 부르는 것(렌더 스레드) ====================

	/** 엔티티를 그리기 직전. true면 건너뛴다. */
	public static boolean hideEntity(Entity e) {
		if (!on || e == null) {
			return false;
		}
		seenEntities.add(e);
		return hiddenEntities.contains(e);
	}

	/** 블록 엔티티를 그리기 직전. true면 건너뛴다. */
	public static boolean hideBlock(int x, int y, int z) {
		if (!on) {
			return false;
		}
		long key = BlockPos.asLong(x, y, z);
		seenBlocks.add(key);
		return hiddenBlocks.contains(key);
	}

	public static void clear() {
		seenEntities.clear();
		seenBlocks.clear();
		hiddenEntities = Collections.emptySet();
		hiddenBlocks = Collections.emptySet();
	}

	// ==================== 틱(렌더 스레드에서 시작, 계산은 다른 스레드) ====================

	/** 한 번의 판정에 필요한 것을 렌더 스레드에서 복사해 둔 스냅샷. */
	private static final class Job {
		final Object world;
		final double ex, ey, ez;
		final List<Entity> entities = new ArrayList<>();
		final List<double[]> boxes = new ArrayList<>();   // {minX,minY,minZ,maxX,maxY,maxZ}
		final List<Long> blocks = new ArrayList<>();

		Job(Object world, double ex, double ey, double ez) {
			this.world = world;
			this.ex = ex;
			this.ey = ey;
			this.ez = ez;
		}
	}

	/** 모듈이 틱마다 부른다. 후보를 스냅샷으로 뜨고 스레드에 넘긴다. */
	public static void tick(MinecraftClient mc) {
		if (!on || mc == null || mc.world == null || busy) {
			return;
		}
		Job job;
		try {
			net.minecraft.util.math.Vec3d eye = LunaCompat.cameraPos(LunaCompat.getCamera(mc), mc);
			if (eye == null) {
				return;
			}
			job = new Job(mc.world, eye.x, eye.y, eye.z);
			Entity camEntity = mc.getCameraEntity();
			Entity vehicle = mc.player == null ? null : mc.player.getVehicle();
			for (Entity e : seenEntities) {
				if (e == null || e == camEntity || e == vehicle || e == mc.player
						|| e instanceof net.minecraft.entity.player.PlayerEntity || e.isGlowing()) {
					continue;
				}
				double w = e.getWidth();
				double h = e.getHeight();
				double x = EntityPos.x(e), y = EntityPos.y(e), z = EntityPos.z(e);
				job.entities.add(e);
				job.boxes.add(new double[]{x - w / 2, y, z - w / 2, x + w / 2, y + h, z + w / 2});
			}
			job.blocks.addAll(seenBlocks);
		} catch (Throwable t) {
			LunaCompat.warnOnce("occlusion:snapshot", t);
			return;
		} finally {
			seenEntities.clear();
			seenBlocks.clear();
		}
		if (job.entities.isEmpty() && job.blocks.isEmpty()) {
			hiddenEntities = Collections.emptySet();
			hiddenBlocks = Collections.emptySet();
			return;
		}
		busy = true;
		Job finalJob = job;
		Thread t = worker;
		if (t == null || !t.isAlive()) {
			worker = t = new Thread(() -> run(finalJob), "luna-occlusion");
			t.setDaemon(true);
			t.setPriority(Thread.MIN_PRIORITY);
			t.start();
		} else {
			// 앞 작업이 아직 도는 중(busy가 true였을 것) - 여기 오면 안 되지만, 오면 그냥 건너뛴다
			busy = false;
		}
	}

	// ==================== 계산(다른 스레드) ====================

	private static void run(Job job) {
		try {
			Set<Entity> he = new HashSet<>();
			for (int i = 0; i < job.entities.size(); i++) {
				double[] b = job.boxes.get(i);
				if (occluded(job, b)) {
					he.add(job.entities.get(i));
				}
			}
			Set<Long> hb = new HashSet<>();
			for (Long key : job.blocks) {
				int x = BlockPos.unpackLongX(key), y = BlockPos.unpackLongY(key), z = BlockPos.unpackLongZ(key);
				// 블록 엔티티는 자기 칸 하나 - 칸의 가운데와 위쪽 모서리 넷을 본다(상자 뚜껑이 보이는 방향)
				if (occluded(job, new double[]{x, y, z, x + 1, y + 1, z + 1})) {
					hb.add(key);
				}
			}
			hiddenEntities = he;
			hiddenBlocks = hb;
		} catch (Throwable t) {
			LunaCompat.warnOnce("occlusion:run", t);
			hiddenEntities = Collections.emptySet();
			hiddenBlocks = Collections.emptySet();
		} finally {
			busy = false;
		}
	}

	/** 상자 하나가 가려졌나 - 다섯 점 중 하나라도 보이면 false. */
	private static boolean occluded(Job job, double[] b) {
		double cx = (b[0] + b[3]) / 2, cy = (b[1] + b[4]) / 2, cz = (b[2] + b[5]) / 2;
		double dx = cx - job.ex, dy = cy - job.ey, dz = cz - job.ez;
		double distSq = dx * dx + dy * dy + dz * dz;
		if (distSq < NEAR_SQ || distSq > MAX_DIST * MAX_DIST) {
			return false;
		}
		// 자기 칸 안에서 출발/도착하는 광선이 자기 블록에 막히지 않게 상자를 살짝 안쪽으로
		double in = 0.05;
		double[][] targets = {
			{cx, cy, cz},
			{b[0] + in, b[4] - in, b[2] + in}, {b[3] - in, b[4] - in, b[2] + in},
			{b[0] + in, b[4] - in, b[5] - in}, {b[3] - in, b[4] - in, b[5] - in},
			{cx, b[1] + in, cz},
		};
		for (double[] t : targets) {
			if (!blocked(job, t[0], t[1], t[2])) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 눈에서 (tx,ty,tz)까지 불투명 블록이 있나. 정수 격자를 한 칸씩 걷는다(Amanatides-Woo).
	 * 출발 칸(눈이 있는 블록)과 도착 칸(대상이 있는 블록)은 세지 않는다.
	 */
	private static boolean blocked(Job job, double tx, double ty, double tz) {
		double ox = job.ex, oy = job.ey, oz = job.ez;
		double dx = tx - ox, dy = ty - oy, dz = tz - oz;
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1e-6) {
			return false;
		}
		dx /= len;
		dy /= len;
		dz /= len;
		int x = (int) Math.floor(ox), y = (int) Math.floor(oy), z = (int) Math.floor(oz);
		int endX = (int) Math.floor(tx), endY = (int) Math.floor(ty), endZ = (int) Math.floor(tz);
		int stepX = dx > 0 ? 1 : dx < 0 ? -1 : 0;
		int stepY = dy > 0 ? 1 : dy < 0 ? -1 : 0;
		int stepZ = dz > 0 ? 1 : dz < 0 ? -1 : 0;
		double tMaxX = stepX == 0 ? Double.MAX_VALUE : ((stepX > 0 ? x + 1 - ox : ox - x) / Math.abs(dx));
		double tMaxY = stepY == 0 ? Double.MAX_VALUE : ((stepY > 0 ? y + 1 - oy : oy - y) / Math.abs(dy));
		double tMaxZ = stepZ == 0 ? Double.MAX_VALUE : ((stepZ > 0 ? z + 1 - oz : oz - z) / Math.abs(dz));
		double tDeltaX = stepX == 0 ? Double.MAX_VALUE : 1.0 / Math.abs(dx);
		double tDeltaY = stepY == 0 ? Double.MAX_VALUE : 1.0 / Math.abs(dy);
		double tDeltaZ = stepZ == 0 ? Double.MAX_VALUE : 1.0 / Math.abs(dz);
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int i = 0; i < 256; i++) {
			double tNext = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));
			if (tNext > len) {
				return false;                  // 다음 경계가 대상 너머 - 막힌 것 없이 도착
			}
			if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
				x += stepX;
				tMaxX += tDeltaX;
			} else if (tMaxY <= tMaxZ) {
				y += stepY;
				tMaxY += tDeltaY;
			} else {
				z += stepZ;
				tMaxZ += tDeltaZ;
			}
			if (x == endX && y == endY && z == endZ) {
				return false;                  // 도착 칸 - 대상 자신의 블록
			}
			pos.set(x, y, z);
			if (LunaCompat.isOpaqueFullCube(job.world, pos)) {
				return true;
			}
		}
		return false;
	}

	/** 진단용: 지금 숨기고 있는 개수. */
	public static int hiddenCount() {
		return hiddenEntities.size() + hiddenBlocks.size();
	}
}

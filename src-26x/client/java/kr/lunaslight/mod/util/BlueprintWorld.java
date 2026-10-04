package kr.lunaslight.mod.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 49-266차 → 49-267차(26.x): 설계도 홀로그램을 월드 안에 진짜 블록 모델로 그린다(리터메티카 방식). 사용자: "나머지도 다 해줘".
 * 26.x는 Fabric 월드 렌더 이벤트 대신 LevelRenderer#submitEntities 끝에 걸어(BlueprintWorldMixin) 그 SubmitNodeCollector에
 * 사용자 정의 도형(submitCustomGeometry, 반투명 블록 시트)으로 넣는다. 쿼드 꼭짓점(위치, UV, 면 방향)을 직접 꺼내 넣으므로
 * 알파(진하기), 바이옴 색, 크기 키우기를 마음대로 정할 수 있다. 26.1~26.3 공통 API만 쓴다.
 * BlueprintModule(두 트리 공통 코드)이 begin → block … → end로 부른다.
 */
public final class BlueprintWorld {
	private BlueprintWorld() {
	}

	/** 블록 상태 하나의 쿼드: 방향별(0~5 = 아래, 위, 북, 남, 서, 동 / 6 = 방향 없음) 쿼드와 쿼드별 색(-1 = 색 없음). */
	public static final class Model {
		final BakedQuad[][] quads = new BakedQuad[7][];
		final int[][] tints = new int[7][];
	}

	/** 믹스인이 넘겨 주는 이번 프레임 정보(onWorldRender의 context). */
	public static final class Ctx {
		final PoseStack pose;
		final Vec3 cam;
		final SubmitNodeCollector collector;

		public Ctx(PoseStack pose, Vec3 cam, SubmitNodeCollector collector) {
			this.pose = pose;
			this.cam = cam;
			this.collector = collector;
		}
	}

	private static boolean broken;
	private static Ctx ctx;
	/** 이번 프레임에 넣을 블록들(그리기는 나중에 한꺼번에 - 도형 하나). */
	private static final List<Object[]> queue = new ArrayList<>();
	private static final Direction[] DIRS = Direction.values();

	public static boolean usable() {
		return !broken;
	}

	public static void fail(Throwable t) {
		broken = true;
		LunaCompat.warnOnce("blueprint:world", t);
	}

	// ==================== 블록 상태 / 모델 ====================

	/** 설계도 항목(id + 속성) → 블록 상태. 없는 블록이면 null. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static BlockState stateOf(String id, String props) {
		try {
			Identifier ident = Identifier.tryParse(id);
			if (ident == null || !BuiltInRegistries.BLOCK.containsKey(ident)) {
				return null;
			}
			Block block = BuiltInRegistries.BLOCK.getValue(ident);
			BlockState st = block.defaultBlockState();
			for (Map.Entry<String, String> e : Blueprint.parseProps(props).entrySet()) {
				Property p = block.getStateDefinition().getProperty(e.getKey());
				if (p == null) {
					continue;
				}
				java.util.Optional v = p.getValue(e.getValue());
				if (v.isPresent()) {
					st = st.setValue(p, (Comparable) v.get());
				}
			}
			return st;
		} catch (Throwable t) {
			LunaCompat.warnOnce("blueprint:state", t);
			return null;
		}
	}

	/** 블록 상태의 쿼드를 모은다(색은 pos 자리의 바이옴 색). 쿼드가 없으면(상자 등) null - 그 칸은 화면 방식으로. */
	public static Model model(BlockState state, BlockPos pos) {
		if (broken || state == null) {
			return null;
		}
		try {
			Minecraft mc = Minecraft.getInstance();
			BlockStateModel m = mc.getModelManager().getBlockStateModelSet().get(state);
			if (m == null) {
				return null;
			}
			List<BlockStateModelPart> parts = new ArrayList<>();
			m.collectParts(RandomSource.create(42L), parts);
			Model out = new Model();
			int total = 0;
			for (int d = 0; d < 7; d++) {
				Direction dir = d < 6 ? DIRS[d] : null;
				List<BakedQuad> qs = new ArrayList<>();
				for (BlockStateModelPart part : parts) {
					List<BakedQuad> l = part.getQuads(dir);
					if (l != null) {
						qs.addAll(l);
					}
				}
				out.quads[d] = qs.toArray(new BakedQuad[0]);
				int[] tints = new int[qs.size()];
				for (int i = 0; i < tints.length; i++) {
					tints[i] = tintOf(mc, state, pos, qs.get(i));
				}
				out.tints[d] = tints;
				total += tints.length;
			}
			return total == 0 ? null : out;
		} catch (Throwable t) {
			fail(t);
			return null;
		}
	}

	private static int tintOf(Minecraft mc, BlockState state, BlockPos pos, BakedQuad q) {
		try {
			int ti = q.materialInfo().tintIndex();
			if (ti < 0) {
				return -1;
			}
			BlockTintSource src = mc.getBlockColors().getTintSource(state, ti);
			if (src == null) {
				return -1;
			}
			int c = mc.level instanceof BlockAndTintGetter g ? src.colorInWorld(state, g, pos) : src.color(state);
			return c & 0xFFFFFF;
		} catch (Throwable t) {
			return -1;
		}
	}

	// ==================== 그리기 ====================

	public static boolean begin(Object context, float alpha) {
		if (broken || !(context instanceof Ctx c) || c.pose == null || c.cam == null || c.collector == null) {
			return false;
		}
		ctx = c;
		queue.clear();
		return true;
	}

	/** 블록 하나를 이번 프레임 목록에 넣는다(실제로는 end에서 한꺼번에). */
	public static void block(Model m, int x, int y, int z, float r, float g, float b, float a, int cull, double grow) {
		if (m == null || ctx == null) {
			return;
		}
		queue.add(new Object[]{m, x - ctx.cam.x, y - ctx.cam.y, z - ctx.cam.z, r, g, b, a, cull, grow});
	}

	public static void end() {
		Ctx c = ctx;
		ctx = null;
		if (c == null || queue.isEmpty() || broken) {
			queue.clear();
			return;
		}
		final List<Object[]> items = new ArrayList<>(queue);
		queue.clear();
		try {
			RenderType type = Sheets.translucentBlockItemSheet();
			c.collector.submitCustomGeometry(c.pose, type, (pose, consumer) -> {
				try {
					for (Object[] it : items) {
						emit(pose, consumer, it);
					}
				} catch (Throwable t) {
					fail(t);
				}
			});
		} catch (Throwable t) {
			fail(t);
		}
	}

	private static void emit(PoseStack.Pose pose, VertexConsumer vc, Object[] it) {
		Model m = (Model) it[0];
		float ox = (float) (double) (Double) it[1], oy = (float) (double) (Double) it[2], oz = (float) (double) (Double) it[3];
		float r = (Float) it[4], g = (Float) it[5], b = (Float) it[6], a = (Float) it[7];
		int cull = (Integer) it[8];
		float grow = (float) (double) (Double) it[9];
		float s = 1 + grow * 2;
		int alpha = Math.max(0, Math.min(255, Math.round(a * 255)));
		for (int d = 0; d < 7; d++) {
			if (d < 6 && (cull >> d & 1) != 0) {
				continue;
			}
			BakedQuad[] qs = m.quads[d];
			int[] ts = m.tints[d];
			for (int i = 0; i < qs.length; i++) {
				BakedQuad q = qs[i];
				float cr = r, cg = g, cb = b;
				int t = ts[i];
				if (t >= 0) {
					cr *= ((t >> 16) & 0xFF) / 255f;
					cg *= ((t >> 8) & 0xFF) / 255f;
					cb *= (t & 0xFF) / 255f;
				}
				int argb = alpha << 24 | clamp(cr) << 16 | clamp(cg) << 8 | clamp(cb);
				Direction dir = q.direction();
				float nx = dir.getStepX(), ny = dir.getStepY(), nz = dir.getStepZ();
				for (int k = 0; k < 4; k++) {
					Vector3fc p = k == 0 ? q.position0() : k == 1 ? q.position1() : k == 2 ? q.position2() : q.position3();
					long uv = k == 0 ? q.packedUV0() : k == 1 ? q.packedUV1() : k == 2 ? q.packedUV2() : q.packedUV3();
					float px = p.x(), py = p.y(), pz = p.z();
					if (grow > 0) {
						px = 0.5f + (px - 0.5f) * s;
						py = 0.5f + (py - 0.5f) * s;
						pz = 0.5f + (pz - 0.5f) * s;
					}
					vc.addVertex(pose, ox + px, oy + py, oz + pz)
							.setColor(argb)
							.setUv(UVPair.unpackU(uv), UVPair.unpackV(uv))
							.setOverlay(655360)
							.setLight(0xF000F0)
							.setNormal(pose, nx, ny, nz);
				}
			}
		}
	}

	private static int clamp(float c) {
		return Math.max(0, Math.min(255, Math.round(c * 255)));
	}
}

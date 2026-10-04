package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.EnumSetting;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaProjection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 이름표 제어.
 *
 * 49-21차 재작성(사용자: "아이템만 이름 뜨는데 일반 엔티티도 되게 하고, 아이템은 부가 선택 기능으로,
 * 아이템 개수 표시도, 플레이어 닉네임도 개별로"):
 *  - 몹 / 플레이어 이름표는 각각 [기본 / 항상 / 숨김]. 렌더러 클래스별 믹스인 4개
 *    (EntityRenderer·LivingEntityRenderer·MobEntityRenderer·PlayerEntityRenderer의 hasLabel)가
 *    이 모듈의 정적 판정(decide)을 부른다.
 *  - 아이템 이름표는 바닐라 라벨 대신 **직접 화면에 투영해 그린다**(LunaProjection). 그래야
 *    개수(×N)를 붙일 수 있고, 버전 차이(라벨 텍스트를 바꾸는 API가 버전마다 다름)도 안 탄다.
 *
 * 49-22차:
 *  ① "아이템 이름이 멀어지면 커지는데 다른 엔티티 이름처럼 고정으로" - 바닐라 이름표는 월드 크기가
 *     고정(글자 1px = 0.025블록)이라 멀어질수록 화면에서 작아진다. 아이템 라벨도 같은 공식으로
 *     화면 배율을 계산해(LunaCompat.guiScale) 똑같이 줄어들게 했다.
 *  ② "이름표는 벽 너머 볼 수 없게, 설정도 없이" - 몹/플레이어/아이템 전부, 플레이어 눈에서 그 엔티티까지
 *     시야가 블록에 막히면(LivingEntity#canSee - 바닐라 광선 검사) 이름표를 안 그린다. 검사는 엔티티당
 *     **틱에 한 번**만(캐시) - 매 프레임 광선을 쏘지 않는다(프레임 드랍 대책).
 *  ③ 아이템 목록/이름 문자열도 틱에 한 번만 만들고, 프레임에서는 투영만 한다.
 */
public class NametagVisibilityModule extends Module {

	public enum Mode {
		DEFAULT, ALWAYS, HIDDEN;

		@Override
		public String toString() {
			return switch (this) {
				case DEFAULT -> "기본";
				case ALWAYS -> "항상";
				case HIDDEN -> "숨김";
			};
		}
	}

	private final EnumSetting<Mode> mobs = register(new EnumSetting<>(
			"mobs", "몹", "몹 이름표 표시 방식입니다.", Mode.ALWAYS, Mode.class));

	private final EnumSetting<Mode> players = register(new EnumSetting<>(
			"players", "플레이어", "플레이어 이름표 표시 방식입니다.", Mode.ALWAYS, Mode.class));

	private final BooleanSetting items = register(new BooleanSetting(
			"items", "아이템", "떨어진 아이템 위에 이름을 표시합니다.", false));

	private final BooleanSetting itemCount = register(new BooleanSetting(
			"item_count", "아이템 개수", "아이템 이름 옆에 개수를 표시합니다.", true));

	private final IntSetting itemRange = register(new IntSetting(
			"item_range", "아이템 거리", "아이템 이름표를 표시하는 거리(블록)입니다.", 24, 4, 64, 2).unit("블록"));

	private final ColorSetting itemColor = register(new ColorSetting(
			"item_color", "아이템 글자 색", "아이템 이름표의 색입니다.", 0xFFFFFFFF));

	private static volatile Mode mobModeStatic = Mode.ALWAYS;
	private static volatile Mode playerModeStatic = Mode.ALWAYS;
	private static volatile boolean enabledStatic = false;

	public NametagVisibilityModule() {
		super("nametag_visibility", "이름표", ModuleCategory.VIEW,
				"몹 | 플레이어 이름표 표시 방식과 아이템 이름표");
		items.withColor(itemColor); // 49-23차: 1줄 통합
	}

	@Override
	protected void onEnable() {
		enabledStatic = true;
		sync();
	}

	@Override
	protected void onDisable() {
		enabledStatic = false;
		VIS.clear();
	}

	@Override
	public void onTick() {
		sync();
		refreshItemLabels();
	}

	private void sync() {
		mobModeStatic = mobs.get();
		playerModeStatic = players.get();
	}

	/** 믹스인용: 몹(MobEntityRenderer) 이름표 모드. */
	public static Mode mobMode() {
		return enabledStatic ? mobModeStatic : Mode.DEFAULT;
	}

	/** 믹스인용: 플레이어 이름표 모드. */
	public static Mode playerMode() {
		if (shotHidePlayers) {
			return Mode.HIDDEN;
		}
		return enabledStatic ? playerModeStatic : Mode.DEFAULT;
	}

	/** 49-192차: 스크린샷을 찍는 순간(ScreenshotToolModule)에만 플레이어 이름표를 숨긴다 - 이 기능이 꺼져 있어도. */
	public static volatile boolean shotHidePlayers;

	/** 하위 호환(예전 믹스인 이름) - 몹 모드와 동일. */
	public static Mode getActiveMode() {
		return mobMode();
	}

	// ==================== 시야(벽 너머) 검사 - 틱 캐시 ====================

	private static final Map<Entity, long[]> VIS = new HashMap<>();   // [검사한 틱, 보임(1/0)]

	/**
	 * 플레이어 눈에서 이 엔티티가 블록에 안 가리고 보이는지(50ms에 1회 계산, 캐시).
	 * 49-198차(사용자: "히트박스 같은 거 벽 뚫고 보이면 안 되지 - ESP잖아"): 이름표뿐 아니라 히트박스, 버려진 아이템, TNT 표시도
	 * 이걸로 거른다. 그래서 캐시를 이 기능의 틱(꺼져 있으면 안 돎)이 아니라 시간으로 갱신한다.
	 */
	public static boolean canSeeCached(Entity e) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (e == null || mc.player == null || e == mc.player) {
			return true;
		}
		long now = System.currentTimeMillis();
		long[] st = VIS.get(e);
		if (st != null && now - st[0] < 50L) {
			return st[1] == 1;
		}
		if (VIS.size() > 512) {
			VIS.entrySet().removeIf(en -> now - en.getValue()[0] > 2000L);
		}
		boolean vis;
		try {
			vis = mc.player.canSee(e);
		} catch (Throwable t) {
			vis = true;
		}
		if (st == null) {
			VIS.put(e, new long[]{now, vis ? 1 : 0});
		} else {
			st[0] = now;
			st[1] = vis ? 1 : 0;
		}
		return vis;
	}

	/**
	 * 믹스인용 최종 판정(hasLabel의 RETURN에서 호출). null이면 바닐라 값 그대로.
	 * 모드(항상/숨김)를 적용한 뒤, 보이는 경우에만 벽 너머 검사를 한다(안 보이는 이름표엔 광선을 안 쏨).
	 */
	public static Boolean decide(boolean player, Entity entity, boolean vanilla) {
		if (player && shotHidePlayers) {
			lastLiving = false;
			return false;
		}
		if (!enabledStatic) {
			return null;
		}
		Mode mode = player ? playerModeStatic : mobModeStatic;
		boolean visible = mode == Mode.HIDDEN ? false : (mode == Mode.ALWAYS || vanilla);
		if (visible && entity != null && !canSeeCached(entity)) {
			visible = false;
		}
		if (player) {
			lastLiving = visible;
		}
		return visible;
	}

	private static boolean lastLiving;

	/** 1.21.9+ PlayerEntityRenderer 훅용: 직전에 LivingEntityRenderer 단계에서 플레이어에 내린 판정. */
	public static boolean lastLivingDecision() {
		return lastLiving;
	}

	// ==================== 아이템 이름표(직접 투영) ====================

	/** 틱마다 갱신되는 아이템 라벨 후보(이름 문자열까지 미리 만들어 둠). */
	private static final class ItemLabel {
		final ItemEntity entity;
		final String text;
		boolean visible;

		ItemLabel(ItemEntity entity, String text) {
			this.entity = entity;
			this.text = text;
		}
	}

	private List<ItemLabel> itemLabels = List.of();

	private void refreshItemLabels() {
		if (!items.get() || client.world == null || client.player == null) {
			itemLabels = List.of();
			return;
		}
		Vec3d eye = client.player.getCameraPosVec(1f);
		double range = itemRange.get();
		Box search = new Box(eye.x - range, eye.y - range, eye.z - range, eye.x + range, eye.y + range, eye.z + range);
		List<ItemEntity> entities = LunaCompat.getEntitiesByClass(client.world, ItemEntity.class, search);
		if (entities == null || entities.isEmpty()) {
			itemLabels = List.of();
			return;
		}
		List<ItemLabel> out = new ArrayList<>(Math.min(48, entities.size()));
		double rangeSq = range * range;
		boolean count = itemCount.get();
		for (ItemEntity e : entities) {
			ItemStack stack = e.getStack();
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			Vec3d pos = LunaCompat.getPos(e);
			if (pos == null || pos.squaredDistanceTo(eye) > rangeSq) {
				continue;
			}
			String text = stack.getName().getString();
			if (count && stack.getCount() > 1) {
				text += " ×" + stack.getCount();
			}
			ItemLabel l = new ItemLabel(e, text);
			l.visible = canSeeCached(e);
			out.add(l);
			if (out.size() >= 48) {
				break;
			}
		}
		itemLabels = out;
	}

	private record Projected(double sx, double sy, double depth, String text) {
	}

	// 49-47차(사용자: "미리보기 필요 없는 것들은 없애도 돼"): 이름표는 월드 안에 그리는 것이라
	// 작은 미리보기 칸에 담으면 실제와 전혀 달라 보인다 - 미리보기 없음(기본값 그대로).

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		if (isPreview()) {
			// 49-88차(8-12): 아이템 이름표를 껐으면 미리보기도 비고, 개수 표시도 설정대로
			if (items.get()) {
				drawLabel(context, previewCenterX(), previewCenterY() + 6, 1.0f,
					itemCount.get() ? "다이아몬드 ×3" : "다이아몬드", itemColor.getArgb());
			}
			return;
		}
		if (!items.get() || client.world == null || client.player == null
				|| client.currentScreen != null) {
			return;
		}
		List<ItemLabel> labels = itemLabels;
		if (labels.isEmpty()) {
			return;
		}
		LunaProjection proj = LunaProjection.capture(client);
		if (proj == null) {
			return;
		}
		List<Projected> visible = new ArrayList<>(labels.size());
		double[] out = new double[3];
		for (ItemLabel l : labels) {
			if (!l.visible || !l.entity.isAlive()) {
				continue;
			}
			Vec3d pos = LunaCompat.getPos(l.entity);
			if (pos == null || !proj.project(pos.x, pos.y + 0.55, pos.z, out)) {
				continue;
			}
			if (out[0] < -80 || out[0] > proj.sw + 80 || out[1] < -80 || out[1] > proj.sh + 80) {
				continue;
			}
			visible.add(new Projected(out[0], out[1], out[2], l.text));
		}
		// 먼 것부터 그려서 가까운 라벨이 위에 오게
		visible.sort((a, b) -> Double.compare(b.depth, a.depth));
		int color = itemColor.getArgb();
		for (Projected p : visible) {
			// 바닐라 이름표와 같은 월드 고정 크기: 글자 1px = 0.025블록 → 화면 배율 = 0.025·sh / (2·tan(fov/2)·거리)
			float scale = (float) (0.025 * proj.sh / (2.0 * proj.tanHalf * Math.max(0.5, p.depth)));
			scale = Math.max(0.3f, Math.min(2.0f, scale));
			drawLabel(context, (float) p.sx, (float) p.sy, scale, p.text, color);
		}
	}

	/** (cx, baseY)를 아래 중앙으로 하는 이름표(배경 + 글자)를 scale 배율로. */
	private void drawLabel(DrawContext context, float cx, float baseY, float scale, String text, int color) {
		int tw = LunaCompat.getTextWidth(client.textRenderer, text);
		boolean xf = scale != 1f && LunaCompat.guiTransformSupported(context);
		if (xf) {
			LunaCompat.guiPush(context);
			LunaCompat.guiTranslate(context, cx, baseY);
			LunaCompat.guiScale(context, scale, scale);
			cx = 0;
			baseY = 0;
		}
		int x = Math.round(cx) - tw / 2;
		int y = Math.round(baseY) - 12;
		context.fill(x - 3, y - 2, x + tw + 3, y + 10, 0x66000000);
		LunaCompat.drawHudText(context, client.textRenderer, text, x, y, color);
		if (xf) {
			LunaCompat.guiPop(context);
		}
	}
}

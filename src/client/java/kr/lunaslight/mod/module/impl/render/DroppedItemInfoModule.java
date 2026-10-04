package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.util.EntityPos;
import kr.lunaslight.mod.util.WindowAccess;
import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaProjection;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 49-66차(4-51): <b>버려진 아이템</b> - 땅에 떨어진 아이템 위에 <b>아이콘 + 이름 + 개수</b>를 띄운다.
 *
 * <p>사용자 요청 4-51: "버려진 아이템 정보 - 개수·종류·아이콘·인챈트·이름 + <b>같은 아이템이 많이
 * 겹치면 한 세트로 묶어 표시</b>".
 *
 * <p><b>묶기가 이 기능의 핵심이다.</b> 상자를 통째로 쏟으면 아이템 엔티티가 수십 개 생기는데, 하나하나에
 * 이름표를 달면 글자가 겹쳐서 아무것도 못 읽는다. 그래서 <b>같은 종류 + 같은 자리(2블록 칸)</b>를 하나로
 * 합쳐 개수만 더한다 - "돌 ×384" 한 줄이 "돌 ×64" 여섯 줄보다 훨씬 쓸모 있다.
 *
 * <p><b>인챈트가 붙은 것은 안 묶는다.</b> 인챈트된 다이아몬드 검 두 자루는 "같은 아이템"이 아니다 -
 * 묶어 버리면 정작 중요한 한 자루를 못 알아본다. 그래서 인챈트가 있으면 따로 세고 이름 옆에 ✦를 붙인다.
 *
 * <p><b>왜 HUD에서 그리나</b>: Fabric 월드 렌더 이벤트가 1.21.9+에서 사라져서(49-19차), 월드 쪽에
 * 얹으면 사용자의 주 버전(1.21.11)에서 안 나온다. 2D 투영으로 화면 좌표를 직접 구해 HUD에 그린다.
 *
 * <p><b>성능</b>: 아이템을 모으고 묶는 일은 <b>틱마다 한 번</b>(초 20회)만 하고, 프레임에서는 만들어 둔
 * 목록을 투영해 그리기만 한다. 줄 수도 {@link #MAX_LABELS}개로 자른다.
 */
public class DroppedItemInfoModule extends Module {

	/*
	 * 49-87차(8-15, 사용자: "묶인 아이템이 한 세트로만 보여야 하고, 정보가 이름표로 뜨는 게 아니라 엔티티 정보처럼
	 * 위쪽에 떠야"): 두 가지를 바꿨다.
	 *   · 묶기는 설정이 아니라 항상이다(겹친 것 묶기 스위치 삭제). 같은 종류·같은 2블록 칸은 언제나 한 세트.
	 *   · 아이템마다 이름표를 띄우지 않는다. 대신 조준선에 가장 가까운 더미 하나를 엔티티 정보와 같은 틀의 패널로
	 *     화면 위쪽(기본 위 가운데, 블록 정보 아래)에 보여 준다 - 아이콘 · 이름 · ×개수 · ✦(인챈트).
	 *   조준은 바닐라 crosshairTarget이 아이템 엔티티를 안 잡으므로(ItemEntity.canHit=false) 화면 투영 좌표가
	 *   화면 가운데에서 AIM_RADIUS 안이면 "보고 있다"로 친다.
	 */

	private static final int MAX_LABELS = 40;
	private static final int ICON = 16;
	/** 조준선(화면 가운데)에서 이 픽셀 안에 투영된 더미만 "보고 있는 것"으로 친다. */
	private static final int AIM_RADIUS = 48;

	// 49-122차(사용자: "사거리 = 플레이어 사거리"): [거리] 설정을 없애고 플레이어가 닿는 거리(reachDistance)로 고정.
	// 49-116차: 상단중앙 → 하단중앙(핫바 위). 49-122차(사용자: "위치 조금만 위로"): 26 → 44.
	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "패널이 뜨는 자리입니다.", HudPosition.of(HudPosition.Anchor.BOTTOM_CENTER, 0, 44)));
	private final ColorSetting textColor = register(new ColorSetting(
			"text_color", "글자 색", "글자의 색입니다.", 0xFFECECF1));

	/** 한 줄 = 화면에 그릴 이름표 하나. */
	private record Label(double x, double y, double z, ItemStack icon, String text) {
	}

	private volatile List<Label> labels = new ArrayList<>();

	public DroppedItemInfoModule() {
		super("dropped_item_info", "버려진 아이템", ModuleCategory.INVENTORY, "떨어진 아이템의 이름/개수");
		// 49-156차(사용자: "배경 설정 따로, 배경 색도 조절"): 이 기능도 [배경] [배경 색] [윤곽선] [배경 모양] 설정을 갖는다.
		enableHudStyle(0xD20E1013, true, 0x14FFFFFF, kr.lunaslight.mod.module.Module.HudShape.FOLLOW);
	}

	@Override
	protected void onDisable() {
		labels = new ArrayList<>();
	}

	@Override
	public void onTick() {
		labels = collect();
	}

	private List<Label> collect() {
		List<Label> out = new ArrayList<>();
		if (client == null || client.world == null || client.player == null) {
			return out;
		}
		// 49-122차(사용자: "사거리 = 플레이어 상호작용 사거리"): 24블록 설정을 없애고 플레이어가 닿는 거리 안만 본다.
		double reach = reachDistance();
		double max = reach + 1.0;   // 탐색 상자는 살짝 넉넉히(정확한 사거리 컷은 아래 눈-거리로 한다)
		net.minecraft.util.math.Vec3d eye = LunaCompat.getEyePos(client.player);
		double reachSq = reach * reach;
		try {
			Box searchBox = new Box(
					EntityPos.x(client.player) - max, EntityPos.y(client.player) - max, EntityPos.z(client.player) - max,
					EntityPos.x(client.player) + max, EntityPos.y(client.player) + max, EntityPos.z(client.player) + max);
			List<ItemEntity> items = LunaCompat.getEntitiesByClass(client.world, ItemEntity.class, searchBox);
			if (items == null || items.isEmpty()) {
				return out;
			}
			// 묶기: (아이템 종류 + 2블록 칸 + 인챈트 여부)가 같으면 한 줄로
			Map<String, double[]> sums = new HashMap<>();        // 키 → {개수, x합, y합, z합, 묶인 수}
			Map<String, ItemStack> icons = new HashMap<>();
			for (ItemEntity e : items) {
				ItemStack stack = e.getStack();
				if (stack == null || stack.isEmpty()) {
					continue;
				}
				// 눈에서 아이템까지가 사거리 밖이면 건너뛴다(플레이어가 닿는 거리 안만).
				if (eye != null && eye.squaredDistanceTo(EntityPos.x(e), EntityPos.y(e) + 0.25, EntityPos.z(e)) > reachSq) {
					continue;
				}
				// 49-198차(사용자: "벽 뚫고 보이면 안 되지 - ESP잖아"): 블록에 가려 안 보이는 아이템은 표시하지 않는다.
				if (!NametagVisibilityModule.canSeeCached(e)) {
					continue;
				}
				String key = groupKey(e, stack);
				double[] acc = sums.computeIfAbsent(key, k -> new double[5]);
				acc[0] += stack.getCount();
				acc[1] += EntityPos.x(e);
				acc[2] += EntityPos.y(e);
				acc[3] += EntityPos.z(e);
				acc[4] += 1;
				icons.putIfAbsent(key, stack);
			}
			for (Map.Entry<String, double[]> en : sums.entrySet()) {
				if (out.size() >= MAX_LABELS) {
					break;
				}
				double[] acc = en.getValue();
				int n = (int) acc[4];
				ItemStack icon = icons.get(en.getKey());
				out.add(new Label(acc[1] / n, acc[2] / n, acc[3] / n, icon, textFor(icon, (int) acc[0])));
			}
		} catch (Throwable ignored) {
			// 월드가 바뀌는 중 등 - 이번 틱은 건너뛴다(직전 목록 유지)
		}
		return out;
	}

	/** 묶음 키 - 같은 종류 + 같은 2블록 칸 + 인챈트 여부. 49-87차: 항상 묶는다. */
	private String groupKey(ItemEntity e, ItemStack stack) {
		boolean enchanted = hasEnchant(stack);
		long cell = ((long) Math.floor(EntityPos.x(e) / 2) * 73856093)
				^ ((long) Math.floor(EntityPos.y(e) / 2) * 19349663)
				^ ((long) Math.floor(EntityPos.z(e) / 2) * 83492791);
		return System.identityHashCode(stack.getItem()) + ":" + cell + (enchanted ? ":e" : "");
	}

	/**
	 * 인챈트(반짝임)가 붙었는지. <b>직접 부르면 안 된다</b> - 이름이 시대마다 갈린다(실측: 1.15.2에는
	 * {@code hasGlint()}가 없어 컴파일에서 막혔다). 후보 이름을 순서대로 찾아본다(결과는 클래스별로 캐시).
	 */
	private static final String[] GLINT_METHODS = {"hasGlint", "hasEnchantmentGlint", "hasEnchantments"};

	private static boolean hasEnchant(ItemStack stack) {
		if (stack == null) {
			return false;
		}
		for (String name : GLINT_METHODS) {
			try {
				java.lang.reflect.Method m = LunaCompat.findMethod(stack.getClass(), name);
				if (m != null) {
					Object v = m.invoke(stack);
					if (v instanceof Boolean b) {
						return b;
					}
				}
			} catch (Throwable ignored) {
			}
		}
		return false;
	}

	private String textFor(ItemStack stack, int count) {
		String name;
		try {
			name = stack.getName().getString();
		} catch (Throwable ignored) {
			name = "?";
		}
		String mark = hasEnchant(stack) ? "§b✦§r " : "";
		return count > 1 ? mark + name + " §7×" + count : mark + name;
	}

	@Override
	public boolean hasPreview() {
		return true;
	}

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		if (isPreview()) {
			ItemStack sample = new ItemStack(net.minecraft.item.Items.DIAMOND, 1);
			drawPanel(context, sample, "다이아몬드 §7×3", true);
			return;
		}
		if (client.world == null || client.currentScreen != null) {
			return;
		}
		List<Label> list = labels;
		if (list.isEmpty()) {
			return;
		}
		LunaProjection proj = LunaProjection.capture(client);
		if (proj == null) {
			return;
		}
		// 조준선에 가장 가까운 더미 하나
		int cx = WindowAccess.of(client).getScaledWidth() / 2;
		int cy = WindowAccess.of(client).getScaledHeight() / 2;
		Label best = null;
		double bestD = AIM_RADIUS * (double) AIM_RADIUS;
		double[] out = new double[3];
		for (Label label : list) {
			if (!proj.project(label.x(), label.y() + 0.25, label.z(), out)) {
				continue;
			}
			double dx = out[0] - cx, dy = out[1] - cy;
			double d = dx * dx + dy * dy;
			// 49-122차(사용자: "사거리 내에 있어야 보이고 벽 못뚫게"): 조준선 근처 + 벽에 안 가린 것만.
			if (d < bestD && hasLineOfSight(label.x(), label.y(), label.z())) {
				bestD = d;
				best = label;
			}
		}
		if (best != null) {
			drawPanel(context, best.icon(), best.text(), false);
		}
	}

	/** 플레이어 상호작용 사거리(대략): 크리에이티브는 더 멀리 닿는다. 못 읽으면 4.5. */
	private double reachDistance() {
		try {
			Object v = LunaCompat.callNoArg(client.player, "isCreative");
			if (v instanceof Boolean b && b) {
				return 6.0;
			}
		} catch (Throwable ignored) {
		}
		return 4.5;
	}

	/**
	 * 49-122차(사용자: "벽 못뚫게"): 내 눈 → 아이템 사이에 막는 블록이 있으면 안 보여 준다.
	 * 눈에서 아이템으로 레이캐스트해서 아이템보다 <b>앞에</b> 블록을 맞으면 벽 뒤 = 가림.
	 * 못 하면(구버전 등) 그냥 보여 준다(안전).
	 */
	private boolean hasLineOfSight(double ix, double iy, double iz) {
		try {
			net.minecraft.util.math.Vec3d eye = LunaCompat.getEyePos(client.player);
			if (eye == null || client.world == null) {
				return true;
			}
			// 49-124차: RaycastContext는 1.15.2~1.16.x에 없어 빌드가 깨졌다. 눈 → 아이템 직선을 직접
			// 훑으며(0.4블록 간격) 막는 블록이 있나 본다 - 모든 버전에서 컴파일되는 방식.
			double dx = ix - eye.x, dy = (iy + 0.25) - eye.y, dz = iz - eye.z;
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (dist < 1.0e-4) {
				return true;
			}
			int steps = (int) Math.ceil(dist / 0.4);
			double stop = dist - 0.45;   // 아이템이 놓인 칸(바닥 등)은 벽으로 안 침
			int lx = Integer.MIN_VALUE, ly = 0, lz = 0;
			for (int i = 1; i <= steps; i++) {
				double t = dist * i / steps;
				if (t >= stop) {
					break;
				}
				double f = t / dist;
				int bx = (int) Math.floor(eye.x + dx * f);
				int by = (int) Math.floor(eye.y + dy * f);
				int bz = (int) Math.floor(eye.z + dz * f);
				if (bx == lx && by == ly && bz == lz) {
					continue;
				}
				lx = bx;
				ly = by;
				lz = bz;
				if (LunaCompat.isOpaqueFullCube(client.world, new net.minecraft.util.math.BlockPos(bx, by, bz))) {
					return false;   // 벽 뒤
				}
			}
			return true;
		} catch (Throwable ignored) {
			return true;
		}
	}

	/**
	 * 엔티티 정보와 같은 틀: 어두운 패널 + 얇은 테두리, 안에 아이콘 · 이름 · ×개수.
	 * 49-122차(사용자: "크기 전으로 백 - 너무 작아 글씨 안 보임"): 49-116의 0.85배 축소를 되돌려 원래 크기로.
	 */
	private void drawPanel(DrawContext context, ItemStack icon, String text, boolean preview) {
		int pad = 6;
		int tw = LunaCompat.getTextWidth(client.textRenderer, text);
		int panelW = pad + ICON + 4 + tw + pad;
		int panelH = pad + ICON + pad - 2;
		int x, y;
		if (preview && isPreviewBoxed()) {
			// 설정 패널의 작은 미리보기 칸: 칸 가운데에.
			x = previewCenterX() - panelW / 2;
			y = previewCenterY() - panelH / 2;
		} else {
			// 49-122차(사용자: "박스는 움직이는데 보이는 게 저기 고정된다"): 실제 HUD 위치로 그린다.
			// HUD 편집기 샘플(preview지만 boxed 아님)도 이 길로 와야 편집기에서 끌면 표시도 따라온다
			// (예전엔 preview면 무조건 화면 가운데로 그려서, 편집기에선 박스만 움직이고 패널은 가운데 고정됐다).
			x = position.get().resolveX(WindowAccess.of(client).getScaledWidth(), panelW);
			y = position.get().resolveY(WindowAccess.of(client).getScaledHeight(), panelH);
		}
		drawHudBox(context, x, y, panelW, panelH);   // 49-156차: 이 기능의 [배경] 설정대로
		context.drawItem(icon, x + pad, y + (panelH - ICON) / 2);
		LunaCompat.drawHudText(context, client.textRenderer, text, x + pad + ICON + 4,
			LunaDraw.textY(y, panelH), textColor.getArgb());
	}
}

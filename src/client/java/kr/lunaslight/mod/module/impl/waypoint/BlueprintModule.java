package kr.lunaslight.mod.module.impl.waypoint;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.module.setting.KeybindSetting;
import kr.lunaslight.mod.module.setting.StringSetting;
import kr.lunaslight.mod.util.Blueprint;
import kr.lunaslight.mod.util.BlueprintTex;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaProjection;
import kr.lunaslight.mod.util.MeasureHook;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 49-253차: 설계도(사용자: "라이트매티카 같은 걸 우리 쪽에서 편하게").
 *
 * <ol>
 *   <li>도구(기본 나무 삽, 49-291차)를 들고 좌클릭 = 지점 1, 우클릭 = 지점 2. 클릭은 MeasureHook이 먹어서 블록이 안 깨진다.</li>
 *   <li>O = 설계도 창(gui.BlueprintScreen): 두 지점 좌표, 기준 위치(기본 지점 1), 이름 + [빌드](저장), 저장된 목록에서 불러오기,
 *       불러온 설계도의 층 범위, 필요한 블록 목록.</li>
 *   <li>불러오면 내가 서 있는 칸이 기준 위치가 된다. 아직 안 놓은 칸은 반투명 홀로그램(하늘색), 다른 블록이 놓였으면 빨강,
 *       블록은 맞는데 방향이 틀리면 주황. 맞게 놓으면 사라진다(util.Blueprint#check).</li>
 *   <li>층 보기: 설계도 아래부터 1층. 최대 층은 PageUp/PageDown, 최소 층은 창이나 따로 지정한 키로.</li>
 *   <li>홀로그램을 보고 가운데 클릭 = 그 블록을 인벤토리에서 손으로(없으면 알림).</li>
 * </ol>
 *
 * <p>홀로그램은 다른 월드 표시(아이템 찾기, 거리 재기)처럼 HUD 투영으로 그린다 - 월드 그리기 훅이 없는 1.21.11·26.x에서도 같다.
 * 그래서 벽 너머로도 보인다(층 범위로 줄여 보면 된다). 칸 상태는 매 틱 일부씩 돌아가며 확인하고, 내 주변 6칸은 매 틱 다시 본다.
 */
public class BlueprintModule extends Module implements MeasureHook.Handler {

	public static BlueprintModule instance;

	private final StringSetting tool = register(new StringSetting(
			"tool", "도구", "지점을 찍을 아이템입니다. 좌클릭 = 지점 1, 우클릭 = 지점 2. id(wooden_shovel)나 이름 아무거나 됩니다.", "wooden_shovel"));
	/** 49-291차(사용자: "설계도 도구 삽으로 바꿔줘"): 예전 기본값(나무 도끼)을 나무 삽으로 한 번 옮겼는지(숨김, 저장용). */
	private final kr.lunaslight.mod.module.setting.BooleanSetting toolShovel = register(
			new kr.lunaslight.mod.module.setting.BooleanSetting("tool_shovel_v1", "삽으로 옮김", "", false));
	private final KeybindSetting openKey = register(new KeybindSetting(
			"open_key", "설계도 창 키", "설계도 창을 엽니다.", 79));   // O
	private final KeybindSetting layerUp = register(new KeybindSetting(
			"layer_up", "최대 층 올리기", "불러온 설계도에서 보이는 맨 위 층을 한 칸 올립니다.", 266));   // Page Up
	private final KeybindSetting layerDown = register(new KeybindSetting(
			"layer_down", "최대 층 내리기", "보이는 맨 위 층을 한 칸 내립니다.", 267));   // Page Down
	private final KeybindSetting minUp = register(new KeybindSetting(
			"min_up", "최소 층 올리기", "보이는 맨 아래 층을 한 칸 올립니다.", -1));
	private final KeybindSetting minDown = register(new KeybindSetting(
			"min_down", "최소 층 내리기", "보이는 맨 아래 층을 한 칸 내립니다.", -1));
	private final BooleanSetting pickMiddle = register(new BooleanSetting(
			"pick_middle", "가운데 클릭으로 가져오기", "홀로그램 블록을 보고 가운데 클릭하면 그 블록을 인벤토리에서 손으로 가져옵니다.", true));
	private final IntSetting range = register(new IntSetting(
			"range", "보이는 거리", "홀로그램을 그리는 거리입니다.", 48, 32, 128, 4).unit("블록"));
	// 49-258차(사용자: "홀로그램이 너무 투명해서 잘 안 보여"): 기본 50% → 80%. 저장된 예전 값이 남지 않게 설정 id를 새로.
	private final IntSetting alpha = register(new IntSetting(
			"holo_alpha", "홀로그램 진하기", "홀로그램 블록 그림의 진하기입니다.", 80, 20, 100, 5).unit("%"));
	// 49-266차(사용자: "그냥 블록 자체를 홀로그램으로 못 띄워?"): 월드 안에 진짜 블록 모델로 그린다(리터메티카 방식).
	private final BooleanSetting worldRender = register(new BooleanSetting(
			"world_render", "진짜 블록 모양으로", "홀로그램을 월드 안에 실제 블록 모델로 그립니다. 끄면 화면 위에 그리는 예전 방식입니다.", true));
	private final BooleanSetting autoPick = register(new BooleanSetting(
			"auto_pick", "놓을 때 블록 바꾸기", "블록을 들고 홀로그램 자리에 우클릭하면 그 자리에 맞는 블록을 인벤토리에서 손으로 가져와 놓습니다.", true));
	// 49-268차(사용자: "홀로그램 색을 그 설정한 푸른색을 껴 줘야 구분이 잘 가지"): 블록 그림에 '놓을 곳 색'을 이만큼 섞는다.
	private final IntSetting tintMix = register(new IntSetting(
			"holo_tint", "색 입히기", "홀로그램 블록 그림에 '놓을 곳 색'을 섞는 정도입니다. 0이면 블록 색 그대로입니다.", 0, 0, 100, 5).unit("%"));
	// 49-281차(사용자: "파란색 테두리 없애고 그 색 자체를 리터매티카랑 똑같이"): 기본 색을 리터매티카 기본값 그대로
	// (Colors: schematicOverlayColorMissing #2C33B3E6, WrongBlock #4CFF3333, WrongState #4CFF9010 - 알파 포함 ARGB).
	// 칸마다 이 색의 반투명 면 + 같은 색 1px 윤곽선을 덮는다(리터매티카 오버레이). 예전 기본값으로 저장돼 있으면 새 기본값으로 본다.
	private final ColorSetting missingColor = register(new ColorSetting(
			"missing_color", "놓을 곳 색", "아직 안 놓은 칸에 덮는 색입니다. 기본은 리터매티카와 같은 하늘색입니다.", LITE_MISSING));
	private final ColorSetting wrongColor = register(new ColorSetting(
			"wrong_color", "틀린 블록 색", "다른 블록이 놓인 칸에 덮는 색입니다.", LITE_WRONG));
	private final ColorSetting rotatedColor = register(new ColorSetting(
			"rotated_color", "방향 틀림 색", "블록은 맞는데 방향이 다른 칸에 덮는 색입니다.", LITE_STATE));

	public static final int LITE_MISSING = 0x2C33B3E6, LITE_WRONG = 0x4CFF3333, LITE_STATE = 0x4CFF9010;

	/** 49-281차: 예전 기본값(불투명 하늘/빨강/주황)으로 저장된 값은 리터매티카 기본값으로. */
	private static int liteColor(ColorSetting s, int oldDefault, int def) {
		int v = s.getArgb();
		return v == oldDefault ? def : v;
	}

	private int missArgb() {
		return liteColor(missingColor, 0xFF7FD4FF, LITE_MISSING);
	}

	private int wrongArgb() {
		return liteColor(wrongColor, 0xFFFF5A50, LITE_WRONG);
	}

	private int stateArgb() {
		return liteColor(rotatedColor, 0xFFFFA040, LITE_STATE);
	}

	/** 윤곽선 = 같은 색 불투명. */
	private static int lineOf(int argb) {
		return 0xFF000000 | (argb & 0xFFFFFF);
	}

	/** 49-281차: 색 입히기 - 예전 기본값 55는 0(리터매티카처럼 블록 색 그대로)으로 본다. */
	private int tintPct() {
		int v = tintMix.get();
		return v == 55 ? 0 : v;
	}

	// ---- 선택 ----
	public BlockPos pos1, pos2;
	/** 기준 위치: 0 = 지점 1, 1 = 지점 2, 2 = 직접(refCustom). */
	public int refMode;
	public BlockPos refCustom;

	// ---- 불러온 설계도 ----
	public Blueprint bp;
	/** 설계도 (0,0,0) 칸의 월드 좌표. */
	public BlockPos origin;
	public int layerMin = 1, layerMax = 1;
	private int[] sx = new int[0], sy = new int[0], sz = new int[0], scell = new int[0];
	/** 칸 인덱스(bp.cells) → 상태(Blueprint.MISSING 등). 공기 칸은 OK. */
	private byte[] status = new byte[0];
	private int cursor;
	// 다 훑은 뒤의 집계
	public int totalLeft, layerLeft, wrongCount, rotatedCount;
	public final Map<Integer, Integer> leftByPalette = new HashMap<>();
	public final Map<Integer, Integer> leftByPaletteLayer = new HashMap<>();
	private boolean scannedOnce;

	private boolean openHeld, upHeld, downHeld, minUpHeld, minDownHeld;

	public BlueprintModule() {
		super("blueprint", "설계도", ModuleCategory.FEATURE, "나무 삽으로 구역 찍기 | O로 저장, 불러오기 | 홀로그램 보며 짓기");
		defaultEnabled(true);
		instance = this;
		toolShovel.hidden();
		MeasureHook.add(this);
	}

	// ==================== 클릭(지점 찍기) ====================

	@Override
	public boolean active() {
		try {
			return isEnabled() && client != null && client.player != null && client.currentScreen == null && holdingTool();
		} catch (Throwable t) {
			return false;
		}
	}

	public boolean holdingTool() {
		try {
			ItemStack main = client.player.getMainHandStack();
			if (main == null || main.isEmpty()) {
				return false;
			}
			String want = tool.get() == null ? "" : tool.get().trim().toLowerCase();
			if (want.isEmpty()) {
				want = "wooden_shovel";
			}
			Object id = LunaCompat.getItemId(main.getItem());
			if (id != null) {
				String full = id.toString().toLowerCase();
				String path = full.substring(full.indexOf(':') + 1);
				if (want.equals(full) || want.equals(path)) {
					return true;
				}
			}
			String shown = main.getName().getString();
			return shown != null && want.equals(shown.trim().toLowerCase());
		} catch (Throwable t) {
			return false;
		}
	}

	@Override
	public boolean onAttack() {
		BlockPos p = LunaCompat.targetedBlock(client);
		if (p == null) {
			LunaCompat.sendActionBar(client, "§7블록을 바라보고 눌러 주세요");
			return true;
		}
		pos1 = p;
		saveState();
		LunaCompat.sendActionBar(client, "§b지점 1 §f" + fmt(p) + sizeText());
		return true;
	}

	@Override
	public boolean onUse() {
		BlockPos p = LunaCompat.targetedBlock(client);
		if (p == null) {
			LunaCompat.sendActionBar(client, "§7블록을 바라보고 눌러 주세요");
			return true;
		}
		pos2 = p;
		saveState();
		LunaCompat.sendActionBar(client, "§d지점 2 §f" + fmt(p) + sizeText());
		return true;
	}

	@Override
	public boolean onPick() {
		if (!isEnabled() || !pickMiddle.get() || bp == null || client == null || client.player == null || client.currentScreen != null) {
			return false;
		}
		int ci = lookedCell();
		if (ci < 0) {
			return false;
		}
		pickCell(ci);
		return true;
	}

	/**
	 * 49-258차(사용자: "우클릭해서 설치했을 때 블록도 알아서 안 바꿔 주고"): 블록(또는 빈손)을 들고 우클릭하면, 놓일 자리(바라보는 블록 +
	 * 바라보는 면 쪽 한 칸)가 아직 안 맞은 설계도 칸일 때 그 칸의 블록을 먼저 손에 쥔다(핫바에서 고르거나 가방에서 맞바꿈). 바닐라 우클릭은
	 * 그대로 이어져 바뀐 블록이 놓인다. 음식이나 도구를 들고 있으면 건드리지 않는다.
	 */
	@Override
	public void beforeUse() {
		if (!isEnabled() || !autoPick.get() || bp == null || client == null || client.player == null || client.currentScreen != null
				|| holdingTool()) {
			return;
		}
		ItemStack main = client.player.getMainHandStack();
		if (main != null && !main.isEmpty() && !(main.getItem() instanceof net.minecraft.item.BlockItem)) {
			return;
		}
		int[] pp = LunaCompat.targetedPlacePos(client);
		if (pp == null) {
			return;
		}
		int x = pp[0] - origin.getX(), y = pp[1] - origin.getY(), z = pp[2] - origin.getZ();
		if (x < 0 || y < 0 || z < 0 || x >= bp.w || y >= bp.h || z >= bp.l || !inLayer(y)) {
			return;
		}
		int ci = bp.index(x, y, z);
		if (bp.cells[ci] == Blueprint.AIR || status[ci] == Blueprint.OK) {
			return;
		}
		Blueprint.Entry e = bp.entry(bp.cells[ci]);
		if (e == null || e.item.isEmpty()) {
			return;
		}
		Object hid = main == null || main.isEmpty() ? null : LunaCompat.getItemId(main.getItem());
		if (hid != null && e.item.equals(hid.toString())) {
			return;   // 이미 맞는 블록
		}
		pickCell(ci);
	}

	/** 그 칸의 블록을 인벤토리에서 손으로. */
	public void pickCell(int ci) {
		Blueprint.Entry e = bp.entry(bp.cells[ci]);
		if (e == null) {
			return;
		}
		if (e.item.isEmpty()) {
			LunaCompat.sendActionBar(client, "§7" + e.name + "은(는) 아이템이 없는 블록입니다");
			return;
		}
		int r = -1;
		try {
			Class<?> c = Class.forName("kr.lunaslight.mod.util.BlueprintPick");
			Object v = c.getMethod("pick", net.minecraft.client.MinecraftClient.class, String.class).invoke(null, client, e.item);
			r = v instanceof Integer i ? i : -1;
		} catch (Throwable t) {
			LunaCompat.warnOnce("blueprint:pick", t);
		}
		if (r < 0) {
			LunaCompat.sendActionBar(client, "§c인벤토리에 " + e.name + "이(가) 없습니다");
			LunaCompat.playUiSound(client, "BLOCK_NOTE_BLOCK_BASS", 0.8f);
		} else if (r > 0) {
			LunaCompat.sendActionBar(client, "§a" + e.name + " 손에 들었습니다");
		}
	}

	/** 창에 보여 줄 도구 이름(설정 글자 그대로, 비었으면 나무 삽). */
	public String toolLabel() {
		String t = tool.get() == null ? "" : tool.get().trim();
		if (t.isEmpty() || t.equalsIgnoreCase("wooden_shovel") || t.equalsIgnoreCase("minecraft:wooden_shovel")) {
			return "나무 삽";
		}
		return t.equalsIgnoreCase("wooden_axe") || t.equalsIgnoreCase("minecraft:wooden_axe") ? "나무 도끼" : t;
	}

	public BlockPos refPos() {
		if (refMode == 1 && pos2 != null) {
			return pos2;
		}
		if (refMode == 2 && refCustom != null) {
			return refCustom;
		}
		return pos1 != null ? pos1 : pos2;
	}

	public String sizeText() {
		if (pos1 == null || pos2 == null) {
			return "";
		}
		return " §7| " + spanX() + " × " + spanY() + " × " + spanZ();
	}

	public int spanX() {
		return Math.abs(pos1.getX() - pos2.getX()) + 1;
	}

	public int spanY() {
		return Math.abs(pos1.getY() - pos2.getY()) + 1;
	}

	public int spanZ() {
		return Math.abs(pos1.getZ() - pos2.getZ()) + 1;
	}

	public static String fmt(BlockPos p) {
		return p == null ? "-" : p.getX() + ", " + p.getY() + ", " + p.getZ();
	}

	public BlockPos playerBlock() {
		Vec3d v = LunaCompat.getPos(client.player);
		return new BlockPos((int) Math.floor(v.x), (int) Math.floor(v.y), (int) Math.floor(v.z));
	}

	// ==================== 저장 / 불러오기 ====================

	/** [빌드]: 두 지점 사이를 설계도 파일로. 결과 메시지를 돌려준다. */
	public String build(String rawName) {
		if (client == null || client.world == null) {
			return "월드에 들어가 있어야 합니다";
		}
		if (pos1 == null || pos2 == null) {
			return "도구로 지점 1(좌클릭)과 지점 2(우클릭)를 먼저 찍어 주세요";
		}
		long vol = (long) spanX() * spanY() * spanZ();
		if (vol > Blueprint.MAX_VOLUME) {
			return "구역이 너무 큽니다(" + vol + "칸, 최대 " + Blueprint.MAX_VOLUME + "칸)";
		}
		String name = Blueprint.cleanName(rawName);
		if (name == null) {
			name = Blueprint.nextAutoName();
		}
		try {
			Blueprint b = Blueprint.capture(client, pos1, pos2, refPos(), name);
			b.save();
			return "§a저장했습니다: " + name + " (블록 " + b.solid + "개)";
		} catch (Throwable t) {
			LunaCompat.warnOnce("blueprint:save", t);
			return "§c저장하지 못했습니다: " + t.getMessage();
		}
	}

	/** 불러오기: 내가 서 있는 칸이 기준 위치가 된다. */
	public String load(String name) {
		if (client == null || client.player == null) {
			return "월드에 들어가 있어야 합니다";
		}
		try {
			Blueprint b = Blueprint.load(name);
			BlockPos me = playerBlock();
			xform = "";   // 49-280차: 새로 불러오면 돌리기/뒤집기 없음
			place(b, new BlockPos(me.getX() - b.rx, me.getY() - b.ry, me.getZ() - b.rz));
			return "§a불러왔습니다: " + name;
		} catch (Throwable t) {
			LunaCompat.warnOnce("blueprint:load", t);
			return "§c불러오지 못했습니다: " + t.getMessage();
		}
	}

	/** 불러온 설계도를 지금 서 있는 칸 기준으로 옮긴다. */
	public void moveHere() {
		if (bp == null || client.player == null) {
			return;
		}
		BlockPos me = playerBlock();
		place(bp, new BlockPos(me.getX() - bp.rx, me.getY() - bp.ry, me.getZ() - bp.rz));
	}

	private void place(Blueprint b, BlockPos org) {
		int n = b.solid;
		int[] ax = new int[n], ay = new int[n], az = new int[n], ac = new int[n];
		int k = 0;
		for (int y = 0; y < b.h; y++) {
			for (int z = 0; z < b.l; z++) {
				for (int x = 0; x < b.w; x++) {
					int idx = b.index(x, y, z);
					if (b.cells[idx] != Blueprint.AIR && k < n) {
						ax[k] = x;
						ay[k] = y;
						az[k] = z;
						ac[k] = idx;
						k++;
					}
				}
			}
		}
		sx = ax;
		sy = ay;
		sz = az;
		scell = ac;
		status = new byte[b.cells.length];
		cursor = 0;
		scannedOnce = false;
		bp = b;
		origin = org;
		layerMin = 1;
		layerMax = b.h;
		statusVer++;
		palTex = null;
		palBoxes = null;
		palRot = null;
		leftByPalette.clear();
		leftByPaletteLayer.clear();
		totalLeft = b.solid;
		layerLeft = b.solid;
		saveState();
	}

	public void unload() {
		clearMemory();
		saveState();   // 이 월드의 기록에서 뺀다
	}

	// ==================== 49-280차: 돌리기 / 뒤집기 ====================

	/** 지금 설계도에 적용한 돌리기/뒤집기(Blueprint.composeOps로 줄인 꼴). 다시 들어오면 이대로 다시 적용. */
	private String xform = "";

	public String xform() {
		return xform;
	}

	/**
	 * 불러온 설계도를 돌리거나 뒤집는다(op = Blueprint.ROT_CW / ROT_CCW / MIRROR_X / MIRROR_Z / FLIP_Y). 기준 위치(불러올 때 서 있던 칸)는
	 * 월드에서 그대로 두고 그 둘레로 돈다. 층 범위는 같은 층을 가리키게 맞춘다(위아래 뒤집기면 층 번호도 뒤집힌다).
	 */
	public String transform(char op) {
		if (bp == null || origin == null) {
			return "불러온 설계도가 없습니다";
		}
		if (op == 'H') {
			transform(Blueprint.ROT_CW);
			transform(Blueprint.ROT_CW);
			return "§a반 바퀴(180도) 돌렸습니다";
		}
		Blueprint nb = bp.transformed(op);
		int wx = origin.getX() + bp.rx, wy = origin.getY() + bp.ry, wz = origin.getZ() + bp.rz;
		int lmin = layerMin, lmax = layerMax, hh = bp.h;
		xform = Blueprint.composeOps(xform + op);
		place(nb, new BlockPos(wx - nb.rx, wy - nb.ry, wz - nb.rz));
		if (op == Blueprint.FLIP_Y) {
			setLayers(hh - lmax + 1, hh - lmin + 1);
		} else {
			setLayers(lmin, lmax);
		}
		return switch (op) {
			case Blueprint.ROT_CW -> "§a오른쪽으로 90도 돌렸습니다";
			case Blueprint.ROT_CCW -> "§a왼쪽으로 90도 돌렸습니다";
			case Blueprint.MIRROR_X -> "§a좌우(동서)로 뒤집었습니다";
			case Blueprint.MIRROR_Z -> "§a앞뒤(남북)로 뒤집었습니다";
			default -> "§a위아래로 뒤집었습니다";
		};
	}

	private void clearMemory() {
		xform = "";
		bp = null;
		origin = null;
		status = new byte[0];
		sx = sy = sz = scell = new int[0];
	}

	// ==================== 49-260차: 나갔다 와도 그대로 ====================
	// 사용자: "나갔다 와도 설계도는 계속 저장돼 있게, 상황이". 월드(서버 주소 또는 싱글 저장 폴더 + 차원, 아이템 찾기와 같은 키)마다
	// 불러온 설계도 이름, 놓인 자리(origin), 층 범위, 지점 1, 2, 기준 위치를 config/lunaslight/blueprints/loaded.state에 적어 두고,
	// 그 월드(차원)에 다시 들어오면 자동으로 다시 불러온다. 칸 상태(맞음/틀림)는 들어오면 다시 확인하니 따로 안 적는다.

	private static final String STATE_FILE = "loaded.state";
	private String stateKey;
	private Object stateWorld;
	private boolean restoring;

	private static java.nio.file.Path statePath() {
		return Blueprint.folder().resolve(STATE_FILE);
	}

	private static com.google.gson.JsonObject readStates() {
		try {
			java.nio.file.Path p = statePath();
			if (java.nio.file.Files.exists(p)) {
				String raw = new String(java.nio.file.Files.readAllBytes(p), java.nio.charset.StandardCharsets.UTF_8);
				com.google.gson.JsonElement el = new com.google.gson.JsonParser().parse(raw);
				if (el != null && el.isJsonObject()) {
					return el.getAsJsonObject();
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("blueprint:stateRead", t);
		}
		return new com.google.gson.JsonObject();
	}

	private static com.google.gson.JsonArray posJson(BlockPos p) {
		com.google.gson.JsonArray a = new com.google.gson.JsonArray();
		a.add(p.getX());
		a.add(p.getY());
		a.add(p.getZ());
		return a;
	}

	private static BlockPos posOf(com.google.gson.JsonObject o, String k) {
		try {
			if (o.has(k) && o.get(k).isJsonArray()) {
				com.google.gson.JsonArray a = o.getAsJsonArray(k);
				return new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	/** 지금 월드의 상태를 파일에 적는다(설계도가 없고 지점도 없으면 그 월드 줄을 지운다). */
	public void saveState() {
		if (restoring || stateKey == null) {
			return;
		}
		try {
			com.google.gson.JsonObject all = readStates();
			if (bp == null && pos1 == null && pos2 == null) {
				all.remove(stateKey);
			} else {
				com.google.gson.JsonObject o = new com.google.gson.JsonObject();
				if (bp != null && origin != null) {
					o.addProperty("name", bp.name);
					o.add("origin", posJson(origin));
					o.addProperty("layerMin", layerMin);
					o.addProperty("layerMax", layerMax);
					if (!xform.isEmpty()) {
						o.addProperty("xform", xform);   // 49-280차
					}
				}
				if (pos1 != null) {
					o.add("pos1", posJson(pos1));
				}
				if (pos2 != null) {
					o.add("pos2", posJson(pos2));
				}
				o.addProperty("refMode", refMode);
				if (refCustom != null) {
					o.add("refCustom", posJson(refCustom));
				}
				o.addProperty("saved", System.currentTimeMillis());
				all.add(stateKey, o);
			}
			java.nio.file.Files.createDirectories(Blueprint.folder());
			java.nio.file.Files.write(statePath(), all.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
		} catch (Throwable t) {
			LunaCompat.warnOnce("blueprint:stateWrite", t);
		}
	}

	/** 월드(차원)가 바뀌었으면 이전 것은 메모리에서만 내리고, 새 월드의 기록이 있으면 다시 불러온다. */
	private void trackWorld() {
		Object w = client == null ? null : client.world;
		if (w == stateWorld) {
			return;
		}
		stateWorld = w;
		String key = null;
		if (w != null) {
			try {
				key = kr.lunaslight.mod.util.ContainerIndex.worldKey(client);
			} catch (Throwable t) {
				key = null;
			}
		}
		if (java.util.Objects.equals(key, stateKey)) {
			return;
		}
		clearMemory();
		pos1 = null;
		pos2 = null;
		refMode = 0;
		refCustom = null;
		stateKey = key;
		if (key == null) {
			return;
		}
		com.google.gson.JsonObject all = readStates();
		if (!all.has(key) || !all.get(key).isJsonObject()) {
			return;
		}
		com.google.gson.JsonObject o = all.getAsJsonObject(key);
		restoring = true;
		try {
			pos1 = posOf(o, "pos1");
			pos2 = posOf(o, "pos2");
			refCustom = posOf(o, "refCustom");
			refMode = o.has("refMode") ? o.get("refMode").getAsInt() : 0;
			BlockPos org = posOf(o, "origin");
			if (o.has("name") && org != null) {
				Blueprint b = Blueprint.load(o.get("name").getAsString());
				// 49-280차: 돌리거나 뒤집어 둔 것도 그대로
				String xf = o.has("xform") ? Blueprint.composeOps(o.get("xform").getAsString()) : "";
				for (char c : xf.toCharArray()) {
					b = b.transformed(c);
				}
				xform = xf;
				place(b, org);
				setLayers(o.has("layerMin") ? o.get("layerMin").getAsInt() : 1, o.has("layerMax") ? o.get("layerMax").getAsInt() : b.h);
				LunaCompat.sendActionBar(client, "§b설계도 §f" + b.name + " §7이어서 불러왔습니다");
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("blueprint:restore", t);   // 파일이 지워졌으면 지점만 살린다
		} finally {
			restoring = false;
		}
	}

	public void setLayers(int min, int max) {
		if (bp == null) {
			return;
		}
		layerMax = Math.max(1, Math.min(bp.h, max));
		layerMin = Math.max(1, Math.min(layerMax, min));
		recount();
		saveState();
	}

	public boolean inLayer(int y) {
		int layer = y + 1;
		return layer >= layerMin && layer <= layerMax;
	}

	public int statusOf(int cellIndex) {
		return cellIndex < 0 || cellIndex >= status.length ? Blueprint.UNKNOWN : status[cellIndex];
	}

	// ==================== 매 틱 ====================

	@Override
	public void onTick() {
		if (!toolShovel.get()) {
			// 49-291차: 예전 기본값(나무 도끼) 그대로면 나무 삽으로. 직접 다른 걸 적어 둔 건 그대로.
			toolShovel.setValue(true);
			String t = tool.get() == null ? "" : tool.get().trim();
			if (t.isEmpty() || t.equalsIgnoreCase("wooden_axe") || t.equalsIgnoreCase("minecraft:wooden_axe")) {
				tool.setValue("wooden_shovel");
			}
			try {
				kr.lunaslight.mod.config.LunaClientConfig.save();
			} catch (Throwable ignored) {
			}
		}
		trackWorld();   // 49-260차
		if (client == null || client.player == null) {
			return;
		}
		boolean noScreen = client.currentScreen == null;
		boolean open = noScreen && openKey.isDown(client);
		if (open && !openHeld) {
			LunaCompat.setScreen(new kr.lunaslight.mod.gui.BlueprintScreen(null));
		}
		openHeld = open;
		if (bp != null) {
			boolean up = noScreen && layerUp.isDown(client);
			boolean down = noScreen && layerDown.isDown(client);
			boolean mUp = noScreen && minUp.isDown(client);
			boolean mDown = noScreen && minDown.isDown(client);
			if (up && !upHeld) {
				setLayers(layerMin, layerMax + 1);
				layerMessage();
			}
			if (down && !downHeld) {
				setLayers(Math.min(layerMin, layerMax - 1), layerMax - 1);
				layerMessage();
			}
			if (mUp && !minUpHeld) {
				setLayers(layerMin + 1, Math.max(layerMax, layerMin + 1));
				layerMessage();
			}
			if (mDown && !minDownHeld) {
				setLayers(layerMin - 1, layerMax);
				layerMessage();
			}
			upHeld = up;
			downHeld = down;
			minUpHeld = mUp;
			minDownHeld = mDown;
			scan();
		}
	}

	private void layerMessage() {
		LunaCompat.sendActionBar(client, "§f설계도 층 §b" + layerMin + "§7 ~ §b" + layerMax + " §7(Y " + (origin.getY() + layerMin - 1)
				+ " ~ " + (origin.getY() + layerMax - 1) + ")");
	}

	/** 칸 상태 확인: 매 틱 정해진 수만큼 돌아가며 + 내 주변은 매 틱. */
	private void scan() {
		if (client.world == null || bp == null) {
			return;
		}
		int n = scell.length;
		if (n == 0) {
			return;
		}
		int budget = Math.min(n, 6000);
		for (int i = 0; i < budget; i++) {
			if (cursor >= n) {
				cursor = 0;
				scannedOnce = true;
				recount();
			}
			checkSolid(cursor);
			cursor++;
		}
		// 내 주변 6칸은 바로바로(놓자마자 홀로그램이 사라지게)
		BlockPos me = playerBlock();
		int r = 6;
		for (int dy = -r; dy <= r; dy++) {
			int y = me.getY() + dy - origin.getY();
			if (y < 0 || y >= bp.h) {
				continue;
			}
			for (int dz = -r; dz <= r; dz++) {
				int z = me.getZ() + dz - origin.getZ();
				if (z < 0 || z >= bp.l) {
					continue;
				}
				for (int dx = -r; dx <= r; dx++) {
					int x = me.getX() + dx - origin.getX();
					if (x < 0 || x >= bp.w) {
						continue;
					}
					int idx = bp.index(x, y, z);
					if (bp.cells[idx] != Blueprint.AIR) {
						checkCell(idx, x, y, z);
					}
				}
			}
		}
		if (!scannedOnce) {
			return;
		}
		if (client.world.getTime() % 10 == 0) {
			recount();
		}
	}

	private void checkSolid(int s) {
		checkCell(scell[s], sx[s], sy[s], sz[s]);
	}

	private void checkCell(int idx, int x, int y, int z) {
		BlockPos p = new BlockPos(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
		BlockState have = client.world.getBlockState(p);
		byte nv = (byte) Blueprint.check(client, bp.entry(bp.cells[idx]), have, p);
		if (status[idx] != nv) {
			status[idx] = nv;
			statusVer++;
		}
	}

	/** 남은 블록 수 집계(전체, 층, 블록 종류별). */
	public void recount() {
		if (bp == null) {
			return;
		}
		int total = 0, layer = 0, wrong = 0, rot = 0;
		leftByPalette.clear();
		leftByPaletteLayer.clear();
		for (int s = 0; s < scell.length; s++) {
			int st = status[scell[s]];
			if (st == Blueprint.OK) {
				continue;
			}
			int cell = bp.cells[scell[s]];
			total++;
			leftByPalette.merge(cell, 1, Integer::sum);
			if (inLayer(sy[s])) {
				layer++;
				leftByPaletteLayer.merge(cell, 1, Integer::sum);
			}
			if (st == Blueprint.WRONG) {
				wrong++;
			} else if (st == Blueprint.ROTATED) {
				rot++;
			}
		}
		totalLeft = total;
		layerLeft = layer;
		wrongCount = wrong;
		rotatedCount = rot;
	}

	/** 손에 든 블록이 앞으로 놓아야 하는 수(층 범위 / 전체). [층, 전체]. */
	public int[] heldLeft() {
		int[] out = {0, 0};
		if (bp == null || client.player == null) {
			return out;
		}
		ItemStack main = client.player.getMainHandStack();
		if (main == null || main.isEmpty()) {
			return out;
		}
		Object id = LunaCompat.getItemId(main.getItem());
		if (id == null) {
			return out;
		}
		String held = id.toString();
		for (int i = 0; i < bp.palette.size(); i++) {
			if (held.equals(bp.palette.get(i).item)) {
				out[0] += leftByPaletteLayer.getOrDefault(i + 1, 0);
				out[1] += leftByPalette.getOrDefault(i + 1, 0);
			}
		}
		return out;
	}

	/** 필요한 블록 목록: 아이템 id → [전체, 남음]. 보이는 이름은 names에. */
	public LinkedHashMap<String, int[]> materials(Map<String, String> names) {
		LinkedHashMap<String, int[]> out = new LinkedHashMap<>();
		if (bp == null) {
			return out;
		}
		int[] total = new int[bp.palette.size() + 1];
		for (int c : bp.cells) {
			if (c > 0 && c <= bp.palette.size()) {
				total[c]++;
			}
		}
		for (int i = 0; i < bp.palette.size(); i++) {
			Blueprint.Entry e = bp.palette.get(i);
			String key = e.item.isEmpty() ? e.id : e.item;
			int[] v = out.computeIfAbsent(key, k -> new int[2]);
			v[0] += total[i + 1];
			v[1] += leftByPalette.getOrDefault(i + 1, scannedOnce ? 0 : total[i + 1]);
			names.putIfAbsent(key, e.name);
		}
		List<Map.Entry<String, int[]>> list = new ArrayList<>(out.entrySet());
		list.sort((a, b) -> b.getValue()[1] != a.getValue()[1] ? b.getValue()[1] - a.getValue()[1] : b.getValue()[0] - a.getValue()[0]);
		LinkedHashMap<String, int[]> sorted = new LinkedHashMap<>();
		for (Map.Entry<String, int[]> en : list) {
			sorted.put(en.getKey(), en.getValue());
		}
		return sorted;
	}

	public boolean scanned() {
		return scannedOnce;
	}

	// ==================== 바라보는 칸 ====================

	/** 시선을 따라가다 처음 만나는 "아직 맞지 않은" 설계도 칸(층 범위 안). 그 전에 진짜 블록에 막히면 -1. */
	public int lookedCell() {
		if (bp == null || client.player == null || client.world == null) {
			return -1;
		}
		Object cam = LunaCompat.getCamera(client);
		Vec3d eye = LunaCompat.cameraPos(cam, client);
		if (eye == null) {
			return -1;
		}
		double yaw = Math.toRadians(LunaCompat.getYaw(client.player));
		double pitch = Math.toRadians(LunaCompat.getPitch(client.player));
		double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
		int lastX = Integer.MIN_VALUE, lastY = 0, lastZ = 0;
		for (double t = 0.05; t < 7.0; t += 0.05) {
			int wx = (int) Math.floor(eye.x + fx * t), wy = (int) Math.floor(eye.y + fy * t), wz = (int) Math.floor(eye.z + fz * t);
			if (wx == lastX && wy == lastY && wz == lastZ) {
				continue;
			}
			lastX = wx;
			lastY = wy;
			lastZ = wz;
			int x = wx - origin.getX(), y = wy - origin.getY(), z = wz - origin.getZ();
			if (x >= 0 && y >= 0 && z >= 0 && x < bp.w && y < bp.h && z < bp.l) {
				int idx = bp.index(x, y, z);
				if (bp.cells[idx] != Blueprint.AIR && inLayer(y) && status[idx] != Blueprint.OK) {
					return idx;
				}
			}
			BlockState st = client.world.getBlockState(new BlockPos(wx, wy, wz));
			if (st != null && !st.isAir() && !Blueprint.isFluidId(Blueprint.idOf(st.toString()))) {
				return -1;
			}
		}
		return -1;
	}

	/** 내가 보는 가로 방향(north/south/east/west). */
	public String playerFacing() {
		float yaw = LunaCompat.getYaw(client.player);
		int q = Math.floorMod(Math.round(yaw / 90f), 4);
		return switch (q) {
			case 0 -> "south";
			case 1 -> "west";
			case 2 -> "north";
			default -> "east";
		};
	}


	// ==================== 그리기 ====================
	//
	// 49-255차(사용자: "홀로그램이 이상해, 무슨 블록인지 보여야지, 렉 최대한 덜하게"):
	//  · 블록 그림(util.BlueprintTex)을 면마다 반투명하게 입힌다. 다른 블록/방향 틀림은 빨강/주황 색 칸.
	//  · 면은 네 꼭짓점 가운데를 지나는 평행사변형으로 맞추고(LunaProjection), 가까워서 원근 차이가 큰 면은 2x2 / 4x4로 나눠 그린다.
	//  · 그릴 칸 고르기(거리 안, 층 안, 아직 안 맞은 칸 → 가까운 MAX_BOXES개)는 매 프레임이 아니라 카메라가 다른 칸으로 옮기거나
	//    칸 상태가 바뀌었을 때만 다시 한다(최대 2틱에 한 번). 거리 상자 안만 훑고, 거리별 개수로 자를 거리를 먼저 찾아 정렬 양을 줄인다.
	//  · 매 프레임은 화면 밖 칸을 건너뛰고(시야 절두체), 테두리는 가까운 칸에만.

	private static final int MAX_BOXES = 1500;
	private static final int EDGE_BOXES = 200;
	/** 49-281차: 월드에 진짜 블록으로 그린 칸에 리터매티카 오버레이(반투명 면 + 윤곽선)를 덮는 최대 칸 수(가까운 것부터). */
	private static final int OVERLAY_BOXES = 900;
	/** 49-274차: 칸 테두리 굵기(예전 1px - 블록 그림에 묻혔다). */
	private static final float EDGE_W = 1f;   // 49-281차: 1.8 → 1(리터매티카 윤곽선 굵기 1)
	private static final int[][] FACE_DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
	/** 면 f의 네 꼭짓점(a, b, c, d 둘레 순서, a = 그림 왼쪽 위, a→b 가로, a→d 아래로). 꼭짓점 번호 = x | z<<1 | y<<2. */
	private static final int[][] FACE_CORNERS = {
		{7, 5, 1, 3},   // +x (49-264차: 다른 면과 감는 방향을 맞춤 - 거꾸로 감긴 면은 화면 그리기에서 버려져 안 보였다)
		{4, 6, 2, 0},   // -x
		{4, 5, 7, 6},   // +y
		{2, 3, 1, 0},   // -y
		{6, 7, 3, 2},   // +z
		{5, 4, 0, 1},   // -z
	};
	private final double[][] vbuf = new double[8][3];
	private final double[][] sub = new double[25][3];
	private final double[] cbuf = new double[3];

	private int statusVer;
	/** 49-261차: 이번 목록에서 실제로 그리는 칸(옆면 가리기 판정용). */
	private final java.util.BitSet drawnBits = new java.util.BitSet();
	private int[] drawIdx = new int[0];
	private int drawN;
	private long[] keyBuf = new long[0];
	private int[] hist = new int[0];
	private Blueprint builtBp;
	private BlockPos builtOrigin;
	private int builtVer = -1, builtMin, builtMax, builtRange, builtCx, builtCy, builtCz;
	private long builtTick = Long.MIN_VALUE;
	/** palette 번호(k-1) → [옆면, 윗면] 그림. 처음 그릴 때 찾는다. */
	private BlueprintTex.Tex[][] palTex;

	@Override
	public boolean rendersOnDebugHud() {
		return true;
	}

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		if (isPreview() || client == null || client.player == null || client.currentScreen != null) {
			return;
		}
		LunaProjection proj = LunaProjection.capture(client);
		if (proj == null) {
			return;
		}
		try {
			if (holdingTool() || bp == null) {
				drawSelection(context, proj);
			}
			if (bp != null) {
				drawHologram(context, proj);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("blueprint:draw", t);
		}
	}

	private void drawSelection(DrawContext ctx, LunaProjection proj) {
		if (pos1 == null && pos2 == null) {
			return;
		}
		if (!holdingTool()) {
			return;
		}
		if (pos1 != null) {
			selBox(ctx, proj, 0, pos1.getX(), pos1.getY(), pos1.getZ(), pos1.getX() + 1, pos1.getY() + 1, pos1.getZ() + 1, 2f, 0xFF55C8FF);
		}
		if (pos2 != null) {
			selBox(ctx, proj, 1, pos2.getX(), pos2.getY(), pos2.getZ(), pos2.getX() + 1, pos2.getY() + 1, pos2.getZ() + 1, 2f, 0xFFE070FF);
		}
		if (pos1 != null && pos2 != null) {
			selBox(ctx, proj, 2, Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()),
					Math.max(pos1.getX(), pos2.getX()) + 1, Math.max(pos1.getY(), pos2.getY()) + 1, Math.max(pos1.getZ(), pos2.getZ()) + 1,
					1.2f, 0xB0FFFFFF);
		}
		BlockPos ref = refPos();
		if (ref != null) {
			double g = 0.25;
			selBox(ctx, proj, 3, ref.getX() + g, ref.getY() + g, ref.getZ() + g, ref.getX() + 1 - g, ref.getY() + 1 - g, ref.getZ() + 1 - g, 1.6f, 0xFFFFD84A);
		}
	}

	// ==================== 49-286차: 도끼로 찍은 지점/구역 상자 - 블록에 가린 선은 옅게 ====================
	// 사용자(사진): "도끼로 빌드하는 거 내가 보는 방향에 따라 이상하게 보여". 상자 선은 화면 위에 덧그려서 블록에 안 가렸다 -
	// 바닥 블록을 찍으면 상자 아래 반이 땅속인데도 다 보여서 공중에 뜬 상자처럼 보이고 방향 따라 모양이 바뀌었다.
	// 이제 모서리를 잘게 나눠 조각마다 카메라에서 그 점까지 불투명 블록이 막는지 보고(DDA), 막힌 조각은 25% 진하기로만 그린다.
	// 상자마다 결과를 담아 두고 카메라가 0.2블록 넘게 움직였거나 상자가 바뀌었거나 0.25초가 지나면 다시 본다.

	private final double[][] selKey = new double[5][];
	private final boolean[][] selSeen = new boolean[5][];
	private final long[] selAt = new long[5];
	/** 49-290차: selBox가 가려진 조각을 흐리게 대신 아예 안 그릴지. */
	private boolean selHide;

	private void selBox(DrawContext ctx, LunaProjection proj, int key, double x0, double y0, double z0, double x1, double y1, double z1,
			float width, int argb) {
		double[] k = {x0, y0, z0, x1, y1, z1, proj.camX, proj.camY, proj.camZ};
		long now = System.currentTimeMillis();
		double[] old = selKey[key];
		boolean fresh = old != null && now - selAt[key] < 250;
		if (fresh) {
			for (int i = 0; i < 6; i++) {
				if (old[i] != k[i]) {
					fresh = false;
					break;
				}
			}
			double mx = k[6] - old[6], my = k[7] - old[7], mz = k[8] - old[8];
			if (mx * mx + my * my + mz * mz > 0.04) {
				fresh = false;
			}
		}
		// 모서리마다 조각 수(길이 2배, 1~24)
		int[] pieces = new int[EDGES.length];
		int total = 0;
		double[][] corner = new double[8][];
		for (int i = 0; i < 8; i++) {
			corner[i] = new double[]{(i & 1) == 0 ? x0 : x1, (i & 4) == 0 ? y0 : y1, (i & 2) == 0 ? z0 : z1};
		}
		for (int e = 0; e < EDGES.length; e++) {
			double[] a = corner[EDGES[e][0]], b = corner[EDGES[e][1]];
			double len = Math.abs(b[0] - a[0]) + Math.abs(b[1] - a[1]) + Math.abs(b[2] - a[2]);
			pieces[e] = Math.max(1, Math.min(24, (int) Math.ceil(len * 2)));
			total += pieces[e];
		}
		boolean[] seen = selSeen[key];
		if (!fresh || seen == null || seen.length != total) {
			seen = new boolean[total];
			int n = 0;
			for (int e = 0; e < EDGES.length; e++) {
				double[] a = corner[EDGES[e][0]], b = corner[EDGES[e][1]];
				for (int i = 0; i < pieces[e]; i++) {
					double t = (i + 0.5) / pieces[e];
					seen[n++] = seenFromCam(proj, a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t);
				}
			}
			selSeen[key] = seen;
			selKey[key] = k;
			selAt[key] = now;
		}
		int faint = ((Math.max(0x18, ((argb >>> 24) & 0xFF) / 4)) << 24) | (argb & 0xFFFFFF);
		double[] va = new double[3], vb = new double[3];
		int n = 0;
		for (int e = 0; e < EDGES.length; e++) {
			double[] a = corner[EDGES[e][0]], b = corner[EDGES[e][1]];
			int i = 0;
			while (i < pieces[e]) {
				boolean v = seen[n + i];
				int j = i;
				while (j + 1 < pieces[e] && seen[n + j + 1] == v) {
					j++;
				}
				if (!v && selHide) {
					i = j + 1;
					continue;
				}
				double t0 = (double) i / pieces[e], t1 = (double) (j + 1) / pieces[e];
				proj.toView(a[0] + (b[0] - a[0]) * t0, a[1] + (b[1] - a[1]) * t0, a[2] + (b[2] - a[2]) * t0, va);
				proj.toView(a[0] + (b[0] - a[0]) * t1, a[1] + (b[1] - a[1]) * t1, a[2] + (b[2] - a[2]) * t1, vb);
				proj.drawViewSegment(ctx, va, vb, v ? width : Math.max(1f, width * 0.6f), v ? argb : faint);
				i = j + 1;
			}
			n += pieces[e];
		}
	}

	/** 카메라에서 그 점(카메라 쪽으로 0.03 당김)까지 불투명 블록이 없나. 점이 든 칸까지 본다. */
	private boolean seenFromCam(LunaProjection proj, double px, double py, double pz) {
		double sx = proj.camX, sy = proj.camY, sz = proj.camZ;
		double dx = sx - px, dy = sy - py, dz = sz - pz;
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 0.05) {
			return true;
		}
		if (len > 96) {
			return true;   // 너무 멀면 보지 않는다(옛 방식대로 진하게)
		}
		px += dx / len * 0.03;
		py += dy / len * 0.03;
		pz += dz / len * 0.03;
		double ex = px - sx, ey = py - sy, ez = pz - sz;
		int x = (int) Math.floor(sx), y = (int) Math.floor(sy), z = (int) Math.floor(sz);
		int stepX = ex > 0 ? 1 : -1, stepY = ey > 0 ? 1 : -1, stepZ = ez > 0 ? 1 : -1;
		double adx = Math.abs(ex), ady = Math.abs(ey), adz = Math.abs(ez);
		double tMaxX = adx < 1e-9 ? Double.MAX_VALUE : (ex > 0 ? x + 1 - sx : sx - x) / adx;
		double tMaxY = ady < 1e-9 ? Double.MAX_VALUE : (ey > 0 ? y + 1 - sy : sy - y) / ady;
		double tMaxZ = adz < 1e-9 ? Double.MAX_VALUE : (ez > 0 ? z + 1 - sz : sz - z) / adz;
		double tdX = adx < 1e-9 ? Double.MAX_VALUE : 1 / adx, tdY = ady < 1e-9 ? Double.MAX_VALUE : 1 / ady, tdZ = adz < 1e-9 ? Double.MAX_VALUE : 1 / adz;
		for (int guard = 0; guard < 400; guard++) {
			if (tMaxX < tMaxY && tMaxX < tMaxZ) {
				if (tMaxX > 1) {
					return true;
				}
				x += stepX;
				tMaxX += tdX;
			} else if (tMaxY < tMaxZ) {
				if (tMaxY > 1) {
					return true;
				}
				y += stepY;
				tMaxY += tdY;
			} else {
				if (tMaxZ > 1) {
					return true;
				}
				z += stepZ;
				tMaxZ += tdZ;
			}
			if (opaqueAt(x, y, z)) {
				return false;
			}
		}
		return true;
	}

	/** 그릴 칸 목록을 다시 만들어야 하면 만든다. 결과: drawIdx[0..drawN) = 칸 인덱스, 가까운 순. */
	private void rebuildCandidates(LunaProjection proj) {
		int cx = (int) Math.floor(proj.camX), cy = (int) Math.floor(proj.camY), cz = (int) Math.floor(proj.camZ);
		int r = range.get();
		long tick = client.world == null ? 0 : client.world.getTime();
		boolean same = builtBp == bp && builtOrigin == origin && builtVer == statusVer && builtMin == layerMin && builtMax == layerMax
				&& builtRange == r;
		boolean moved = cx != builtCx || cy != builtCy || cz != builtCz;
		if (same && !moved && tick - builtTick < 20 && tick >= builtTick) {
			return;   // 1초마다는 다시(가려짐 판정이 주변 블록 변화를 따라오게)
		}
		if (same && tick - builtTick < 2 && tick >= builtTick) {
			return;   // 움직이는 중에는 2틱에 한 번만
		}
		builtBp = bp;
		builtOrigin = origin;
		builtVer = statusVer;
		builtMin = layerMin;
		builtMax = layerMax;
		builtRange = r;
		builtCx = cx;
		builtCy = cy;
		builtCz = cz;
		builtTick = tick;
		drawN = 0;
		drawnBits.clear();
		int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
		int x0 = Math.max(0, cx - r - ox), x1 = Math.min(bp.w - 1, cx + r - ox);
		int y0 = Math.max(Math.max(0, layerMin - 1), cy - r - oy), y1 = Math.min(Math.min(bp.h - 1, layerMax - 1), cy + r - oy);
		int z0 = Math.max(0, cz - r - oz), z1 = Math.min(bp.l - 1, cz + r - oz);
		if (x0 > x1 || y0 > y1 || z0 > z1) {
			return;
		}
		double ccx = proj.camX - ox - 0.5, ccy = proj.camY - oy - 0.5, ccz = proj.camZ - oz - 0.5;
		int r2 = r * r;
		// 1) 거리(정수)별 개수
		int maxD = r + 1;
		if (hist.length < maxD + 1) {
			hist = new int[maxD + 1];
		}
		java.util.Arrays.fill(hist, 0, maxD + 1, 0);
		int total = 0;
		for (int y = y0; y <= y1; y++) {
			double dy = y - ccy;
			for (int z = z0; z <= z1; z++) {
				double dz = z - ccz;
				int row = (y * bp.l + z) * bp.w;
				for (int x = x0; x <= x1; x++) {
					int idx = row + x;
					if (bp.cells[idx] == Blueprint.AIR) {
						continue;
					}
					int st = status[idx];
					if (st == Blueprint.OK || st == Blueprint.UNKNOWN) {
						continue;
					}
					double dx = x - ccx;
					double d2 = dx * dx + dy * dy + dz * dz;
					if (d2 > r2) {
						continue;
					}
					hist[Math.min(maxD, (int) Math.sqrt(d2))]++;
					total++;
				}
			}
		}
		if (total == 0) {
			return;
		}
		// 2) 가까운 MAX_BOXES개가 들어가는 거리까지만
		int cut = maxD, acc = 0;
		for (int d = 0; d <= maxD; d++) {
			acc += hist[d];
			if (acc >= MAX_BOXES) {
				cut = d;
				break;
			}
		}
		if (keyBuf.length < Math.min(total, acc + 8)) {
			keyBuf = new long[Math.min(total, acc + 8) + 64];
		}
		int n = 0;
		for (int y = y0; y <= y1 && n < keyBuf.length; y++) {
			double dy = y - ccy;
			for (int z = z0; z <= z1 && n < keyBuf.length; z++) {
				double dz = z - ccz;
				int row = (y * bp.l + z) * bp.w;
				for (int x = x0; x <= x1 && n < keyBuf.length; x++) {
					int idx = row + x;
					if (bp.cells[idx] == Blueprint.AIR) {
						continue;
					}
					int st = status[idx];
					if (st == Blueprint.OK || st == Blueprint.UNKNOWN) {
						continue;
					}
					double dx = x - ccx;
					double d2 = dx * dx + dy * dy + dz * dz;
					if (d2 > r2 || (int) Math.sqrt(d2) > cut) {
						continue;
					}
					keyBuf[n++] = ((long) (d2 * 16) << 23) | idx;
				}
			}
		}
		java.util.Arrays.sort(keyBuf, 0, n);
		n = Math.min(n, MAX_BOXES);
		if (drawIdx.length < n) {
			drawIdx = new int[MAX_BOXES];
		}
		if (faceMask.length < drawIdx.length) {
			faceMask = new byte[drawIdx.length];
		}
		// 49-258차(사용자: "맞는 블록을 놓아도 홀로그램이 안 사라져"): 화면 위에 덧그리는 방식이라 놓은 블록 뒤의 홀로그램이 그 블록을
		// 뚫고 보였다. 카메라에서 칸까지 진짜 불투명 블록에 막히면(가운데와 가장 가까운 모서리 둘 다) 그리지 않는다.
		// 49-268차(사용자: "홀로그램이 블록에 막히면 안 보이게"): 월드에 진짜 블록으로 그리는 중이면 깊이 판정이 화소 단위로 가려 주므로
		// 여기서 거르지 않는다(반쯤 가려진 칸이 통째로 사라지지 않게). 화면 방식일 때는 칸이 아니라 면마다 카메라에서 그 면 가운데까지
		// 막힘을 본다 - 보이는 면만 그리고, 보이는 면이 하나도 없으면 칸을 뺀다(예전엔 가운데나 모서리 하나만 보여도 칸 전체를 블록 위에
		// 덧그려서 벽 너머가 비쳤다).
		boolean live = worldLive();
		int wl = bp.w * bp.l, kept = 0;
		drawnBits.clear();
		for (int i = 0; i < n; i++) {
			int idx = (int) (keyBuf[i] & 0x7FFFFF);
			int y = idx / wl, rem = idx - y * wl, z = rem / bp.w, x = rem - z * bp.w;
			visLoose = true;   // 49-290차: 후보 고르기는 가운데 한 점만(빠르게), 그릴 때 다섯 점으로 다시 본다
			int mask = live ? 0x3F : faceVisMask(proj.camX, proj.camY, proj.camZ, ox + x, oy + y, oz + z);
			visLoose = false;
			if (mask != 0) {
				faceMask[kept] = (byte) mask;
				drawIdx[kept++] = idx;
				drawnBits.set(idx);
			}
		}
		drawN = kept;
	}

	private final BlockPos.Mutable rayPos = new BlockPos.Mutable();
	/** drawIdx와 같은 순서: 칸마다 보이는 면(FACE_DIRS 순서 비트). */
	private byte[] faceMask = new byte[0];
	/** 지금 그리는 칸의 보이는 면(drawCell이 본다). */
	private int faceMaskNow = 0x3F;

	/** 월드에 진짜 블록으로 그리기가 최근(1초 안) 성공했나. */
	private boolean worldLive() {
		return worldRender.get() && worldOk && System.nanoTime() - worldStamp < 1_000_000_000L;
	}

	/**
	 * 49-268차: 칸 (tx,ty,tz)의 면 가운데 중 카메라에서 막힘 없이 보이는 면(비트). 카메라 반대쪽 면, 바로 옆이 불투명 블록인 면은 뺀다.
	 * 카메라가 그 칸 안이면 다 보인다고 친다.
	 */
	private int faceVisMask(double cx, double cy, double cz, int tx, int ty, int tz) {
		if (Math.floor(cx) == tx && Math.floor(cy) == ty && Math.floor(cz) == tz) {
			return 0x3F;
		}
		int mask = 0;
		for (int f = 0; f < 6; f++) {
			int[] d = FACE_DIRS[f];
			double px = tx + 0.5 + d[0] * 0.49, py = ty + 0.5 + d[1] * 0.49, pz = tz + 0.5 + d[2] * 0.49;
			if ((cx - px) * d[0] + (cy - py) * d[1] + (cz - pz) * d[2] <= 0) {
				continue;   // 뒷면
			}
			if (opaqueAt(tx + d[0], ty + d[1], tz + d[2])) {
				continue;   // 진짜 블록에 붙은 면
			}
			if (faceClear(cx, cy, cz, px, py, pz, d, tx, ty, tz)) {
				mask |= 1 << f;
			}
		}
		return mask;
	}

	/**
	 * 49-290차(사용자: "설계도 홀로그램 블록 뒤에 가려지면 안 보이게"): 면 가운데만 보면 반쯤 가려진 면이 통째로 블록 위에 덧그려져
	 * 벽 너머가 비쳤다. 가운데와 네 모서리(안쪽으로 0.05) 다섯 점이 모두 카메라에서 막힘 없이 보일 때만 그 면을 그린다.
	 */
	private boolean faceClear(double cx, double cy, double cz, double px, double py, double pz, int[] d, int tx, int ty, int tz) {
		if (!clearRay(cx, cy, cz, px, py, pz, tx, ty, tz)) {
			return false;
		}
		if (visLoose) {
			return true;
		}
		double ux = d[0] == 0 ? 1 : 0, uy = d[0] != 0 ? 1 : 0, uz = 0;
		double vx = 0, vy = d[2] != 0 ? 1 : 0, vz = d[2] == 0 ? 1 : 0;
		for (int k = 0; k < 4; k++) {
			double su = (k & 1) == 0 ? -0.45 : 0.45, sv = (k & 2) == 0 ? -0.45 : 0.45;
			if (!clearRay(cx, cy, cz, px + ux * su + vx * sv, py + uy * su + vy * sv, pz + uz * su + vz * sv, tx, ty, tz)) {
				return false;
			}
		}
		return true;
	}

	// 49-290차: 칸마다 보이는 면 기억(drawIdx 순서). 카메라가 0.2블록 넘게 움직이거나 0.25초 지나면 세대를 올려 다시 본다.
	private byte[] visMask = new byte[0];
	private int[] visIdx = new int[0], visGen = new int[0];
	private int visGeneration = 1;
	private double visCamX = Double.NaN, visCamY, visCamZ;
	private long visTime;
	private static final int VIS_BUDGET = 350;
	private boolean visLoose;

	private void visPrepare(LunaProjection proj) {
		if (visMask.length < drawIdx.length) {
			visMask = new byte[drawIdx.length];
			visIdx = new int[drawIdx.length];
			visGen = new int[drawIdx.length];
			java.util.Arrays.fill(visIdx, -1);
		}
		long now = System.nanoTime();
		double dx = proj.camX - visCamX, dy = proj.camY - visCamY, dz = proj.camZ - visCamZ;
		if (Double.isNaN(visCamX) || dx * dx + dy * dy + dz * dz > 0.04 || now - visTime > 250_000_000L) {
			visGeneration++;
			visCamX = proj.camX;
			visCamY = proj.camY;
			visCamZ = proj.camZ;
			visTime = now;
		}
		// 가까운 칸부터 한 프레임에 VIS_BUDGET개까지 새로 본다(나머지는 다음 프레임에 - 그 사이엔 직전 값)
		int budget = VIS_BUDGET;
		for (int i = 0; i < drawN && budget > 0; i++) {
			if (visIdx[i] == drawIdx[i] && visGen[i] == visGeneration) {
				continue;
			}
			visCompute(proj, i);
			budget--;
		}
	}

	private int visOf(LunaProjection proj, int i) {
		if (i >= visIdx.length) {
			return 0x3F;
		}
		if (visIdx[i] != drawIdx[i]) {
			visCompute(proj, i);   // 처음 보는 칸은 지금 바로
		}
		return visMask[i] & 0x3F;
	}

	private void visCompute(LunaProjection proj, int i) {
		int idx = drawIdx[i];
		int wl = bp.w * bp.l;
		int y = idx / wl, rem = idx - y * wl, z = rem / bp.w, x = rem - z * bp.w;
		rayHolo = worldDrawn.get(idx) && worldOk && System.nanoTime() - worldStamp < 250_000_000L;
		try {
			visMask[i] = (byte) faceVisMask(proj.camX, proj.camY, proj.camZ, origin.getX() + x, origin.getY() + y, origin.getZ() + z);
		} finally {
			rayHolo = false;
		}
		visIdx[i] = idx;
		visGen[i] = visGeneration;
	}

	/** 3차원 격자 따라가기(DDA): 시작 칸과 목표 칸은 빼고, 지나가는 칸에 불투명 블록이 있으면 false. */
	private boolean clearRay(double sx0, double sy0, double sz0, double ex, double ey, double ez, int tx, int ty, int tz) {
		double dx = ex - sx0, dy = ey - sy0, dz = ez - sz0;
		int x = (int) Math.floor(sx0), y = (int) Math.floor(sy0), z = (int) Math.floor(sz0);
		int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
		double adx = Math.abs(dx), ady = Math.abs(dy), adz = Math.abs(dz);
		double tMaxX = adx < 1e-9 ? Double.MAX_VALUE : (dx > 0 ? x + 1 - sx0 : sx0 - x) / adx;
		double tMaxY = ady < 1e-9 ? Double.MAX_VALUE : (dy > 0 ? y + 1 - sy0 : sy0 - y) / ady;
		double tMaxZ = adz < 1e-9 ? Double.MAX_VALUE : (dz > 0 ? z + 1 - sz0 : sz0 - z) / adz;
		double tdX = adx < 1e-9 ? Double.MAX_VALUE : 1 / adx, tdY = ady < 1e-9 ? Double.MAX_VALUE : 1 / ady, tdZ = adz < 1e-9 ? Double.MAX_VALUE : 1 / adz;
		for (int guard = 0; guard < 600; guard++) {
			if (tMaxX < tMaxY && tMaxX < tMaxZ) {
				if (tMaxX > 1) {
					return true;
				}
				x += stepX;
				tMaxX += tdX;
			} else if (tMaxY < tMaxZ) {
				if (tMaxY > 1) {
					return true;
				}
				y += stepY;
				tMaxY += tdY;
			} else {
				if (tMaxZ > 1) {
					return true;
				}
				z += stepZ;
				tMaxZ += tdZ;
			}
			if (x == tx && y == ty && z == tz) {
				return true;
			}
			if (opaqueAt(x, y, z)) {
				return false;
			}
			if (rayHolo && holoSolidAt(x, y, z)) {
				return false;   // 49-274차: 테두리만 그릴 땐 앞에 있는 홀로그램 블록도 가린다(뒤 칸 테두리가 비쳐 어지럽지 않게)
			}
		}
		return true;
	}

	/** 49-274차: clearRay가 월드에 진짜 블록으로 그린 (꽉 찬) 홀로그램 칸도 막힘으로 볼지. */
	private boolean rayHolo;

	private boolean holoSolidAt(int wx, int wy, int wz) {
		int x = wx - origin.getX(), y = wy - origin.getY(), z = wz - origin.getZ();
		if (x < 0 || y < 0 || z < 0 || x >= bp.w || y >= bp.h || z >= bp.l || !inLayer(y)) {
			return false;
		}
		int idx = bp.index(x, y, z);
		int st = status[idx];
		return bp.cells[idx] != Blueprint.AIR && st != Blueprint.OK && st != Blueprint.UNKNOWN && worldDrawn.get(idx)
				&& boxesOf(bp.cells[idx]) == null;
	}

	private boolean opaqueAt(int x, int y, int z) {
		try {
			BlockState st = client.world.getBlockState(rayPos.set(x, y, z));
			return st != null && st.isOpaque();
		} catch (Throwable t) {
			return false;
		}
	}

	private BlueprintTex.Tex[] texOf(int cell) {
		if (palTex == null || palTex.length != bp.palette.size()) {
			palTex = new BlueprintTex.Tex[bp.palette.size()][];
		}
		int k = cell - 1;
		if (k < 0 || k >= palTex.length) {
			return null;
		}
		if (palTex[k] == null) {
			palTex[k] = BlueprintTex.of(bp.palette.get(k).id);
		}
		return palTex[k];
	}

	/** 49-260차: palette 번호(k-1) → 모델 y 회전(0~3, 90도 단위). -1 = 아직 안 읽음. */
	private int[] palRot;

	private int rotOf(int cell) {
		int k = cell - 1;
		if (k < 0 || k >= bp.palette.size()) {
			return 0;
		}
		if (palRot == null || palRot.length != bp.palette.size()) {
			palRot = new int[bp.palette.size()];
			java.util.Arrays.fill(palRot, -1);
		}
		if (palRot[k] < 0) {
			Blueprint.Entry e = bp.palette.get(k);
			palRot[k] = (BlueprintTex.yRot(e.id, e.props) / 90) & 3;
		}
		return palRot[k];
	}

	/** palette 번호(k-1) → 모양 상자들(null = 꽉 찬 블록). */
	private double[][][] palBoxes;
	private boolean[] palBoxesDone;

	private double[][] boxesOf(int cell) {
		int k = cell - 1;
		if (k < 0 || k >= bp.palette.size()) {
			return null;
		}
		if (palBoxes == null || palBoxes.length != bp.palette.size()) {
			palBoxes = new double[bp.palette.size()][][];
			palBoxesDone = new boolean[bp.palette.size()];
		}
		if (!palBoxesDone[k]) {
			palBoxes[k] = Blueprint.parseBoxes(bp.palette.get(k).shape);
			palBoxesDone[k] = true;
		}
		return palBoxes[k];
	}

	private void drawHologram(DrawContext ctx, LunaProjection proj) {
		if (scell.length == 0) {
			return;
		}
		// 바깥 테두리(보이는 층만)
		int y0 = origin.getY() + layerMin - 1, y1 = origin.getY() + layerMax;
		// 49-290차: 바깥 테두리도 블록에 가려진 조각은 안 그린다
		selHide = true;
		try {
			selBox(ctx, proj, 4, origin.getX(), y0, origin.getZ(), origin.getX() + bp.w, y1, origin.getZ() + bp.l, 1f, 0x60FFFFFF);
		} finally {
			selHide = false;
		}
		rebuildCandidates(proj);
		if (drawN == 0) {
			return;
		}
		boolean tex = kr.lunaslight.mod.gui.LunaGfx.texturesUsable(ctx);
		// 49-266차: 월드에 진짜 블록으로 그린 칸은 여기서 건너뛴다(이번 프레임 월드 그리기가 성공했을 때만)
		boolean inWorld = worldOk && System.nanoTime() - worldStamp < 250_000_000L;
		int a = Math.round(255 * alpha.get() / 100f);
		int fillA = Math.min(255, a * 3 / 4 + 30);
		int missC = missArgb(), wrongC = wrongArgb(), stateC = stateArgb();
		int miss = missC & 0xFFFFFF;
		double limX = proj.tanHalf * proj.aspect * 1.05, limY = proj.tanHalf * 1.05;
		int wl = bp.w * bp.l;
		visPrepare(proj);
		// 먼 것부터(가까운 게 위에)
		for (int i = drawN - 1; i >= 0; i--) {
			int idx = drawIdx[i];
			int st = status[idx];
			if (st == Blueprint.OK || st == Blueprint.UNKNOWN) {
				continue;   // 목록을 만든 뒤에 맞게 놓인 칸
			}
			// 49-281차: 월드에 진짜 블록으로 그린 칸은 리터매티카처럼 반투명 색 면 + 윤곽선만 덮는다(가까운 OVERLAY_BOXES칸)
			boolean edgeOnly = inWorld && worldDrawn.get(idx);
			if (edgeOnly && i >= OVERLAY_BOXES) {
				continue;
			}
			int y = idx / wl, rem = idx - y * wl, z = rem / bp.w, x = rem - z * bp.w;
			// 화면 방식: 보이는 면만. 월드 그리기 중인데 모델이 없어 여기로 온 칸(상자 등)은 면 막힘을 지금 본다.
			// 49-290차: 월드 방식이든 화면 방식이든 다섯 점 판정(기억해 둔 값)으로 - 가려진 면은 안 덧그린다
			faceMaskNow = visOf(proj, i);
			if (faceMaskNow == 0) {
				continue;
			}
			int bx = origin.getX() + x, by = origin.getY() + y, bz = origin.getZ() + z;
			// 화면 밖이면 건너뛴다(칸 반지름 0.87)
			proj.toView(bx + 0.5, by + 0.5, bz + 0.5, cbuf);
			double vz = cbuf[2];
			if (vz < -0.9 || Math.abs(cbuf[0]) - 0.9 > (vz + 0.9) * limX || Math.abs(cbuf[1]) - 0.9 > (vz + 0.9) * limY) {
				continue;
			}
			if (cbuf[0] * cbuf[0] + cbuf[1] * cbuf[1] + vz * vz < 0.7) {
				continue;   // 머리가 그 칸 안 - 화면을 덮는다
			}
			boolean edges = edgeOnly || i < EDGE_BOXES;
			edgesOnly = false;
			if (st == Blueprint.MISSING && edgeOnly
					&& kr.lunaslight.mod.util.BlueprintWorld.crossLike(modelOf(bp.cells[idx], bx, by, bz))) {
				continue;   // 49-309차: 잔디, 꽃 같은 X자 판은 모델 자체가 파랗다 - 상자 덮개 없음
			}
			if (st == Blueprint.MISSING && edgeOnly) {
				// 리터매티카 '없는 블록' 오버레이: 블록 모양 그대로(월드에 그린 진짜 블록 위) 하늘색 반투명 면 + 윤곽선
				rotNow = 0;
				// 49-287차(사용자: "홀로그램 테두리 없애, 블록 많을 때 이상하게 보여"): 윤곽선 없이 반투명 하늘색 면만
				drawCell(ctx, proj, x, y, z, bx, by, bz, boxesOf(bp.cells[idx]), null, missC, 0);
			} else if (st == Blueprint.MISSING) {
				// 화면 방식(월드 그리기를 못 쓸 때): 블록 그림 + 같은 색 윤곽선
				BlueprintTex.Tex[] t = tex ? texOf(bp.cells[idx]) : null;
				boolean has = t != null && t[0] != null;
				rotNow = has ? rotOf(bp.cells[idx]) : 0;
				drawCell(ctx, proj, x, y, z, bx, by, bz, boxesOf(bp.cells[idx]), has ? t : null,
						has ? a : (Math.min(255, a * 3 / 5) << 24) | miss, 0);   // 49-287차: 테두리 없음
			} else {
				// 리터매티카 '틀린 블록'(빨강) / '틀린 상태'(주황): 그 칸 전체에 반투명 색 면 + 윤곽선(놓인 진짜 블록 위)
				int c = st == Blueprint.WRONG ? wrongC : stateC;
				rotNow = 0;
				drawCell(ctx, proj, x, y, z, bx, by, bz, null, null, c, lineOf(c));
			}
		}
		edgesOnly = false;
	}

	/** 지금 칸은 테두리만(면은 월드에 진짜 블록으로 이미 그림). */
	private boolean edgesOnly;

	/** 49-274차: 테두리 색 = '놓을 곳 색'을 더 짙은 파랑 쪽으로(블록 그림과 섞여 안 보이지 않게). */
	private static int edgeBlue(int rgb) {
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		r = r * 2 / 5;
		g = (g * 3 + 0x8C * 2) / 5 * 4 / 5;
		b = Math.max(b, 0xFF);
		return (r << 16) | (g << 8) | b;
	}

	// ==================== 49-266차: 월드 안에 진짜 블록으로 ====================

	private final java.util.BitSet worldDrawn = new java.util.BitSet();
	private boolean worldOk;
	private long worldStamp;
	/** palette 번호(k-1) → 블록 모델 쿼드(없으면 NO_MODEL). */
	private Object[] palModel;
	private Blueprint palModelBp;
	private static final Object NO_MODEL = new Object();
	private static final int[][] DIR6 = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};   // 아래 위 북 남 서 동

	private kr.lunaslight.mod.util.BlueprintWorld.Model modelOf(int cell, int wx, int wy, int wz) {
		int k = cell - 1;
		if (k < 0 || k >= bp.palette.size()) {
			return null;
		}
		if (palModel == null || palModelBp != bp || palModel.length != bp.palette.size()) {
			palModel = new Object[bp.palette.size()];
			palModelBp = bp;
		}
		Object m = palModel[k];
		if (m == null) {
			Blueprint.Entry e = bp.palette.get(k);
			m = kr.lunaslight.mod.util.BlueprintWorld.model(kr.lunaslight.mod.util.BlueprintWorld.stateOf(e.id, e.props),
					new BlockPos(wx, wy, wz));
			palModel[k] = m == null ? NO_MODEL : m;
		}
		return m instanceof kr.lunaslight.mod.util.BlueprintWorld.Model mm ? mm : null;
	}

	@Override
	public void onWorldRender(Object context) {
		worldOk = false;
		if (!worldRender.get() || bp == null || origin == null || status == null || drawN == 0 || client.world == null
				|| !kr.lunaslight.mod.util.BlueprintWorld.usable()) {
			return;
		}
		float af = alpha.get() / 100f;
		if (!kr.lunaslight.mod.util.BlueprintWorld.begin(context, af)) {
			return;
		}
		try {
			worldDrawn.clear();
			int wl = bp.w * bp.l;
			for (int i = 0; i < drawN; i++) {
				int idx = drawIdx[i];
				if (idx >= status.length || idx >= bp.cells.length) {
					continue;
				}
				int st = status[idx];
				if (st != Blueprint.MISSING) {
					continue;   // 49-281차: 맞음은 안 그리고, 틀린 블록/틀린 상태는 리터매티카처럼 색 상자만(화면 쪽에서)
				}
				int y = idx / wl, rem = idx - y * wl, z = rem / bp.w, x = rem - z * bp.w;
				int bx = origin.getX() + x, by = origin.getY() + y, bz = origin.getZ() + z;
				kr.lunaslight.mod.util.BlueprintWorld.Model m = modelOf(bp.cells[idx], bx, by, bz);
				if (m == null) {
					continue;   // 모델이 없는 블록(상자 등) - 화면 방식으로
				}
				if (st == Blueprint.MISSING) {
					// 옆 칸도 (꽉 찬) 홀로그램이거나 진짜 불투명 블록이면 그쪽 면은 안 그린다(속 격자가 비치지 않게)
					int cull = 0;
					for (int d = 0; d < 6; d++) {
						int[] o = DIR6[d];
						if (neighborHolo(x + o[0], y + o[1], z + o[2]) || opaqueAt(bx + o[0], by + o[1], bz + o[2])) {
							cull |= 1 << d;
						}
					}
					// 49-309차(사용자: "잔디 같은 블록은 파란색이어야지 박스가 생기면 어떡해"): X자 판 블록은 위에 상자를 안 덮으니 모델을 놓을 곳 색으로 칠한다
					int ht = kr.lunaslight.mod.util.BlueprintWorld.crossLike(m) ? (missArgb() & 0xFFFFFF) : holoTint();
					kr.lunaslight.mod.util.BlueprintWorld.block(m, bx, by, bz, ((ht >> 16) & 0xFF) / 255f, ((ht >> 8) & 0xFF) / 255f, (ht & 0xFF) / 255f,
							af, cull, 0);
				}
				worldDrawn.set(idx);
			}
			worldOk = true;
			worldStamp = System.nanoTime();
		} catch (Throwable t) {
			kr.lunaslight.mod.util.BlueprintWorld.fail(t);
			worldOk = false;
		} finally {
			kr.lunaslight.mod.util.BlueprintWorld.end();
		}
	}

	private static final double[][] FULL_BOX = {{0, 0, 0, 1, 1, 1}};
	/** 지금 그리는 칸의 모델 y 회전(90도 단위) - 윗면, 아랫면 그림을 이만큼 돌린다. */
	private int rotNow;
	private final int[] qrot = new int[4];

	/**
	 * 칸 하나: 카메라 쪽으로 보이는 면 중, 옆 칸도 (꽉 찬) 홀로그램이 아닌 면만. tex가 있으면 그림(alphaOrColor = 알파 0~255),
	 * 없으면 색(alphaOrColor = ARGB). edge가 0이 아니면 테두리도.
	 * 49-258차: boxes(블록의 실제 모양 상자들)가 있으면 상자마다 면을 그리고 그림도 그 상자 크기만큼만 잘라 입힌다(계단, 반 블록, 울타리 …).
	 */
	private void drawCell(DrawContext ctx, LunaProjection proj, int x, int y, int z, int bx, int by, int bz,
			double[][] boxes, BlueprintTex.Tex[] tex, int alphaOrColor, int edge) {
		boolean full = boxes == null;
		for (double[] b : full ? FULL_BOX : boxes) {
			for (int i = 0; i < 8; i++) {
				proj.toView(bx + ((i & 1) == 0 ? b[0] : b[3]), by + (((i >> 2) & 1) == 0 ? b[1] : b[4]),
						bz + (((i >> 1) & 1) == 0 ? b[2] : b[5]), vbuf[i]);
			}
			for (int f = 0; f < 6; f++) {
				int[] d = FACE_DIRS[f];
				double fcx = bx + (b[0] + b[3]) / 2 + d[0] * (b[3] - b[0]) / 2 - proj.camX;
				double fcy = by + (b[1] + b[4]) / 2 + d[1] * (b[4] - b[1]) / 2 - proj.camY;
				double fcz = bz + (b[2] + b[5]) / 2 + d[2] * (b[5] - b[2]) / 2 - proj.camZ;
				if (fcx * d[0] + fcy * d[1] + fcz * d[2] >= 0) {
					continue;   // 뒷면
				}
				if ((faceMaskNow >> f & 1) == 0) {
					continue;   // 49-268차: 카메라에서 막힌 면
				}
				if (full && neighborHolo(x + d[0], y + d[1], z + d[2])) {
					continue;
				}
				int[] q = FACE_CORNERS[f];
				if (edgesOnly) {
					if (edge != 0) {
						for (int e = 0; e < 4; e++) {
							proj.drawViewSegment(ctx, vbuf[q[e]], vbuf[q[(e + 1) % 4]], EDGE_W, edge);
						}
					}
					continue;
				}
				BlueprintTex.Tex t = tex == null ? null : f == 2 ? tex[1] : f == 3 ? tex[tex.length > 2 && tex[2] != null ? 2 : 1] : tex[0];
				// 49-260차: 윗면, 아랫면은 모델 회전만큼 그림을 돌린다(꼭짓점 순서를 돌림). 그림 일부만 쓰는 면(반 블록 등)은 그대로.
				boolean fullUv = b[0] <= 0.001 && b[2] <= 0.001 && b[3] >= 0.999 && b[5] >= 0.999;
				if (t != null && rotNow != 0 && (f == 2 || f == 3) && fullUv) {
					int sh = f == 2 ? rotNow : (4 - rotNow) & 3;
					for (int e = 0; e < 4; e++) {
						qrot[e] = q[(e + sh) & 3];
					}
					q = qrot;
				}
				double[] va = vbuf[q[0]], vb = vbuf[q[1]], vc = vbuf[q[2]], vd = vbuf[q[3]];
				double skew = proj.quadSkew(va, vb, vc, vd);
				// 이 면이 그림에서 차지하는 부분(0~1) - FACE_CORNERS의 a→b(가로), a→d(아래) 방향에 맞춘다
				double u0, u1, v0, v1;
				switch (f) {
					case 0 -> { u0 = 1 - b[5]; u1 = 1 - b[2]; v0 = 1 - b[4]; v1 = 1 - b[1]; }
					case 1 -> { u0 = b[2]; u1 = b[5]; v0 = 1 - b[4]; v1 = 1 - b[1]; }
					case 2 -> { u0 = b[0]; u1 = b[3]; v0 = b[2]; v1 = b[5]; }
					case 3 -> { u0 = b[0]; u1 = b[3]; v0 = 1 - b[5]; v1 = 1 - b[2]; }
					case 4 -> { u0 = b[0]; u1 = b[3]; v0 = 1 - b[4]; v1 = 1 - b[1]; }
					default -> { u0 = 1 - b[3]; u1 = 1 - b[0]; v0 = 1 - b[4]; v1 = 1 - b[1]; }
				}
				int tw = t == null ? 16 : t.w;
				int U = clampTex((int) Math.round(u0 * tw), tw), V = clampTex((int) Math.round(v0 * tw), tw);
				int RW = Math.max(1, Math.min(tw - U, (int) Math.round((u1 - u0) * tw)));
				int RH = Math.max(1, Math.min(tw - V, (int) Math.round((v1 - v0) * tw)));
				// 49-265차(사용자: "입체감 있는 블록이 안 나와"): 바닐라처럼 면 방향마다 밝기를 달리한다(위 1, 남북 0.8, 동서 0.6, 아래 0.5).
				int shade = FACE_SHADE[f];
				int col = t != null ? (alphaOrColor << 24) | mulRgb(mulColor(t.tint, holoTint()), shade) : (alphaOrColor & 0xFF000000) | mulRgb(alphaOrColor, shade);
				// 49-265차: 원근이 큰 가까운 면은 2x2 / 4x4로 나눠 그린다. 49-262차에 깨져 보였던 건 조각마다 따로 맞춘 평행사변형이라
				// 이음매가 어긋나서였다 - 이제 조각도 삼각형 두 개로 꼭짓점이 딱 맞으므로 이음매가 없다. 근평면에 걸친 면도 나눠서
				// 앞쪽 조각은 그린다(예전엔 면을 통째로 빼서 바로 옆 블록 면이 사라졌다).
				int k = skew < 0 ? 4 : skew > 24 ? 4 : skew > 5 ? 2 : 1;
				if (t != null) {
					while (k > 1 && (RW % k != 0 || RH % k != 0)) {
						k >>= 1;
					}
				}
				if (k == 1) {
					if (skew >= 0) {
						face(ctx, proj, va, vb, vc, vd, t, U, V, RW, RH, col);
					}
				} else {
					// 면을 k×k로 나눔(평면이라 3차원에서 나눈 뒤 투영하면 정확)
					for (int jj = 0; jj <= k; jj++) {
						double tv = jj / (double) k;
						for (int ii = 0; ii <= k; ii++) {
							double su = ii / (double) k;
							double[] o = sub[jj * (k + 1) + ii];
							for (int c = 0; c < 3; c++) {
								o[c] = va[c] * (1 - su) * (1 - tv) + vb[c] * su * (1 - tv) + vc[c] * su * tv + vd[c] * (1 - su) * tv;
							}
						}
					}
					int rw = t == null ? 16 : RW / k, rh = t == null ? 16 : RH / k;
					for (int jj = 0; jj < k; jj++) {
						for (int ii = 0; ii < k; ii++) {
							double[] p0 = sub[jj * (k + 1) + ii], p1 = sub[jj * (k + 1) + ii + 1];
							double[] p2 = sub[(jj + 1) * (k + 1) + ii + 1], p3 = sub[(jj + 1) * (k + 1) + ii];
							if (proj.quadSkew(p0, p1, p2, p3) < 0) {
								continue;   // 카메라 뒤 조각
							}
							face(ctx, proj, p0, p1, p2, p3, t, t == null ? 0 : U + ii * rw, t == null ? 0 : V + jj * rh, rw, rh, col);
						}
					}
				}
				if (edge != 0) {
					for (int e = 0; e < 4; e++) {
						proj.drawViewSegment(ctx, vbuf[q[e]], vbuf[q[(e + 1) % 4]], EDGE_W, edge);
					}
				}
			}
		}
	}

	private static int clampTex(int v, int w) {
		return Math.max(0, Math.min(w - 1, v));
	}

	/** 면 하나(argb = 진하기와 색 곱하기, 그림이 없으면 채울 색). */
	private static void face(DrawContext ctx, LunaProjection proj, double[] a, double[] b, double[] c, double[] d,
			BlueprintTex.Tex t, int u, int v, int rw, int rh, int argb) {
		// 49-264차: 대각선으로 나눈 두 삼각형(반쪽 그림)으로 정확히 그린다. 반쪽 그림이 아직 없으면(한 프레임에 몇 개씩만 만든다) 예전 방식.
		BlueprintTex.Masked mk = t != null ? BlueprintTex.masked(t, u, v, rw, rh, argb >>> 24) : BlueprintTex.masked(null, 0, 0, 16, 16, argb >>> 24);
		if (mk != null && proj.texTri(ctx, a, b, c, d, mk.a, mk.b, mk.size, argb)) {
			return;
		}
		if (t != null) {
			proj.texViewQuad(ctx, a, b, c, d, t.id, u, v, rw, rh, t.w, t.h, argb);
		} else {
			proj.fillViewQuad(ctx, a, b, c, d, argb);
		}
	}

	/** 면 방향별 밝기(0~256): +x, -x, +y, -y, +z, -z. */
	private static final int[] FACE_SHADE = {154, 154, 256, 128, 205, 205};

	/** 49-268차: 블록 그림에 곱할 색 - '놓을 곳 색'을 '색 입히기'만큼(0 = 흰색 = 그대로). */
	private int holoTint() {
		int c = missArgb();
		int k = tintPct();
		int r = 255 - (255 - ((c >> 16) & 0xFF)) * k / 100, g = 255 - (255 - ((c >> 8) & 0xFF)) * k / 100, b = 255 - (255 - (c & 0xFF)) * k / 100;
		return r << 16 | g << 8 | b;
	}

	private static int mulColor(int a, int b) {
		int r = ((a >> 16) & 0xFF) * ((b >> 16) & 0xFF) / 255, g = ((a >> 8) & 0xFF) * ((b >> 8) & 0xFF) / 255, bl = (a & 0xFF) * (b & 0xFF) / 255;
		return r << 16 | g << 8 | bl;
	}

	private static int mulRgb(int rgb, int shade) {
		int r = ((rgb >> 16) & 0xFF) * shade >> 8, g = ((rgb >> 8) & 0xFF) * shade >> 8, b = (rgb & 0xFF) * shade >> 8;
		return r << 16 | g << 8 | b;
	}

	private boolean neighborHolo(int x, int y, int z) {
		if (x < 0 || y < 0 || z < 0 || x >= bp.w || y >= bp.h || z >= bp.l || !inLayer(y)) {
			return false;
		}
		int idx = bp.index(x, y, z);
		int st = status[idx];
		// 49-258차: 옆 칸이 꽉 찬 블록일 때만 이 면을 가린다(계단, 반 블록 옆면은 보여야 한다)
		// 49-261차(사용자: "블록 자체가 이상해 - 면이 비어 보여"): 옆 칸이 이번에 실제로 그려질 때만 가린다. 가려짐 판정이나 개수 제한으로
		// 빠진 옆 칸이 이 칸의 면만 지워서, 테두리만 남고 속이 빈 면이 생겼다.
		return bp.cells[idx] != Blueprint.AIR && st != Blueprint.OK && st != Blueprint.UNKNOWN && drawnBits.get(idx)
				&& boxesOf(bp.cells[idx]) == null;
	}

	private static final int[][] EDGES = {
		{0, 1}, {1, 3}, {3, 2}, {2, 0}, {4, 5}, {5, 7}, {7, 6}, {6, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}
	};

	private void box(DrawContext ctx, LunaProjection proj, double x0, double y0, double z0, double x1, double y1, double z1,
			float width, int argb) {
		for (int i = 0; i < 8; i++) {
			proj.toView((i & 1) == 0 ? x0 : x1, (i & 4) == 0 ? y0 : y1, (i & 2) == 0 ? z0 : z1, vbuf[i]);
		}
		for (int[] e : EDGES) {
			proj.drawViewSegment(ctx, vbuf[e[0]], vbuf[e[1]], width, argb);
		}
	}
}

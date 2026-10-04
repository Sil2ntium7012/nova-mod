package kr.lunaslight.mod.module.impl.hud;

import kr.lunaslight.mod.gui.LunaGfx;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.util.ArmorBarHook;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * 49-39차: 갑옷 줄 색(사용자: "다이아 모자 + 금 갑옷이면 다이아는 파랑, 금은 노랑으로 갑옷 표시가 뜨는 거야,
 * 인챈트되면 그 갑옷 부분이 반짝이게").
 *
 * 바닐라 갑옷 줄은 10칸(칸 하나 = 방어력 2)을 회색 한 가지로만 채운다. 여기서는 입은 조각(투구 → 흉갑 → 각반 →
 * 부츠 순서)이 내는 방어력만큼을 그 조각의 재질 색으로 칠한다. 조각의 방어력은 재질×부위 표(가죽 1/3/2/1, 사슬
 * 2/5/4/1, 철 2/6/5/2, 금 2/5/3/1, 다이아·네더라이트 3/8/6/3, 거북 2)로 계산하고, 표에 없는(모드) 조각은 실제
 * 방어력(getArmor)에서 남는 만큼을 회색으로. 인챈트된 조각의 칸은 밝기가 숨 쉬듯 오르내리는 글린트.
 *
 * 그리기는 흰 갑옷 스프라이트(textures/gui/armor_w_{full,half,rhalf}.png)에 색을 곱한다. 두 조각이 한 칸을 나눠
 * 가지면 왼쪽 반 + 오른쪽 반을 각각 칠한다. 실제 호출은 ArmorBarHook(믹스인)에서.
 *
 * 49-91차(8-1, 사용자: "색 더 밝은 계열, 모르는 갑옷 색 제거, 갑옷별 색·반짝임 색 고르게"): 재질마다 색 설정
 * 하나씩(기본값을 한 단계 밝게), 반짝임 색 설정. "모르는 갑옷 색"은 지웠다 - 표에 없는 조각은 그냥 밝은 회색.
 */
public class ArmorBarColorModule extends Module {

	private final BooleanSetting glint = register(new BooleanSetting(
			"glint", "인챈트 반짝임", "인챈트된 조각의 칸이 은은하게 반짝입니다.", true));

	private final ColorSetting glintColor = register(new ColorSetting(
			"glint_color", "반짝임 색", "인챈트 반짝임의 색입니다.", 0xFFFFFFFF));

	private final ColorSetting leatherColor = register(new ColorSetting("leather", "가죽", "가죽 갑옷 칸의 색입니다.", 0xFFE0955A));
	private final ColorSetting chainColor = register(new ColorSetting("chainmail", "사슬", "사슬 갑옷 칸의 색입니다.", 0xFFBCC4CC));
	private final ColorSetting ironColor = register(new ColorSetting("iron", "철", "철 갑옷 칸의 색입니다.", 0xFFF4F6F8));
	private final ColorSetting goldColor = register(new ColorSetting("gold", "금", "금 갑옷 칸의 색입니다.", 0xFFFFE04D));
	private final ColorSetting diamondColor = register(new ColorSetting("diamond", "다이아몬드", "다이아몬드 갑옷 칸의 색입니다.", 0xFF7EDBFF));
	private final ColorSetting netheriteColor = register(new ColorSetting("netherite", "네더라이트", "네더라이트 갑옷 칸의 색입니다.", 0xFF9C8A8A));
	private final ColorSetting turtleColor = register(new ColorSetting("turtle", "거북", "거북 등딱지 칸의 색입니다.", 0xFF6ED97F));

	/** 표에 없는(모드·플러그인) 조각 - 밝은 회색. 설정으로 두지 않는다(8-1). */
	private static final int UNKNOWN_COLOR = 0xFFD6DAE0;

	private static final int[] PIECE_SLOTS = {39, 38, 37, 36};   // 투구, 흉갑, 각반, 부츠 (PlayerInventory)

	private enum Material {
		LEATHER(1, 3, 2, 1),
		CHAINMAIL(2, 5, 4, 1),
		IRON(2, 6, 5, 2),
		GOLD(2, 5, 3, 1),
		DIAMOND(3, 8, 6, 3),
		NETHERITE(3, 8, 6, 3),
		TURTLE(2, 0, 0, 0),
		UNKNOWN(0, 0, 0, 0);

		final int[] points;   // 투구, 흉갑, 각반, 부츠

		Material(int helmet, int chest, int legs, int boots) {
			this.points = new int[]{helmet, chest, legs, boots};
		}
	}

	private int colorOf(Material m) {
		return switch (m) {
			case LEATHER -> leatherColor.getArgb();
			case CHAINMAIL -> chainColor.getArgb();
			case IRON -> ironColor.getArgb();
			case GOLD -> goldColor.getArgb();
			case DIAMOND -> diamondColor.getArgb();
			case NETHERITE -> netheriteColor.getArgb();
			case TURTLE -> turtleColor.getArgb();
			default -> UNKNOWN_COLOR;
		};
	}

	/** 색 설정을 바꾸면 캐시가 바로 새로 계산되게 해시에 섞는다. */
	private int colorsHash() {
		return leatherColor.getArgb() * 31 + chainColor.getArgb() * 37 + ironColor.getArgb() * 41 + goldColor.getArgb() * 43
			+ diamondColor.getArgb() * 47 + netheriteColor.getArgb() * 53 + turtleColor.getArgb() * 59;
	}

	private final int[] halfColor = new int[20];       // 반 칸(방어력 1)마다 색, 0 = 비어 있음
	private final boolean[] halfGlint = new boolean[20];
	private long cacheAt;
	private int cacheHash;

	public ArmorBarColorModule() {
		super("armor_bar_color", "갑옷 줄 색", ModuleCategory.HUD, "입은 갑옷별 색 | 인챈트 반짝임");
		defaultEnabled(true);
		glint.withColor(glintColor);
		ArmorBarHook.set(new ArmorBarHook.Handler() {
			@Override
			public boolean drawRow(Object ctx, int x, int y) {
				return ArmorBarColorModule.this.drawRow(ctx, x, y);
			}

			@Override
			public boolean drawSlot(Object ctx, int x, int y, int slot) {
				return ArmorBarColorModule.this.drawSlot(ctx, x, y, slot);
			}
		});
	}

	// ==================== 조각 → 반 칸 색 ====================

	private boolean refresh() {
		if (!isEnabled() || client == null || client.player == null) {
			return false;
		}
		PlayerEntity player = client.player;
		int armor;
		try {
			armor = Math.max(0, Math.min(20, player.getArmor()));
		} catch (Throwable t) {
			return false;
		}
		if (armor <= 0) {
			return false;   // 갑옷 없음 → 바닐라도 안 그림
		}
		long now = System.currentTimeMillis();
		int hash = armor * 31 + colorsHash();
		ItemStack[] pieces = new ItemStack[4];
		Object inv = LunaCompat.getPlayerInventory(player);
		for (int i = 0; i < 4; i++) {
			ItemStack s = LunaCompat.invGetStack(inv, PIECE_SLOTS[i]);
			pieces[i] = s == null ? ItemStack.EMPTY : s;
			hash = hash * 31 + (pieces[i].isEmpty() ? 0 : System.identityHashCode(pieces[i].getItem()) + (pieces[i].hasEnchantments() ? 7 : 0));
		}
		if (hash == cacheHash && now - cacheAt < 500) {
			return true;
		}
		cacheHash = hash;
		cacheAt = now;
		java.util.Arrays.fill(halfColor, 0);
		java.util.Arrays.fill(halfGlint, false);

		int[] pts = new int[4];
		int[] col = new int[4];
		boolean[] ench = new boolean[4];
		int known = 0;
		int unknownPieces = 0;
		for (int i = 0; i < 4; i++) {
			ItemStack s = pieces[i];
			if (s.isEmpty()) {
				continue;
			}
			Material m = materialOf(s);
			ench[i] = s.hasEnchantments();
			if (m == Material.UNKNOWN) {
				col[i] = UNKNOWN_COLOR;
				pts[i] = -1;
				unknownPieces++;
			} else {
				col[i] = colorOf(m);
				pts[i] = m.points[i];
				known += pts[i];
			}
		}
		// 모드 갑옷: 실제 방어력에서 표로 아는 만큼을 뺀 나머지를 나눠 준다
		int rest = Math.max(0, armor - known);
		for (int i = 0; i < 4; i++) {
			if (pts[i] < 0) {
				int share = unknownPieces > 0 ? rest / unknownPieces : 0;
				pts[i] = share;
				rest -= share;
				unknownPieces--;
			}
		}
		// 반 칸 채우기(투구부터). 실제 방어력(armor)을 넘지 않게.
		int pos = 0;
		for (int i = 0; i < 4 && pos < armor; i++) {
			for (int k = 0; k < pts[i] && pos < armor; k++) {
				halfColor[pos] = col[i];
				halfGlint[pos] = ench[i];
				pos++;
			}
		}
		// 표가 실제보다 적게 잡았으면(서버 플러그인 등) 남는 반 칸은 회색
		while (pos < armor) {
			halfColor[pos++] = UNKNOWN_COLOR;
		}
		return true;
	}

	private static Material materialOf(ItemStack stack) {
		String id;
		try {
			id = LunaCompat.registryIdOf("ITEM", stack.getItem());
		} catch (Throwable t) {
			id = null;
		}
		if (id == null) {
			return Material.UNKNOWN;
		}
		String p = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		if (p.startsWith("netherite_")) return Material.NETHERITE;
		if (p.startsWith("diamond_")) return Material.DIAMOND;
		if (p.startsWith("golden_") || p.startsWith("gold_")) return Material.GOLD;
		if (p.startsWith("iron_")) return Material.IRON;
		if (p.startsWith("chainmail_")) return Material.CHAINMAIL;
		if (p.startsWith("leather_")) return Material.LEATHER;
		if (p.startsWith("turtle_")) return Material.TURTLE;
		return Material.UNKNOWN;
	}

	// ==================== 그리기 ====================

	private boolean drawRow(Object ctxObj, int x, int y) {
		if (!(ctxObj instanceof DrawContext ctx) || !refresh()) {
			return false;
		}
		for (int i = 0; i < 10; i++) {
			drawIcon(ctx, x + i * 8, y, i);
		}
		return true;
	}

	private boolean drawSlot(Object ctxObj, int x, int y, int slot) {
		if (!(ctxObj instanceof DrawContext ctx) || !refresh()) {
			return false;
		}
		drawIcon(ctx, x, y, slot);
		return true;
	}

	private static final boolean SPRITE_ERA = kr.lunaslight.mod.util.LunaVersion.isWithin("1.20.2", null);

	private void drawIcon(DrawContext ctx, int x, int y, int slot) {
		int left = halfColor[slot * 2];
		int right = halfColor[slot * 2 + 1];
		drawEmpty(ctx, x, y);
		if (left == 0 && right == 0) {
			return;
		}
		if (left != 0 && right != 0 && left == right) {
			tex(ctx, "armor_w_full", x, y, left);
		} else {
			if (left != 0) {
				tex(ctx, "armor_w_half", x, y, left);
			}
			if (right != 0) {
				tex(ctx, "armor_w_rhalf", x, y, right);
			}
		}
		if (glint.get()) {
			boolean gl = halfGlint[slot * 2], gr = halfGlint[slot * 2 + 1];
			if (gl || gr) {
				// 숨 쉬듯 밝아졌다 어두워지는 글린트(칸마다 위상이 조금씩 달라 물결처럼)
				double t = System.currentTimeMillis() / 1000.0;
				float a = (float) (0.16 + 0.24 * (0.5 + 0.5 * Math.sin(t * 5.0 + slot * 0.9)));
				int white = (Math.round(a * 255) << 24) | (glintColor.getArgb() & 0xFFFFFF);
				if (gl && gr) {
					tex(ctx, "armor_w_full", x, y, white);
				} else if (gl) {
					tex(ctx, "armor_w_half", x, y, white);
				} else {
					tex(ctx, "armor_w_rhalf", x, y, white);
				}
			}
		}
	}

	private void drawEmpty(DrawContext ctx, int x, int y) {
		boolean ok;
		if (SPRITE_ERA) {
			ok = LunaGfx.drawTex(ctx, LunaGfx.mcId("textures/gui/sprites/hud/armor_empty.png"), x, y, 9, 9, 0, 0, 9, 9, 9, 0xFFFFFFFF);
		} else {
			Identifier icons = LunaGfx.mcId("textures/gui/icons.png");
			ok = LunaGfx.drawTex(ctx, icons, x, y, 9, 9, 16, 9, 9, 9, 256, 0xFFFFFFFF);
		}
		if (!ok) {
			ctx.fill(x + 1, y + 1, x + 8, y + 8, 0xFF3D3D3D);
		}
	}

	private void tex(DrawContext ctx, String name, int x, int y, int argb) {
		if (!LunaGfx.drawTex(ctx, LunaGfx.id("textures/gui/" + name + ".png"), x, y, 9, 9, 0, 0, 9, 9, 9, argb)) {
			int w = name.endsWith("full") ? 7 : 3;
			int ox = name.endsWith("rhalf") ? 5 : 1;
			ctx.fill(x + ox, y + 1, x + ox + w, y + 8, argb);
		}
	}

	@Override
	public boolean hasPreview() {
		return true;
	}

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		if (!isPreview()) {
			return;   // 실제 그리기는 ArmorBarHook(믹스인)에서 바닐라 갑옷 자리 그대로
		}
		// 미리보기: 다이아 투구(1.5칸, 인챈트) + 금 흉갑(2.5칸)
		int hx = previewCenterX() - 20;
		int hy = previewCenterY() - 4;
		int d = colorOf(Material.DIAMOND), g = colorOf(Material.GOLD);
		int[] cols = {d, d, d, g, g, g, g, g, 0, 0};
		java.util.Arrays.fill(halfColor, 0);
		java.util.Arrays.fill(halfGlint, false);
		for (int i = 0; i < cols.length; i++) {
			halfColor[i] = cols[i];
			halfGlint[i] = i < 3;
		}
		cacheHash = -1;   // 실제 렌더 때 다시 계산하게
		for (int i = 0; i < 5; i++) {
			drawIcon(context, hx + i * 8, hy, i);
		}
	}
}

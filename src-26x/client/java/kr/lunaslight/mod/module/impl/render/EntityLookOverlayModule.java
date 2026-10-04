package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.gui.LunaGfx;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaVersion;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 엔티티 정보 - 바라보는 생명체의 이름 / 체력 / 방어력 / 효과 / (옵션) 죽였을 때 나오는 아이템.
 *
 * 49-21차 재작업(사용자 5건):
 *  ① 방어력을 숫자 대신 **바닐라 갑옷 아이콘**으로(2방어 = 아이콘 1개, 반 칸 지원). 1.20.2+는 스프라이트
 *     파일(hud/armor_full.png 9×9), 그 전은 icons.png(34,9)/(25,9) 영역 - LunaGfx.drawTex.
 *  ② 기본 위치를 위로(상단 중앙 y=8) - 시야 가림 해소. (HUD 배치 버전 올려서 기존 설정도 이동)
 *  ③ 세로 크기 축소: 이름+갑옷 한 줄, 체력 바 한 줄(6px)로 압축(예전 ~50px → 30px대).
 *  ④ 체력이 닳을 때 **빨간 잔상**이 뒤따라 줄어드는 애니메이션(엔티티별 지연 체력 추적).
 *  ⑤ 죽였을 때 드롭 아이템(바닐라 드롭 표, 내장)을 아이콘 + 개수 범위로. 설정으로 끌 수 있음.
 *     ※ 클라이언트는 서버의 루트 테이블을 알 수 없어 바닐라 기본값만 표시(모드/데이터팩 변경분은 반영 안 됨).
 */
public class EntityLookOverlayModule extends Module {

	// 49-22차: "훨씬 위로"(나침반을 가려도 됨) - 상단 중앙 y=6. HUD 배치 버전을 올려 기존 설정도 이동.
	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "표시 위치입니다.", HudPosition.of(HudPosition.Anchor.TOP_CENTER, 0, 42)));

	// 49-22차: "가까워야 보이게" - 기본 10블록.
	private final IntSetting range = register(new IntSetting(
			"range", "감지 거리", "엔티티를 감지하는 거리(블록)입니다.", 10, 2, 64, 1).unit("블록"));

	private final BooleanSetting showArmor = register(new BooleanSetting(
			"show_armor", "방어력", "방어력을 갑옷 아이콘으로 표시합니다.", true));

	private final BooleanSetting showEffects = register(new BooleanSetting(
			"show_effects", "효과", "걸린 포션 효과를 표시합니다.", true));

	private final BooleanSetting showDrops = register(new BooleanSetting(
			"show_drops", "드롭", "죽였을 때 나오는 아이템을 표시합니다.", true));

	private final BooleanSetting damageAnim = register(new BooleanSetting(
			"damage_anim", "감소 효과", "닳는 체력을 빨간 잔상으로 보여줍니다.", true));

	public EntityLookOverlayModule() {
		super("entity_look_overlay", "엔티티 정보", ModuleCategory.HUD, "바라보는 생명체의 체력 | 방어 | 효과 | 드롭");
		// 49-156차(사용자: "배경 설정 따로, 배경 색도 조절"): 이 기능도 [배경] [배경 색] [윤곽선] [배경 모양] 설정을 갖는다.
		enableHudStyle(0xD20E1013, true, 0x24FFFFFF, kr.lunaslight.mod.module.Module.HudShape.SQUARE);
	}

	/** 49-23차: 나침반(상단 중앙) 위에 덮이도록 나중에 그림. */
	@Override
	public int renderOrder() {
		return 10;
	}

	// ==================== 대상 찾기 ====================

	// 49-22차: 효과 이름은 효과 타입 객체별로 한 번만 풀어서 캐시(매 프레임 리플렉션 + Text 생성 제거).
	private static final Map<Object, String> EFFECT_NAMES = new java.util.IdentityHashMap<>();

	private static String resolveEffectName(Object effectType) {
		String cached = EFFECT_NAMES.get(effectType);
		if (cached != null) {
			return cached;
		}
		Object effect = effectType;
		try {
			java.lang.reflect.Method value = LunaCompat.findNoArgMethod(effectType.getClass(), "value");
			if (value != null) {
				effect = value.invoke(effectType);
			}
		} catch (Throwable ignored) {
		}
		String name = ((MobEffect) effect).getDisplayName().getString();
		if (EFFECT_NAMES.size() > 256) {
			EFFECT_NAMES.clear();
		}
		EFFECT_NAMES.put(effectType, name);
		return name;
	}

	// 49-22차(프레임 드랍 대책): 월드의 모든 엔티티를 **매 프레임** 광선 검사하던 것을 **틱마다 한 번**
	// (20회/초)으로 내리고, 검사 대상도 감지 거리 안의 생명체(getEntitiesByClass + 상자)로 좁힘.
	// 크로스헤어에 직접 잡힌 엔티티(바닐라가 이미 계산해 둔 것)는 매 프레임 그대로 씀.
	private LivingEntity scannedTarget;

	@Override
	public void onTick() {
		scannedTarget = scanTarget();
	}

	private LivingEntity resolveTarget() {
		HitResult hit = client.hitResult;
		if (hit instanceof EntityHitResult entityHit) {
			var entity = entityHit.getEntity();
			if (entity instanceof HangingEntity) {
				return null;
			}
			if (entity instanceof LivingEntity living) {
				return living;
			}
		}
		LivingEntity t = scannedTarget;
		if (t != null && !t.isAlive()) {
			return null;
		}
		return t;
	}

	private LivingEntity scanTarget() {
		if (client.level == null || client.player == null) {
			return null;
		}
		HitResult hit = client.hitResult;
		net.minecraft.world.phys.Vec3 start = client.player.getEyePosition(1f);
		net.minecraft.world.phys.Vec3 dir = client.player.getViewVector(1f);
		double maxDist = range.get();
		if (hit instanceof net.minecraft.world.phys.BlockHitResult blockHit
				&& hit.getType() == HitResult.Type.BLOCK) {
			maxDist = Math.min(maxDist, Math.sqrt(start.distanceToSqr(blockHit.getLocation())));
		}
		if (maxDist <= 0) {
			return null;
		}
		net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(
				start.x, start.y, start.z, start.x + dir.x * maxDist, start.y + dir.y * maxDist, start.z + dir.z * maxDist)
				.inflate(1.5);
		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (LivingEntity living : LunaCompat.getEntitiesByClass(client.level, LivingEntity.class, area)) {
			if (living == client.player || !living.isAlive()) {
				continue;
			}
			double d = rayBox(start, dir, living.getBoundingBox().inflate(0.2), maxDist);
			if (d >= 0 && d < bestDist) {
				bestDist = d;
				best = living;
			}
		}
		return best;
	}

	private static double rayBox(net.minecraft.world.phys.Vec3 o, net.minecraft.world.phys.Vec3 d, net.minecraft.world.phys.AABB b, double maxT) {
		double tMin = 0, tMax = maxT;
		double[] os = {o.x, o.y, o.z}, ds = {d.x, d.y, d.z};
		double[] bb = LunaCompat.boxBounds(b); // 49-36차: 1.15.2는 필드가 x1..z2
		double[] mins = {bb[0], bb[1], bb[2]}, maxs = {bb[3], bb[4], bb[5]};
		for (int i = 0; i < 3; i++) {
			if (Math.abs(ds[i]) < 1e-9) {
				if (os[i] < mins[i] || os[i] > maxs[i]) {
					return -1;
				}
				continue;
			}
			double inv = 1.0 / ds[i];
			double t1 = (mins[i] - os[i]) * inv;
			double t2 = (maxs[i] - os[i]) * inv;
			if (t1 > t2) {
				double tmp = t1; t1 = t2; t2 = tmp;
			}
			tMin = Math.max(tMin, t1);
			tMax = Math.min(tMax, t2);
			if (tMin > tMax) {
				return -1;
			}
		}
		return tMin;
	}

	// ==================== 체력 잔상 ====================

	private final Map<UUID, float[]> lagHp = new HashMap<>(); // [지연 체력, 마지막 갱신 nanos]
	private long lastCleanup;

	private float lagFor(LivingEntity e, float hp) {
		long now = System.nanoTime();
		UUID id = e.getUUID();
		float[] st = lagHp.get(id);
		if (st == null) {
			st = new float[]{hp, now};
			lagHp.put(id, st);
		}
		float dt = Math.min(0.1f, (now - (long) st[1]) / 1_000_000_000f);
		st[1] = now;
		if (hp >= st[0]) {
			st[0] = hp; // 회복/초기화는 즉시
		} else {
			// 잔상: 차이가 클수록 빨리, 최소 초당 4칸
			float speed = Math.max(4f, (st[0] - hp) * 2.5f);
			st[0] = Math.max(hp, st[0] - speed * dt);
		}
		if (now - lastCleanup > 5_000_000_000L) {
			lastCleanup = now;
			lagHp.entrySet().removeIf(en -> now - (long) en.getValue()[1] > 10_000_000_000L);
		}
		return st[0];
	}

	// ==================== 드롭 표(바닐라 기본) ====================

	private record Drop(String item, int min, int max) {
	}

	private static final Map<String, Drop[]> DROPS = new HashMap<>();

	private static void d(String mob, String... specs) {
		Drop[] arr = new Drop[specs.length];
		for (int i = 0; i < specs.length; i++) {
			String[] p = specs[i].split(":");
			String[] r = p[1].split("-");
			arr[i] = new Drop(p[0], Integer.parseInt(r[0]), Integer.parseInt(r.length > 1 ? r[1] : r[0]));
		}
		DROPS.put("minecraft:" + mob, arr);
	}

	static {
		d("zombie", "rotten_flesh:0-2");
		d("husk", "rotten_flesh:0-2");
		d("drowned", "rotten_flesh:0-2");
		d("zombie_villager", "rotten_flesh:0-2");
		d("skeleton", "bone:0-2", "arrow:0-2");
		d("stray", "bone:0-2", "arrow:0-2");
		d("bogged", "bone:0-2", "arrow:0-2");
		d("wither_skeleton", "bone:0-2", "coal:0-1");
		d("creeper", "gunpowder:0-2");
		d("spider", "string:0-2", "spider_eye:0-1");
		d("cave_spider", "string:0-2", "spider_eye:0-1");
		d("enderman", "ender_pearl:0-1");
		d("witch", "glass_bottle:0-2", "redstone:0-2", "gunpowder:0-2", "sugar:0-2", "stick:0-2", "spider_eye:0-2", "glowstone_dust:0-2");
		d("slime", "slime_ball:0-2");
		d("magma_cube", "magma_cream:0-1");
		d("blaze", "blaze_rod:0-1");
		d("breeze", "breeze_rod:1-2");
		d("ghast", "ghast_tear:0-1", "gunpowder:0-2");
		d("zombified_piglin", "rotten_flesh:0-1", "gold_nugget:0-1");
		d("zombie_pigman", "rotten_flesh:0-1", "gold_nugget:0-1");
		d("hoglin", "porkchop:2-4", "leather:0-1");
		d("guardian", "prismarine_shard:0-2", "cod:0-1");
		d("elder_guardian", "prismarine_shard:0-2", "wet_sponge:1-1");
		d("phantom", "phantom_membrane:0-1");
		d("shulker", "shulker_shell:0-1");
		d("vindicator", "emerald:0-1");
		d("evoker", "totem_of_undying:1-1", "emerald:0-1");
		d("ravager", "saddle:1-1");
		d("warden", "sculk_catalyst:1-1");
		d("iron_golem", "iron_ingot:3-5", "poppy:0-2");
		d("snow_golem", "snowball:0-15");
		d("wither", "nether_star:1-1");
		d("strider", "string:2-5");
		d("cow", "leather:0-2", "beef:1-3");
		d("mooshroom", "leather:0-2", "beef:1-3");
		d("sheep", "white_wool:1-1", "mutton:1-2");
		d("pig", "porkchop:1-3");
		d("chicken", "feather:0-2", "chicken:1-1");
		d("rabbit", "rabbit_hide:0-1", "rabbit:0-1");
		d("horse", "leather:0-2");
		d("donkey", "leather:0-2");
		d("mule", "leather:0-2");
		d("llama", "leather:0-2");
		d("squid", "ink_sac:1-3");
		d("glow_squid", "glow_ink_sac:1-3");
		d("cod", "cod:1-1");
		d("salmon", "salmon:1-1");
		d("tropical_fish", "tropical_fish:1-1");
		d("pufferfish", "pufferfish:1-1");
		d("dolphin", "cod:0-1");
		d("turtle", "seagrass:0-2");
		d("polar_bear", "cod:0-2", "salmon:0-2");
		d("panda", "bamboo:0-2");
		d("cat", "string:0-2");
		d("parrot", "feather:1-2");
	}

	private final Map<String, List<Object[]>> dropCache = new HashMap<>(); // mobId → [ItemStack, "0-2"]

	private List<Object[]> dropsFor(LivingEntity e) {
		String id;
		try {
			Identifier ident = EntityType.getKey(e.getType());
			id = ident == null ? null : ident.toString();
		} catch (Throwable t) {
			return List.of();
		}
		if (id == null) {
			return List.of();
		}
		List<Object[]> cached = dropCache.get(id);
		if (cached != null) {
			return cached;
		}
		List<Object[]> list = new ArrayList<>();
		Drop[] drops = DROPS.get(id);
		if (drops != null) {
			for (Drop dr : drops) {
				Item item = LunaCompat.itemById("minecraft:" + dr.item);
				if (item == null) {
					continue; // 이 버전에 없는 아이템
				}
				String rangeText = dr.min == dr.max ? Integer.toString(dr.min) : dr.min + "-" + dr.max;
				list.add(new Object[]{new ItemStack(item), rangeText});
			}
		}
		dropCache.put(id, list);
		return list;
	}

	// ==================== 갑옷 아이콘 ====================

	private static final boolean SPRITE_ERA = LunaVersion.isWithin("1.20.2", null);
	private static Identifier armorFull, armorHalf, iconsPng;

	private void drawArmorIcon(GuiGraphicsExtractor ctx, int x, int y, boolean half) {
		if (SPRITE_ERA) {
			if (armorFull == null) {
				armorFull = LunaGfx.mcId("textures/gui/sprites/hud/armor_full.png");
				armorHalf = LunaGfx.mcId("textures/gui/sprites/hud/armor_half.png");
			}
			if (!LunaGfx.drawTex(ctx, half ? armorHalf : armorFull, x, y, 9, 9, 0, 0, 9, 9, 9, 0xFFFFFFFF)) {
				fallbackArmor(ctx, x, y, half);
			}
		} else {
			if (iconsPng == null) {
				iconsPng = LunaGfx.mcId("textures/gui/icons.png");
			}
			if (!LunaGfx.drawTex(ctx, iconsPng, x, y, 9, 9, half ? 25 : 34, 9, 9, 9, 256, 0xFFFFFFFF)) {
				fallbackArmor(ctx, x, y, half);
			}
		}
	}

	private static void fallbackArmor(GuiGraphicsExtractor ctx, int x, int y, boolean half) {
		ctx.fill(x + 1, y + 1, x + (half ? 5 : 8), y + 8, 0xFFB8C0C8);
	}

	// ==================== 렌더 ====================

	/** 미리보기용 예시 드롭(썩은 살점 0-2). */
	private List<Object[]> previewDrops;

	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		if (isPreview()) {
			// 49-22차: 설정 화면 미리보기 - 예시 데이터(좀비, 체력 14/20, 방어 5, 효과, 드롭)로 그림
			if (previewDrops == null) {
				previewDrops = new ArrayList<>();
				Item rotten = LunaCompat.itemById("minecraft:rotten_flesh");
				if (rotten != null) {
					previewDrops.add(new Object[]{new ItemStack(rotten), "0-2"});
				}
			}
			List<String> fx = new ArrayList<>();
			if (showEffects.get()) {
				fx.add("신속 2");
			}
			// 49-88차(8-12): 감소 효과를 끄면 미리보기의 빨간 잔량 띠도 없다
			drawPanel(context, "좀비", 14f, 20f, showArmor.get() ? 5 : 0, fx, showDrops.get() ? previewDrops : List.of(),
				damageAnim.get() ? 0.82f : 14f / 20f);
			return;
		}
		LivingEntity target = resolveTarget();
		if (target == null) {
			return;
		}
		String name = target.getDisplayName().getString();
		float hp = Math.max(0f, target.getHealth());
		float maxHp = Math.max(1f, (float) LunaCompat.getMaxHealth(target));
		int armor = showArmor.get() ? Math.max(0, Math.min(20, target.getArmorValue())) : 0;

		List<String> effects = new ArrayList<>();
		if (showEffects.get()) {
			Collection<MobEffectInstance> list = target.getActiveEffects();
			for (MobEffectInstance effect : list) {
				try {
					effects.add(resolveEffectName(effect.getEffect()) + (effect.getAmplifier() > 0 ? " " + (effect.getAmplifier() + 1) : ""));
				} catch (Throwable ignored) {
				}
			}
		}
		List<Object[]> drops = showDrops.get() ? dropsFor(target) : List.of();
		float lag = damageAnim.get() ? Math.min(1f, lagFor(target, hp) / maxHp) : Math.min(1f, hp / maxHp);
		drawPanel(context, name, hp, maxHp, armor, effects, drops, lag);
	}

	private void drawPanel(GuiGraphicsExtractor context, String name, float hp, float maxHp, int armor,
			List<String> effects, List<Object[]> drops, float lag) {

		int fontH = client.font.lineHeight;
		int pad = 4; // 49-24차: "위로 너무 커" - 여백 6→4, 줄 간격 3→2, 바 6→5로 세로 압축
		int nameW = LunaCompat.getTextWidth(client.font, name);
		int armorIcons = (armor + 1) / 2;
		int armorW = armor > 0 ? armorIcons * 8 + 1 : 0;
		int row1W = nameW + (armorW > 0 ? 8 + armorW : 0);
		String effectLine = String.join(" | ", effects);
		int effectW = effects.isEmpty() ? 0 : LunaCompat.getTextWidth(client.font, effectLine);

		int dropW = 0;
		for (Object[] dr : drops) {
			dropW += 16 + 2 + LunaCompat.getTextWidth(client.font, (String) dr[1]) + 6;
		}
		int barH = 5;
		int panelW = Math.max(110, Math.max(row1W, Math.max(effectW, dropW))) + pad * 2;
		int panelH = pad + fontH + 2 + barH + (effects.isEmpty() ? 0 : 2 + fontH) + (drops.isEmpty() ? 0 : 3 + 16) + pad - 1;

		int x, y;
		if (isPreviewBoxed()) {
			x = previewCenterX() - panelW / 2;
			y = previewCenterY() - panelH / 2;
		} else {
			int sw = client.getWindow().getGuiScaledWidth();
			x = position.get().resolveX(sw, panelW);
			y = position.get().resolveY(client.getWindow().getGuiScaledHeight(), panelH);
		}

		// 49-22차: "깎임 없는 완전 네모" - 둥근 모서리 제거
		drawHudBox(context, x, y, panelW, panelH);   // 49-156차: 이 기능의 [배경] 설정대로

		// 1행: 이름(왼쪽) + 갑옷 아이콘(오른쪽)
		int cy = y + pad;
		LunaCompat.drawHudText(context, client.font, name, x + pad, cy, 0xFFFFFFFF);
		if (armor > 0) {
			int ax = x + panelW - pad - armorW;
			for (int i = 0; i < armorIcons; i++) {
				boolean half = (i == armorIcons - 1) && (armor % 2 == 1);
				drawArmorIcon(context, ax + i * 8, cy - 1, half);
			}
		}
		cy += fontH + 2;

		// 2행: 체력 바(트랙 + 빨간 잔상 + 채움) + 수치
		int bx = x + pad, bw = panelW - pad * 2;
		float pct = Math.min(1f, hp / maxHp);
		int fill = pct > 0.6f ? 0xFF7ED957 : (pct > 0.25f ? 0xFFF5C542 : 0xFFE05A50);
		context.fill(bx, cy, bx + bw, cy + barH, 0xC8202429);
		int lagW = Math.round((bw - 2) * lag);
		int fillW = Math.round((bw - 2) * pct);
		if (lagW > fillW) {
			context.fill(bx + 1, cy + 1, bx + 1 + lagW, cy + barH - 1, 0xFFC0392B);
		}
		if (fillW > 0) {
			context.fill(bx + 1, cy + 1, bx + 1 + fillW, cy + barH - 1, fill);
		}
		String hpText = Math.round(hp) + " / " + Math.round(maxHp);
		int hpW = LunaCompat.getTextWidth(client.font, hpText);
		// 수치는 바 오른쪽 위 작게(바 안에 넣기엔 6px이라 안 들어감) - 이름 줄 갑옷 왼쪽에 공간이 있으면 거기
		int hpX = x + panelW - pad - hpW - (armorW > 0 ? armorW + 6 : 0);
		if (hpX > x + pad + nameW + 6) {
			LunaCompat.drawHudText(context, client.font, hpText, hpX, y + pad, 0xFFB8C0C8);
		}
		cy += barH + 2;

		// 3행: 효과
		if (!effects.isEmpty()) {
			LunaCompat.drawHudText(context, client.font, effectLine, x + pad, cy, 0xFFD7DBE0);
			cy += fontH + 2;
		}

		// 4행: 드롭 아이템
		if (!drops.isEmpty()) {
			cy += 1;
			int dx = x + pad;
			for (Object[] dr : drops) {
				context.item((ItemStack) dr[0], dx, cy);
				String rt = (String) dr[1];
				LunaCompat.drawHudText(context, client.font, rt, dx + 18, cy + Math.round(8f - LunaCompat.textVisualCenter()), 0xFFB8C0C8);
				dx += 16 + 2 + LunaCompat.getTextWidth(client.font, rt) + 6;
			}
		}
	}
}

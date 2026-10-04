package kr.lunaslight.mod.module.impl.inventory;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.EnumSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * 갑옷 표시 - 착용 중인 갑옷(투구/흉갑/레깅스/부츠)과 보조손 아이템을 세로로 상시 표시.
 * 49-21차: 내구도 표시 추가(사용자 요청) - 아이콘 옆에 남은 내구도를 숫자/퍼센트/막대로.
 * ItemStack#getDamage/getMaxDamage/isDamageable은 1.15.2~1.21.11 전부 같은 이름(tiny 실측).
 */
public class ArmorHudModule extends Module {

	public enum Durability {
		OFF, NUMBER, PERCENT, BAR;

		@Override
		public String toString() {
			return switch (this) {
				case OFF -> "끔";
				case NUMBER -> "숫자";
				case PERCENT -> "퍼센트";
				case BAR -> "막대";
			};
		}
	}

	private static final EquipmentSlot[] ARMOR_SLOTS = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private final PositionSetting position;
	private final BooleanSetting showOffhand;
	private final EnumSetting<Durability> durability;

	public ArmorHudModule() {
		super("armor_hud", "갑옷", ModuleCategory.HUD, "착용한 갑옷과 보조손 아이템");
		// 49-22차: 기본 위치 오른쪽 아래 모서리(사용자 요청) - 키스트로크는 그 왼쪽으로 옮김
		position = register(new PositionSetting("position", "위치", "표시 위치입니다.",
			HudPosition.of(HudPosition.Anchor.BOTTOM_RIGHT, 6, 6)));
		showOffhand = register(new BooleanSetting("show_offhand", "보조손", "보조손 아이템도 표시합니다.", true));
		durability = register(new EnumSetting<>("durability", "내구도", "내구도를 표시하는 방식입니다.", Durability.NUMBER, Durability.class));
	}

	private static int durabilityColor(float ratio) {
		// 초록(1.0) → 노랑(0.5) → 빨강(0.0)
		float r = ratio < 0.5f ? 1f : 1f - (ratio - 0.5f) * 2f;
		float g = ratio > 0.5f ? 1f : ratio * 2f;
		return 0xFF000000 | (Math.round(r * 230) << 16) | (Math.round(g * 210) << 8) | 60;
	}

	private String durabilityText(ItemStack stack) {
		if (durability.get() == Durability.OFF || !stack.isDamageableItem()) {
			return null;
		}
		int max = stack.getMaxDamage();
		if (max <= 0) {
			return null;
		}
		int left = max - stack.getDamageValue();
		return switch (durability.get()) {
			case NUMBER -> Integer.toString(left);
			case PERCENT -> Math.round(left * 100f / max) + "%";
			default -> null;
		};
	}

	/** 미리보기용 예시 장비(다이아 세트, 내구도 일부 소모 + 보조손 방패). */
	private ItemStack[] previewStacks;

	private ItemStack[] previewStacks() {
		if (previewStacks == null) {
			String[] ids = {"minecraft:diamond_helmet", "minecraft:diamond_chestplate", "minecraft:diamond_leggings",
				"minecraft:diamond_boots", "minecraft:shield"};
			int[] damage = {40, 120, 200, 30, 10};
			previewStacks = new ItemStack[ids.length];
			for (int i = 0; i < ids.length; i++) {
				var item = LunaCompat.itemById(ids[i]);
				ItemStack st = item == null ? ItemStack.EMPTY : new ItemStack(item);
				try {
					if (!st.isEmpty() && st.isDamageableItem()) {
						st.setDamageValue(damage[i]);
					}
				} catch (Throwable ignored) {
				}
				previewStacks[i] = st;
			}
		}
		return previewStacks;
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		if (client.player == null && !isPreview()) {
			return;
		}
		int iconSize = 16;
		int spacing = 2;
		boolean textMode = durability.get() == Durability.NUMBER || durability.get() == Durability.PERCENT;
		boolean barMode = durability.get() == Durability.BAR;

		// 표시할 항목 수집(빈 칸은 자리만 차지)
		ItemStack[] stacks;
		if (isPreview()) {
			ItemStack[] pv = previewStacks();
			stacks = showOffhand.get() ? pv : java.util.Arrays.copyOf(pv, ARMOR_SLOTS.length);
		} else {
			stacks = new ItemStack[ARMOR_SLOTS.length + (showOffhand.get() ? 1 : 0)];
			for (int i = 0; i < ARMOR_SLOTS.length; i++) {
				stacks[i] = client.player.getItemBySlot(ARMOR_SLOTS[i]);
			}
			if (showOffhand.get()) {
				stacks[ARMOR_SLOTS.length] = client.player.getOffhandItem();
			}
		}

		int textW = 0;
		if (textMode) {
			for (ItemStack s : stacks) {
				String t = s == null ? null : durabilityText(s);
				if (t != null) {
					textW = Math.max(textW, LunaCompat.getTextWidth(client.font, t));
				}
			}
		}
		int elementWidth = iconSize + (textW > 0 ? 3 + textW : 0);
		int elementHeight = stacks.length * iconSize + (stacks.length - 1) * spacing;

		int sw = client.getWindow().getGuiScaledWidth();
		int x = position.get().resolveX(sw, elementWidth);
		int y = position.get().resolveY(client.getWindow().getGuiScaledHeight(), elementHeight);
		// 오른쪽 앵커면 아이콘을 오른쪽에, 글자를 왼쪽에(핫바 근처에 붙었을 때 자연스럽게)
		boolean rightSide = position.get().anchor == HudPosition.Anchor.TOP_RIGHT
				|| position.get().anchor == HudPosition.Anchor.BOTTOM_RIGHT;
		int iconX = rightSide ? x + elementWidth - iconSize : x;

		for (ItemStack stack : stacks) {
			if (stack != null && !stack.isEmpty()) {
				context.item(stack, iconX, y);
				if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
					float ratio = 1f - (float) stack.getDamageValue() / stack.getMaxDamage();
					if (textMode) {
						String t = durabilityText(stack);
						if (t != null) {
							int tw = LunaCompat.getTextWidth(client.font, t);
							int tx = rightSide ? iconX - 3 - tw : iconX + iconSize + 3;
							LunaCompat.drawHudText(context, client.font, t, tx,
									y + Math.round(iconSize / 2f - LunaCompat.textVisualCenter()), durabilityColor(ratio));
						}
					} else if (barMode) {
						int bw = Math.round(13 * ratio);
						context.fill(iconX + 2, y + 13, iconX + 15, y + 15, 0xFF000000);
						context.fill(iconX + 2, y + 13, iconX + 2 + bw, y + 14, durabilityColor(ratio));
					}
				}
			}
			y += iconSize + spacing;
		}
	}
}

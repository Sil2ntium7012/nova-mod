package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.NeogulData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 49-564차(사용자: 무한 물뿌리개 툴팁 사진 + "이 녀석도 우클릭 꾹 누르면 ABPM 모드처럼 딜레이 없이 설치되게, 물 까는 건데"): <b>물뿌리개 연속 뿌리기</b>.
 *
 * <p>너굴마을 물뿌리개(이름에 "물뿌리개", 겉은 종이)는 블록 아이템이 아니라 Accurate Block Placement가 손을 안 댄다. 바닐라는 우클릭을 누르고 있으면
 * 4틱마다 같은 자리를 다시 누를 뿐이라, 끌면서 깔면 칸을 건너뛴다. 여기서는 누르고 있는 동안 <b>조준한 칸(블록 + 면)이 바뀌는 틱마다</b> 그 칸에 바로
 * 우클릭을 보낸다. 처음 누른 칸은 바닐라가 이미 눌렀으니 건너뛴다. 창이 열려 있으면 안 한다.
 */
public class NeogulWateringModule extends Module {

	private BlockPos lastPos;
	private Direction lastFace;
	private boolean wasDown;

	public NeogulWateringModule() {
		super("neogul_watering", "물뿌리개 연속 뿌리기", ModuleCategory.SERVER, "물뿌리개를 들고 우클릭을 꾹 누르면 지나가는 칸마다 바로 뿌려요");
		serverGroup("너굴마을");
		newFeature("49-564");
	}

	@Override
	protected void onDisable() {
		wasDown = false;
		lastPos = null;
	}

	@Override
	public void onTick() {
		try {
			tickUse();
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulWatering", t);
		}
	}

	private void tickUse() {
		if (client.player == null || client.gameMode == null || client.options == null || LunaCompat.screenOf(client) != null
				|| !NeogulData.onNeogul(client)) {
			wasDown = false;
			lastPos = null;
			return;
		}
		if (!client.options.keyUse.isDown()) {
			wasDown = false;
			lastPos = null;
			return;
		}
		InteractionHand hand = canHand();
		if (hand == null) {
			wasDown = true;   // 다른 걸 들고 누르다가 바꿔 들면 그다음 칸부터
			lastPos = null;
			return;
		}
		HitResult hr = client.hitResult;
		if (!(hr instanceof BlockHitResult bhr) || hr.getType() != HitResult.Type.BLOCK) {
			return;
		}
		BlockPos p = bhr.getBlockPos();
		Direction f = bhr.getDirection();
		if (!wasDown) {
			wasDown = true;   // 처음 누른 칸은 바닐라가 눌렀다
			lastPos = p;
			lastFace = f;
			return;
		}
		if (p.equals(lastPos) && f == lastFace) {
			return;
		}
		lastPos = p;
		lastFace = f;
		client.gameMode.useItemOn(client.player, hand, bhr);
		swing(hand);
	}

	/** 손 휘두르기. 26.3은 swing(손, 모양, boolean)으로 바뀌어 직접 부르면 빌드가 깨진다 - 26.1~26.2의 swing(손)만 찾아 부른다. */
	private void swing(InteractionHand hand) {
		try {
			client.player.getClass().getMethod("swing", InteractionHand.class).invoke(client.player, hand);
		} catch (Throwable ignored) {
		}
	}

	/** 물뿌리개를 든 손(주 손 먼저). 없으면 null. */
	private InteractionHand canHand() {
		if (isCan(client.player.getMainHandItem())) {
			return InteractionHand.MAIN_HAND;
		}
		if (isCan(client.player.getOffhandItem())) {
			return InteractionHand.OFF_HAND;
		}
		return null;
	}

	private static boolean isCan(ItemStack st) {
		if (st == null || st.isEmpty()) {
			return false;
		}
		try {
			return st.getHoverName().getString().contains("물뿌리개");
		} catch (Throwable t) {
			return false;
		}
	}

	// 49-570차: 새 기능 창 미리보기 - 밭 여덟 칸 위로 물뿌리개(물 양동이 그림)가 지나가며 칸마다 바로 물이 차는 그림(2.4초마다 다시).
	@Override
	public boolean hasNewFeaturePreview() {
		return true;
	}

	@Override
	public void drawNewFeaturePreview(net.minecraft.client.gui.GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
		var font = client.font;
		int n = 8, tile = 18, gap = 2;
		int rowW = n * tile + (n - 1) * gap;
		int sx = x + (w - rowW) / 2, sy = y + h / 2 - 4;
		long t = System.currentTimeMillis() % 2400L;
		float pos = Math.min(n, t / 220f);   // 지나간 칸 수
		for (int i = 0; i < n; i++) {
			int cx = sx + i * (tile + gap);
			boolean wet = i < (int) pos;
			ctx.fill(cx, sy, cx + tile, sy + tile, wet ? 0xFF3F76E4 : 0xFF6B4A2B);
			ctx.fill(cx, sy, cx + tile, sy + 3, wet ? 0xFF6E9CF2 : 0xFF8A6239);
		}
		int ix = sx + Math.round(Math.min(pos, n - 0.5f) * (tile + gap)) - 8;
		ctx.item(new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET), ix, sy - 18);
		String label = "우클릭을 꾹 누른 채 지나가면";
		String sub = "칸마다 바로 물을 뿌려요";
		kr.lunaslight.mod.gui.LunaDraw.text(ctx, font, label, x + (w - kr.lunaslight.mod.gui.LunaDraw.width(font, label)) / 2, y + 8, 0xFFFFE08A);
		kr.lunaslight.mod.gui.LunaDraw.text(ctx, font, sub, x + (w - kr.lunaslight.mod.gui.LunaDraw.width(font, sub)) / 2, sy + tile + 8, 0xFFB8C2CF);
	}
}

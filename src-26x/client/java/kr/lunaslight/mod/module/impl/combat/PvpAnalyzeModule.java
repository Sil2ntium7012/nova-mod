package kr.lunaslight.mod.module.impl.combat;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaTheme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import java.util.ArrayList;
import java.util.List;

/**
 * 49-71차(4-43): <b>상대 분석</b> - 지금 싸우고 있는 사람이 <b>어떻게 치고 있는지</b>를 읽어 준다.
 *
 * <p>사용자 요청 4-43: "PVP 공격 방식 판별".
 *
 * <h3>무엇을 보고 판단하나</h3>
 * 서버는 다른 사람의 <b>팔 흔들기</b>·<b>달리기 상태</b>·<b>아이템 사용 중</b>을 나에게 그대로 보내 준다
 * (그래야 화면에 그릴 수 있으니까). 그 셋만으로도 공격 방식이 거의 다 읽힌다.
 *
 * <table>
 *   <tr><th>읽는 것</th><th>보는 자리</th><th>알 수 있는 것</th></tr>
 *   <tr><td>팔 흔들기</td><td>{@code handSwingTicks}(1.15.2부터 전 버전 동일 - javap 실측)</td><td>초당 몇 번 휘두르는가</td></tr>
 *   <tr><td>달리기 켜짐/꺼짐</td><td>{@code isSprinting()}</td><td><b>W탭</b>(치면서 달리기를 끊는 기술)</td></tr>
 *   <tr><td>아이템 사용 중</td><td>{@code isBlocking()}</td><td><b>블록힛</b>(막기와 공격을 섞는 기술)</td></tr>
 * </table>
 *
 * <h3>판별</h3>
 * <ul>
 *   <li><b>연타형</b> - 휘두름이 초당 {@link #SPAM_CPS}회 이상. 쿨다운을 무시하고 계속 누르는 방식
 *       (여기가 <b>마인크래프트가 보내 주는 최대치</b>에 붙은 상태다).</li>
 *   <li><b>쿨다운형</b> - 초당 1.1~2.3회면서 <b>간격이 고른</b> 경우. 검 쿨다운(1.6초)에 맞춰 치는 방식.</li>
 *   <li><b>섞어 침</b> - 그 사이. 대부분의 사람이 여기다.</li>
 * </ul>
 *
 * <h3>⚠️ 이건 핵 잡는 도구가 아니다</h3>
 * 여기서 세는 것은 <b>서버가 보내 준 팔 흔들기</b>다. 팔을 안 흔들고 때리는 클라이언트는 <b>0으로 보인다</b>.
 * 반대로 허공을 치기만 해도 세어진다. "저 사람 CPS가 이상하다"는 <b>참고</b>이지 증거가 아니고,
 * 그렇게 쓰라고 만든 것도 아니다. 이 설명은 설정 화면에도 그대로 적어 뒀다.
 *
 * <p><b>정확한 CPS는 애초에 알 수 없다.</b> 마인크래프트는 팔 휘두르기가 절반 넘게 진행된 뒤에야
 * 다시 시작하므로, 상대가 초당 8번을 치든 20번을 치든 <b>내 쪽에 도착하는 휘두름은 초당 5~6이 최대</b>다
 * ({@link #SPAM_CPS} 주석에 자세히). 그래서 이 모듈은 "CPS"라고 적지 않고 <b>"휘두름/초"</b>라고 적는다 -
 * 알 수 없는 숫자를 아는 척하지 않으려고.
 *
 * <p><b>성능</b>: <b>한 사람만</b> 따라간다. 틱마다 하는 일은 그 사람의 값 셋을 읽고 링 버퍼에 시각을
 * 적는 것뿐이고, 목표가 없으면 그마저도 안 한다.
 */
public class PvpAnalyzeModule extends Module {

	/**
	 * 이 이상이면 연타형(초당 휘두름 기준).
	 *
	 * <p><b>왜 8이 아니라 4인가 - 여기서 셀 수 있는 것의 한계</b>: 마인크래프트는 팔 휘두르기 애니메이션이
	 * <b>절반 이상 진행된 뒤에야</b> 다시 시작한다({@code swingHand()}의 조건 - 휘두름 길이 6틱의 절반).
	 * 그래서 상대가 초당 8번을 치든 20번을 치든 <b>내 쪽에 보이는 휘두름은 초당 5~6이 최대</b>다.
	 *
	 * <p>그래서 이 모듈은 <b>"CPS"라고 말하지 않는다.</b> 셀 수 있는 것은 "초당 휘두름"이고,
	 * 그 값은 5쯤에서 천장을 친다. 그 천장에 붙어 있으면 연타, 1.6 근처에서 고르면 쿨다운이다 -
	 * <b>방식은 갈리지만 정확한 CPS 숫자는 알 수 없다</b>. 모르는 숫자를 지어내지 않으려고 이렇게 뒀다.
	 */
	private static final double SPAM_CPS = 4.0;
	/** 기억하는 공격 시각 개수(2초짜리 창을 재기에 충분). */
	private static final int SWINGS = 32;
	/** 목표를 놓친 뒤 이만큼은 계속 보여 준다(잠깐 시야에서 벗어나도 패널이 깜빡이지 않게). */
	private static final long KEEP_MS = 4000;
	/** 세는 창(ms). */
	private static final long WINDOW_MS = 2000;

	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "표시 위치입니다.", HudPosition.of(HudPosition.Anchor.BOTTOM_CENTER, 0, 206)));
	private final IntSetting range = register(new IntSetting(
			"range", "거리", "이 거리(블록) 안의 사람만 봅니다.", 8, 3, 24, 1).unit("블록"));
	private final BooleanSetting showCps = register(new BooleanSetting(
			"show_cps", "초당 휘두름",
			"상대의 팔 휘두름을 초당 몇 번 보이는지 셉니다. 마인크래프트가 휘두름을 초당 5~6번까지만 보내므로 "
			+ "실제 클릭 수(CPS)와는 다릅니다 - 더 빨리 쳐도 5쯤에서 멈춥니다.", true));
	private final BooleanSetting showStyle = register(new BooleanSetting(
			"show_style", "공격 방식", "연타형 | 쿨다운형 | 섞어 침 중 어느 쪽인지 보여 줍니다.", true));
	private final BooleanSetting showWtap = register(new BooleanSetting(
			"show_wtap", "W탭", "치면서 달리기를 끊는 기술을 쓰는지 보여 줍니다.", true));
	private final BooleanSetting showBlock = register(new BooleanSetting(
			"show_block", "블록힛", "막기와 공격을 섞는지 보여 줍니다.", true));
	private final ColorSetting textColor = register(new ColorSetting(
			"text_color", "글자 색", "글자의 색입니다.", LunaTheme.HUD_TEXT_DEFAULT));

	// ---- 따라가는 한 사람 ----
	private Entity target;
	private String targetName = "";
	private long targetSeenAt;
	private int lastSwingTicks = Integer.MIN_VALUE;
	private boolean lastSprint;
	private boolean lastBlocking;

	private final long[] swingAt = new long[SWINGS];
	private int swingIndex;
	private final long[] sprintToggleAt = new long[16];
	private int sprintIndex;
	private long lastBlockChangeAt;
	private long lastBlockhitAt;

	public PvpAnalyzeModule() {
		super("pvp_analyze", "상대 분석", ModuleCategory.COMBAT, "상대가 어떻게 치는지 읽기");
		enableHudStyle();
	}

	@Override
	protected void onDisable() {
		forget();
	}

	private void forget() {
		target = null;
		targetName = "";
		lastSwingTicks = Integer.MIN_VALUE;
		java.util.Arrays.fill(swingAt, 0L);
		java.util.Arrays.fill(sprintToggleAt, 0L);
		swingIndex = 0;
		sprintIndex = 0;
		lastBlockhitAt = 0;
	}

	// ==================== 따라가기 ====================

	@Override
	public void onTick() {
		if (client == null || client.player == null || client.level == null) {
			return;
		}
		long now = System.currentTimeMillis();
		Entity next = pick();
		if (next != null && next != target) {
			forget();
			target = next;
			targetName = safeName(next);
		}
		if (target == null) {
			return;
		}
		if (next == null && now - targetSeenAt > KEEP_MS) {
			forget();
			return;
		}
		if (next != null) {
			targetSeenAt = now;
		}
		sample(now);
	}

	/**
	 * 볼 사람 고르기: <b>조준하고 있는 사람</b>이 우선, 없으면 <b>가장 가까운 사람</b>.
	 * 싸우는 중에는 조준이 왔다 갔다 하므로 놓쳐도 {@link #KEEP_MS} 동안은 하던 사람을 계속 본다.
	 */
	private Entity pick() {
		try {
			if (client.crosshairPickEntity instanceof Player p && p != client.player) {
				return p;
			}
			double best = range.get() * (double) range.get();
			Entity found = null;
			for (Entity e : client.level.players()) {
				if (e == client.player || e == null) {
					continue;
				}
				double d = e.distanceToSqr(client.player);
				if (d < best) {
					best = d;
					found = e;
				}
			}
			return found;
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static java.lang.reflect.Field swingTimeField, swingStateField, swingStateTicks;
	private static boolean swingResolved;

	/**
	 * 49-215차: 팔 흔든 틱 수. 26.2까지는 LivingEntity.swingTime, 26.3은 swingState(SwingState).ticks로 옮겨갔다
	 * (휘두르는 중이 아니면 0). 두 판을 한 소스로 돌리려고 이름으로 찾는다.
	 */
	private static int swingTicks(net.minecraft.world.entity.LivingEntity living) {
		try {
			if (!swingResolved) {
				swingResolved = true;
				swingTimeField = LunaCompat.findField(net.minecraft.world.entity.LivingEntity.class, "swingTime");
				if (swingTimeField == null) {
					swingStateField = LunaCompat.findField(net.minecraft.world.entity.LivingEntity.class, "swingState");
					if (swingStateField != null) {
						swingStateField.setAccessible(true);
						swingStateTicks = LunaCompat.findField(swingStateField.getType(), "ticks");
						if (swingStateTicks != null) {
							swingStateTicks.setAccessible(true);
						}
					}
				} else {
					swingTimeField.setAccessible(true);
				}
			}
			if (swingTimeField != null) {
				return swingTimeField.getInt(living);
			}
			if (swingStateField != null && swingStateTicks != null) {
				Object st = swingStateField.get(living);
				if (st == null) {
					return 0;
				}
				Object swinging = st.getClass().getMethod("isSwinging").invoke(st);
				return Boolean.TRUE.equals(swinging) ? swingStateTicks.getInt(st) : 0;
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("pvp:swing", t);
		}
		return 0;
	}

	/** 이번 틱의 값 셋을 읽어 기록한다. */
	private void sample(long now) {
		try {
			if (!(target instanceof net.minecraft.world.entity.LivingEntity living)) {
				return;
			}
			// ① 팔 흔들기: **0 → 1로 넘어가는 순간**이 정확히 한 번의 휘두름이다(아래 주석 참고)
			int ticks = swingTicks(living);
			if (lastSwingTicks == 0 && ticks == 1) {
				swingAt[swingIndex] = now;
				swingIndex = (swingIndex + 1) % SWINGS;
				// 막기와 공격이 0.3초 안에 붙어 있으면 블록힛으로 본다
				if (now - lastBlockChangeAt < 300) {
					lastBlockhitAt = now;
				}
			}
			lastSwingTicks = ticks;

			// ② 달리기 켜짐/꺼짐
			boolean sprint = target.isSprinting();
			if (sprint != lastSprint) {
				sprintToggleAt[sprintIndex] = now;
				sprintIndex = (sprintIndex + 1) % sprintToggleAt.length;
				lastSprint = sprint;
			}

			// ③ 막는 중
			boolean blocking = living.isBlocking();
			if (blocking != lastBlocking) {
				lastBlockChangeAt = now;
				lastBlocking = blocking;
			}
		} catch (Throwable ignored) {
			// 이 버전에 없는 모양의 엔티티 - 이번 틱은 건너뛴다
		}
	}

	private static String safeName(Entity e) {
		try {
			return e.getName().getString();
		} catch (Throwable ignored) {
			return "?";
		}
	}

	// ==================== 읽어 내기 ====================

	private static int countWithin(long[] ring, long now, long windowMs) {
		int n = 0;
		for (long t : ring) {
			if (t > 0 && now - t <= windowMs) {
				n++;
			}
		}
		return n;
	}

	/** 최근 {@link #WINDOW_MS} 동안의 초당 공격 횟수. */
	private double cps(long now) {
		return countWithin(swingAt, now, WINDOW_MS) / (WINDOW_MS / 1000.0);
	}

	/**
	 * 공격 간격이 고른지(0 = 제멋대로, 1 = 시계처럼). 쿨다운형은 간격이 거의 일정하다.
	 * 표본이 셋 미만이면 판단하지 않고 -1.
	 */
	private double regularity(long now) {
		List<Long> times = new ArrayList<>();
		for (long t : swingAt) {
			if (t > 0 && now - t <= WINDOW_MS) {
				times.add(t);
			}
		}
		if (times.size() < 4) {
			return -1;
		}
		java.util.Collections.sort(times);
		double sum = 0;
		int n = times.size() - 1;
		double[] gaps = new double[n];
		for (int i = 0; i < n; i++) {
			gaps[i] = times.get(i + 1) - times.get(i);
			sum += gaps[i];
		}
		double mean = sum / n;
		if (mean <= 0) {
			return -1;
		}
		double var = 0;
		for (double g : gaps) {
			var += (g - mean) * (g - mean);
		}
		// 변동계수가 작을수록 고르다. 0.25(=25% 흔들림)를 경계로 0~1로 편다.
		double cv = Math.sqrt(var / n) / mean;
		return Math.max(0, Math.min(1, 1 - cv / 0.25));
	}

	private String style(long now, double cps) {
		if (cps <= 0.4) {
			return null;                       // 거의 안 치는 중 - 아무 말도 안 하는 게 맞다
		}
		if (cps >= SPAM_CPS) {
			return "§c연타형";
		}
		double reg = regularity(now);
		if (cps >= 1.1 && cps <= 2.3 && reg >= 0.5) {
			return "§a쿨다운형";       // 검 쿨다운이 1.6초당 1회 → 초당 1.6 근처에서 고르게
		}
		return "§e섞어 침";
	}

	// ==================== 그리기 ====================

	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		List<String> lines = new ArrayList<>();
		if (isPreview()) {
			// 49-88차(8-12): 켠 항목만 미리보기에
			lines.add("§fSteve");
			String head = showCps.get() ? "§7휘두름 §f5.0§8/초" : "";
			if (showStyle.get()) {
				head = head.isEmpty() ? "§c연타형" : head + " §8|  §c연타형";
			}
			if (!head.isEmpty()) {
				lines.add(head);
			}
			if (showWtap.get()) {
				lines.add("§7W탭 §f3§8/초");
			}
			if (showBlock.get()) {
				lines.add("§b막는 중");
			}
		} else {
			if (target == null || client.player == null) {
				return;
			}
			long now = System.currentTimeMillis();
			double cps = cps(now);
			lines.add("§f" + targetName);
			String head = "";
			if (showCps.get()) {
				head = "§7휘두름 §f" + String.format(java.util.Locale.ROOT, "%.1f", cps) + "§8/초";
			}
			if (showStyle.get()) {
				String st = style(now, cps);
				if (st != null) {
					head = head.isEmpty() ? st : head + " §8|  " + st;
				}
			}
			if (!head.isEmpty()) {
				lines.add(head);
			}
			if (showWtap.get()) {
				// 달리기를 껐다 켜는 한 번이 토글 둘이므로 2로 나눈다
				int taps = countWithin(sprintToggleAt, now, 1000) / 2;
				if (taps >= 1) {
					lines.add("§7W탭 §f" + taps + "§8/초");
				}
			}
			if (showBlock.get()) {
				if (lastBlocking) {
					lines.add("§b막는 중");
				} else if (now - lastBlockhitAt < 1500) {
					lines.add("§b블록힛");
				}
			}
			if (lines.size() <= 1) {
				return;                        // 이름만 남으면 띄울 값이 없다
			}
		}
		int w = hudLinesWidth(lines);
		int h = hudLinesHeight(lines);
		int x = position.get().resolveX(client.getWindow().getGuiScaledWidth(), w);
		int y = position.get().resolveY(client.getWindow().getGuiScaledHeight(), h);
		drawHudLines(context, lines, x, y, textColor.getArgb());
	}

	@Override
	public boolean hasPreview() {
		return true;
	}
}

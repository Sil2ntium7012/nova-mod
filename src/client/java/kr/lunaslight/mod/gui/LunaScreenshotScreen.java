package kr.lunaslight.mod.gui;

import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 49-67차(4-20): <b>스크린샷 보관함</b> - 찍은 스크린샷을 게임 안에서 바로 넘겨 본다.
 *
 * <p>사용자 요청 4-20: "스크린샷 인게임 저장소 - 새 화면 하나".
 *
 * <p>F2로 찍은 사진을 확인하려면 지금까지는 게임을 나가거나 알트탭으로 탐색기를 열어야 했다.
 * 정작 "방금 그거 잘 찍혔나"가 궁금한 순간은 게임 안이다. 이 화면은 <b>screenshots 폴더</b>와
 * Luna가 저장한 <b>luna-stats 폴더</b>를 함께 읽어 최근 것부터 격자로 보여 주고, 하나를 누르면
 * 크게 본다.
 *
 * <h3>프레임을 지키는 방법(이 화면의 대부분은 이 이야기다)</h3>
 * 스크린샷 한 장은 1920×1080쯤 되고, 푸는 데만 수십 ms가 든다. 폴더에 200장이 있을 때 이것을
 * 그리는 김에 하나씩 풀면 스크롤할 때마다 화면이 멈춘다. 그래서:
 * <ul>
 *   <li><b>푸는 일은 백그라운드 스레드 하나</b>에서만 한다. GL을 안 건드리는 일이라 가능하다
 *       ({@link LunaCompat#readScaledImage}).</li>
 *   <li>백그라운드는 <b>줄여서</b> 넘긴다 - 격자용 {@link #THUMB_PX}px, 크게 보기용
 *       {@link #BIG_PX}px. 원본 크기 그대로 GPU에 올리면 한 장에 8MB다.</li>
 *   <li>렌더 스레드는 <b>한 프레임에 최대 {@link #UPLOAD_PER_FRAME}장</b>만 올린다.</li>
 *   <li><b>화면에 보이는 칸(+위아래 한 줄)만</b> 불러오고, 그 밖으로 밀려난 것은 GPU에서 내린다.
 *       그래서 폴더에 몇 장이 있든 살아 있는 텍스처 수는 일정하다.</li>
 *   <li>화면을 닫을 때 <b>전부 내린다</b> - 안 내리면 열었다 닫을 때마다 쌓인다.</li>
 * </ul>
 *
 * <p><b>삭제는 확인 창이 한 번 더 묻는다</b>({@link LunaConfirm}). 49-90차(8-19): 예전엔 버튼이 4초 동안
 * "정말 지울까요?"로 바뀌었는데 글·배경이 둘 다 빨강이라 안 읽히고 버튼 폭이 바뀌어 옆 버튼이 밀렸다
 * (사용자). 버튼은 그대로 두고 창이 묻는다. 같은 줄에 <b>복사</b>(클립보드로 그림+파일)·<b>이름 바꾸기</b>
 * (그 자리에서 입력, Enter 저장·ESC 취소)를 넣었다.
 */
public class LunaScreenshotScreen extends LunaScreenBase {

	// ==================== 49-247차: GUI 배율과 무관한 크기 + 창이 작으면 같이 작게(LunaVScale) ====================
	private final LunaVScale lunaV = new LunaVScale(this, 480, 320);

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		boolean e = lunaV.begin(ctx);
		try {
			if (lunaV.needsInit()) {
				lunaInit0();
				lunaV.markInit();
			}
			lunaRender0(ctx, lunaV.mouse(mouseX), lunaV.mouse(mouseY), delta);
		} finally {
			lunaV.end(ctx, e);
		}
	}

	@Override
	protected void init() {
		boolean e = lunaV.enter();
		try {
			lunaInit0();
			lunaV.markInit();
		} finally {
			lunaV.exit(e);
		}
	}

	@Override
	protected boolean lunaMouseClicked(double mouseX, double mouseY, int button) {
		boolean e = lunaV.enter();
		double k = lunaV.k(e);
		try {
			return lunaMouseClicked0(mouseX * k, mouseY * k, button);
		} finally {
			lunaV.exit(e);
		}
	}

	@Override
	protected boolean lunaMouseReleased(double mouseX, double mouseY, int button) {
		boolean e = lunaV.enter();
		double k = lunaV.k(e);
		try {
			return lunaMouseReleased0(mouseX * k, mouseY * k, button);
		} finally {
			lunaV.exit(e);
		}
	}

	@Override
	protected boolean lunaMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		boolean e = lunaV.enter();
		double k = lunaV.k(e);
		try {
			return lunaMouseDragged0(mouseX * k, mouseY * k, button, deltaX * k, deltaY * k);
		} finally {
			lunaV.exit(e);
		}
	}

	@Override
	protected boolean lunaMouseScrolled(double mouseX, double mouseY, double verticalAmount) {
		boolean e = lunaV.enter();
		double k = lunaV.k(e);
		try {
			return lunaMouseScrolled0(mouseX * k, mouseY * k, verticalAmount);
		} finally {
			lunaV.exit(e);
		}
	}

	@Override
	protected boolean lunaKeyPressed(int keyCode, int scanCode, int modifiers) {
		boolean e = lunaV.enter();
		double k = lunaV.k(e);
		try {
			return lunaKeyPressed0(keyCode, scanCode, modifiers);
		} finally {
			lunaV.exit(e);
		}
	}

	@Override
	protected boolean lunaCharTyped(char chr) {
		boolean e = lunaV.enter();
		double k = lunaV.k(e);
		try {
			return lunaCharTyped0(chr);
		} finally {
			lunaV.exit(e);
		}
	}

	/** 격자 미리보기의 긴 변(px). 160×90 칸에 그리므로 이 정도면 또렷하다. */
	private static final int THUMB_PX = 320;
	/** 크게 보기의 긴 변(px). 한 장만 살아 있으므로 넉넉히 준다. */
	private static final int BIG_PX = 1600;
	/** 한 프레임에 GPU로 올리는 장수 - 스크롤을 확 내려도 한 프레임이 길어지지 않게. */
	private static final int UPLOAD_PER_FRAME = 2;
	/** 동시에 풀고 있을 최대 장수(백그라운드가 한 줄로 처리하므로 줄이 길어지는 것만 막는다). */
	private static final int MAX_IN_FLIGHT = 6;

	private static final int PAD = 12;
	private static final int HEAD_H = 44;
	private static final int GAP = 8;
	private static final int CELL_W = 160;
	private static final int CAP_H = 12;

	private static final int ST_NONE = 0;
	private static final int ST_LOADING = 1;
	private static final int ST_READY = 2;      // 다 풀렸다 - 렌더 스레드가 올리기만 하면 됨
	private static final int ST_LIVE = 3;
	private static final int ST_FAILED = 4;

	/** 한 장. 파일 정보는 처음에 한 번만 읽고, 그림은 필요해질 때 붙는다. */
	private static final class Shot {
		Path path;                              // 이름 바꾸기로 바뀔 수 있다
		String name;
		final long time;
		final long size;
		/** 49-72차(4-11): 녹화 클립(.gif)인지. 미리보기가 안 될 때 "못 읽음" 대신 "GIF"라고 적기 위해. */
		final boolean gif;
		volatile int state = ST_NONE;
		volatile NativeImage pending;           // 백그라운드가 넘긴 그림(렌더 스레드가 가져간다)
		Identifier tex;
		NativeImage held;                       // 텍스처가 쥐고 있는 그림(내릴 때 같이 닫는다)
		int iw, ih;

		Shot(Path path, long time, long size) {
			this.path = path;
			this.name = path.getFileName().toString();
			this.time = time;
			this.size = size;
			String low = this.name.toLowerCase(java.util.Locale.ROOT);
			this.gif = low.endsWith(".gif") || low.endsWith(".mp4");   // 49-76차: 영상도 "클립"으로 같이 다룬다
		}
	}

	private final Screen parent;
	private final List<Shot> shots = new ArrayList<>();

	private ExecutorService loader;
	private volatile boolean closed;
	private int inFlight;
	/** 텍스처 이름을 겹치지 않게 하는 번호. 내린 것과 새로 올린 것이 같은 이름을 쓰면 서로를 지운다. */
	private int texSeq;

	private int px, py, panelW, panelH;
	private double scroll;
	private int cols = 3, cellW = CELL_W, thumbH = 90;
	/** 이 버전에서 그림을 화면에 올릴 수 있는지. 못 올리면 격자 대신 목록으로 바꾼다(아래 주석 참고). */
	private boolean thumbs = true;

	/** 크게 보는 중인 사진의 번호(-1이면 격자). */
	private int open = -1;
	private final Shot[] bigSlot = new Shot[1];
	private Identifier bigTex;
	private NativeImage bigHeld;
	private int bigW, bigH;
	private int bigState = ST_NONE;
	private volatile NativeImage bigPending;

	private final LunaConfirm confirm = new LunaConfirm();

	// ==================== 49-192차: 보정(사용자: "스크린샷 보는 곳에서 보정할 수 있게") ====================
	private static final String[] ADJ_NAMES = {"밝기", "대비", "채도", "색온도", "비네팅"};
	private static final int ADJ_W = 150;
	private static final int ADJ_ROW = 28;
	/** 보정 칸이 열려 있는지(크게 보기에서 [보정]). */
	private boolean editing;
	private final kr.lunaslight.mod.util.ShotAdjust adj = new kr.lunaslight.mod.util.ShotAdjust();
	/** 미리보기용으로 줄인 원본(백그라운드가 채운다). */
	private volatile java.awt.image.BufferedImage editSrc;
	private final java.util.concurrent.atomic.AtomicInteger editSession = new java.util.concurrent.atomic.AtomicInteger();
	private final java.util.concurrent.atomic.AtomicInteger editVer = new java.util.concurrent.atomic.AtomicInteger();
	private ExecutorService editor;
	private int dragSlider = -1;
	private boolean saving;

	/** 이름 바꾸는 중(크게 보기에서). 확장자는 못 바꾼다 - 이름만. */
	private boolean renaming;
	private final StringBuilder renameDraft = new StringBuilder();
	private String renameExt = "";
	private String notice;
	private long noticeUntil;

	public LunaScreenshotScreen(Screen parent) {
		super(LunaCompat.textLiteral("스크린샷"));
		this.parent = parent;
		LunaDraw.resetAnim("shots");
	}

	// ==================== 목록 ====================

	private void lunaInit0() {
		panelW = Math.min(520, Math.max(240, width - 20));
		panelH = Math.min(330, Math.max(160, height - 20));
		px = (width - panelW) / 2;
		py = Math.max(8, (height - panelH) / 2);
		layout();
		if (shots.isEmpty()) {
			scan();
		}
	}

	/**
	 * 칸 크기를 정한다. {@link #thumbs}에 따라 <b>격자</b>와 <b>목록</b> 두 모양이 나온다 -
	 * 그림을 못 올리는 버전에서 빈 네모만 늘어놓지 않기 위해서다. 매 프레임 부르지만 정수 계산 몇 줄이다.
	 */
	private void layout() {
		int listW = panelW - PAD * 2;
		if (!thumbs) {
			cols = 1;
			cellW = listW;
			thumbH = 0;
			return;
		}
		cols = Math.max(1, (listW + GAP) / (CELL_W + GAP));
		cellW = (listW - (cols - 1) * GAP) / cols;
		thumbH = Math.max(24, Math.round(cellW * 9f / 16f));
	}

	/** 스크린샷이 있을 만한 폴더들. 없는 폴더는 그냥 건너뛴다. */
	private List<Path> folders() {
		List<Path> out = new ArrayList<>();
		try {
			Path gameDir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
			out.add(gameDir.resolve("screenshots"));
			out.add(gameDir.resolve("stats-export"));    // 통계 [이미지로 저장]이 여기에 남긴다
			out.add(gameDir.resolve("clips"));    // 49-76차(6-5): 녹화 영상(.mp4)이 여기에 남는다
		} catch (Throwable ignored) {
		}
		return out;
	}

	private void scan() {
		shots.clear();
		for (Path dir : folders()) {
			if (!Files.isDirectory(dir)) {
				continue;
			}
			try (java.util.stream.Stream<Path> files = Files.list(dir)) {
				for (Path p : files.toList()) {
					String n = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
					// 49-72차(4-11): 녹화 클립(.gif)도 같이 모은다 - 같은 폴더에 저장된다
					if ((!n.endsWith(".png") && !n.endsWith(".gif") && !n.endsWith(".mp4")) || !Files.isRegularFile(p)) {
						continue;
					}
					long time = 0;
					long size = 0;
					try {
						time = Files.getLastModifiedTime(p).toMillis();
						size = Files.size(p);
					} catch (Throwable ignored) {
					}
					shots.add(new Shot(p, time, size));
				}
			} catch (Throwable t) {
				LunaCompat.warnOnce("shots:scan", t);
			}
		}
		shots.sort(Comparator.comparingLong((Shot s) -> s.time).reversed());
	}

	private ExecutorService loader() {
		if (loader == null) {
			loader = Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r, "luna-shots");
				t.setDaemon(true);
				t.setPriority(Thread.MIN_PRIORITY);    // 게임이 먼저다
				return t;
			});
		}
		return loader;
	}

	private void request(Shot shot, int maxSide) {
		if (shot.state != ST_NONE || inFlight >= MAX_IN_FLIGHT || closed) {
			return;
		}
		shot.state = ST_LOADING;
		inFlight++;
		loader().submit(() -> {
			NativeImage image = LunaCompat.readScaledImage(shot.path, maxSide);
			if (closed || image == null) {
				if (image != null) {
					try {
						image.close();
					} catch (Throwable ignored) {
					}
				}
				shot.state = image == null ? ST_FAILED : ST_NONE;
				return;
			}
			shot.pending = image;
			shot.state = ST_READY;
		});
	}

	/** 렌더 스레드에서 한 장 올린다. */
	private void upload(Shot shot) {
		NativeImage image = shot.pending;
		shot.pending = null;
		if (image == null) {
			shot.state = ST_FAILED;
			inFlight = Math.max(0, inFlight - 1);
			return;
		}
		shot.iw = image.getWidth();
		shot.ih = image.getHeight();
		Identifier id = LunaCompat.registerImageTexture(client, "shot/" + (texSeq++), image);
		if (id == null) {
			shot.state = ST_FAILED;
		} else {
			shot.tex = id;
			shot.held = image;
			shot.state = ST_LIVE;
		}
		inFlight = Math.max(0, inFlight - 1);
	}

	private void release(Shot shot) {
		if (shot.state == ST_LIVE) {
			LunaCompat.unregisterImageTexture(client, shot.tex, shot.held);
		}
		NativeImage waiting = shot.pending;
		shot.pending = null;
		if (waiting != null) {
			try {
				waiting.close();
			} catch (Throwable ignored) {
			}
		}
		shot.tex = null;
		shot.held = null;
		if (shot.state != ST_FAILED) {
			shot.state = ST_NONE;
		}
	}

	// ==================== 크게 보기 ====================

	private void openAt(int index) {
		renaming = false;
		editing = false;
		dragSlider = -1;
		editSession.incrementAndGet();
		editSrc = null;
		if (index < 0 || index >= shots.size()) {
			return;
		}
		open = index;
		releaseBig();
		if (!thumbs) {
			return;                                // 그림을 못 띄우는 버전 - 읽어 봐야 보여 줄 데가 없다
		}
		Shot shot = shots.get(index);
		if (shot.gif) {
			return;                                // 클립은 크게 봐도 첫 장면만 나온다 - 안 읽는 게 낫다
		}
		bigSlot[0] = shot;
		bigState = ST_LOADING;
		loader().submit(() -> {
			NativeImage image = LunaCompat.readScaledImage(shot.path, BIG_PX);
			if (closed || bigSlot[0] != shot || image == null) {
				if (image != null) {
					try {
						image.close();
					} catch (Throwable ignored) {
					}
				}
				if (bigSlot[0] == shot) {
					bigState = image == null ? ST_FAILED : ST_NONE;
				}
				return;
			}
			bigPending = image;
			bigState = ST_READY;
		});
	}

	private void releaseBig() {
		if (bigTex != null) {
			LunaCompat.unregisterImageTexture(client, bigTex, bigHeld);
		}
		bigTex = null;
		bigHeld = null;
		bigSlot[0] = null;
		bigState = ST_NONE;
		NativeImage waiting = bigPending;
		bigPending = null;
		if (waiting != null) {
			try {
				waiting.close();
			} catch (Throwable ignored) {
			}
		}
	}

	private void uploadBig() {
		NativeImage image = bigPending;
		bigPending = null;
		if (image == null) {
			bigState = ST_FAILED;
			return;
		}
		bigW = image.getWidth();
		bigH = image.getHeight();
		// 49-192차: 보정 미리보기는 같은 칸의 그림을 계속 바꿔 끼운다 - 새 이름으로 올리고 예전 것을 내린다
		Identifier oldTex = bigTex;
		NativeImage oldHeld = bigHeld;
		Identifier id = LunaCompat.registerImageTexture(client, "shot/big" + (++texSeq), image);
		if (id == null) {
			bigState = oldTex != null ? ST_LIVE : ST_FAILED;
			try {
				image.close();
			} catch (Throwable ignored) {
			}
		} else {
			bigTex = id;
			bigHeld = image;
			bigState = ST_LIVE;
			if (oldTex != null && !oldTex.equals(id)) {
				LunaCompat.unregisterImageTexture(client, oldTex, oldHeld);
			}
		}
	}

	// ==================== 화면 ====================

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		closed = true;
		if (editor != null) {
			editor.shutdownNow();
		}
		if (loader != null) {
			loader.shutdownNow();
			try {
				loader.awaitTermination(200, TimeUnit.MILLISECONDS);
			} catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
			}
		}
		for (Shot shot : shots) {
			release(shot);
		}
		releaseBig();
		LunaCompat.setScreen(parent);
	}

	private void notice(String s) {
		notice = s;
		noticeUntil = System.currentTimeMillis() + 4000;
	}

	private void lunaRender0(DrawContext ctx, int mouseX, int mouseY, float delta) {
		LunaDraw.beginFrame();
		LunaGfx.drawScreenBackdrop(this, ctx, width, height, mouseX, mouseY, delta, LunaDraw.OVERLAY);
		LunaDraw.setAlpha(LunaDraw.animFrom("shots", 0f, 1f, 18f));
		// 49-67차: 그림을 못 올리는 시대(1.15.2~1.19.4 - DrawContext shim에 drawTexture가 없다)에서는
		// 미리보기를 포기하고 목록으로 보여 준다. 빈 네모 40개보다 이름과 시각이 쓸모 있다.
		boolean canDraw = LunaGfx.texturesUsable(ctx);
		if (canDraw != thumbs) {
			thumbs = canDraw;
			layout();
			scroll = 0;
		}

		LunaDraw.panel3d(ctx, px, py, panelW, panelH, 8);   // 49-227차: 사진 시안 판

		boolean backHover = LunaDraw.in(mouseX, mouseY, px + 10, py + 12, 20, 20);
		LunaDraw.card3d(ctx, px + 10, py + 12, 20, 20, backHover ? 1f : 0f, false);   // 49-227차
		LunaIcons.draw(ctx, textRenderer, LunaIcons.BACK, px + 14, LunaDraw.iconY(py + 12, 20), LunaDraw.TEXT);
		LunaDraw.text(ctx, textRenderer, open >= 0 ? "스크린샷 보기" : "스크린샷", px + 38, py + 18, LunaDraw.TEXT);
		if (open < 0) {
			LunaDraw.text(ctx, textRenderer, shots.size() + "장", px + 38
				+ LunaDraw.width(textRenderer, "스크린샷") + 8, py + 18, LunaDraw.TEXT_DIM);
		}

		// 오른쪽 위: 폴더 열기
		int fw = LunaDraw.width(textRenderer, "폴더 열기") + 16;
		int fx = px + panelW - PAD - fw;
		boolean fHover = LunaDraw.in(mouseX, mouseY, fx, py + 14, fw, 16);
		LunaDraw.card3d(ctx, fx, py + 14, fw, 16, fHover ? 1f : 0f, false);   // 49-227차
		LunaDraw.text(ctx, textRenderer, "폴더 열기", fx + 8, LunaDraw.textY(py + 14, 16),
			fHover ? LunaDraw.TEXT : LunaDraw.TEXT_SUB);

		int lx = px + PAD;
		int ly = py + HEAD_H;
		int lw = panelW - PAD * 2;
		int lh = panelH - HEAD_H - PAD;
		ctx.fill(lx, ly - 6, lx + lw, ly - 5, 0x1AFFFFFF);

		if (open >= 0) {
			renderOne(ctx, lx, ly, lw, lh, mouseX, mouseY);
		} else {
			lunaV.scissor(ctx, lx, ly, lx + lw, ly + lh);
			renderGrid(ctx, lx, ly, lw, lh, mouseX, mouseY);
			ctx.disableScissor();
		}

		if (notice != null && System.currentTimeMillis() < noticeUntil) {
			int nw = LunaDraw.width(textRenderer, notice) + 16;
			LunaDraw.roundRectBordered(ctx, px + (panelW - nw) / 2, py + panelH - 24, nw, 16, 4,
				0xE0141820, LunaDraw.withAlpha(LunaDraw.ACCENT, 0x8C));
			LunaDraw.text(ctx, textRenderer, notice, px + (panelW - nw) / 2 + 8,
				LunaDraw.textY(py + panelH - 24, 16), LunaDraw.TEXT);
		}
		confirm.render(ctx, textRenderer, width, height, mouseX, mouseY);
	}

	private int rowCount() {
		return (shots.size() + cols - 1) / cols;
	}

	private int cellH() {
		return thumbs ? thumbH + CAP_H : 18;
	}

	private void renderGrid(DrawContext ctx, int lx, int ly, int lw, int lh, int mouseX, int mouseY) {
		if (shots.isEmpty()) {
			LunaDraw.textCentered(ctx, textRenderer, "스크린샷이 없습니다 - F2로 찍으면 여기에 모입니다",
				lx + lw / 2, ly + lh / 2 - 4, LunaDraw.TEXT_DIM);
			return;
		}
		int step = cellH() + GAP;
		int maxScroll = Math.max(0, rowCount() * step - GAP - lh);
		scroll = Math.max(0, Math.min(scroll, maxScroll));

		int firstRow = Math.max(0, (int) (scroll / step) - 1);
		int lastRow = Math.min(rowCount() - 1, (int) ((scroll + lh) / step) + 1);
		int uploaded = 0;
		String hoverName = null;

		for (int row = firstRow; row <= lastRow; row++) {
			int y = ly + row * step - (int) scroll;
			for (int c = 0; c < cols; c++) {
				int index = row * cols + c;
				if (index >= shots.size()) {
					break;
				}
				Shot shot = shots.get(index);
				int x = lx + c * (cellW + GAP);
				boolean hov = LunaDraw.in(mouseX, mouseY, x, y, cellW, cellH());
				if (!thumbs) {
					LunaDraw.card3d(ctx, x, y, cellW, cellH() - 2, hov ? 1f : 0f, false);   // 49-227차
					LunaDraw.text(ctx, textRenderer,
						LunaDraw.ellipsize(textRenderer, shot.name, cellW - 120),
						x + 8, LunaDraw.textY(y, cellH() - 2), LunaDraw.TEXT);
					LunaDraw.text(ctx, textRenderer, when(shot.time) + "  §8|  §7" + size(shot.size),
						x + cellW - 110, LunaDraw.textY(y, cellH() - 2), LunaDraw.TEXT_SUB);
					continue;
				}
				if (shot.state == ST_NONE) {
					request(shot, THUMB_PX);
				} else if (shot.state == ST_READY && uploaded < UPLOAD_PER_FRAME) {
					upload(shot);
					uploaded++;
				}
				if (hov) {
					hoverName = shot.name;
				}
				LunaDraw.roundRectBordered(ctx, x, y, cellW, thumbH, 4, 0xFF090B0E,
					hov ? LunaDraw.ACCENT : LunaDraw.CARD_BORDER);
				if (shot.state == ST_LIVE) {
					drawFitted(ctx, shot.tex, shot.iw, shot.ih, x + 1, y + 1, cellW - 2, thumbH - 2);
				} else {
					LunaDraw.textCentered(ctx, textRenderer,
						shot.state == ST_FAILED ? (shot.gif ? "§8영상" : "§8못 읽음") : "§8…",
						x + cellW / 2, y + thumbH / 2 - 4, LunaDraw.TEXT_DIM);
				}
				LunaDraw.text(ctx, textRenderer, when(shot.time), x + 2,
					y + thumbH + 2, hov ? LunaDraw.TEXT_SUB : LunaDraw.TEXT_DIM);
			}
		}
		// 파일 이름은 칸에 다 안 들어가므로 마우스를 올린 것만 한 줄로.
		// 49-172차(사용자: "마우스 가져다 대면 이름이 이상한 곳에 뜸"): 목록 맨 아래가 아니라 마우스 바로 아래(툴팁처럼).
		if (hoverName != null) {
			int tw = LunaDraw.width(textRenderer, hoverName) + 14;
			int tx = Math.min(width - tw - 4, Math.max(4, mouseX - tw / 2));
			int ty = mouseY + 14;
			if (ty + 15 > height - 4) {
				ty = mouseY - 20;
			}
			LunaDraw.roundRectBordered(ctx, tx, ty, tw, 15, 4, 0xE0141820, LunaDraw.CARD_BORDER);
			LunaDraw.text(ctx, textRenderer, hoverName, tx + 7, LunaDraw.textY(ty, 15), LunaDraw.TEXT);
		}
		// 보이지 않게 된 칸은 GPU에서 내린다 - 폴더가 몇 장이든 살아 있는 텍스처 수가 일정해진다
		int keepFrom = firstRow * cols;
		int keepTo = (lastRow + 1) * cols;
		for (int i = 0; i < shots.size(); i++) {
			Shot shot = shots.get(i);
			if ((i < keepFrom || i >= keepTo) && (shot.state == ST_LIVE || shot.state == ST_READY)) {
				release(shot);
			}
		}
	}

	private void renderOne(DrawContext ctx, int lx, int ly, int lw, int lh, int mouseX, int mouseY) {
		Shot shot = shots.get(open);
		if (bigState == ST_READY) {
			uploadBig();
		}
		int barH = 20;
		int imgH = lh - barH - 6;
		int fullW = lw;
		if (editing) {
			lw = fullW - ADJ_W - 8;
			renderAdjust(ctx, lx + lw + 8, ly, ADJ_W, imgH, mouseX, mouseY);
		}
		LunaDraw.roundRectBordered(ctx, lx, ly, lw, imgH, 4, 0xFF090B0E, LunaDraw.CARD_BORDER);
		if (!thumbs) {
			LunaDraw.textCentered(ctx, textRenderer, "§8이 버전에서는 게임 안에서 사진을 띄울 수 없습니다",
				lx + lw / 2, ly + imgH / 2 - 10, LunaDraw.TEXT_DIM);
			LunaDraw.textCentered(ctx, textRenderer, "§8[폴더 열기]로 보세요",
				lx + lw / 2, ly + imgH / 2 + 2, LunaDraw.TEXT_DIM);
		} else if (bigState == ST_LIVE) {
			drawFitted(ctx, bigTex, bigW, bigH, lx + 1, ly + 1, lw - 2, imgH - 2);
		} else {
			// 49-72차(4-11): 클립은 게임 안에서 재생하지 않는다(움직이는 그림을 돌리는 길이 없다) -
			// 그 사실을 그대로 적고 폴더로 안내한다. "못 읽음"이라고 하면 깨진 파일처럼 읽힌다.
			String why = shot.gif
					? (bigState == ST_LIVE || bigState == ST_FAILED
						? "§8영상은 게임 안에서 재생하지 않습니다" : "§8불러오는 중…")
					: (bigState == ST_FAILED ? "§8이 파일은 읽지 못했습니다" : "§8불러오는 중…");
			LunaDraw.textCentered(ctx, textRenderer, why,
				lx + lw / 2, ly + imgH / 2 - (shot.gif ? 10 : 4), LunaDraw.TEXT_DIM);
			if (shot.gif) {
				LunaDraw.textCentered(ctx, textRenderer, "§8[폴더 열기]로 보세요",
					lx + lw / 2, ly + imgH / 2 + 2, LunaDraw.TEXT_DIM);
			}
		}

		lw = fullW;
		int by = ly + imgH + 6;
		int bh = barH - 2;
		int[] b = bar(lx, lw);
		boolean canEdit = thumbs && !shot.gif;
		if (canEdit) {
			barButton(ctx, b[7], by, b[8], bh, LunaIcons.SLIDERS, editing ? "보정 닫기" : "보정", false, mouseX, mouseY);
		}
		// 오른쪽부터 [삭제] [이름 바꾸기] [복사] · [←][→] - 폭은 글자로 정해져 절대 안 바뀐다
		barButton(ctx, b[0], by, b[1], bh, LunaIcons.TRASH, "삭제", true, mouseX, mouseY);
		barButton(ctx, b[2], by, b[3], bh, LunaIcons.EDIT, "이름 바꾸기", false, mouseX, mouseY);
		barButton(ctx, b[4], by, b[5], bh, LunaIcons.COPY, "복사", false, mouseX, mouseY);
		drawArrow(ctx, b[6], by, bh, LunaIcons.LEFT, open > 0, mouseX, mouseY);
		drawArrow(ctx, b[6] + 22, by, bh, LunaIcons.RIGHT, open < shots.size() - 1, mouseX, mouseY);

		int infoW = b[6] - 8 - lx;
		if (renaming) {
			// 그 자리에서 이름 입력 - 확장자는 뒤에 흐리게 붙어 있고 못 바꾼다
			LunaDraw.roundRectBordered(ctx, lx, by, infoW, bh, 4, LunaDraw.CARD, LunaDraw.withAlpha(LunaDraw.ACCENT, 0x8C));
			String shown = renameDraft.toString();
			int maxW = infoW - 12 - LunaDraw.width(textRenderer, renameExt);
			while (LunaDraw.width(textRenderer, shown) > maxW && shown.length() > 1) {
				shown = shown.substring(1);
			}
			int tx = lx + 6;
			LunaDraw.text(ctx, textRenderer, shown, tx, LunaDraw.textY(by, bh), LunaDraw.TEXT);
			int cx = tx + LunaDraw.width(textRenderer, shown) + 1;
			if ((System.currentTimeMillis() / 500) % 2 == 0) {
				ctx.fill(cx, by + 4, cx + 1, by + bh - 4, LunaDraw.applyAlpha(LunaDraw.ACCENT));
			}
			LunaDraw.text(ctx, textRenderer, renameExt, cx + 2, LunaDraw.textY(by, bh), LunaDraw.TEXT_DIM);
		} else {
			String info = shot.name + "  §8|  §7" + when(shot.time) + "  §8|  §7" + size(shot.size);
			LunaDraw.text(ctx, textRenderer, LunaDraw.ellipsizeFormatted(textRenderer, info, infoW),
				lx, LunaDraw.textY(by, barH), LunaDraw.TEXT_SUB);
		}
	}

	private int adjValue(int i) {
		return switch (i) {
			case 0 -> adj.bright;
			case 1 -> adj.contrast;
			case 2 -> adj.saturation;
			case 3 -> adj.warmth;
			default -> adj.vignette;
		};
	}

	private void setAdjValue(int i, int v) {
		int min = i == 4 ? 0 : -100;
		v = Math.max(min, Math.min(100, v));
		switch (i) {
			case 0 -> adj.bright = v;
			case 1 -> adj.contrast = v;
			case 2 -> adj.saturation = v;
			case 3 -> adj.warmth = v;
			default -> adj.vignette = v;
		}
	}

	/** 보정 칸 자리(그리기와 클릭이 같은 계산). {x, y, w, h}. */
	private int[] adjustBox() {
		int lx = px + PAD;
		int ly = py + HEAD_H;
		int lw = panelW - PAD * 2;
		int lh = panelH - HEAD_H - PAD;
		int imgH = lh - 20 - 6;
		return new int[]{lx + lw - ADJ_W, ly, ADJ_W, imgH};
	}

	private int sliderY(int boxY, int i) {
		return boxY + 22 + i * ADJ_ROW + 12;
	}

	private void renderAdjust(DrawContext ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
		LunaDraw.roundRectBordered(ctx, x, y, w, h, 4, LunaDraw.CARD, LunaDraw.CARD_BORDER);
		LunaDraw.text(ctx, textRenderer, "보정", x + 8, y + 7, LunaDraw.TEXT);
		if (editSrc == null) {
			LunaDraw.text(ctx, textRenderer, "§8불러오는 중…", x + 8 + LunaDraw.width(textRenderer, "보정") + 6, y + 7, LunaDraw.TEXT_DIM);
		}
		int sx = x + 8, sw = w - 16;
		for (int i = 0; i < ADJ_NAMES.length; i++) {
			int ry = y + 22 + i * ADJ_ROW;
			int v = adjValue(i);
			LunaDraw.text(ctx, textRenderer, ADJ_NAMES[i], sx, ry, LunaDraw.TEXT_SUB);
			String vs = (v > 0 && i != 4 ? "+" : "") + v;
			LunaDraw.text(ctx, textRenderer, vs, x + w - 8 - LunaDraw.width(textRenderer, vs), ry, v == 0 ? LunaDraw.TEXT_DIM : LunaDraw.TEXT);
			float ratio = i == 4 ? v / 100f : (v + 100) / 200f;
			boolean hov = dragSlider == i || LunaDraw.in(mouseX, mouseY, sx - 2, sliderY(y, i) - 2, sw + 4, 14);
			LunaDraw.slider(ctx, sx, sliderY(y, i), sw, ratio, hov);
		}
		int by = y + h - 8 - 18 - 4 - 18;
		boolean h1 = LunaDraw.in(mouseX, mouseY, sx, by, sw, 18);
		LunaDraw.button3d(ctx, textRenderer, sx, by, sw, 18, "원래대로", LunaDraw.B_NEUTRAL, h1 ? 1f : 0f);   // 49-227차
		int by2 = by + 22;
		boolean can = !adj.isIdentity() && !saving;
		boolean h2 = can && LunaDraw.in(mouseX, mouseY, sx, by2, sw, 18);
		if (can) {
			LunaDraw.button3d(ctx, sx, by2, sw, 18, 4, LunaDraw.B_PRIMARY, h2 ? 1f : 0f);   // 49-227차
		} else {
			LunaDraw.roundRect(ctx, sx, by2, sw, 18, 4, LunaDraw.withAlpha(LunaDraw.ACCENT, 0x40));
		}
		LunaDraw.textCentered(ctx, textRenderer, saving ? "저장하는 중…" : "새로 저장", sx + sw / 2, LunaDraw.textY(by2, 18),
			can ? kr.lunaslight.mod.util.LunaTheme.ON_ACCENT : LunaDraw.TEXT_DIM);
	}

	private ExecutorService editor() {
		if (editor == null) {
			editor = Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r, "luna-shot-edit");
				t.setDaemon(true);
				return t;
			});
		}
		return editor;
	}

	private void startEdit() {
		Shot shot = shots.get(open);
		if (!thumbs || shot.gif) {
			return;
		}
		editing = true;
		adj.reset();
		editSrc = null;
		final int session = editSession.incrementAndGet();
		editor().submit(() -> {
			try {
				java.awt.image.BufferedImage src = kr.lunaslight.mod.util.ShotAdjust.scaled(
					kr.lunaslight.mod.util.ShotAdjust.read(shot.path), 960);
				if (session == editSession.get() && !closed) {
					editSrc = src;
					if (!adj.isIdentity()) {
						refreshEdit();
					}
				}
			} catch (Throwable t) {
				LunaCompat.warnOnce("shots:edit", t);
			}
		});
	}

	private void stopEdit() {
		if (open >= 0) {
			openAt(open);   // 원본 그림으로 되돌린다(editing도 꺼진다)
		} else {
			editing = false;
		}
	}

	/** 지금 값으로 미리보기를 다시 만든다(백그라운드). 새로 부탁하면 밀린 옛 부탁은 버린다. */
	private void refreshEdit() {
		final int v = editVer.incrementAndGet();
		final int session = editSession.get();
		final kr.lunaslight.mod.util.ShotAdjust a = adj.copy();
		editor().submit(() -> {
			java.awt.image.BufferedImage src = editSrc;
			if (src == null || v != editVer.get() || session != editSession.get() || closed) {
				return;
			}
			try {
				byte[] png = kr.lunaslight.mod.util.ShotAdjust.png(a.apply(src));
				if (v != editVer.get() || session != editSession.get() || closed) {
					return;
				}
				NativeImage img = NativeImage.read(new java.io.ByteArrayInputStream(png));
				NativeImage old = bigPending;
				bigPending = img;
				bigState = ST_READY;
				if (old != null) {
					try {
						old.close();
					} catch (Throwable ignored) {
					}
				}
			} catch (Throwable t) {
				LunaCompat.warnOnce("shots:edit-preview", t);
			}
		});
	}

	private void saveEdit() {
		if (saving || open < 0 || adj.isIdentity()) {
			return;
		}
		Shot shot = shots.get(open);
		final kr.lunaslight.mod.util.ShotAdjust a = adj.copy();
		saving = true;
		notice("저장하는 중…");
		editor().submit(() -> {
			Path out = null;
			Throwable err = null;
			try {
				out = a.saveBeside(shot.path);
			} catch (Throwable t) {
				err = t;
			}
			final Path o = out;
			final Throwable e = err;
			if (client == null) {
				return;
			}
			client.execute(() -> {
				saving = false;
				if (closed) {
					return;
				}
				if (o == null) {
					LunaCompat.warnOnce("shots:edit-save", e);
					notice("§c저장하지 못했습니다");
					return;
				}
				for (Shot sh : shots) {
					release(sh);
				}
				scan();
				int idx = 0;
				for (int i = 0; i < shots.size(); i++) {
					if (shots.get(i).path.equals(o)) {
						idx = i;
						break;
					}
				}
				openAt(idx);
				notice("보정한 사진을 새로 저장했습니다  §8" + o.getFileName());
			});
		});
	}

	/** 보정 칸 클릭(슬라이더 잡기, 버튼). 처리했으면 true. */
	private boolean adjustClick(double mouseX, double mouseY, int button) {
		int[] b = adjustBox();
		if (!LunaDraw.in(mouseX, mouseY, b[0], b[1], b[2], b[3])) {
			return false;
		}
		int sx = b[0] + 8, sw = b[2] - 16;
		for (int i = 0; i < ADJ_NAMES.length; i++) {
			int sy = sliderY(b[1], i);
			if (LunaDraw.in(mouseX, mouseY, sx - 4, sy - 8, sw + 8, 20)) {
				if (button == 1) {
					setAdjValue(i, 0);   // 오른쪽 클릭 = 그 값만 0으로
				} else {
					dragSlider = i;
					dragAdjust(mouseX);
				}
				refreshEdit();
				return true;
			}
		}
		int by = b[1] + b[3] - 8 - 18 - 4 - 18;
		if (LunaDraw.in(mouseX, mouseY, sx, by, sw, 18)) {
			adj.reset();
			refreshEdit();
			return true;
		}
		if (LunaDraw.in(mouseX, mouseY, sx, by + 22, sw, 18)) {
			saveEdit();
			return true;
		}
		return true;
	}

	private void dragAdjust(double mouseX) {
		if (dragSlider < 0) {
			return;
		}
		int[] b = adjustBox();
		int sx = b[0] + 8, sw = b[2] - 16;
		float t = (float) Math.max(0, Math.min(1, (mouseX - sx) / (double) sw));
		int v = dragSlider == 4 ? Math.round(t * 100) : Math.round(t * 200 - 100);
		// 가운데(0) 근처는 붙게
		if (dragSlider != 4 && Math.abs(v) <= 3) {
			v = 0;
		}
		if (v != adjValue(dragSlider)) {
			setAdjValue(dragSlider, v);
			refreshEdit();
		}
	}

	private boolean lunaMouseDragged0(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (editing && dragSlider >= 0) {
			dragAdjust(mouseX);
			return true;
		}
		return false;
	}

	private boolean lunaMouseReleased0(double mouseX, double mouseY, int button) {
		if (dragSlider >= 0) {
			dragSlider = -1;
			return true;
		}
		return false;
	}

	/** 아래 줄 버튼 자리: {삭제x, 삭제w, 이름x, 이름w, 복사x, 복사w, 화살표x, 보정x, 보정w}. 그리기와 클릭이 같은 계산을 본다. */
	private int[] bar(int lx, int lw) {
		int dw = barW("삭제");
		int dx = lx + lw - dw;
		int rw = barW("이름 바꾸기");
		int rx = dx - 4 - rw;
		int cw = barW("복사");
		int cx = rx - 4 - cw;
		int ew = barW("보정");
		int ex = cx - 4 - ew;
		int nx = ex - 10 - 42;
		return new int[]{dx, dw, rx, rw, cx, cw, nx, ex, ew};
	}

	private int barW(String label) {
		return LunaIcons.SIZE + 4 + LunaDraw.width(textRenderer, label) + 14;
	}

	private void barButton(DrawContext ctx, int x, int y, int w, int h, String icon, String label, boolean danger,
			int mouseX, int mouseY) {
		boolean hov = LunaDraw.in(mouseX, mouseY, x, y, w, h);
		// 49-227차: 공용 입체 버튼(지우기는 회색 몸통 + 빨간 글자)
		int fg = danger ? (hov ? 0xFFFF8B82 : 0xFFCF7B74) : LunaDraw.buttonText(LunaDraw.B_NEUTRAL, hov ? 1f : 0f);
		LunaDraw.button3d(ctx, x, y, w, h, 4, LunaDraw.B_NEUTRAL, hov ? 1f : 0f);
		LunaIcons.draw(ctx, textRenderer, icon, x + 7, LunaDraw.iconY(y, h), fg);
		LunaDraw.text(ctx, textRenderer, label, x + 7 + LunaIcons.SIZE + 4, LunaDraw.textY(y, h), fg);
	}

	private void drawArrow(DrawContext ctx, int x, int y, int h, String icon, boolean on, int mouseX, int mouseY) {
		boolean hov = on && LunaDraw.in(mouseX, mouseY, x, y, 20, h);
		LunaDraw.button3d(ctx, x, y, 20, h, 4, LunaDraw.B_NEUTRAL, hov ? 1f : 0f);   // 49-227차
		LunaIcons.draw(ctx, textRenderer, icon, x + 6, LunaDraw.iconY(y, h),
			on ? LunaDraw.TEXT : LunaDraw.TEXT_DIM);
	}

	/** 비율을 지키며 상자 안에 넣어 그린다(남는 쪽은 그냥 검은 바탕). */
	private void drawFitted(DrawContext ctx, Identifier tex, int iw, int ih, int x, int y, int w, int h) {
		if (tex == null || iw <= 0 || ih <= 0 || w <= 0 || h <= 0) {
			return;
		}
		double k = Math.min(w / (double) iw, h / (double) ih);
		int dw = Math.max(1, (int) Math.round(iw * k));
		int dh = Math.max(1, (int) Math.round(ih * k));
		LunaGfx.drawImage(ctx, tex, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh, iw, ih,
			LunaDraw.applyAlpha(0xFFFFFFFF));
	}

	private static String when(long millis) {
		if (millis <= 0) {
			return "?";
		}
		return new SimpleDateFormat("M/d HH:mm").format(new Date(millis));
	}

	private static String size(long bytes) {
		if (bytes >= 1024 * 1024) {
			return String.format(java.util.Locale.ROOT, "%.1fMB", bytes / 1048576.0);
		}
		return Math.max(1, bytes / 1024) + "KB";
	}

	// ==================== 입력 ====================

	private void openFolder(Path file) {
		try {
			Path dir = file == null ? folders().get(0) : file.getParent();
			Files.createDirectories(dir);
			net.minecraft.util.Util.getOperatingSystem().open(dir.toUri());
		} catch (Throwable t) {
			LunaCompat.warnOnce("shots:folder", t);
			notice("§c폴더를 열지 못했습니다");
		}
	}

	private void deleteCurrent() {
		Shot shot = shots.get(open);
		try {
			release(shot);
			releaseBig();
			Files.deleteIfExists(shot.path);
			shots.remove(open);
			notice(shot.gif ? "클립을 삭제했습니다" : "스크린샷을 삭제했습니다");
			if (shots.isEmpty()) {
				open = -1;
			} else {
				openAt(Math.min(open, shots.size() - 1));
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("shots:delete", t);
			notice("§c삭제하지 못했습니다 (파일이 열려 있을 수 있습니다)");
		}
	}

	private void askDelete() {
		Shot shot = shots.get(open);
		confirm.show(LunaIcons.TRASH, shot.gif ? "이 클립을 삭제할까요?" : "이 스크린샷을 삭제할까요?",
			shot.name, "삭제", this::deleteCurrent);
	}

	/** [복사] - 그림+파일을 클립보드로(백그라운드). 클립은 파일로만. */
	private void copyCurrent() {
		Shot shot = shots.get(open);
		notice("복사하는 중…");
		kr.lunaslight.mod.util.ClipboardFiles.copy(shot.path, !shot.gif).whenComplete((err, t) -> {
			if (client == null || closed) {
				return;
			}
			client.execute(() -> notice(t != null ? "§c복사하지 못했습니다"
				: err != null ? "§c" + err : (shot.gif ? "클립을 복사했습니다" : "스크린샷을 복사했습니다")));
		});
	}

	private void startRename() {
		Shot shot = shots.get(open);
		int dot = shot.name.lastIndexOf('.');
		renameExt = dot > 0 ? shot.name.substring(dot) : "";
		renameDraft.setLength(0);
		renameDraft.append(dot > 0 ? shot.name.substring(0, dot) : shot.name);
		renaming = true;
	}

	/** Enter(또는 다른 곳 클릭) - 이름을 바꾼다. 빈 이름·그대로면 아무것도 안 한다. */
	private void applyRename() {
		renaming = false;
		if (open < 0 || open >= shots.size()) {
			return;
		}
		String base = renameDraft.toString().trim();
		Shot shot = shots.get(open);
		if (base.isEmpty() || (base + renameExt).equals(shot.name)) {
			return;
		}
		for (char c : "\\/:*?\"<>|".toCharArray()) {
			if (base.indexOf(c) >= 0) {
				notice("§c이름에 쓸 수 없는 글자가 있습니다  \\ / : * ? \" < > |");
				return;
			}
		}
		Path target = shot.path.resolveSibling(base + renameExt);
		if (Files.exists(target)) {
			notice("§c같은 이름의 파일이 이미 있습니다");
			return;
		}
		try {
			Files.move(shot.path, target);
			shot.path = target;
			shot.name = target.getFileName().toString();
			notice("이름을 바꿨습니다");
		} catch (Throwable t) {
			LunaCompat.warnOnce("shots:rename", t);
			notice("§c이름을 바꾸지 못했습니다 (파일이 열려 있을 수 있습니다)");
		}
	}

	private boolean lunaMouseClicked0(double mouseX, double mouseY, int button) {
		if (confirm.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (renaming) {
			int lx = px + PAD;
			int lw = panelW - PAD * 2;
			int by = py + HEAD_H + (panelH - HEAD_H - PAD - 20 - 6) + 6;
			if (LunaDraw.in(mouseX, mouseY, lx, by, bar(lx, lw)[6] - 8 - lx, 18)) {
				return true;   // 입력 칸 안 - 그대로
			}
			applyRename();     // 다른 곳 클릭 = 저장하고 그 클릭은 버린다
			return true;
		}
		if (LunaDraw.in(mouseX, mouseY, px + 10, py + 12, 20, 20)) {
			if (open >= 0) {
				releaseBig();
				open = -1;
				return true;
			}
			close();
			return true;
		}
		if (!LunaDraw.in(mouseX, mouseY, px, py, panelW, panelH)) {
			close();
			return true;
		}
		int fw = LunaDraw.width(textRenderer, "폴더 열기") + 16;
		int fx = px + panelW - PAD - fw;
		if (LunaDraw.in(mouseX, mouseY, fx, py + 14, fw, 16)) {
			openFolder(open >= 0 ? shots.get(open).path : null);
			return true;
		}

		int lx = px + PAD;
		int ly = py + HEAD_H;
		int lw = panelW - PAD * 2;
		int lh = panelH - HEAD_H - PAD;

		if (open >= 0) {
			if (editing && adjustClick(mouseX, mouseY, button)) {
				return true;
			}
			int barH = 20;
			int by = ly + (lh - barH - 6) + 6;
			int bh = barH - 2;
			int[] b = bar(lx, lw);
			Shot cur = shots.get(open);
			if (thumbs && !cur.gif && LunaDraw.in(mouseX, mouseY, b[7], by, b[8], bh)) {
				if (editing) {
					stopEdit();
				} else {
					startEdit();
				}
				return true;
			}
			if (LunaDraw.in(mouseX, mouseY, b[0], by, b[1], bh)) {
				askDelete();
			} else if (LunaDraw.in(mouseX, mouseY, b[2], by, b[3], bh)) {
				startRename();
			} else if (LunaDraw.in(mouseX, mouseY, b[4], by, b[5], bh)) {
				copyCurrent();
			} else if (LunaDraw.in(mouseX, mouseY, b[6], by, 20, bh)) {
				openAt(open - 1);
			} else if (LunaDraw.in(mouseX, mouseY, b[6] + 22, by, 20, bh)) {
				openAt(open + 1);
			}
			return true;
		}

		if (LunaDraw.in(mouseX, mouseY, lx, ly, lw, lh) && !shots.isEmpty()) {
			int step = cellH() + GAP;
			int row = (int) ((mouseY - ly + scroll) / step);
			int col = (int) ((mouseX - lx) / (double) (cellW + GAP));
			int inCol = (int) ((mouseX - lx) % (cellW + GAP));
			int inRow = (int) ((mouseY - ly + scroll) % step);
			if (col >= 0 && col < cols && inCol <= cellW && inRow <= cellH()) {
				int index = row * cols + col;
				if (index >= 0 && index < shots.size()) {
					openAt(index);
				}
			}
		}
		return true;
	}

	private boolean lunaMouseScrolled0(double mouseX, double mouseY, double verticalAmount) {
		if (open >= 0) {
			return true;
		}
		scroll = Math.max(0, scroll - verticalAmount * 24);
		return true;
	}

	private boolean lunaKeyPressed0(int keyCode, int scanCode, int modifiers) {
		if (confirm.keyPressed(keyCode)) {
			return true;
		}
		if (renaming) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				renaming = false;
			} else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				applyRename();
			} else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && renameDraft.length() > 0) {
				renameDraft.setLength(renameDraft.length() - 1);
			}
			return true;   // 입력 중엔 다른 키가 화면을 넘기거나 닫지 않게
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			if (editing) {
				stopEdit();
				return true;
			}
			if (open >= 0) {
				releaseBig();
				open = -1;
			} else {
				close();
			}
			return true;
		}
		if (open >= 0 && keyCode == GLFW.GLFW_KEY_LEFT) {
			openAt(open - 1);
			return true;
		}
		if (open >= 0 && keyCode == GLFW.GLFW_KEY_RIGHT) {
			openAt(open + 1);
			return true;
		}
		return false;
	}

	private boolean lunaCharTyped0(char chr) {
		if (!renaming || chr < ' ') {
			return false;
		}
		if (renameDraft.length() < 80) {
			renameDraft.append(chr);
		}
		return true;
	}
}

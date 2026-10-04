package kr.lunaslight.mod.util;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.IoSupplier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 49-200차(사용자: 사진 "queue에 연결할 수 없습니다: 내부 서버 연결 오류" + "서버 리소스팩 다운로드 받다가 이러는데 왜 그래?"):
 * 서버 리소스팩(zip)에서 파일 목록을 뽑는 {@code FilePackResources.listResources}를 색인으로 빠르게 한다.
 *
 * <h3>왜 끊겼나</h3>
 * 바닐라는 목록을 뽑을 때마다(글꼴, 텍스처, 모델, 소리 … 종류마다, 그리고 <b>팩 안의 네임스페이스마다</b>) zip의 항목을
 * <b>처음부터 끝까지 전부</b> 훑는다. MineRest처럼 파일 이름을 뒤섞어(q7:rk/nd/… 같은) 네임스페이스가 수백 개인 팩은
 * "네임스페이스 수 × 항목 수"번을 훑게 되고, 그중 글꼴 목록(FontManager.prepare)은 <b>렌더 스레드에서</b> 돌아서
 * 게임이 17~20초 멈췄다(런처 로그의 [화면 멈춤] 스택). 그동안 서버의 연결 확인에 답을 못 해 프록시가 대기열(queue)
 * 서버로 넘기다 실패하고 연결을 끊었다.
 *
 * <h3>어떻게</h3>
 * zip마다 한 번만 항목 이름을 정렬해 두고(색인), 요청한 경로로 시작하는 구간만 이진 탐색으로 바로 찾는다.
 * 돌려주는 결과는 바닐라와 똑같다(같은 Identifier, 같은 IoSupplier, zip 안 순서 그대로). 무엇이든 실패하면 false를
 * 돌려 바닐라가 원래대로 처리한다.
 */
public final class ZipListIndex {
	private ZipListIndex() {
	}

	private static final class Index {
		final String[] names;     // 이름순으로 정렬
		final ZipEntry[] entries; // names와 같은 순서
		final int[] order;        // zip 안 원래 순서

		Index(String[] names, ZipEntry[] entries, int[] order) {
			this.names = names;
			this.entries = entries;
			this.order = order;
		}
	}

	private static final Map<ZipFile, Index> CACHE = new WeakHashMap<>();
	private static Field prefixField;
	private static Field accessField;
	private static Method openZip;
	private static boolean broken;

	private static Index indexOf(ZipFile zip) {
		synchronized (CACHE) {
			Index hit = CACHE.get(zip);
			if (hit != null) {
				return hit;
			}
		}
		List<ZipEntry> list = new ArrayList<>();
		Enumeration<? extends ZipEntry> en = zip.entries();
		while (en.hasMoreElements()) {
			ZipEntry e = en.nextElement();
			if (!e.isDirectory()) {
				list.add(e);
			}
		}
		int n = list.size();
		Integer[] idx = new Integer[n];
		for (int i = 0; i < n; i++) {
			idx[i] = i;
		}
		Arrays.sort(idx, (a, b) -> list.get(a).getName().compareTo(list.get(b).getName()));
		String[] names = new String[n];
		ZipEntry[] entries = new ZipEntry[n];
		int[] order = new int[n];
		for (int i = 0; i < n; i++) {
			ZipEntry e = list.get(idx[i]);
			names[i] = e.getName();
			entries[i] = e;
			order[i] = idx[i];
		}
		Index made = new Index(names, entries, order);
		synchronized (CACHE) {
			CACHE.put(zip, made);
		}
		return made;
	}

	private static ZipFile zipOf(Object pack) throws Exception {
		if (accessField == null) {
			Class<?> c = pack.getClass();
			while (c != null && !c.getName().endsWith("FilePackResources")) {
				c = c.getSuperclass();
			}
			if (c == null) {
				throw new IllegalStateException("FilePackResources 아님");
			}
			Field pf = c.getDeclaredField("prefix");
			pf.setAccessible(true);
			Field af = c.getDeclaredField("zipFileAccess");
			af.setAccessible(true);
			Method m = af.getType().getDeclaredMethod("getOrCreateZipFile");
			m.setAccessible(true);
			prefixField = pf;
			openZip = m;
			accessField = af;
		}
		Object access = accessField.get(pack);
		return access == null ? null : (ZipFile) openZip.invoke(access);
	}

	/** 처리했으면 true(바닐라는 건너뜀), 못 했으면 false(바닐라가 원래대로). */
	public static boolean list(Object pack, String directory, String namespace, String path,
			PackResources.ResourceOutput output) {
		if (broken) {
			return false;
		}
		try {
			ZipFile zip = zipOf(pack);
			if (zip == null) {
				return true;   // 바닐라도 zip이 없으면 아무것도 안 하고 끝낸다
			}
			String prefix = (String) prefixField.get(pack);
			String rel = directory + "/" + namespace + "/";
			String base = prefix == null || prefix.isEmpty() ? rel : prefix + "/" + rel;
			String full = base + path + "/";
			Index ix = indexOf(zip);
			int lo = lowerBound(ix.names, full);
			int hi = lo;
			while (hi < ix.names.length && ix.names[hi].startsWith(full)) {
				hi++;
			}
			if (hi == lo) {
				return true;
			}
			Integer[] hits = new Integer[hi - lo];
			for (int i = lo; i < hi; i++) {
				hits[i - lo] = i;
			}
			Arrays.sort(hits, (a, b) -> Integer.compare(ix.order[a], ix.order[b]));   // zip 안 원래 순서로(바닐라와 같게)
			for (Integer i : hits) {
				String name = ix.names[i];
				Identifier id = Identifier.tryBuild(namespace, name.substring(base.length()));
				if (id != null) {
					output.accept(id, IoSupplier.create(zip, ix.entries[i]));
				}
			}
			return true;
		} catch (Throwable t) {
			broken = true;
			LunaCompat.warnOnce("zipListIndex", t);
			return false;
		}
	}

	private static int lowerBound(String[] a, String key) {
		int lo = 0;
		int hi = a.length;
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (a[mid].compareTo(key) < 0) {
				lo = mid + 1;
			} else {
				hi = mid;
			}
		}
		return lo;
	}
}

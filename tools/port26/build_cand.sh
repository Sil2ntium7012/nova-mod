#!/bin/bash
cd /home/claude/port
rm -rf cand26 && cp -r zip26/client/java cand26
python3 - <<'PY'
import os,shutil,glob
NEW=['kr/novaclient/mod/module/impl/misc/SessionFixModule.java','kr/novaclient/mod/module/impl/inventory/InventorySortModule.java','kr/novaclient/mod/module/setting/KeybindListSetting.java','kr/novaclient/mod/module/setting/PriceListSetting.java']
SKIP_PREFIX=['kr/novaclient/mod/mixin/']
SKIP=set(open('skip.txt').read().split()) if os.path.exists('skip.txt') else set()
for a in glob.glob('port26/**/*.java',recursive=True):
    rel=a.split('port26/')[1]
    if any(rel.startswith(p) for p in SKIP_PREFIX) or rel in SKIP: continue
    dst='cand26/'+rel
    if not os.path.exists(dst) and rel not in NEW: continue
    os.makedirs(os.path.dirname(dst),exist_ok=True); shutil.copy(a,dst)
# extra overlays (hand-ported files)
for a in glob.glob('hand26/**/*.java',recursive=True):
    rel=a.split('hand26/')[1]; dst='cand26/'+rel
    os.makedirs(os.path.dirname(dst),exist_ok=True); shutil.copy(a,dst)
PY
find cand26 -name "*.java" > cand26.list; rm -rf cc; mkdir cc
javac -proc:none -d cc -cp "$(cat cp26.txt)" -encoding UTF-8 --release 21 -Xmaxerrs 3000 @cand26.list 2>&1 | grep -v JAVA_TOOL > cand_errors.txt
echo "errors: $(grep -c 'error:' cand_errors.txt)"
grep -A3 "error:" cand_errors.txt | grep -v "fabric\|voicechat\|terraformers\|^--$" | grep -E "error:|symbol:" | grep -v "package .* does not exist\|cannot access" | paste - - 2>/dev/null | sed 's/cand26\/kr\/novaclient\/mod\///' | head -${1:-60}

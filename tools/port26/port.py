import re,os,sys,json,glob,collections
maps=json.load(open('/home/claude/port/maps.json')); CLS=maps['cls']; MEM=maps['mem']
G=collections.defaultdict(set)
for o,d in MEM.items():
    for k,v in d.items():
        kind,name=k.split(':',1)
        for x in v: G[(kind,name)].add(x)
PROJ=set(json.load(open('/home/claude/port/project_members.json')))
IDENT=r'[A-Za-z_][A-Za-z0-9_]*'
WHITELIST=set(json.load(open('/home/claude/port/whitelist.json'))) if os.path.exists('/home/claude/port/whitelist.json') else set()

# ---- 26.x specific renames (mojang 1.21.11 -> 26.x) ----
CLASS262={
 'net.minecraft.client.gui.GuiGraphics':'net.minecraft.client.gui.GuiGraphicsExtractor',
 'net.minecraft.world.inventory.ClickType':'net.minecraft.world.inventory.ContainerInput',
 'net.minecraft.client.gui.screens.LoadingOverlay':'net.minecraft.client.gui.screens.LoadingOverlay',
}
SIMPLE262={'GuiGraphics':'GuiGraphicsExtractor','ClickType':'ContainerInput'}
# member renames applied by regex on ".name(" (26.x renamed API)
MEMBER262=[
 ('handleInventoryMouseClick','handleContainerInput'),
 ('drawString','text'),
 ('drawText','text'),
 ('drawTextWithShadow','text'),
 ('drawCenteredString','centeredText'),
 ('renderItem','item'),
 ('renderFakeItem','fakeItem'),
 ('renderItemDecorations','itemDecorations'),
 ('renderBackground','extractBackground'),
 ('renderImage','extractImage'),

 ('getUuid','getUUID'),
 ('hasStatusEffect','hasEffect'),
 ('getEffectType','getEffect'),
 ('getScaleFactor','getGuiScale'),
]
def fq_strings(s):
    # translate FQCN string literals "net.minecraft...." (yarn) -> mojang
    def rep(m):
        v=m.group(1)
        parts=v.split('.')
        for n in range(len(parts),1,-1):
            pre='.'.join(parts[:n])
            if pre in CLS:
                mo=CLS[pre]
                mo=CLASS262.get(mo,mo)
                return '"'+mo+('.'+'.'.join(parts[n:]) if n<len(parts) else '')+'"'
        return m.group(0)
    return re.sub(r'"((?:net\.minecraft|com\.mojang)(?:\.'+IDENT+r')+)"',rep,s)
def fallback_members(s,log):
    # rename remaining yarn member names Mercury missed (unique global mapping, not a project member)
    def repl(m):
        name=m.group(1); paren=m.group(2)
        kind='m' if paren else 'f'
        if name in PROJ: return m.group(0)
        if name not in WHITELIST: return m.group(0)
        c=G.get((kind,name))
        if c and len(c)==1:
            t=next(iter(c))
            if t!=name: log.append((kind,name,t)); return '.'+t+(paren or '')
        return m.group(0)
    out=[]
    for ln in s.split('\n'):
        st=ln.lstrip()
        if st.startswith('//') or st.startswith('*') or st.startswith('/*') or st.startswith('import ') or st.startswith('package '):
            out.append(ln); continue
        segs=re.split(r'("(?:\\.|[^"\\])*")',ln)
        for i in range(0,len(segs),2):
            segs[i]=re.sub(r'(?<![\w.])(?<!kr\.novaclient\.)\.('+IDENT+r')(\s*\()?',lambda m: repl(m) if not re.search(r'kr\.novaclient(\.\w+)*$', segs[i][:m.start()]) else m.group(0), segs[i])
        out.append(''.join(segs))
    return '\n'.join(out)
def fix262(s, rel):
    for a,b in CLASS262.items():
        s=s.replace(a,b)
    s=re.sub(r'\bGuiGraphics\b','GuiGraphicsExtractor',s)
    s=re.sub(r'\bClickType\b','ContainerInput',s)
    for a,b in MEMBER262:
        if a!=b: s=re.sub(r'\.'+a+r'\s*\(', '.'+b+'(', s)
    for a,b in [('renderImage','extractImage'),('renderBackground','extractBackground')]:
        s=re.sub(r'\b(public|protected|private)(\s+\w+)*\s+void\s+'+a+r'\(', lambda m: m.group(0).replace(a+'(', b+'('), s)
    # Minecraft.screen field / setScreen -> NovaCompat helpers (26.x)
    if 'class NovaCompat' not in s:
        s=re.sub(r'\b(client|mc|minecraft|this\.minecraft|this\.client|Minecraft\.getInstance\(\))\.screen\b(?!\s*\()', r'kr.novaclient.mod.util.NovaCompat.screenOf(\1)', s)
        s=re.sub(r'\b(client|mc|minecraft|this\.minecraft|this\.client|Minecraft\.getInstance\(\))\.setScreen\(', r'kr.novaclient.mod.util.NovaCompat.showScreen(\1, ', s)
        s=re.sub(r'\b(client|mc|minecraft)\.getMainRenderTarget\(\)', r'kr.novaclient.mod.util.NovaCompat.mainRenderTarget(\1)', s)
    # Screen.render override -> extractRenderState (screens + mixins injecting into render)
    if re.search(r'extends\s+(Nova\w*Screen\w*|Screen|NovaScreenBase)\b', s):
        s=re.sub(r'\bvoid render\(GuiGraphicsExtractor', 'void extractRenderState(GuiGraphicsExtractor', s)
        s=re.sub(r'\bsuper\.render\(', 'super.extractRenderState(', s)
    return s
def process(src):
    log=[]
    s=src.replace('\r\n','\n')
    s=fq_strings(s)
    s=fallback_members(s,log)
    s=fix262(s,'')
    return s,log
if __name__=='__main__':
    inp=sys.argv[1]; outp=sys.argv[2]
    alllog=collections.Counter()
    for a in glob.glob(inp+'/**/*.java',recursive=True):
        rel=a.split(inp+'/')[1]
        s=open(a,encoding='utf-8').read()
        r,log=process(s)
        for l in log: alllog[l]+=1
        o=os.path.join(outp,rel); os.makedirs(os.path.dirname(o),exist_ok=True)
        open(o,'w',encoding='utf-8').write(r)
    for k,v in alllog.most_common(): print(v,k)

import re,sys
def extract(src, name):
    lines=src.split('\n')
    for i,ln in enumerate(lines):
        if re.match(r'\s*(public|private|protected)?\s*(static\s+)?(final\s+)?(synchronized\s+)?[\w<>\[\],.? ]+\s+'+re.escape(name)+r'\s*\(', ln) and '=' not in ln.split('(')[0]:
            # preceding javadoc
            s=i
            while s>0 and (lines[s-1].strip().startswith('*') or lines[s-1].strip().startswith('/**') or lines[s-1].strip().startswith('//') or lines[s-1].strip().startswith('@')):
                s-=1
            depth=0; started=False; k=i
            while k<len(lines):
                code=re.sub(r'"(?:\\.|[^"\\])*"','""',lines[k]); code=re.sub(r'//.*','',code); code=re.sub(r"'(?:\\.|[^'\\])'","''",code)
                depth+=code.count('{')-code.count('}')
                if '{' in code: started=True
                if started and depth==0: break
                if not started and ';' in code: break
                k+=1
            return '\n'.join(lines[s:k+1])
    return None
if __name__=='__main__':
    src=open(sys.argv[1],encoding='utf-8').read().replace('\r','')
    dst=open(sys.argv[2],encoding='utf-8').read().replace('\r','')
    names=sys.argv[3:]
    add=[]
    for n in names:
        seg=extract(src,n)
        if seg is None: print('NOT FOUND',n); continue
        if re.search(r'\b'+re.escape(n)+r'\s*\(', dst) and extract(dst,n) is not None:
            print('EXISTS',n); continue
        add.append(seg)
    end=dst.rstrip().rfind('}')
    out=dst[:end].rstrip('\n')+'\n\n'+'\n\n'.join(add)+'\n}\n'
    open(sys.argv[2],'w',encoding='utf-8').write(out)
    print('added',len(add))

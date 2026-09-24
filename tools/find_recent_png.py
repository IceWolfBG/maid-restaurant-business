import os, datetime
from PIL import Image

roots = [r"C:\Users\26529\Downloads", r"C:\Users\26529\Desktop",
         r"C:\Users\26529\Documents", r"C:\Users\26529\Pictures"]
cutoff = datetime.datetime(2026,9,18)
cands = []
for root in roots:
    for dp,_,fns in os.walk(root):
        for fn in fns:
            if not fn.lower().endswith(".png"): continue
            p = os.path.join(dp,fn)
            try:
                st = os.stat(p)
                mt = datetime.datetime.fromtimestamp(st.st_mtime)
                if mt < cutoff: continue
                im = Image.open(p); w,h = im.size
                cands.append((st.st_size,w,h,mt,p))
            except Exception:
                pass
for size,w,h,mt,p in sorted(cands,key=lambda x:-x[0]):
    flag = " <<<" if (size>3000 and (w in (64,128,256) or h in (64,128,256))) else ""
    print(f"{size:>9} {w}x{h} {mt:%m-%d %H:%M} {p}{flag}")

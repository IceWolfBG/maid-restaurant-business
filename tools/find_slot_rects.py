import numpy as np
from PIL import Image
im=Image.open(r'C:\Users\26529\AppData\Local\DoubaoWork\User Data\ClipboardTemp\5758da9c-7986-4cf7-a6d0-78d19167231f.png').convert('RGB')
a=np.array(im).astype(int)
# panel region
x0,x1,y0,y1=455,1010,290,478
sub=a[y0:y1,x0:x1]
lum=sub.mean(2)
# near-black slot interiors
dark=lum<55
# label connected components (simple BFS)
H,W=dark.shape
seen=np.zeros_like(dark,bool)
comps=[]
from collections import deque
for sy in range(H):
    for sx in range(W):
        if dark[sy,sx] and not seen[sy,sx]:
            q=deque([(sy,sx)]);seen[sy,sx]=1;pts=[]
            while q:
                cy,cx=q.popleft();pts.append((cy,cx))
                for dy,dx in((1,0),(-1,0),(0,1),(0,-1)):
                    ny,nx=cy+dy,cx+dx
                    if 0<=ny<H and 0<=nx<W and dark[ny,nx] and not seen[ny,nx]:
                        seen[ny,nx]=1;q.append((ny,nx))
            if len(pts)>150:
                ys=[p[0] for p in pts];xs=[p[1] for p in pts]
                comps.append((min(xs)+x0,max(xs)+x0,min(ys)+y0,max(ys)+y0,len(pts)))
for c in sorted(comps):
    print('rect x%d-%d y%d-%d area%d'%c)

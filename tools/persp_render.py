import json, math, io
import numpy as np
from PIL import Image

RES=r'D:\DoubaoWork\MaidRestaurantBusiness\forge-1.20.1\src\main\resources\assets\maid_restaurant_business'
TEX=np.array(Image.open(r'D:\DoubaoWork\jiuhu_station_models\sakefox_station.png').convert('RGBA'))
TH,THW=TEX.shape[0],TEX.shape[1]
def px(u,v):
    x=int(round(u/16*THW)); y=int(round(v/16*TH))
    x=max(0,min(THW-1,x)); y=max(0,min(TH-1,y))
    return TEX[y,x]

def load(p):
    return json.load(open(p,encoding='utf-8'))
static=load(RES+r'\models\block\jiuhu_station.json')
bags=[load(RES+rf'\models\block\anim\bag{i}.json') for i in range(1,6)]

# collect quads: each quad = verts(4,3), color(4,), shade
def model_quads(m, shift=(0,0,0)):
    qs=[]
    for e in m['elements']:
        f=np.array(e['from'],float); t=np.array(e['to'],float)
        # 8 corners
        c={}
        for i,(x) in enumerate([f[0],t[0]]):
            for j,y in enumerate([f[1],t[1]]):
                for k,z in enumerate([f[2],t[2]]):
                    c[(i,j,k)]=np.array([x+shift[0],y+shift[1],z+shift[2]])
        for face,fd in e['faces'].items():
            uv=fd['uv']
            col=np.array(px((uv[0]+uv[2])/2,(uv[1]+uv[3])/2),float)
            if col[3]<2: continue
            if face=='north': vs=[c[(0,0,0)],c[(1,0,0)],c[(1,1,0)],c[(0,1,0)]]; n=(0,0,-1)
            elif face=='south': vs=[c[(1,0,1)],c[(0,0,1)],c[(0,1,1)],c[(1,1,1)]]; n=(0,0,1)
            elif face=='west': vs=[c[(0,0,1)],c[(0,0,0)],c[(0,1,0)],c[(0,1,1)]]; n=(-1,0,0)
            elif face=='east': vs=[c[(1,0,0)],c[(1,0,1)],c[(1,1,1)],c[(1,1,0)]]; n=(1,0,0)
            elif face=='up': vs=[c[(0,1,1)],c[(1,1,1)],c[(1,1,0)],c[(0,1,0)]]; n=(0,1,0)
            elif face=='down': vs=[c[(0,0,0)],c[(1,0,0)],c[(1,0,1)],c[(0,0,1)]]; n=(0,-1,0)
            qs.append((np.array(vs),col,np.array(n,float)))
    return qs

def build_scene(occupied, apply_none_minus_half=True):
    scene=[]
    scene+=model_quads(static)
    sh=(-0.5,-0.5,-0.5) if apply_none_minus_half else (0,0,0)
    for i in range(5):
        if occupied[i]:
            scene+=model_quads(bags[i],sh)
    return scene

def render(occupied, elev_deg, azim_deg, fname, size=560, half=True):
    scene=build_scene(occupied,half)
    elev=math.radians(elev_deg); azim=math.radians(azim_deg)
    # camera position in block units; looking at center (8,8,8)
    target=np.array([8,7,8.])
    dist=30
    eye=np.array([
        target[0]+dist*math.cos(elev)*math.sin(azim),
        target[1]-dist*math.sin(elev),
        target[2]+dist*math.cos(elev)*math.cos(azim)])
    fwd=target-eye; fwd/=np.linalg.norm(fwd)
    up0=np.array([0,1.,0])
    right=np.cross(fwd,up0); right/=np.linalg.norm(right)
    up=np.cross(right,fwd)
    fov=math.radians(42)
    img=np.zeros((size,size,4),np.uint8)
    zbuf=np.full((size,size),1e9)
    items=[]
    for vs,col,n in scene:
        # lighting
        ldir=np.array([0.3,1,0.25]); ldir/=np.linalg.norm(ldir)
        shade=0.55+0.45*max(0,abs(np.dot(n/np.linalg.norm(n),ldir)))
        c=col.copy(); c[:3]=np.clip(c[:3]*shade,0,255); c[3]=255
        # transform verts
        cam=np.array([[np.dot(v-eye,right),np.dot(v-eye,up),np.dot(v-eye,fwd)] for v in vs])  # (4,3)
        items.append((cam,c))
    # painter's algorithm sort by average depth (far first)
    items.sort(key=lambda it:-it[0][:,2].mean())
    for cam,c in items:
        if (cam[:,2]<=0.1).all(): continue
        pts=[]
        for x,y,z in cam:
            if z<=0.1: z=0.1
            sx=size/2+(x/z)*(size/2)/math.tan(fov/2)
            sy=size/2-(y/z)*(size/2)/math.tan(fov/2)
            pts.append((sx,sy))
        # rasterize via PIL draw on a temp layer (per-face flat)
        from PIL import ImageDraw
        layer=Image.new('RGBA',(size,size),(0,0,0,0))
        d=ImageDraw.Draw(layer)
        d.polygon(pts,fill=tuple(int(v) for v in c))
        img=np.array(Image.alpha_composite(Image.fromarray(img),layer))
    Image.fromarray(img).save(fname)
    print('saved',fname)

occ=[True,False,False,False,False]
render(occ,0,0,r'D:\DoubaoWork\MaidRestaurantBusiness\art\persp_front.png')
render(occ,40,0,r'D:\DoubaoWork\MaidRestaurantBusiness\art\persp_e40.png')
render(occ,60,0,r'D:\DoubaoWork\MaidRestaurantBusiness\art\persp_e60.png')
# also without the -0.5 to compare
render(occ,60,0,r'D:\DoubaoWork\MaidRestaurantBusiness\art\persp_e60_nohalf.png',half=False)

import json, io, os
import numpy as np
from PIL import Image

TEX = r"D:\DoubaoWork\jiuhu_station_models\sakefox_station.png"
STATIC = r"D:\DoubaoWork\MaidRestaurantBusiness\forge-1.20.1\src\main\resources\assets\maid_restaurant_business\models\block\jiuhu_station.json"
ANIM = r"D:\DoubaoWork\MaidRestaurantBusiness\forge-1.20.1\src\main\resources\assets\maid_restaurant_business\models\block\anim"
OUT = r"D:\DoubaoWork\MaidRestaurantBusiness\art"
tex = np.asarray(Image.open(TEX).convert("RGBA"))
TH,TW = tex.shape[0], tex.shape[1]
SCALE = TW/16.0  # uv 16-space -> px

def load_faces(path):
    m=json.load(io.open(path,"r",encoding="utf-8"))
    out=[]
    for e in m.get("elements",[]):
        f=np.array(e["from"],float); t=np.array(e["to"],float)
        C=np.array([[f[0],f[1],f[2]],[t[0],f[1],f[2]],[t[0],t[1],f[2]],[f[0],t[1],f[2]],
                    [f[0],f[1],t[2]],[t[0],f[1],t[2]],[t[0],t[1],t[2]],[f[0],t[1],t[2]]])
        WIND={'north':[3,2,1,0],'south':[6,7,4,5],'west':[3,7,4,0],
              'east':[6,2,1,5],'up':[3,2,6,7],'down':[4,5,1,0]}
        for dirn,fc in (e.get("faces") or {}).items():
            uv=fc["uv"]; idx=WIND[dirn]
            q=C[idx]  # 4 corners world
            out.append((dirn,q,uv))
    return out

def Ry(points,deg):
    a=np.radians(deg);c,s=np.cos(a),np.sin(a)
    R=np.array([[c,0,s],[0,1,0],[-s,0,c]])
    p=points.copy()
    center=np.array([8,0,8])
    p-=center; p=p@R.T; p+=center
    return p

def render(facing_deg, bagmask, fname):
    # gather faces: static (blockstate) + bags (wrapper); same rotation Ry(-facing)
    faces=[]
    for d,q,uv in load_faces(STATIC):
        faces.append((d,Ry(q,-facing_deg),uv,0))
    for i in range(5):
        if bagmask[i]:
            for d,q,uv in load_faces(os.path.join(ANIM,"bag%d"%(i+1)+".json")):
                faces.append((d,Ry(q,-facing_deg),uv,1))
    # front orthographic: view along +Z (camera -Z). painter sort far z first
    items=[]
    for d,q,uv,grp in faces:
        items.append((q[:,2].mean(),d,q,uv))
    items.sort(key=lambda x:-x[0])
    PAD=2; S=24  # px per world unit
    W=H=S*20
    img=np.zeros((H,W,4),np.uint8)
    for z,d,q,uv in items:
        # screen coords
        sx=(q[:,0]+2)*S; sy=H-(q[:,1]+2)*S
        u0,v0,u1,v1=[v*SCALE for v in uv]
        u0=max(0,min(TW-1,int(round(u0))));u1=max(u0+1,min(TW,int(round(u1))))
        v0=max(0,min(TH-1,int(round(v0))));v1=max(v0+1,min(TH,int(round(v1))))
        patch=tex[v0:v1,u0:u1]
        # quad bbox raster via PIL
        minx,maxx=sx.min(),sx.max();miny,maxy=sy.min(),sy.max()
        if maxx-minx<1 or maxy-miny<1: continue
        # affine-warp patch into quad
        pil=Image.fromarray(patch)
        canvas=Image.fromarray(img)
        dw=max(1,int(maxx-minx));dh=max(1,int(maxy-miny))
        pil=pil.resize((dw,dh),Image.NEAREST)
        canvas.alpha_composite(pil,(int(minx),int(miny)))
        img=np.asarray(canvas)
    Image.fromarray(img).save(os.path.join(OUT,fname))
    print("saved",fname)

render(0,[1,0,1,0,1],"front_north.png")
render(90,[1,0,1,0,1],"front_east.png")

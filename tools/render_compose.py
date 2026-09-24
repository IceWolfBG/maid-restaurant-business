import json, os
import numpy as np
from PIL import Image

A = r'D:\DoubaoWork\MaidRestaurantBusiness\forge-1.20.1\src\main\resources\assets\maid_restaurant_business'
TEX = r'D:\DoubaoWork\jiuhu_station_models\sakefox_station.png'
OUT = r'D:\DoubaoWork\MaidRestaurantBusiness\art'
teximg = Image.open(TEX).convert('RGBA')

def corners(f,t):
    fx,fy,fz=f;tx,ty,tz=t
    return np.array([[fx,fy,fz],[tx,fy,fz],[tx,ty,fz],[fx,ty,fz],
                     [fx,fy,tz],[tx,fy,tz],[tx,ty,tz],[fx,ty,tz]],float)
FACES={'north':((0,0,-1),[3,2,1,0],(-1,0,0),(0,-1,0)),
 'south':((0,0,1),[6,7,4,5],(1,0,0),(0,-1,0)),
 'west':((-1,0,0),[3,7,4,0],(0,0,1),(0,-1,0)),
 'east':((1,0,0),[6,2,1,5],(0,0,-1),(0,-1,0)),
 'up':((0,1,0),[3,2,6,7],(1,0,0),(0,0,1)),
 'down':((0,-1,0),[4,5,1,0],(1,0,0),(0,0,-1))}
def Rx(d):
    a=np.radians(d);c,s=np.cos(a),np.sin(a);return np.array([[1,0,0],[0,c,-s],[0,s,c]])
def Ry(d):
    a=np.radians(d);c,s=np.cos(a),np.sin(a);return np.array([[c,0,s],[0,1,0],[-s,0,c]])

def load_model(rel):
    m=json.load(open(os.path.join(A,rel),encoding='utf-8'))
    out=[]
    texmap=m.get('textures',{})
    for el in m.get('elements',[]):
        C=corners(el['from'],el['to'])
        for key,face in el.get('faces',{}).items():
            if key not in FACES or not face.get('uv'):continue
            n,wind,uR,vd=FACES[key]
            out.append((C[wind],np.array(n,float),np.array(uR,float),np.array(vd,float),face['uv'],key))
    return out

def find_coeffs(src,dst):
    A=[]
    for (x,y),(u,v) in zip(dst,src):
        A.append([x,y,1,0,0,0,-u*x,-u*y]);A.append([0,0,0,x,y,1,-v*x,-v*y])
    return np.linalg.solve(np.array(A),np.array(src).reshape(8))

def render(models,name,V):
    world=[]
    for md in models: world+=load_model(md)
    items=[]
    for q,n,uR,vd,uv,key in world:
        nv=V@n
        if nv[2]>=-0.001:continue
        pv=(V@q.T).T
        items.append((pv.mean(0)[2],pv,q,uR,vd,uv,key,nv))
    items.sort(key=lambda z:z[0],reverse=True)
    allv=np.vstack([z[1] for z in items]);sx,sy=allv[:,0],-allv[:,1]
    SCALE=22;W=int((sx.max()-sx.min())*SCALE)+60;H=int((sy.max()-sy.min())*SCALE)+60
    ox=30-sx.min()*SCALE;oy=30-sy.min()*SCALE
    canvas=Image.new('RGBA',(W,H),(46,50,60,255))
    S=8.0  # MC UV 0-16 -> texture px 0-128
    for dep,pv,wq,uR,vd,uv,key,nv in items:
        u1,v1,u2,v2=[x*S for x in uv]
        du=wq@uR;dv=wq@vd
        bright=np.clip(0.55+0.45*(-nv[2]),0.55,1.0)
        if key=='up':bright=1.0
        crop=teximg.crop((max(0,u1),max(0,v1),min(128,u2),min(128,v2))).convert('RGBA')
        cw,ch=crop.size
        if cw<1 or ch<1:continue
        arr=np.array(crop).astype(float);arr[:,:,:3]*=bright
        crop=Image.fromarray(arr.astype(np.uint8))
        spts=[(x*SCALE+ox,-y*SCALE+oy) for x,y in pv[:,:2]]
        tpts=[(0 if x<=du.min()+1e-6 else cw, 0 if y<=dv.min()+1e-6 else ch) for x,y in zip(du,dv)]
        coeffs=find_coeffs(tpts,spts)
        warped=crop.transform((W,H),Image.PERSPECTIVE,coeffs,Image.BILINEAR)
        canvas=Image.alpha_composite(canvas,warped)
    out=os.path.join(OUT,name);canvas.save(out);print('saved',out,canvas.size)

static='models/block/jiuhu_station.json'
render([static],'compose_static_front.png',Rx(-8))
bags=[f'models/block/anim/bag{i}.json' for i in range(1,6)]
render([static]+[bags[0]],'compose_bag1_front.png',Rx(-8))
render([static]+bags,'compose_all_front.png',Rx(-8))
render([static]+bags,'compose_all_iso.png',Rx(-18)@Ry(-28))

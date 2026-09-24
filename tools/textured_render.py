import json, os
from collections import Counter
import numpy as np
from PIL import Image, ImageDraw

BB = r'C:\Users\26529\Downloads\sakefox_station.bbmodel'
TEX = r'D:\DoubaoWork\jiuhu_station_models\sakefox_station.png'
OUTDIR = r'D:\DoubaoWork\MaidRestaurantBusiness\art'
bb = json.load(open(BB, encoding='utf-8'))
teximg = Image.open(TEX).convert('RGBA')
TW, TH = bb['resolution']['width'], bb['resolution']['height']

def corners(f, t):
    fx,fy,fz=f; tx,ty,tz=t
    return np.array([[fx,fy,fz],[tx,fy,fz],[tx,ty,fz],[fx,ty,fz],
                     [fx,fy,tz],[tx,fy,tz],[tx,ty,tz],[fx,ty,tz]],float)

# face: normal, corner index winding, uRight, vDown (world space, outside view)
FACES = {
 'north': ((0,0,-1),[3,2,1,0],(-1,0,0),(0,-1,0)),
 'south': ((0,0, 1),[6,7,4,5],( 1,0,0),(0,-1,0)),
 'west':  ((-1,0,0),[3,7,4,0],(0,0, 1),(0,-1,0)),
 'east':  (( 1,0,0),[6,2,1,5],(0,0,-1),(0,-1,0)),
 'up':    ((0, 1,0),[3,2,6,7],(1,0,0),(0,0, 1)),
 'down':  ((0,-1,0),[4,5,1,0],(1,0,0),(0,0,-1)),
}
def rotmat(axis,deg):
    a=np.radians(deg);c,s=np.cos(a),np.sin(a)
    if axis=='x': return np.array([[1,0,0],[0,c,-s],[0,s,c]])
    if axis=='y': return np.array([[c,0,s],[0,1,0],[-s,0,c]])
    return np.array([[c,-s,0],[s,c,0],[0,0,1]])
def Rx(d):
    a=np.radians(d);c,s=np.cos(a),np.sin(a);return np.array([[1,0,0],[0,c,-s],[0,s,c]])
def Ry(d):
    a=np.radians(d);c,s=np.cos(a),np.sin(a);return np.array([[c,0,s],[0,1,0],[-s,0,c]])

topmap={}
def walk(nodes,top):
    for n in nodes:
        if isinstance(n,str): topmap[n]=top
        elif isinstance(n,dict):
            nm=n.get('name')
            walk(n.get('children',[]), top if top else nm)
walk(bb['outliner'],None)

def build_world(show_gap):
    world=[]
    for el in bb['elements']:
        grp=topmap.get(el['uuid'],'?')
        if grp in ('gap','dropbag','fall') and not (show_gap and grp=='gap'):
            continue
        C=corners(el['from'],el['to']); R=None
        r=el.get('rotation')
        if r and (r[0] or r[1] or r[2]):
            o=np.array(el['origin'])
            R=rotmat('z',r[2])@rotmat('y',r[1])@rotmat('x',r[0])
            C=(R@(C-o).T).T+o
        for key,face in el.get('faces',{}).items():
            if key not in FACES or not face.get('uv'): continue
            n,wind,uR,vd=FACES[key]
            n=np.array(n,float);uR=np.array(uR,float);vd=np.array(vd,float)
            if R is not None: n,uR,vd=R@n,R@uR,R@vd
            q=C[wind]
            world.append((q,n,uR,vd,face['uv'],key))
    return world

def find_coeffs(src,dst):
    # maps dst(screen)->src(texture); standard 8.5
    A=[]
    for (x,y),(u,v) in zip(dst,src):
        A.append([x,y,1,0,0,0,-u*x,-u*y]); A.append([0,0,0,x,y,1,-v*x,-v*y])
    A=np.array(A); b=np.array(src).reshape(8)
    return np.linalg.solve(A,b)

def render(V,name,show_gap):
    world=build_world(show_gap)
    items=[]
    for q,n,uR,vd,uv,key in world:
        nv=V@n
        if nv[2]>=-0.001: continue
        pv=(V@q.T).T
        items.append((pv.mean(0)[2],pv,q,uR,vd,uv,key,nv))
    items.sort(key=lambda z:z[0],reverse=True)
    allv=np.vstack([z[1] for z in items]);sx,sy=allv[:,0],-allv[:,1]
    SCALE=26
    W=int((sx.max()-sx.min())*SCALE)+60;H=int((sy.max()-sy.min())*SCALE)+60
    ox=30-sx.min()*SCALE;oy=30-sy.min()*SCALE
    canvas=Image.new('RGBA',(W,H),(41,44,52,255))
    for dep,pv,wq,uR,vd,uv,key,nv in items:
        u1,v1,u2,v2=uv
        du=wq@uR; dv=wq@vd
        # brightness from normal
        bright=np.clip(0.55+0.45*(-nv[2]),0.55,1.0)
        if key=='up': bright=1.0
        # crop texture (pad 0.5)
        pad=0.0
        crop=teximg.crop((max(0,u1-pad),max(0,v1-pad),min(TW,u2+pad),min(TH,v2+pad))).convert('RGBA')
        cw,ch=crop.size
        if cw<1 or ch<1: continue
        arr=np.array(crop).astype(float)
        arr[:,:,:3]*=bright
        crop=Image.fromarray(arr.astype(np.uint8))
        # screen quad
        spts=[(x*SCALE+ox,-y*SCALE+oy) for x,y in pv[:,:2]]
        # texture quad corners corresponding to same q ordering
        tpts=[]
        for x,y in zip(du,dv):
            uu=0 if x<=du.min()+1e-6 else cw
            vv=0 if y<=dv.min()+1e-6 else ch
            tpts.append((uu,vv))
        coeffs=find_coeffs(tpts,spts)
        warped=crop.transform((W,H),Image.PERSPECTIVE,coeffs,Image.BILINEAR)
        canvas=Image.alpha_composite(canvas,warped)
    out=os.path.join(OUTDIR,name);canvas.save(out)
    print('saved',out,canvas.size,'faces',len(items))

render(Rx(-12),'tex_idle_front.png',False)
render(Rx(-22)@Ry(-32),'tex_idle_iso.png',False)
render(Rx(-12),'tex_deliver_front.png',True)
render(Rx(-22)@Ry(-32),'tex_deliver_iso.png',True)

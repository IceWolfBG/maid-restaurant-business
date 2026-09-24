import json, math
import numpy as np

RES=r'D:\DoubaoWork\MaidRestaurantBusiness\forge-1.20.1\src\main\resources\assets\maid_restaurant_business'
def load(p): return json.load(open(p,encoding='utf-8'))
bags=[load(RES+rf'\models\block\anim\bag{i}.json') for i in range(1,6)]
drop=load(RES+r'\models\block\anim\dropbag.json')

def baked_box(els):
    # element coords /16 -> block space; return list of (min,max)
    out=[]
    for e in els:
        out.append((np.array(e['from'])/16, np.array(e['to'])/16))
    return out

def aabb(boxes, M):
    pts=[]
    for mn,mx in boxes:
        for x in (mn[0],mx[0]):
            for y in (mn[1],mx[1]):
                for z in (mn[2],mx[2]):
                    p=M@np.array([x,y,z,1.0]); pts.append(p[:3])
    pts=np.array(pts)
    return pts.min(0),pts.max(0),pts.mean(0)

def T(x,y,z):
    return np.array([[1,0,0,x],[0,1,0,y],[0,0,1,z],[0,0,0,1]])
def Ry(deg):
    r=math.radians(deg); c,s=math.cos(r),math.sin(r)
    return np.array([[c,0,s,0],[0,1,0,0],[-s,0,c,0],[0,0,0,1]])

# keyframes from JiuhuAnimData
ORIGIN_DROP=np.array([8,9.6,8])
POS=[(0,0,11.6,1.1),(0.9,0,11.6,1.1),(1.02,0,11.1,0.2),(1.2,0,11.6,0),(1.4,0,11.74,0),
     (1.58,0,11.52,0),(1.72,0,11.95,-0.3),(1.9,0,11.7,0.55),(2.12,0,11.6,1.1),(3.2,0,11.6,1.1)]
ROT=[(0,0),(1.72,0),(1.86,260),(2.04,520),(2.06,0),(3.2,0)]
SCL=[(0,0.01),(0.9,0.01),(1.08,1.15),(1.25,1),(1.7,0.95),(1.85,0.4),(2,0.05),(2.12,0.01),(3.2,0.01)]
def samp(kf,t):
    if t<=kf[0][0]: return kf[0][1]
    if t>=kf[-1][0]: return kf[-1][1]
    for a,b in zip(kf,kf[1:]):
        if a[0]<=t<=b[0]:
            f=(t-a[0])/(b[0]-a[0]); return a[1]+(b[1]-a[1])*f
    return kf[-1][1]

def applyBone_drop(t):
    o=ORIGIN_DROP/16
    p=np.array([0,samp(POS,t),0]); # will set components properly below
    # proper p sampling
    def psamp(comp):
        return samp([(r[0],r[1+comp]) for r in POS],t)
    p=np.array([psamp(0),psamp(1),psamp(2)])
    s=samp(SCL,t)
    rz=samp(ROT,t)
    A=(ORIGIN_DROP/16)+p/16
    M=T(*A)
    if rz: M=M@np.array([[math.cos(math.radians(rz)),-math.sin(math.radians(rz)),0,0],
                          [math.sin(math.radians(rz)),math.cos(math.radians(rz)),0,0],[0,0,1,0],[0,0,0,1]])
    M=M@np.diag([s,s,s,1])@T(*(-o))
    return M

def facing_wrapper(ydeg):
    return T(0.5,0,0.5)@Ry(-ydeg)@T(-0.5,0,-0.5)

dropboxes=baked_box(drop['elements'])
bagboxes=baked_box(bags[0]['elements'])

print('=== SHELF BAG slot1, NORTH, current (renderStatic -0.5) ===')
M=facing_wrapper(0)@T(-0.5,-0.5,-0.5)
mn,mx,c=aabb(bagboxes,M); print('center',np.round(c,3),'min',np.round(mn,3))
print('=== SHELF BAG slot1, NORTH, with +0.5 fix ===')
M=facing_wrapper(0)@T(0.5,0.5,0.5)@T(-0.5,-0.5,-0.5)
mn,mx,c=aabb(bagboxes,M); print('center',np.round(c,3),'min',np.round(mn,3))

print()
for t in [0.5,1.0,1.1,1.3,1.6,1.9]:
    Mcur=facing_wrapper(0)@applyBone_drop(t)@T(-0.5,-0.5,-0.5)
    Mfix=facing_wrapper(0)@applyBone_drop(t)@T(0.5,0.5,0.5)@T(-0.5,-0.5,-0.5)
    _,_,cc=aabb(dropboxes,Mcur); _,_,ff=aabb(dropboxes,Mfix)
    print(f't={t}  dropbag current center {np.round(cc,3)}   fixed center {np.round(ff,3)}')

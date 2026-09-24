import json, base64, io, os
from collections import Counter
import numpy as np
from PIL import Image, ImageDraw

BB = r'C:\Users\26529\Downloads\sakefox_station.bbmodel'
OUTDIR = r'C:\Users\26529\DoubaoWork\chats\2026-09-10\new-chat'
bb = json.load(open(BB, encoding='utf-8'))

def corners(f, t):
    fx, fy, fz = f; tx, ty, tz = t
    return np.array([
        [fx,fy,fz],[tx,fy,fz],[tx,ty,fz],[fx,ty,fz],
        [fx,fy,tz],[tx,fy,tz],[tx,ty,tz],[fx,ty,tz]], float)

FACES = {
    'north': ((0,0,-1), [3,2,1,0]),
    'south': ((0,0,1),  [6,7,4,5]),
    'west':  ((-1,0,0), [3,7,4,0]),
    'east':  ((1,0,0),  [6,2,1,5]),
    'up':    ((0,1,0),  [3,2,6,7]),
    'down':  ((0,-1,0), [4,5,1,0]),
}

def rotmat(axis, deg):
    a = np.radians(deg); c, s = np.cos(a), np.sin(a)
    if axis == 'x': return np.array([[1,0,0],[0,c,-s],[0,s,c]])
    if axis == 'y': return np.array([[c,0,s],[0,1,0],[-s,0,c]])
    return np.array([[c,-s,0],[s,c,0],[0,0,1]])

def Ry(d):
    a=np.radians(d); c,s=np.cos(a),np.sin(a)
    return np.array([[c,0,s],[0,1,0],[-s,0,c]])
def Rx(d):
    a=np.radians(d); c,s=np.cos(a),np.sin(a)
    return np.array([[1,0,0],[0,c,-s],[0,s,c]])

topmap = {}
def walk(nodes, top):
    for n in nodes:
        if isinstance(n, str):
            topmap[n] = top
        elif isinstance(n, dict):
            t = top if top else n['name']
            walk(n.get('children', []), t)
walk(bb['outliner'], None)
print('groups:', Counter([str(v) for v in topmap.values()]))

world = []
for el in bb['elements']:
    C = corners(el['from'], el['to'])
    R = None
    r = el.get('rotation')
    if r and (r[0] or r[1] or r[2]):
        o = np.array(el['origin'])
        R = rotmat('z', r[2]) @ rotmat('y', r[1]) @ rotmat('x', r[0])
        C = (R @ (C-o).T).T + o
    for key, face in el.get('faces', {}).items():
        if key not in FACES or not face.get('uv'):
            continue
        n, idx = FACES[key]; n = np.array(n, float)
        if R is not None: n = R @ n
        if key == 'up': bright = 1.0
        elif key in ('north', 'south'): bright = 0.82
        else: bright = 0.66
        world.append((C[idx], n, bright))

def render(V, name):
    faces = []
    for pts, n, bright in world:
        nv = V @ n
        if nv[2] >= -0.001:   # camera looks +z; visible faces point -z
            continue
        pv = (V @ pts.T).T
        faces.append((pv.mean(axis=0)[2], pv, bright))
    if not faces:
        print('no faces', name); return
    allv = np.vstack([f[1] for f in faces])
    sx, sy = allv[:,0], -allv[:,1]
    SCALE = 24
    W = int((sx.max()-sx.min())*SCALE)+40; H = int((sy.max()-sy.min())*SCALE)+40
    ox = 20-sx.min()*SCALE; oy = 20-sy.min()*SCALE
    img = Image.new('RGBA',(W,H),(150,140,126,255)); d = ImageDraw.Draw(img)
    base = (234,226,210)
    faces.sort(key=lambda f: f[0], reverse=True)  # far(large z) first
    for dep, pv, bright in faces:
        X = pv[:,0]*SCALE+ox; Y = -pv[:,1]*SCALE+oy
        pts = list(zip(X, Y))
        fill = tuple(int(c*bright) for c in base)+(255,)
        edge = tuple(int(c*bright*0.42) for c in base)+(255,)
        d.polygon(pts, fill=fill, outline=edge)
    out = os.path.join(OUTDIR, name); img.save(out)
    print('saved', out, img.size, 'faces', len(faces))

render(Rx(-15), 'jiuhu_all_front.png')
render(Rx(-25) @ Ry(-35), 'jiuhu_all_iso.png')

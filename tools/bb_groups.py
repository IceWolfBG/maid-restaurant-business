import json, io
from collections import defaultdict

BB = r"C:\Users\26529\Downloads\sakefox_station.bbmodel"
with io.open(BB, "r", encoding="utf-8") as f:
    m = json.load(f)

print("resolution", m.get("resolution"), "elements", len(m.get("elements", [])))

def elem_box(e):
    f_=e["from"]; t=e["to"]
    return (min(f_[0],t[0]),min(f_[1],t[1]),min(f_[2],t[2]),
            max(f_[0],t[0]),max(f_[1],t[1]),max(f_[2],t[2]))

def walk(node, path):
    out=[]
    if isinstance(node,list):
        for c in node: out+=walk(c,path)
    elif isinstance(node,dict):
        nm=node.get("name","?")
        for c in node.get("children",[]): out+=walk(c,path+[nm])
    elif isinstance(node,str):
        out.append((node,path))
    return out

assign=walk(m["outliner"],[])
gbox=defaultdict(lambda:[99,99,99,-99,-99,-99])
emap={e["uuid"]:e for e in m["elements"]}
for eid,path in assign:
    top=path[0] if path else "?"
    e=emap.get(eid)
    if not e: continue
    b=elem_box(e); g=gbox[top]
    for i in range(3): g[i]=min(g[i],b[i]); g[i+3]=max(g[i+3],b[i+3])

print("\n=== TOP-LEVEL GROUPS (16-space bounding boxes) ===")
for name,g in gbox.items():
    print(f"{name:26s} x[{g[0]:6.1f},{g[3]:6.1f}] y[{g[1]:6.1f},{g[4]:6.1f}] z[{g[2]:6.1f},{g[5]:6.1f}]")

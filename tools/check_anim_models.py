import json, io, os

ROOT = r"D:\DoubaoWork\MaidRestaurantBusiness\forge-1.20.1\src\main\resources\assets\maid_restaurant_business\models\block\anim"
for name in ["bag1","bag2","bag3","bag4","bag5"]:
    p = os.path.join(ROOT, name + ".json")
    m = json.load(io.open(p,"r",encoding="utf-8"))
    els = m.get("elements",[])
    xs=[];ys=[];zs=[]
    for e in els:
        f=e["from"];t=e["to"]
        xs += [f[0],t[0]]; ys += [f[1],t[1]]; zs += [f[2],t[2]]
    print("{:6s} n={:2d} x[{:.1f},{:.1f}] y[{:.1f},{:.1f}] z[{:.1f},{:.1f}]".format(
        name,len(els),min(xs),max(xs),min(ys),max(ys),min(zs),max(zs)))

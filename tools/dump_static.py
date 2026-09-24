import json, io
P = r"D:\DoubaoWork\MaidRestaurantBusiness\forge-1.20.1\src\main\resources\assets\maid_restaurant_business\models\block\jiuhu_station.json"
m = json.load(io.open(P,"r",encoding="utf-8"))
els = m["elements"]
print("elements", len(els))
allx=[99,-99];ally=[99,-99];allz=[99,-99]
for i,e in enumerate(els):
    f=e["from"];t=e["to"]
    x0,x1=sorted([f[0],t[0]]);y0,y1=sorted([f[1],t[1]]);z0,z1=sorted([f[2],t[2]])
    allx=[min(allx[0],x0),max(allx[1],x1)];ally=[min(ally[0],y0),max(ally[1],y1)];allz=[min(allz[0],z0),max(allz[1],z1)]
    print("#{:2d} {:14s} x[{:5.1f},{:5.1f}] y[{:5.1f},{:5.1f}] z[{:5.1f},{:5.1f}]".format(
        i, str(e.get("name","")), x0,x1,y0,y1,z0,z1))
print("OVERALL x",allx,"y",ally,"z",allz)

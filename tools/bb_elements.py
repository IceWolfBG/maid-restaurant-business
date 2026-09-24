import json, io
m = json.load(io.open(r"C:\Users\26529\Downloads\sakefox_station.bbmodel", "r", encoding="utf-8"))
for e in m["elements"]:
    f = e["from"]; t = e["to"]
    print("{:24s} x[{:5.1f},{:5.1f}] y[{:5.1f},{:5.1f}] z[{:5.1f},{:5.1f}]".format(
        str(e["name"]),
        min(f[0],t[0]),max(f[0],t[0]),
        min(f[1],t[1]),max(f[1],t[1]),
        min(f[2],t[2]),max(f[2],t[2])))

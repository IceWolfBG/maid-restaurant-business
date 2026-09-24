import re, base64, os

files = {
 "bbmodel": r"C:\Users\26529\Downloads\sakefox_station.bbmodel",
 "bbmodel.json": r"C:\Users\26529\Downloads\sakefox_station.bbmodel.json",
 "json": r"C:\Users\26529\Downloads\sakefox_station.json",
}
outdir = r"D:\DoubaoWork\MaidRestaurantBusiness\art\reextracted"
os.makedirs(outdir, exist_ok=True)
pat = re.compile(r'data:image/(\w+);base64,([A-Za-z0-9+/=]+)')
for tag, p in files.items():
    with open(p, "r", encoding="utf-8") as f:
        raw = f.read()
    hits = pat.findall(raw)
    print("="*70)
    print(tag, "| file size:", len(raw), "| data-uri count:", len(hits))
    for i,(fmt,b64) in enumerate(hits):
        try:
            data = base64.b64decode(b64)
        except Exception as e:
            data = b""; print("decode err", e)
        print(f"   uri#{i} fmt={fmt} b64len={len(b64)} decoded={len(data)}")
        if len(data) > 2000:
            outp = os.path.join(outdir, f"{tag}_uri{i}.{fmt}")
            with open(outp,"wb") as g: g.write(data)
            print("      SAVED ->", outp)

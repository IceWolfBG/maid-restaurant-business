import json, base64, os

p = r"C:\Users\26529\Downloads\sakefox_station.bbmodel"
with open(p, "r", encoding="utf-8") as f:
    data = json.load(f)

print("top-level keys:", list(data.keys()))
texs = data.get("textures", [])
print("texture count:", len(texs))
outdir = r"D:\DoubaoWork\MaidRestaurantBusiness\art\reextracted"
os.makedirs(outdir, exist_ok=True)
for t in texs:
    src = t.get("source", "")
    print("-"*60)
    print("id:", t.get("id"), "name:", t.get("name"), "uuid:", t.get("uuid"))
    print("source type:", "data-uri" if src.startswith("data:") else ("path/empty" if src else "EMPTY"),
          "| source length:", len(src))
    if src.startswith("data:"):
        header, b64 = src.split(",", 1)
        print("header:", header)
        raw = base64.b64decode(b64)
        print("decoded bytes:", len(raw))
        outp = os.path.join(outdir, f"tex_{t.get('id')}_{t.get('name')}")
        with open(outp, "wb") as g:
            g.write(raw)
        print("saved ->", outp)
    elif src:
        print("non-data source value (first 200):", src[:200])

# resolution metadata
print("-"*60)
print("resolution:", data.get("resolution"))

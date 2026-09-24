import os, re, base64

roots = [
 r"C:\Users\26529\AppData\Roaming\Blockbench\IndexedDB",
 r"C:\Users\26529\AppData\Roaming\Blockbench\Local Storage",
 r"C:\Users\26529\AppData\Roaming\Blockbench\Session Storage",
 r"C:\Users\26529\AppData\Roaming\Blockbench\blob_storage",
]
outdir = r"D:\DoubaoWork\MaidRestaurantBusiness\art\reextracted"
os.makedirs(outdir, exist_ok=True)
marker = b"data:image/png;base64,"
b64chars = set(b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=")
found = []
for root in roots:
    for dp,_,fns in os.walk(root):
        for fn in fns:
            p = os.path.join(dp,fn)
            raw = open(p,"rb").read()
            idx = 0
            while True:
                j = raw.find(marker, idx)
                if j < 0: break
                k = j+len(marker)
                while k < len(raw) and raw[k] in b64chars: k += 1
                b64 = raw[j+len(marker):k]
                found.append((p,len(b64)))
                if len(b64) > 2500:
                    try:
                        data = base64.b64decode(b64)
                        outp = os.path.join(outdir, f"storage_{len(data)}_{fn}.png")
                        if data[:8] == b"\x89PNG\r\n\x1a\n":
                            open(outp,"wb").write(data)
                            print("SAVED", outp)
                    except Exception as e:
                        print("decode err", e)
                idx = k
print("total data-uri markers found:", len(found))
for p,l in sorted(set(found), key=lambda x:-x[1])[:20]:
    print(f"b64len={l}  {p}")

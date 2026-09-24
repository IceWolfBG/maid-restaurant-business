from PIL import Image
import numpy as np

p = r"D:\DoubaoWork\jiuhu_station_models\sakefox_station.png"
im = Image.open(p).convert("RGBA")
print("size:", im.size)
a = np.array(im)
# coverage per row (non-transparent, non-near-empty)
alpha = a[:,:,3]
for y in range(0, 45):
    row = alpha[y]
    nz = np.count_nonzero(row)
    if nz: print(f"y={y:2d} opaque_px={nz}")

# crop top 42 rows, scale 4x with nearest
crop = im.crop((0,0,128,42))
crop = crop.resize((128*4,42*4), Image.NEAREST)
out = r"D:\Users_placeholder"
outp = r"D:\DoubaoWork\MaidRestaurantBusiness\art\sheet_top_zoom.png"
crop.save(outp)
print("saved", outp)

# count distinct painted pixels in expected regions vs placeholder
print("total opaque:", np.count_nonzero(alpha))

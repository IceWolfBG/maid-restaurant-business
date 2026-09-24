import numpy as np
from PIL import Image
p=r'D:\DoubaoWork\MaidRestaurantBusiness\forge-1.20.1\src\main\resources\assets\maid_restaurant_business\textures\gui\jiuhu_station_gui.png'
im=Image.open(p).convert('RGBA');a=np.array(im)
print('size',im.size)
# find very dark pixels (recess interiors)
lum=a[:,:,:3].mean(2)
dark=(a[:,:,3]>200)&(lum<70)
ys,xs=np.where(dark)
print('dark bbox x',xs.min(),xs.max(),'y',ys.min(),ys.max())
# column profile of dark, segment into recesses by gaps
colsum=dark.sum(0)
active=colsum> (dark.sum(1).max()*0.4)
segs=[];i=0
while i<len(active):
    if active[i]:
        j=i
        while j<len(active) and active[j]:j+=1
        segs.append((i,j-1));i=j
    else:i+=1
print('recess column segments:',segs)
# row bands
rowsum=dark.sum(1)
print('dark rows range',ys.min(),ys.max())
# overall panel: non-transparent bbox
op=a[:,:,3]>10
oy,ox=np.where(op)
print('panel bbox x',ox.min(),ox.max(),'y',oy.min(),oy.max())

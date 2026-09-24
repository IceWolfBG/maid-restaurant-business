import numpy as np
from PIL import Image
p=r'D:\DoubaoWork\MaidRestaurantBusiness\forge-1.20.1\src\main\resources\assets\maid_restaurant_business\textures\gui\jiuhu_station_gui.png'
a=np.array(Image.open(p).convert('RGBA'))
lum=a[:,:,:3].mean(2)
# recess interiors: near black, within content band y 18..40
band=lum[18:42,:]
dark=band<45
colsum=dark.sum(0)
thr=colsum.max()*0.6
active=colsum>thr
segs=[];i=0
while i<len(active):
    if active[i]:
        j=i
        while j<len(active) and active[j]:j+=1
        segs.append((i,j-1));i=j
    else:i+=1
print('recess interior x-segments (orig px):',segs)
# small bar below each (the progress slot drawn in texture): y 43..50
band2=lum[43:52,:];d2=band2<60;cs2=d2.sum(0);ac2=cs2>cs2.max()*0.6
segs2=[];i=0
while i<len(ac2):
    if ac2[i]:
        j=i
        while j<len(ac2) and ac2[j]:j+=1
        segs2.append((i,j-1));i=j
    else:i+=1
print('small-bar x-segments:',segs2)
print('code slot x (18 wide):',[(27+i*26,27+i*26+17) for i in range(5)])

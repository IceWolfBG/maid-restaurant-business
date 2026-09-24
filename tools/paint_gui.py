from PIL import Image
import os

W,H=176,100
img=Image.new('RGBA',(W,H),(0,0,0,0))
px=img.load()

# palette
D1=(66,46,28,255)      # dark wood
DL=(112,82,54,255)     # wood bevel light
DD=(38,26,15,255)      # wood bevel dark
P=(196,160,108,255)    # tan panel
PL=(228,198,148,255)
PD=(148,116,72,255)
S=(28,19,12,255)       # pocket inner
SH=(17,11,7,255)       # pocket depth
SL=(124,96,62,255)     # pocket rim
TRACK=(46,32,20,255)
G=(206,162,74,255); GL=(244,210,124,255); GD=(148,106,38,255)
RED=(150,52,48,255)

def rect(x0,y0,x1,y1,c):
    for y in range(y0,y1+1):
        for x in range(x0,x1+1):
            px[x,y]=c
def frame_bevel(x0,y0,x1,y1,base,light,dark,th=1):
    rect(x0,y0,x1,y1,base)
    for t in range(th):
        for x in range(x0+t,x1-t+1):
            px[x,y0+t]=light; px[x,y1-t]=dark
        for y in range(y0+t,y1-t+1):
            px[x0+t,y]=light; px[x1-t,y]=dark

# outer frame
frame_bevel(0,0,W-1,H-1,D1,DL,DD,th=2)
rect(2,2,W-3,H-3,D1)

# faint vertical planks in lower band
for x in range(8,W-8,16):
    for y in range(60,95):
        px[x,y]=DL if x%32==8 else DD

# top inset tan panel (beveled, inset look: light top/left, dark bottom/right)
frame_bevel(6,8,169,54,P,PL,PD,th=1)
rect(7,9,168,53,P)

def cubby(x0,y0):
    # rim
    rect(x0,y0,x0+17,y0+17,SL)
    # inner 16x16
    rect(x0+1,y0+1,x0+16,y0+16,S)
    # depth: top two rows + left two cols darker
    for x in range(x0+1,x0+17):
        px[x,y0+1]=SH; px[x,y0+2]=SH
    for y in range(y0+1,y0+17):
        px[x0+1,y]=SH; px[x0+2,y]=SH
    # subtle lighter back-wall bottom-right 1px (floor catching light)
    for x in range(x0+2,x0+17):
        px[x,y0+16]=(40,28,17,255)
    for y in range(y0+2,y0+17):
        px[x0+16,y]=(40,28,17,255)

for i in range(5):
    cubby(19+i*30,18)

# progress-bar grooves under cubbies (fixed at x20+i*30)
for i in range(5):
    x0=20+i*30
    rect(x0,40,x0+15,42,TRACK)
    for x in range(x0,x0+16): px[x,40]=(30,21,13,255)
    for x in range(x0,x0+16): px[x,42]=(74,53,33,255)
    for y in range(40,43): px[x0,y]=(30,21,13,255); px[x0+15,y]=(30,21,13,255)

# gold divider strip with red end caps
rect(8,56,167,57,G)
for x in range(8,168): px[x,56]=GL; px[x,57]=GD
rect(8,56,11,57,RED); rect(164,56,167,57,RED)

def chevron(cx,cy,color):
    # two nested '>' shapes, 7 tall
    for k in range(7):
        dx=k if k<4 else 6-k
        px[cx+dx,cy+k]=color
        px[cx+dx+4,cy+k]=color
chevron(9,72,G)
# bevel the chevron slightly
for k in range(7):
    dx=k if k<4 else 6-k
    px[cx if False else 9+dx,72+k]=GL if k==0 else G

def coin(cx,cy):
    # 9x8 coin
    rect(cx+1,cy,cx+7,cy+7,G)
    rect(cx+2,cy+1,cx+6,cy+6,GL)
    rect(cx+3,cy+2,cx+5,cy+5,G)
    px[cx+4,cy+3]=GD; px[cx+4,cy+4]=GD
    px[cx+1,cy]=GD; px[cx+1,cy+7]=GD
coin(121,72)

out_forge=r'D:\DoubaoWork\MaidRestaurantBusiness\forge-1.20.1\src\main\resources\assets\maid_restaurant_business\textures\gui\jiuhu_station_gui.png'
out_neo=r'D:\DoubaoWork\MaidRestaurantBusiness\neoforge-1.21.1\src\main\resources\assets\maid_restaurant_business\textures\gui\jiuhu_station_gui.png'
# backup old
for p in (out_forge,out_neo):
    b=p+'.bak'
    if not os.path.exists(b): Image.open(p).save(b,format='PNG')
img.save(out_forge); img.save(out_neo)
print('saved', os.path.getsize(out_forge), os.path.getsize(out_neo))

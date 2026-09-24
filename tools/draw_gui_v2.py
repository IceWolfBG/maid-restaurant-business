# Draw the redesigned Jiuhu Station GUI background (176x100) and a composite mock-up.
from PIL import Image, ImageDraw
import os

W, H = 176, 100
OUT = r"D:\DoubaoWork\MaidRestaurantBusiness\art"
os.makedirs(OUT, exist_ok=True)

# palette
BORDER   = (58, 42, 28)
TITLE_BG = (74, 53, 32)
TITLE_HI = (92, 64, 40)
PANEL_BG = (201, 168, 118)
PANEL_HI = (214, 184, 138)
PANEL_LO = (176, 142, 92)
PANEL_BD = (107, 74, 46)
RECESS_BD= (26, 18, 10)
RECESS   = (31, 21, 13)
RECESS_HI= (48, 34, 22)
TRACK    = (58, 42, 28)
BAND_BG  = (74, 53, 32)
BAND_HI  = (92, 64, 40)
GOLD     = (224, 168, 60)
GOLD_HI  = (240, 200, 104)
COIN     = (224, 168, 60)
COIN_HI  = (244, 214, 120)
COIN_LO  = (150, 104, 24)

# layout
SLOT_X0, PITCH, SLOT = 19, 30, 18
SLOT_Y = 18
BAR_Y, BAR_H = 40, 3
TXT_Y = 47
PANEL_X0, PANEL_X1 = 7, 169
PANEL_Y0, PANEL_Y1 = 15, 62

img = Image.new("RGBA", (W, H), BORDER)
d = ImageDraw.Draw(img)

def rect(x0, y0, x1, y1, c):
    d.rectangle([x0, y0, x1, y1], fill=c)

# title band
rect(1, 1, W-2, 13, TITLE_BG)
rect(1, 1, W-2, 2, TITLE_HI)
rect(1, 12, W-2, 13, (40, 28, 17))

# content panel
rect(PANEL_X0, PANEL_Y0, PANEL_X1, PANEL_Y1, PANEL_BD)
rect(PANEL_X0+1, PANEL_Y0+1, PANEL_X1-1, PANEL_Y1-1, PANEL_BG)
rect(PANEL_X0+1, PANEL_Y0+1, PANEL_X1-1, PANEL_Y0+2, PANEL_HI)
rect(PANEL_X0+1, PANEL_Y1-2, PANEL_X1-1, PANEL_Y1-1, PANEL_LO)

# per-column recess + bar track
for i in range(5):
    x = SLOT_X0 + i*PITCH
    # recess (slot) with bevel
    rect(x, SLOT_Y, x+SLOT-1, SLOT_Y+SLOT-1, RECESS_BD)
    rect(x+1, SLOT_Y+1, x+SLOT-2, SLOT_Y+SLOT-2, RECESS)
    rect(x+1, SLOT_Y+1, x+SLOT-2, SLOT_Y+2, RECESS_HI)   # faint top sheen
    rect(x+1, SLOT_Y+SLOT-2, x+SLOT-2, SLOT_Y+SLOT-2, (18,12,7))
    # progress track (16 wide, centered)
    bx = x+1
    rect(bx, BAR_Y, bx+15, BAR_Y+BAR_H-1, TRACK)
    rect(bx, BAR_Y, bx+15, BAR_Y, (40,28,18))
    rect(bx, BAR_Y+BAR_H-1, bx+15, BAR_Y+BAR_H-1, (30,20,12))

# divider
rect(1, 64, W-2, 64, (40, 28, 17))
rect(1, 63, W-2, 63, (92, 64, 40))

# bottom band
rect(1, 66, W-2, H-2, BAND_BG)
rect(1, 66, W-2, 67, BAND_HI)
rect(1, H-3, W-2, H-2, (40, 28, 17))

# small coin icon (left of earnings area is per-column; here draw station coin icons for band)
def coin_icon(cx, cy, r=3):
    d.ellipse([cx-r, cy-r, cx+r, cy+r], fill=COIN, outline=COIN_LO)
    d.ellipse([cx-r+1, cy-r+1, cx+r-1, cy+r-1], outline=COIN_HI)

# speed icon (simple chevrons) at left band
sy = 73
for k in range(2):
    ax = 12 + k*5
    d.line([ax, sy, ax+4, sy+4], fill=GOLD_HI, width=1)
    d.line([ax+4, sy+4, ax, sy+8], fill=GOLD_HI, width=1)
# fee coin icon right band
coin_icon(131, sy+4, 3)

img.save(os.path.join(OUT, "jiuhu_station_gui_v2.png"))

# ---------- composite mock-up: emulate code-drawn fills + text + sample bags ----------
mock = img.copy()
md = ImageDraw.Draw(mock)
try:
    from PIL import ImageFont
    font = ImageFont.load_default()
except Exception:
    font = None

# pretend slots 0,1,4 have bags with progress 0.35 / 0.8 / 1.0 ; slots 2,3 empty
states = {0:(0.35,18), 1:(0.8,18), 4:(1.0,18)}
# bag visual: paste a simple paper-bag swatch into the recess (cream with red seal)
BAG = (236, 230, 214)
BAG_HI = (245, 241, 230)
BAG_LO = (206, 198, 180)
SEAL = (150, 40, 40)
for i,(p,profit) in states.items():
    x = SLOT_X0 + i*PITCH
    # bag body inside recess
    md.rectangle([x+3, SLOT_Y+4, x+SLOT-4, SLOT_Y+SLOT-3], fill=BAG)
    md.line([x+3, SLOT_Y+4, x+SLOT-4, SLOT_Y+4], fill=BAG_HI)
    md.line([x+3, SLOT_Y+SLOT-3, x+SLOT-4, SLOT_Y+SLOT-3], fill=BAG_LO)
    md.line([x+3, SLOT_Y+4, x+3, SLOT_Y+SLOT-3], fill=BAG_HI)
    # handle
    md.line([x+6, SLOT_Y+2, x+6, SLOT_Y+5], fill=(180,170,150))
    md.line([x+SLOT-7, SLOT_Y+2, x+SLOT-7, SLOT_Y+5], fill=(180,170,150))
    md.line([x+6, SLOT_Y+2, x+SLOT-7, SLOT_Y+2], fill=(180,170,150))
    # red seal
    md.rectangle([x+7, SLOT_Y+7, x+SLOT-8, SLOT_Y+10], fill=SEAL)
    # progress fill
    bx = x+1
    fw = int(round(p*16))
    if fw>0:
        md.rectangle([bx, BAR_Y, bx+fw-1, BAR_Y+BAR_H-1], fill=GOLD)
        md.line([bx, BAR_Y, bx+fw-1, BAR_Y], fill=GOLD_HI)
    # earnings text centered
    t = "+%d" % profit
    tw = md.textlength(t, font=font)
    md.text((x+ (SLOT-tw)/2, TXT_Y), t, fill=(154,104,24), font=font)

# band text
md.text((24, 71), "2格/秒", fill=(240,224,184), font=font)
md.text((138, 71), "40%", fill=(240,224,184), font=font)
cnt = len(states)
t = "配送中 %d/5" % cnt
tw = md.textlength(t, font=font)
md.text(((W-tw)/2, 85), t, fill=(224,200,150), font=font)

# title
t = "酒狐速递站"
tw = md.textlength(t, font=font)
md.text(((W-tw)/2, 4), t, fill=(240,224,184), font=font)

mock.save(os.path.join(OUT, "jiuhu_station_gui_v2_mock.png"))
print("done")

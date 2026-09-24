from PIL import Image
import os

W, H = 176, 90
img = Image.new("RGB", (W, H), (0, 0, 0))
d = ImageDraw = None
from PIL import ImageDraw
dr = ImageDraw.Draw(img)

def r(x1, y1, x2, y2, c):
    dr.rectangle([x1, y1, x2, y2], fill=c)

# palette
FRAME   = (107, 74, 50)    # #6B4A32 outer frame
FRAME_D = (90, 62, 43)     # #5A3E2B
PANEL   = (232, 213, 168)  # #E8D5A8
PANEL_H = (240, 224, 184)  # #F0E0B8
PANEL_S = (201, 168, 120)  # #C9A878
SUB     = (227, 206, 158)  # #E3CE9E
TITLE   = (122, 82, 48)    # #7A5230
SLOT_D  = (74, 50, 32)     # #4A3220 recessed dark edge
SLOT_L  = (138, 106, 66)   # #8A6A42 recessed light edge
SLOT_IN = (46, 32, 20)     # #2E2014 empty slot interior
BAR_IN  = (46, 32, 20)

# outer frame + inner panel
r(0, 0, W-1, H-1, FRAME)
r(3, 3, W-4, H-4, PANEL)
r(4, 4, W-5, 4, PANEL_H)
r(4, H-5, W-5, H-5, PANEL_S)

# title bar
r(3, 3, W-4, 15, TITLE)
r(3, 15, W-4, 16, FRAME_D)
# title bar subtle top highlight
r(4, 4, W-5, 4, (140, 98, 60))

# slot sub-panel
r(6, 18, W-7, 60, PANEL_S)
r(7, 19, W-8, 59, SUB)
r(7, 19, W-8, 19, (238, 220, 178))
r(7, 59, W-8, 59, (210, 186, 138))

# five slots: menu slot x = 27 + i*26, y = 22 (16x16 item), recess 18x18
for i in range(5):
    x = 27 + i*26
    # recess 18x18 at (x-1,21)-(x+16,38)
    r(x-1, 21, x+16, 38, FRAME_D)
    r(x, 22, x+15, 37, SLOT_IN)
    # top/left dark, bottom/right light
    r(x-1, 21, x+16, 21, SLOT_D)
    r(x-1, 21, x-1, 38, SLOT_D)
    r(x-1, 38, x+16, 38, SLOT_L)
    r(x+16, 21, x+16, 38, SLOT_L)
    # progress bar slot 18x5 at (x-1,41)-(x+16,45)
    r(x-1, 41, x+16, 45, FRAME_D)
    r(x, 42, x+15, 44, BAR_IN)
    r(x-1, 41, x+16, 41, SLOT_D)
    r(x-1, 45, x+16, 45, SLOT_L)

# divider
r(6, 62, W-7, 63, PANEL_S)
r(6, 62, W-7, 62, (184, 152, 104))
r(6, 63, W-7, 63, PANEL_H)

# coin icon bottom-left (8,68)-(15,75)
r(8, 68, 15, 75, (150, 110, 20))
r(9, 69, 14, 74, (224, 184, 40))
r(10, 70, 12, 72, (248, 222, 110))
r(9, 69, 9, 74, (240, 200, 60))
r(14, 69, 14, 74, (170, 130, 30))

out_rel = r"src\main\resources\assets\maid_restaurant_business\textures\gui\jiuhu_station_gui.png"
targets = [
    r"D:\DoubaoWork\MaidRestaurantBusiness\forge-1.20.1",
    r"D:\DoubaoWork\MaidRestaurantBusiness\neoforge-1.21.1",
]
for t in targets:
    p = os.path.join(t, out_rel)
    img.save(p)
    print("saved", p)

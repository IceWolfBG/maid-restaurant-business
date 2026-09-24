from PIL import Image, ImageDraw, ImageFont
import os

GUI = r'D:\DoubaoWork\MaidRestaurantBusiness\neoforge-1.21.1\src\main\resources\assets\maid_restaurant_business\textures\gui\jiuhu_station_gui.png'
OUTDIR = r'C:\Users\26529\DoubaoWork\chats\2026-09-10\new-chat'
bg = Image.open(GUI).convert('RGBA')
font = ImageFont.load_default()

X0, Y, PITCH = 27, 22, 26
BAR_Y, TEXT_Y = 42, 48


def bag_icon():
    ic = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(ic)
    d.rectangle([3, 6, 12, 14], fill=(202, 162, 98, 255))      # bag body
    d.rectangle([3, 6, 12, 7], fill=(168, 128, 74, 255))       # top rim
    d.rectangle([3, 13, 12, 14], fill=(150, 112, 64, 255))     # bottom shade
    d.rectangle([4, 6, 5, 13], fill=(226, 190, 128, 255))      # left highlight
    d.arc([5, 2, 10, 8], 180, 360, fill=(110, 82, 46, 255))    # handle
    d.arc([5, 3, 10, 9], 180, 360, fill=(150, 112, 64, 255))
    return ic


BAG = bag_icon()


def render(states, name):
    img = bg.copy()
    d = ImageDraw.Draw(img)
    delivering = 0
    for i, st in enumerate(states):
        x = X0 + i * PITCH
        if st is None:
            continue
        delivering += 1
        prog, rem = st
        img.alpha_composite(BAG, (x, Y))
        d.rectangle([x, BAR_Y, x + 16, BAR_Y + 3], fill=(46, 32, 20, 255))
        fw = int(prog * 16)
        if fw > 0:
            d.rectangle([x, BAR_Y, x + fw, BAR_Y + 3], fill=(220, 156, 60, 255))
            d.line([x, BAR_Y, x + fw, BAR_Y], fill=(232, 176, 96, 255))
        txt = f'{rem}s'
        tw = int(d.textlength(txt, font=font))
        d.text((x + (16 - tw) // 2, TEXT_Y), txt, font=font, fill=(74, 50, 30, 255))
    d.text((20, 69), f'配送中 {delivering}/5', font=font, fill=(74, 50, 30, 255))
    img = img.resize((176 * 3, 90 * 3), Image.NEAREST)
    out = os.path.join(OUTDIR, name)
    img.save(out)
    print('saved', out)


# 场景一：全部空闲
render([None, None, None, None, None], 'jiuhu_gui_empty.png')
# 场景二：3 个配送中（不同进度），2 个空
render([(0.30, 28), (0.70, 12), None, (0.5, 20), None], 'jiuhu_gui_mixed.png')
# 场景三：5 个全在配送
render([(0.15, 34), (0.4, 24), (0.55, 18), (0.8, 8), (0.95, 2)], 'jiuhu_gui_full.png')

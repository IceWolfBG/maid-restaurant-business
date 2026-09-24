import json, os
from PIL import Image, ImageDraw

BB = r'C:\Users\26529\Downloads\sakefox_station.bbmodel'
OUT = r'C:\Users\26529\DoubaoWork\chats\2026-09-10\new-chat\jiuhu_uvmap.png'
bb = json.load(open(BB, encoding='utf-8'))

topmap = {}
def walk(nodes, top):
    for n in nodes:
        if isinstance(n, str): topmap[n] = top
        elif isinstance(n, dict):
            t = top if top else n['name']
            walk(n.get('children', []), t)
walk(bb['outliner'], None)

def gcolor(tg, key):
    if tg and tg.startswith('bag'): return (210,170,105,255)   # paper bags
    if tg == 'roof': return (120,90,160,255)                   # roof
    if tg == 'gap': return (200,70,70,255)                     # gap
    if tg == 'dropbag': return (230,150,60,255)
    if tg == 'fall': return (90,160,120,255)
    return (90,130,190,255)                                    # body/shelf/dec

img = Image.new('RGBA', (128,128), (28,28,30,255))
d = ImageDraw.Draw(img)
for el in bb['elements']:
    tg = topmap.get(el.get('uuid'))
    for key, face in el.get('faces', {}).items():
        uv = face.get('uv')
        if not uv: continue
        u1,v1,u2,v2 = uv
        d.rectangle([u1,v1,u2-0.5,v2-0.5], fill=gcolor(tg,key), outline=(255,255,255,200))

img = img.resize((128*4,128*4), Image.NEAREST)
img.save(OUT)
print('saved', OUT)

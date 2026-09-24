import json, base64, io, os
from PIL import Image

BB = r'C:\Users\26529\Downloads\sakefox_station.bbmodel'
OUT = r'D:\DoubaoWork\MaidRestaurantBusiness\art\bb_textures'
os.makedirs(OUT, exist_ok=True)
bb = json.load(open(BB, encoding='utf-8'))
print('resolution', bb.get('resolution'))
print('elements', len(bb['elements']))
print('outliner top', [o.get('name') if isinstance(o, dict) else o for o in bb['outliner']])
print('textures', len(bb['textures']))
for i, t in enumerate(bb['textures']):
    src = t.get('source', '') or ''
    print(i, 'name=', t.get('name'), 'id=', t.get('id'), 'sourcelen=', len(src), 'mode=', t.get('mode'))
    if src.startswith('data:'):
        raw = base64.b64decode(src.split(',', 1)[1])
        im = Image.open(io.BytesIO(raw))
        safe = ''.join(c for c in str(t.get('name')) if c.isalnum()) or 'x'
        fn = os.path.join(OUT, f'tex_{i}_{safe}.png')
        im.save(fn)
        print('    decoded', im.size, im.mode, len(raw), 'bytes ->', fn)

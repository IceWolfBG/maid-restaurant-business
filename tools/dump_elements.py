import json
BB = r'C:\Users\26529\Downloads\sakefox_station.bbmodel'
OUT = r'D:\DoubaoWork\MaidRestaurantBusiness\art\bb_dump.txt'
bb = json.load(open(BB, encoding='utf-8'))
topmap = {}
def walk(nodes, top):
    for n in nodes:
        if isinstance(n, str): topmap[n] = top
        elif isinstance(n, dict):
            t = top if top else n['name']
            walk(n.get('children', []), t)
walk(bb['outliner'], None)
lines = []
for el in bb['elements']:
    tg = topmap.get(el.get('uuid'))
    lines.append(f"[{tg}] {el.get('name')}  from={[round(x,2) for x in el['from']]} to={[round(x,2) for x in el['to']]} rot={el.get('rotation')}")
    for k, f in el.get('faces', {}).items():
        if f.get('uv'):
            lines.append(f"      {k}: uv={f.get('uv')} tex={f.get('texture')} rot={f.get('rotation')}")
open(OUT, 'w', encoding='utf-8').write('\n'.join(lines))
print('wrote', OUT, len(lines), 'lines')

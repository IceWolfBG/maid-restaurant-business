import json
BB=r'C:\Users\26529\Downloads\sakefox_station.bbmodel'
bb=json.load(open(BB,encoding='utf-8'))
elmap={e['uuid']:e for e in bb['elements']}
def walk(nodes,depth=0):
    for n in nodes:
        if isinstance(n,dict):
            kids=n.get('children',[])
            leaves=[k for k in kids if isinstance(k,str)]
            print('  '*depth+f"[GROUP] {n.get('name')!r} origin={n.get('origin')} children={len(kids)} leaves={len(leaves)}")
            for k in leaves:
                e=elmap.get(k)
                if e: print('  '*depth+'     -',e['name'],'from',[round(x,2) for x in e['from']],'to',[round(x,2) for x in e['to']])
            walk(kids,depth+1)
walk(bb['outliner'])

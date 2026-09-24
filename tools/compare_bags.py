import json
BB = r'C:\Users\26529\Downloads\sakefox_station.bbmodel'
bb = json.load(open(BB,encoding='utf-8'))

# map uuid->element
elmap = {e['uuid']:e for e in bb['elements']}
def group_find(name):
    res=[]
    def walk(nodes):
        for n in nodes:
            if isinstance(n,dict):
                if n.get('name')==name:
                    for c in n.get('children',[]):
                        if isinstance(c,str): res.append(elmap[c])
                walk(n.get('children',[]))
    walk(bb['outliner']); return res

for g in ['bag1','bag2','bag3','bag4','bag5','dropbag']:
    els=group_find(g)
    print('=== bbmodel group',g,'===')
    for e in els:
        print('   ',e['name'],'from',[round(x,2) for x in e['from']],'to',[round(x,2) for x in e['to']])

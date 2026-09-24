import json, io
bb=r'C:\Users\26529\Downloads\sakefox_station.bbmodel'
m=json.load(io.open(bb,'r',encoding='utf-8'))
for e in m['elements']:
    f=e['from']; t=e['to']
    print(e.get('name',''),'from',[round(v,2) for v in f],'to',[round(v,2) for v in t])

import json
p=r'D:\DoubaoWork\MaidRestaurantBusiness\forge-1.20.1\src\main\resources\assets\maid_restaurant_business\models\block\jiuhu_station.json'
m=json.load(open(p,encoding='utf-8'))
els=m['elements']
print('total elements',len(els))
for i,e in enumerate(els):
    f=e['from']; t=e['to']
    if min(f[2],t[2]) <= 3 and max(f[1],t[1])>=7 and min(f[1],t[1])<=14:
        print(i,'from',[round(v,2) for v in f],'to',[round(v,2) for v in t])

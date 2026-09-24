import json
p=r'D:\DoubaoWork\MaidRestaurantBusiness\forge-1.20.1\src\main\resources\assets\maid_restaurant_business\models\block\jiuhu_station.json'
m=json.load(open(p,encoding='utf-8'))
for i,e in enumerate(m['elements']):
    f=e['from']; t=e['to']
    print(i,'from',[round(v,2) for v in f],'to',[round(v,2) for v in t])

from PIL import Image
src=r'C:\Users\26529\AppData\Local\DoubaoWork\User Data\ClipboardTemp\dc707b8a-5164-4562-a3c1-beb5a0727ac3.png'
im=Image.open(src)
print(im.size)
crop=im.crop((400,350,720,600)).resize((640,500),Image.NEAREST)
crop.save(r'D:\DoubaoWork\MaidRestaurantBusiness\art\roof_bag_zoom.png')
print('saved')

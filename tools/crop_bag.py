from PIL import Image
src=r'C:\Users\26529\AppData\Local\DoubaoWork\User Data\ClipboardTemp\b334ec4b-715c-4e64-ae67-638994cd6f9a.png'
im=Image.open(src)
print(im.size)
# crop lower-front region
crop=im.crop((80,300,520,560)).resize((880,520),Image.NEAREST)
crop.save(r'D:\DoubaoWork\MaidRestaurantBusiness\art\bag_misalign_zoom.png')
print('saved')

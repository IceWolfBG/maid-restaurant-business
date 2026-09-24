from PIL import Image
src=r'C:\Users\26529\AppData\Local\DoubaoWork\User Data\ClipboardTemp\26e8ac3f-a3bd-4c0e-b568-841a5333b319.png'
im=Image.open(src)
print(im.size)
crop=im.crop((220,180,420,420)).resize((600,720),Image.NEAREST)
crop.save(r'D:\DoubaoWork\MaidRestaurantBusiness\art\bag3_misalign_zoom.png')
print('saved')

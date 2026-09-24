import json, io, os

FILES = [
 r"D:\DoubaoWork\MaidRestaurantBusiness\forge-1.20.1\src\main\resources\assets\maid_restaurant_business\models\block\jiuhu_station.json",
 r"D:\DoubaoWork\MaidRestaurantBusiness\neoforge-1.21.1\src\main\resources\assets\maid_restaurant_business\models\block\jiuhu_station.json",
]

# Vanilla standard block display transforms (identical to the defaults Minecraft
# applies to ordinary cube blocks for GUI/GROUND/FIXED, plus explicit first/third
# person so the held block is normal-sized instead of identity (= huge in face)).
DISPLAY = {
  "thirdperson_righthand": {"rotation":[75,45,0],"translation":[0,2.5,0],"scale":[0.375,0.375,0.375]},
  "thirdperson_lefthand":  {"rotation":[75,45,0],"translation":[0,2.5,0],"scale":[0.375,0.375,0.375]},
  "firstperson_righthand": {"rotation":[0,45,0],"translation":[0,0,0],"scale":[0.4,0.4,0.4]},
  "firstperson_lefthand":  {"rotation":[0,225,0],"translation":[0,0,0],"scale":[0.4,0.4,0.4]},
  "gui":    {"rotation":[30,45,0],"translation":[0,-1,0],"scale":[0.625,0.625,0.625]},
  "ground": {"rotation":[0,0,0],"translation":[0,3,0],"scale":[0.25,0.25,0.25]},
  "fixed":  {"rotation":[0,0,0],"translation":[0,0,0],"scale":[0.5,0.5,0.5]},
}

for f in FILES:
    with io.open(f, "r", encoding="utf-8") as fh:
        m = json.load(fh)
    m["display"] = DISPLAY
    with io.open(f, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(m, fh, indent=2, ensure_ascii=False)
    print("updated", f, os.path.getsize(f))

# -*- coding: utf-8 -*-
"""Convert sakefox_station.bbmodel -> in-mod assets (both Forge 1.20.1 & NeoForge 1.21.1).
Generates:
  - static block model jiuhu_station.json (body/shelf/roof/dec)
  - per-dynamic-group block models under models/block/anim/
  - texture sakefox_station.png (decoded embedded)
  - internal item render_proxy with custom_model_data overrides
  - JiuhuAnimData.java (deliver keyframes; lid rotation flipped so lids open outward)
"""
import json, os, base64
from collections import defaultdict

BB = r'C:\Users\26529\Downloads\sakefox_station.bbmodel'
ROOT = r'D:\DoubaoWork\MaidRestaurantBusiness'
LANES = [os.path.join(ROOT, 'forge-1.20.1'), os.path.join(ROOT, 'neoforge-1.21.1')]
RES_REL = os.path.join('src', 'main', 'resources')
JAVA_REL = os.path.join('src', 'main', 'java')
PKG_PATH = os.path.join('com', 'icewolf', 'maidrestaurant', 'business', 'client', 'render')
NS = 'maid_restaurant_business'
TEX = NS + ':block/sakefox_station'

data = json.load(open(BB, encoding='utf-8'))
RES = data['resolution']
UV_DIV = RES['width'] / 16.0  # 128 -> 8

elements = {e['uuid']: e for e in data['elements']}
groups = {}
elem_group = {}

def parse_outline(nodes, parent):
    for n in nodes:
        if isinstance(n, str):
            elem_group[n] = parent
        else:
            groups[n['uuid']] = {'name': n['name'],
                                 'origin': n.get('origin', [0, 0, 0]),
                                 'parent': parent}
            parse_outline(n.get('children', []), n['uuid'])
parse_outline(data['outliner'], None)
name2uuid = {g['name']: u for u, g in groups.items()}

ge = defaultdict(list)
for eu, gu in elem_group.items():
    ge[gu].append(eu)

# group classification
# static geometry = ungrouped root elements (body::/shelf::/dec::) + the 'roof' group
# dynamic groups: (group name, model key, cmd id)
DYN = [
    ('gap_void', 'void', 1),
    ('gap_lid_top', 'lidt', 2),
    ('gap_lid_bot', 'lidb', 3),
    ('s0', 's0', 4),
    ('s1', 's1', 5),
    ('s2', 's2', 6),
    ('shards', 'shards', 7),
    ('dropbag', 'dropbag', 8),
    ('fall0', 'fall0', 9),
    ('fall1', 'fall1', 10),
    ('fall2', 'fall2', 11),
    ('fall3', 'fall3', 12),
    ('bag1', 'bag1', 13),
    ('bag2', 'bag2', 14),
    ('bag3', 'bag3', 15),
    ('bag4', 'bag4', 16),
    ('bag5', 'bag5', 17),
]

warnings = []

def rnum(v):
    f = round(float(v), 3)
    return int(f) if f == int(f) else f

def conv_element(e):
    el = {'from': [rnum(v) for v in e['from']],
          'to': [rnum(v) for v in e['to']]}
    rot = e.get('rotation', [0, 0, 0])
    nz = [(ax, float(v)) for ax, v in zip(('x', 'y', 'z'), rot) if abs(float(v)) > 1e-6]
    if nz:
        if len(nz) == 1 and abs(round(nz[0][1] / 22.5) * 22.5 - nz[0][1]) < 1e-6:
            el['rotation'] = {'angle': rnum(nz[0][1]), 'axis': nz[0][0],
                              'origin': [rnum(v) for v in e.get('origin', [8, 8, 8])]}
        else:
            warnings.append('unsupported element rotation: ' + e['name'] + ' ' + str(rot))
    if abs(float(e.get('inflate', 0))) > 1e-6:
        warnings.append('element inflate != 0: ' + e['name'])
    # bounds check for MC block model (-16..32)
    for axis in range(3):
        if not (-16 <= float(e['from'][axis]) and float(e['to'][axis]) <= 32):
            warnings.append('element out of -16..32: ' + e['name'])
    faces = {}
    for dirn, f in e.get('faces', {}).items():
        if not f or 'uv' not in f:
            continue
        faces[dirn] = {'uv': [rnum(x / UV_DIV) for x in f['uv']], 'texture': '#0'}
    el['faces'] = faces
    return el

def model_for_elements(euus):
    return {'ambientocclusion': False,
            'textures': {'0': TEX, 'particle': TEX},
            'elements': [conv_element(elements[eu]) for eu in euus]}

def model_for_groups(guuids):
    euuids = []
    for gu in guuids:
        euuids += ge[gu]
    return model_for_elements(euuids)

static_elem_uuids = [eu for eu, gu in elem_group.items() if gu is None]
static_elem_uuids += ge[name2uuid['roof']]
static_model = model_for_elements(static_elem_uuids)
dyn_models = {}
for gn, key, cmd in DYN:
    dyn_models[key] = model_for_groups([name2uuid[gn]])

# render_proxy item model
overrides = [{'predicate': {'custom_model_data': cmd},
              'model': NS + ':block/anim/' + key} for gn, key, cmd in DYN]
proxy_item_model = {
    'parent': 'minecraft:item/generated',
    'textures': {'layer0': NS + ':item/render_proxy'},
    'overrides': overrides,
}

# decode embedded texture
texsrc = data['textures'][0]['source']
b64 = texsrc[texsrc.index(',') + 1:]
tex_bytes = base64.b64decode(b64)

# transparent 16x16 png for proxy fallback
try:
    from PIL import Image
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    import io
    buf = io.BytesIO(); im.save(buf, 'PNG'); proxy_png = buf.getvalue()
except Exception as ex:
    proxy_png = None
    warnings.append('PIL transparent png failed: ' + str(ex))

# ---------- JiuhuAnimData.java ----------
# deliver animation = animations index 1
deliver = data['animations'][1]
# bone order for animation (must match renderer), group name -> java const
BONE_ORDER = [
    ('gap', 'GAP'),
    ('gap_void', 'VOID'),
    ('gap_lid_top', 'LIDT'),
    ('gap_lid_bot', 'LIDB'),
    ('shards', 'SHARDS'),
    ('s0', 'S0'),
    ('s1', 'S1'),
    ('s2', 'S2'),
    ('dropbag', 'DROPBAG'),
    ('fall0', 'FALL0'),
    ('fall1', 'FALL1'),
    ('fall2', 'FALL2'),
    ('fall3', 'FALL3'),
]
FLIP_X = {'gap_lid_top', 'gap_lid_bot'}

def fjava(v):
    f = round(float(v), 3)
    if f == 0:
        return '0f'
    s = ('%.3f' % f).rstrip('0').rstrip('.')
    return s + 'f'

def packed(frames, channel, flipx):
    rows = [fr for fr in frames if fr['channel'] == channel]
    if not rows:
        return None
    rows.sort(key=lambda z: z['time'])
    out = []
    for fr in rows:
        dp = fr['data_points'][0]
        x = float(dp['x']); y = float(dp['y']); z = float(dp['z'])
        if flipx and channel == 'rotation':
            x = -x
        out += [float(fr['time']), x, y, z]
    return out

rot_rows, pos_rows, scl_rows = [], [], []
origin_rows = []
for gn, jc in BONE_ORDER:
    gu = name2uuid[gn]
    o = groups[gu]['origin']
    origin_rows.append([float(o[0]), float(o[1]), float(o[2])])
    anim = deliver['animators'].get(gu)
    if anim is None:
        rot_rows.append(None); pos_rows.append(None); scl_rows.append(None); continue
    flip = gn in FLIP_X
    rot_rows.append(packed(anim['keyframes'], 'rotation', flip))
    pos_rows.append(packed(anim['keyframes'], 'position', flip))
    scl_rows.append(packed(anim['keyframes'], 'scale', flip))

def matrix(name, rows):
    lines = ['public static final float[][] ' + name + ' = {']
    for r in rows:
        if r is None:
            lines.append('    null,')
        else:
            lines.append('    { ' + ', '.join(fjava(v) for v in r) + ' },')
    lines.append('};')
    return '\n'.join(lines)

duration = 3.2
# cmd constants
cmd_lines = ['// render_proxy custom_model_data ids']
for gn, key, cmd in DYN:
    cmd_lines.append('public static final int CMD_%s = %d;' % (key.upper(), cmd))

java = []
java.append('package com.icewolf.maidrestaurant.business.client.render;')
java.append('')
java.append('// Auto-generated from sakefox_station.bbmodel. Do not edit by hand.')
java.append('public final class JiuhuAnimData {')
java.append('    private JiuhuAnimData() {}')
java.append('')
java.append('    public static final float DURATION = 3.2f;')
for i, (gn, jc) in enumerate(BONE_ORDER):
    java.append('    public static final int B_%s = %d;' % (jc, i))
java.append('')
java.append('\n'.join(cmd_lines))
java.append('')
java.append('    // bone pivot origins in texture-pixel units (0..128 space)')
origin_lines = ['public static final float[][] ORIGIN = {']
for r in origin_rows:
    origin_lines.append('    { ' + ', '.join(fjava(v) for v in r) + ' },')
origin_lines.append('};')
java.append('\n'.join(origin_lines))
java.append('')
java.append('    // packed keyframes per bone: time,x,y,z, time,x,y,z ... ; null = no key')
java.append(matrix('ROT', rot_rows))
java.append('')
java.append(matrix('POS', pos_rows))
java.append('')
java.append(matrix('SCL', scl_rows))
java.append('}')
java_text = '\n'.join(java)

# ---------- write to both lanes ----------
def wjson(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as fh:
        json.dump(obj, fh, indent=2, ensure_ascii=False)

report = []
for lane in LANES:
    res = os.path.join(lane, RES_REL, 'assets', NS)
    # static block model
    wjson(os.path.join(res, 'models', 'block', 'jiuhu_station.json'), static_model)
    # dynamic models
    for gn, key, cmd in DYN:
        wjson(os.path.join(res, 'models', 'block', 'anim', key + '.json'), dyn_models[key])
    # texture
    tdir = os.path.join(res, 'textures', 'block')
    os.makedirs(tdir, exist_ok=True)
    with open(os.path.join(tdir, 'sakefox_station.png'), 'wb') as fh:
        fh.write(tex_bytes)
    # proxy item model + texture
    wjson(os.path.join(res, 'models', 'item', 'render_proxy.json'), proxy_item_model)
    if proxy_png is not None:
        idir = os.path.join(res, 'textures', 'item')
        os.makedirs(idir, exist_ok=True)
        with open(os.path.join(idir, 'render_proxy.png'), 'wb') as fh:
            fh.write(proxy_png)
    # java anim data
    jdir = os.path.join(lane, JAVA_REL, PKG_PATH)
    os.makedirs(jdir, exist_ok=True)
    with open(os.path.join(jdir, 'JiuhuAnimData.java'), 'w', encoding='utf-8', newline='\n') as fh:
        fh.write(java_text)
    report.append('lane: ' + os.path.basename(lane) +
                  ' staticEls=' + str(len(static_model['elements'])) +
                  ' dynGroups=' + str(len(DYN)))

print('\n'.join(report))
print('tex png bytes:', len(tex_bytes))
if warnings:
    print('--- WARNINGS ---')
    print('\n'.join(sorted(set(warnings))))
else:
    print('no warnings')

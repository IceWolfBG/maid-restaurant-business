"""Run seed -> public original -> patched -> saved reload on a private server.

Requires NeoForge installer output under verification/server and regressionJar.
Uses downloaded public inputs only. Never reads a user's pack or world.
"""
import hashlib
import json
import os
import pathlib
import shutil
import subprocess
import time

ROOT = pathlib.Path(__file__).resolve().parent
SERVER = ROOT / 'server'
PROJECT = ROOT.parent / 'neoforge-1.21.1'
MODS = SERVER / 'mods'
if any(SERVER.glob('world-*')) or any(SERVER.glob('regression-*.json')):
    raise RuntimeError('Use a fresh verification/server directory; existing worlds/results are not overwritten')
MODS.mkdir(exist_ok=True)
properties = dict(line.split('=', 1) for line in (PROJECT / 'gradle.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
ARTIFACT = PROJECT / ('build/libs/maid_restaurant_business-1.21.1-neoforge-' + properties['mod_version'] + '.jar')
ORIGINAL = ROOT / 'runtime-inputs/maid_restaurant_business-1.21.1-neoforge-0.2.4-fix2.jar'
ORIGINAL_CACHE = ROOT / 'runtime-inputs/maid_restaurant_business-1.21.1-neoforge-0.2.4-fix3.jar'
for jar in (PROJECT / 'libs').glob('*.jar'):
    if not jar.name.startswith('jade-'):
        shutil.copy2(jar, MODS / jar.name)
for jar in [ROOT / 'runtime-inputs/sable-neoforge-1.21.1-2.0.6.jar', PROJECT / 'build/libs/loadedscan-regression.jar']:
    shutil.copy2(jar, MODS / jar.name)
(SERVER / 'eula.txt').write_text('eula=true\n')
(SERVER / 'user_jvm_args.txt').write_text('-Xms512m\n-Xmx2G\n-XX:ActiveProcessorCount=4\n')
phases = ['seed', 'baseline', 'cache_baseline', 'fixed', 'reload', 'scheduled']
summary = []
for phase in phases:
    if phase in ('baseline', 'cache_baseline', 'fixed'):
        shutil.copytree(SERVER / 'world-seed', SERVER / ('world-' + phase))
    world = 'world-fixed' if phase in ('reload', 'scheduled') else 'world-' + phase
    (SERVER / 'server.properties').write_text(
        'server-ip=127.0.0.1\nserver-port=25684\nonline-mode=false\nenable-query=false\n'
        'enable-rcon=false\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\n'
        'max-tick-time=120000\nlevel-seed=67523011\nlevel-type=minecraft:flat\n'
        'generate-structures=false\nspawn-monsters=false\nspawn-animals=true\n'
        'level-name=' + world + '\n')
    for jar in MODS.glob('maid_restaurant_business-*.jar'):
        jar.unlink()
    selected = None
    if phase != 'seed':
        selected = ORIGINAL if phase == 'baseline' else ORIGINAL_CACHE if phase == 'cache_baseline' else ARTIFACT
        shutil.copy2(selected, MODS / selected.name)
    for old_report in [SERVER / ('regression-' + phase + '.json')]:
        if old_report.exists():
            raise RuntimeError('Refuse to overwrite an existing result: ' + str(old_report))
    (ROOT / 'runtime-progress.json').write_text(json.dumps({'phase': phase, 'status': 'running'}))
    args = ['java', '@user_jvm_args.txt', '-Dloadedscan.phase=' + phase,
            '@libraries/net/neoforged/neoforge/21.1.248/unix_args.txt', 'nogui']
    start = time.monotonic()
    with (ROOT / ('runtime-' + phase + '.log')).open('w') as out:
        proc = subprocess.Popen(args, cwd=SERVER, stdout=out, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
        (ROOT / 'runtime.pid').write_text(str(proc.pid))
        try:
            code = proc.wait(timeout=1000)
        except subprocess.TimeoutExpired:
            proc.terminate()
            try: proc.wait(timeout=30)
            except subprocess.TimeoutExpired: proc.kill(); proc.wait()
            code = -1
    report_path = SERVER / ('regression-' + phase + '.json')
    report = json.loads(report_path.read_text()) if report_path.exists() else None
    log = (ROOT / ('runtime-' + phase + '.log')).read_text(errors='replace')
    errors = log.count('Aborting entity get for abnormally large AABB')
    passed = code == 0 and report is not None and report['passed']
    passed = passed and (errors > 0 if phase == 'baseline' else errors == 0)
    if phase == 'cache_baseline':
        # Positive control: fix3 logs no AABB error but misses scheduled binding.
        passed = code == 0 and report is not None and not report['passed'] and errors == 0
        passed = passed and any(a['name'] == 'scheduled_binding_add_without_bauble_tick'
                                and a['status'] == 'FAIL' for a in report['assertions'])
    row = {'phase': phase, 'exit_code': code, 'seconds': time.monotonic() - start,
           'aabb_errors': errors, 'passed': passed,
           'jar_sha256': hashlib.sha256(selected.read_bytes()).hexdigest() if selected else None,
           'report': report}
    summary.append(row)
    (ROOT / 'runtime-results.json').write_text(json.dumps(summary, indent=2))
    print(json.dumps({k: v for k, v in row.items() if k != 'report'}), flush=True)
    if not passed:
        (ROOT / 'runtime-progress.json').write_text(json.dumps({'phase': phase, 'status': 'FAILED'}))
        raise SystemExit(1)
(ROOT / 'runtime-progress.json').write_text(json.dumps({'phase': 'complete', 'status': 'PASS'}))

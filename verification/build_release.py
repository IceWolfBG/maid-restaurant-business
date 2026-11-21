"""Reproducible source build with pinned public dependencies and an explicit proxy."""
import argparse
import hashlib
import json
import os
import pathlib
import subprocess
import urllib.parse
import urllib.request

p = argparse.ArgumentParser()
p.add_argument('--proxy', default='http://127.0.0.1:7890')
p.add_argument('--harness', action='store_true')
args = p.parse_args()
root = pathlib.Path(__file__).resolve().parent.parent
module = root / 'neoforge-1.21.1'
libs = module / 'libs'
libs.mkdir(exist_ok=True)
url = urllib.parse.urlsplit(args.proxy)
if url.scheme != 'http' or not url.hostname or url.username or url.password:
    raise SystemExit('Supply a local HTTP proxy without credentials, e.g. http://127.0.0.1:7890')
opener = urllib.request.build_opener(urllib.request.ProxyHandler({'http': args.proxy, 'https': args.proxy}))
for item in json.loads((root / 'verification/dependencies.json').read_text()):
    dest = libs / item['filename']
    if dest.exists() and hashlib.sha256(dest.read_bytes()).hexdigest() == item['sha256']:
        continue
    content = opener.open(item['url'], timeout=120).read()
    assert hashlib.sha256(content).hexdigest() == item['sha256'], item['filename']
    dest.write_bytes(content)
command = ['bash', 'gradlew', 'build', '-x', 'test', '-Pneo_version=21.1.248',
           '--no-daemon', '--max-workers=4', '--console=plain',
           '-Dhttps.proxyHost=' + url.hostname, '-Dhttps.proxyPort=' + str(url.port or 80),
           '-Dhttp.proxyHost=' + url.hostname, '-Dhttp.proxyPort=' + str(url.port or 80)]
if args.harness:
    command += ['regressionJar', '-I', '../verification/harness.gradle']
env = os.environ.copy()
# The wrapper downloads Gradle before Gradle sees its command-line properties.
env['JAVA_TOOL_OPTIONS'] = (env.get('JAVA_TOOL_OPTIONS', '') + ' '
                           + ' '.join(a for a in command if a.startswith('-D'))).strip()
raise SystemExit(subprocess.call(command, cwd=module, env=env))

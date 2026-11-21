"""Fetch the public inputs pinned in runtime-inputs.json; never read player data."""
import argparse
import concurrent.futures
import hashlib
import json
import pathlib
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument('--proxy', help='Optional HTTP proxy URL')
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parent
destination = root / 'runtime-inputs'
destination.mkdir(exist_ok=True)
opener = urllib.request.build_opener(urllib.request.ProxyHandler(
    {'http': args.proxy, 'https': args.proxy}) if args.proxy else urllib.request.ProxyHandler())


def fetch(item):
    path = destination / item['file']
    if path.exists() and hashlib.sha256(path.read_bytes()).hexdigest() == item['sha256']:
        print(item['file'], 'verified cached input', flush=True)
        return
    content = opener.open(item['url'], timeout=120).read()
    if hashlib.sha256(content).hexdigest() != item['sha256']:
        raise ValueError('SHA-256 mismatch: ' + item['file'])
    path.write_bytes(content)
    print(item['file'], len(content), flush=True)


with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
    list(pool.map(fetch, json.loads((root / 'runtime-inputs.json').read_text())))

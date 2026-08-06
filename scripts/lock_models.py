#!/usr/bin/env python3
# Maintainer tool: downloads MODEL_SOURCES.json entries and writes exact SHA-256/size locks.
import argparse,hashlib,json,tempfile,urllib.request
from pathlib import Path

def main():
 ap=argparse.ArgumentParser(); ap.add_argument('--sources',default='MODEL_SOURCES.json'); ap.add_argument('--output',default='MODEL_MANIFEST.json'); ns=ap.parse_args()
 src=json.loads(Path(ns.sources).read_text()); out={'schema_version':1,'artifacts':[]}
 for a in src['artifacts']:
  h=hashlib.sha256(); size=0
  with urllib.request.urlopen(a['url'],timeout=120) as resp:
   while True:
    b=resp.read(1024*1024)
    if not b: break
    size+=len(b); h.update(b)
  out['artifacts'].append({**a,'sha256':h.hexdigest(),'bytes':size})
 Path(ns.output).write_text(json.dumps(out,indent=2)+'\n')
if __name__=='__main__': main()

#!/usr/bin/env python3
import argparse,hashlib,os,shutil,tarfile,tempfile,urllib.request
from pathlib import Path
from urllib.parse import urlparse
from model_manifest import load_manifest
ALLOWED={'github.com','release-assets.githubusercontent.com','objects.githubusercontent.com','githubusercontent.com','githubassets.com'}
MODEL_DIR='sherpa-onnx-streaming-zipformer-small-bilingual-zh-en-2023-02-16'
def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1024*1024),b''): h.update(b)
 return h.hexdigest()
def fetch(a,root):
 dst=(root/a['destination']).resolve(); root=root.resolve()
 if root not in dst.parents: raise ValueError('destination escapes root')
 dst.parent.mkdir(parents=True,exist_ok=True); part=dst.with_suffix(dst.suffix+'.part')
 req=urllib.request.Request(a['url'],headers={'User-Agent':'locowiki-public-ci/1'})
 with urllib.request.urlopen(req,timeout=120) as resp, part.open('wb') as out:
  if urlparse(resp.geturl()).hostname not in ALLOWED: raise ValueError('redirect host denied')
  shutil.copyfileobj(resp,out,1024*1024)
 if part.stat().st_size!=a['bytes'] or sha(part)!=a['sha256']:
  part.unlink(missing_ok=True); raise ValueError('artifact checksum mismatch: '+a['id'])
 os.replace(part,dst); return dst
def extract_asr(a,archive,root):
 wanted={x['name']:x for x in a['extract']}; outdir=root/'app/src/main/assets'/MODEL_DIR; outdir.mkdir(parents=True,exist_ok=True)
 found={}
 with tarfile.open(archive,'r:bz2') as tf:
  for m in tf.getmembers():
   name=Path(m.name).name
   if name not in wanted or not m.isfile(): continue
   target=outdir/name
   with tf.extractfile(m) as src, target.open('wb') as out: shutil.copyfileobj(src,out)
   found[name]=target
 if set(found)!=set(wanted): raise ValueError('ASR archive missing required files')
 for n,p in found.items():
  meta=wanted[n]
  if p.stat().st_size!=meta['bytes'] or sha(p)!=meta['sha256']: raise ValueError('ASR extracted checksum mismatch: '+n)
 archive.unlink(missing_ok=True)
 cache=archive.parent
 if cache.is_dir() and not any(cache.iterdir()): cache.rmdir()
def main():
 ap=argparse.ArgumentParser(); ap.add_argument('--manifest',default='MODEL_MANIFEST.json'); ap.add_argument('--root',default='.')
 ns=ap.parse_args(); root=Path(ns.root).resolve(); d=load_manifest(Path(ns.manifest))
 for a in d['artifacts']:
  p=fetch(a,root)
  if a['id']=='streaming-zipformer-bilingual': extract_asr(a,p,root)
 print('MODEL_DOWNLOAD_OK')
if __name__=='__main__': main()

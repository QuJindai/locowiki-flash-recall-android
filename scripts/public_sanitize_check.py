#!/usr/bin/env python3
import argparse,re,sys
from pathlib import Path
FORBIDDEN_SUFFIX={'.apk','.aab','.aar','.onnx','.pcm','.wav','.db','.sqlite','.jks','.keystore'}
SKIP={'.git','.gradle','.idea','.kotlin','build','.model-cache','qa-artifacts','__pycache__'}
SECRET=[re.compile(x,re.I) for x in [r'github_pat_',r'ghp_[A-Za-z0-9]{20,}',r'Authorization\s*:',r'Bearer\s+[A-Za-z0-9._-]{12,}',r'sk-[A-Za-z0-9]{20,}',r'C:\\\\Users\\\\',r'/home/[^/\s]+/',r'/private/']]
def scan(root):
 errors=[]
 for bad in ('LICENSE','LICENSE.md','COPYING'):
  if (root/bad).exists(): errors.append('open-source license file: '+bad)
 for req in ('README.md','COPYRIGHT.md','NOTICE-NO-LICENSE.md','CONTRIBUTING.md'):
  if not (root/req).is_file(): errors.append('missing '+req)
 read=(root/'README.md').read_text(errors='ignore') if (root/'README.md').exists() else ''
 if 'All rights reserved' not in read or 'not open source' not in read: errors.append('rights notice missing')
 for p in root.rglob('*'):
  if not p.is_file() or any(x in SKIP for x in p.relative_to(root).parts): continue
  rel=p.relative_to(root)
  if p.suffix.lower() in FORBIDDEN_SUFFIX: errors.append('committed binary: '+str(rel)); continue
  if p.name in {'local.properties','keystore.properties'}: errors.append('local config: '+str(rel)); continue
  try: text=p.read_text(encoding='utf-8')
  except UnicodeDecodeError: errors.append('unexpected binary: '+str(rel)); continue
  if rel.as_posix() not in {'scripts/public_sanitize_check.py','tests/test_public_sanitize_check.py'}:
   for pat in SECRET:
    if pat.search(text): errors.append('sensitive pattern in '+str(rel)); break
 manifest=root/'app/src/main/AndroidManifest.xml'
 if manifest.exists() and 'android.permission.INTERNET' in manifest.read_text(): errors.append('INTERNET permission present')
 return errors
if __name__=='__main__':
 ap=argparse.ArgumentParser(); ap.add_argument('--root',default='.'); ns=ap.parse_args(); e=scan(Path(ns.root).resolve())
 if e: print('\n'.join('ERROR '+x for x in e)); raise SystemExit(1)
 print('PUBLIC_SANITIZE_OK')

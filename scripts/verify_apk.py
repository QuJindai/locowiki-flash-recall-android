#!/usr/bin/env python3
import argparse,os,re,subprocess,zipfile
from pathlib import Path
def tool(name):
 sdk=Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT',''))
 candidates=list((sdk/'build-tools').glob('*/'+name)) if sdk else []
 if name=='apkanalyzer' and sdk: candidates=[sdk/'cmdline-tools/latest/bin/apkanalyzer']+candidates
 for p in reversed(candidates):
  if p.exists(): return str(p)
 return name
def run(*a): return subprocess.check_output(a,text=True,stderr=subprocess.STDOUT)
def main():
 ap=argparse.ArgumentParser(); ap.add_argument('--apk',required=True); ap.add_argument('--expected-package',required=True); ap.add_argument('--expected-version-name',required=True); ap.add_argument('--expected-version-code',required=True,type=int); ap.add_argument('--expected-abi',required=True); ap.add_argument('--require-debuggable',action='store_true'); n=ap.parse_args()
 bad=run(tool('aapt2'),'dump','badging',n.apk)
 checks=[f"package: name='{n.expected_package}'",f"versionCode='{n.expected_version_code}'",f"versionName='{n.expected_version_name}'"]
 if not all(x in bad for x in checks): raise SystemExit('APK identity mismatch\n'+bad)
 if 'uses-permission: name=\'android.permission.INTERNET\'' in bad: raise SystemExit('INTERNET permission present')
 if n.require_debuggable and 'application-debuggable' not in bad: raise SystemExit('APK not debuggable')
 with zipfile.ZipFile(n.apk) as z:
  abis={x.split('/')[1] for x in z.namelist() if x.startswith('lib/') and x.count('/')>=2}
 if abis!={n.expected_abi}: raise SystemExit(f'ABI mismatch: {abis}')
 print('APK_VERIFY_OK')
if __name__=='__main__': main()

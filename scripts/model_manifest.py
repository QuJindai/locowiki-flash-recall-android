import json,re
from pathlib import Path
from urllib.parse import urlparse
HEX=re.compile(r'^[0-9a-f]{64}$')
def load_manifest(path: Path):
 d=json.loads(path.read_text(encoding='utf-8'))
 if d.get('schema_version')!=1 or not isinstance(d.get('artifacts'),list): raise ValueError('invalid schema')
 ids=set()
 for a in d['artifacts']:
  if a.get('id') in ids: raise ValueError('duplicate id')
  ids.add(a.get('id'))
  u=urlparse(a.get('url',''))
  if u.scheme!='https' or u.query or 'latest' in u.path.lower() or u.hostname!='github.com': raise ValueError('unfixed URL')
  dest=Path(a.get('destination',''))
  if not str(dest) or dest.is_absolute() or '..' in dest.parts: raise ValueError('unsafe destination')
  if not HEX.fullmatch(a.get('sha256','')) or not isinstance(a.get('bytes'),int) or a['bytes']<=0: raise ValueError('invalid lock')
 return d

import sys,tempfile,unittest
from pathlib import Path
sys.path.insert(0,str(Path('scripts').resolve()))
from public_sanitize_check import scan
class TestSanitize(unittest.TestCase):
 def base(self,p):
  for n,c in {'README.md':'All rights reserved; not open source','COPYRIGHT.md':'x','NOTICE-NO-LICENSE.md':'x','CONTRIBUTING.md':'x','app/src/main/AndroidManifest.xml':'<manifest />'}.items(): q=p/n;q.parent.mkdir(parents=True,exist_ok=True);q.write_text(c)
 def test_current_tree(self): self.assertEqual([],scan(Path('.').resolve()))
 def test_rejects_binary(self):
  with tempfile.TemporaryDirectory() as d:
   p=Path(d);self.base(p);(p/'bad.onnx').write_bytes(b'x');self.assertTrue(scan(p))
if __name__=='__main__': unittest.main()

import sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path('scripts').resolve()))
from model_manifest import load_manifest
class TestManifest(unittest.TestCase):
 def test_committed_manifest(self):
  d=load_manifest(Path('MODEL_MANIFEST.json')); self.assertEqual(4,len(d['artifacts']))
 def test_all_locks_complete(self):
  for a in load_manifest(Path('MODEL_MANIFEST.json'))['artifacts']:
   self.assertEqual(64,len(a['sha256'])); self.assertGreater(a['bytes'],0)
if __name__=='__main__': unittest.main()

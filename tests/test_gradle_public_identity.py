from pathlib import Path
import unittest
class TestIdentity(unittest.TestCase):
 def test_identity(self):
  t=Path('app/build.gradle.kts').read_text();
  for x in ['applicationId = "com.qujindai.locowiki.flashrecall"','versionCode = 5','versionName = "0.4.0"','VOICEPRINT_QA_PCM_ALLOWED','providers.gradleProperty("targetAbi")']: self.assertIn(x,t)
if __name__=='__main__': unittest.main()

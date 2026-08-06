#!/usr/bin/env bash
set -euo pipefail
APK="${APK:-app/build/outputs/apk/debug/app-debug.apk}"
OUT="${OUT:-qa-artifacts/voiceprint}"; PACKAGE=com.qujindai.locowiki.flashrecall; ACTIVITY=com.qujindai.locowiki.flashrecall.v2.MainActivity
mkdir -p "$OUT"; exec > >(tee "$OUT/qa-console.log") 2>&1
capture(){ adb exec-out screencap -p > "$OUT/$1.png"; adb shell uiautomator dump /sdcard/$1.xml >/dev/null 2>&1 || true; adb pull /sdcard/$1.xml "$OUT/ui-$1.xml" >/dev/null 2>&1 || true; }
collect(){ rc=$?; set +e; capture final; adb logcat -d -v threadtime > "$OUT/logcat.txt"; adb logcat -b crash -d > "$OUT/crash-log.txt"; exit $rc; }; trap collect EXIT
fail(){ echo "QA_FAIL $*"; exit 1; }
dump(){ adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/ui-current.xml" >/dev/null; }
find_text(){ python3 - "$1" "$OUT/ui-current.xml" <<'P'
import re,sys,xml.etree.ElementTree as ET
needle,path=sys.argv[1:]
for n in ET.parse(path).getroot().iter('node'):
 if needle not in (n.attrib.get('text','')+n.attrib.get('content-desc','')): continue
 m=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
 if m:
  x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2); raise SystemExit
raise SystemExit(1)
P
}
seek(){ for i in $(seq 1 15); do dump; if find_text "$1" > "$OUT/xy"; then return 0; fi; adb shell input swipe 520 1850 520 650 250 >/dev/null; sleep 1; done; return 1; }
wait_text(){ end=$((SECONDS+${2:-120})); while ((SECONDS<end)); do for i in $(seq 1 8); do adb shell input swipe 520 600 520 1900 150 >/dev/null; done; if seek "$1"; then return 0; fi; sleep 2; done; return 1; }
click_text(){ wait_text "$1" 30 || return 1; read x y < "$OUT/xy"; adb shell input tap "$x" "$y"; sleep 2; }
adb wait-for-device; adb install -r "$APK"; adb shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO || true
adb shell "run-as $PACKAGE mkdir -p files/voiceprint-qa"
for n in 1 2 3; do adb shell "run-as $PACKAGE sh -c 'cat > files/voiceprint-qa/segment-$n.pcm'" < /tmp/segment.pcm; done
adb shell "run-as $PACKAGE touch files/voiceprint-qa/enabled"; adb logcat -c; adb shell am force-stop "$PACKAGE"; adb shell am start -W -n "$PACKAGE/$ACTIVITY"; sleep 5
wait_text '声纹模型已就绪' 240 || fail model; wait_text 'SELF样本 0/3' 30 || fail initial; capture 00-ready
for n in 1 2 3; do click_text "录制第${n}段" || fail start-$n; wait_text "停止并保存第${n}段" 30 || fail recording-$n; capture ${n}a-recording; click_text "停止并保存第${n}段" || fail stop-$n; wait_text "SELF样本 ${n}/3" 180 || fail saved-$n; capture ${n}b-saved; done
wait_text 'SELF声纹已建立' 60 || fail complete
adb logcat -d > "$OUT/verdict-log.txt"; ! grep -Eq 'FATAL EXCEPTION|has died' "$OUT/verdict-log.txt" || fail crash
printf 'initial=0\nsegment_1=1\nsegment_2=2\nsegment_3=3\nfinal_message=SELF声纹已建立\nfatal_crash=NO\nresult=PASS\n' > "$OUT/RESULT.txt"
echo THREE_SEGMENT_QA_PASS

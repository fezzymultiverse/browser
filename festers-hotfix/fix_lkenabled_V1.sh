#!/usr/bin/env bash
# Festers Browser V1.26.1 hotfix: home page + Settings blank.
# Cause: lkEnabled / LKC / lkBio were "const" and lkLocked "let", declared AFTER the new-tab page is first drawn at start-up
#        -> ReferenceError "Cannot access 'lkEnabled' before initialization" -> whole script stops. Function declarations / var are hoisted.
# Usage: bash fix_lkenabled_V1.sh [path/to/www/index.html]
set -u
F="${1:-www/index.html}"
[ -f "$F" ] || { printf '\033[38;5;196mNot found: %s\033[0m\nRun from the project root or pass the path to index.html\n' "$F"; exit 1; }
command -v python3 >/dev/null 2>&1 || { printf '\033[38;5;196mpython3 missing: pkg install python\033[0m\n'; exit 1; }
BK="$F.bak.$(date +%Y%m%d-%H%M%S)"
cp -- "$F" "$BK" || { printf '\033[38;5;196mCould not write backup\033[0m\n'; exit 1; }
python3 - "$F" <<'PY'
import sys
p = sys.argv[1]
s = open(p, encoding='utf-8').read()
old = ("const LKC=()=>LS.get('lockCfg',null);\n"
       "const lkEnabled=()=>{const c=LKC();return !!(c&&c.h)};\n"
       "const lkBio=()=>{const c=LKC();return !!(c&&c.bio)};\n"
       "let lkLocked=false,lkBusy=false;")
new = ("/* V1.26.1 fix: hoisted so the new-tab page can use them at start-up (was const/let = crash, no home page, no Settings) */\n"
       "function LKC(){return LS.get('lockCfg',null)}\n"
       "function lkEnabled(){const c=LKC();return !!(c&&c.h)}\n"
       "function lkBio(){const c=LKC();return !!(c&&c.bio)}\n"
       "var lkLocked=false,lkBusy=false;")
if new in s:
    print("ALREADY PATCHED"); sys.exit(3)
if s.count(old) != 1:
    print("ANCHOR NOT FOUND (count=%d). File differs from the V1.26.0 build I checked." % s.count(old)); sys.exit(2)
open(p, 'w', encoding='utf-8').write(s.replace(old, new))
print("PATCHED")
PY
rc=$?
if [ "$rc" -eq 0 ]; then printf '\033[38;5;82mDone. Backup: %s\033[0m\nNow: npx cap sync android, rebuild, install.\n' "$BK"
elif [ "$rc" -eq 3 ]; then rm -f -- "$BK"; printf '\033[38;5;220mAlready patched, nothing changed.\033[0m\n'
else cp -- "$BK" "$F"; printf '\033[38;5;196mNot patched, original restored.\033[0m\n'; exit 1; fi

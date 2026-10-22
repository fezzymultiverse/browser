Festers Browser hotfix (home page + Settings blank)
Option A: copy www/index.html over your project www/index.html (only if your source is the V1.26.0 build)
Option B (safer): bash fix_lkenabled_V1.sh www/index.html  (from project root; makes a backup)
Then: npx cap sync android, rebuild.

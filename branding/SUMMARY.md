# Short texts for the project pages

**Summary** (CurseForge "Summary", Modrinth "Summary", max ~100 characters):

> Put your copper golems to work: 10 jobs, from mining and farming to enchanting and brewing.

**Tags / categories:** Technology, Utility, Farming, Mobs (NeoForge 1.21.11).

**Files in this folder**
- `logo.png` (420x420, transparent): project logo for CurseForge/Modrinth, the title with two gears. Same image as `publish/icon.png`.
- `mod_logo.png` (256x256): in-game mod logo, the copper golem (`src/main/resources/simple_automation_logo.png`, `publish/logo.png`).
- `banner.png` (798x228): top of the description.
- `header_*.png` (798x84): one per main section of the description.
- `DESCRIPTION.md`: description, same text as `publish/DESCRIPTION.md`. Images point to
  `https://raw.githubusercontent.com/WoroxIV/simple-automation/main/branding/`.
- `branding.json` + `src/sa_branding.py` + `src/golem32.txt`: sources. Regenerate with
  `python branding/src/sa_branding.py branding/branding.json`.

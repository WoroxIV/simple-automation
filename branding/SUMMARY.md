# Short texts for the project pages

**Summary** (CurseForge "Summary", Modrinth "Summary", max ~100 characters):

> Put your copper golems to work: 10 jobs, from mining and farming to enchanting and brewing.

**Tags / categories:** Technology, Utility, Farming, Mobs (NeoForge 1.21.11).

**Files in this folder**
- `logo.png` (540x540): project icon for CurseForge/Modrinth. Same image as `publish/icon.png`.
- `logo_transparent.png` (256x256): in-game mod logo (`src/main/resources/simple_automation_logo.png`, `publish/logo.png`).
- `banner.png` (840x228): top of the description.
- `header_*.png` (840x84): one per description section.
- `title_logo.png`: transparent title (not used by the mod).
- `DESCRIPTION.md`: full description, same text as `publish/DESCRIPTION.md`. Images point to
  `https://raw.githubusercontent.com/WoroxIV/simple-automation/main/branding/`: they show once that repository is public,
  or upload the PNGs to CurseForge (project > Images) and replace the links.
- `branding.json` + `src/sa_branding.py` + `src/golem32.txt`: sources. Regenerate with
  `python branding/src/sa_branding.py branding/branding.json --only logo,banner,headers`
  (`title` would overwrite `title_logo.png`).

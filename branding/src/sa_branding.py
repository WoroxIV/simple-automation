"""Simple Automation branding: copper golem + oxidized gear theme.

Reuses the shared generator (~/.claude/skills/mod-branding/branding.py) for fonts, skies, frames
and title layout. The copper golem comes from golem32.txt (the mod icon); the pickaxe is drawn
in code so the head always sits centred on the handle.

    python branding/src/sa_branding.py branding/branding.json [--only logo,modlogo,banner,headers]
"""
import json
import math
import os
import sys

# générateur partagé : skill de l'espace (minecraft/.claude/skills), ancien emplacement en secours
_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.expanduser("~/.claude/skills/mod-branding"))
sys.path.insert(0, os.path.normpath(os.path.join(_HERE, "../../../../.claude/skills/mod-branding")))
import branding as B  # noqa: E402
from branding import C, Canvas, pick, N4, N8  # noqa: E402

# --------------------------------------------------------------------------- palette
CU = [C("#f6b08e"), C("#e3826c"), C("#c87456"), C("#a75a40"), C("#7f4130"), C("#5a2e22")]
CU_OUT = C("#2e1710")
OX = [C("#d8f0e8"), C("#8fd0b6"), C("#76b39c"), C("#5a9c84"), C("#3a6e5e"), C("#1b3830")]
OX_OUT = C("#0c1c18")
WOOD = [C("#d8aa68"), C("#c49a5c"), C("#7a5230"), C("#4a2e1a")]
IRON = [C("#ffffff"), C("#f2f2f6"), C("#c4c4d0"), C("#9a9aa6"), C("#6a6a78")]
IRON_OUT = C("#1a1a24")

B.RAMPS["oxidized"] = B.ramp("#e6fff6", "#a6e6cc", "#8fd0b6", "#6fb89c", "#5a9c84",
                             "#3a6e5e", "#24504a", "#183632", "#071412")
B.RAMPS["copper"] = B.ramp("#ffe0cc", "#f6b08e", "#e3826c", "#c87456", "#a75a40",
                           "#7f4130", "#5a2e22", "#3e1e16", "#170907")
B.SKIES["workshop"] = [C(h) for h in [
    "#0a0f18", "#0f1622", "#141e2e", "#1a283a", "#213346", "#2a3f54", "#354c62",
    "#435a70", "#56697c", "#6e7a86", "#8c8c8c", "#b4a08a"]]

# frames / header ornaments use copper instead of gold, the header gem becomes oxidized copper
B.GUARD[:] = [CU[0], CU[2], CU[3], CU[4]]
B.GUARD_OUT = CU_OUT
B.GEM[:] = [OX[1], OX[3], OX[4]]
# engrenages du logo carré en cuivre oxydé
B.ORNAMENT_SHADES[:] = [OX[1], OX[2], OX[3], OX[4]]
B.ORNAMENT_OUTLINE = OX_OUT


# --------------------------------------------------------------------------- shapes
def pickaxe_head(cv, tx, ty, R=10.0, half_angle=66, thick=2.9):
    """Solid iron arc centred on the handle tip (tx, ty) of a (1,-1) handle; bulges away from the
    golem, tips curve back toward the hand. Built as one distance field so the band never breaks."""
    ux, uy = 1 / math.sqrt(2), -1 / math.sqrt(2)  # handle direction
    cx, cy = tx + 0.5 - ux * R, ty + 0.5 - uy * R  # circle centre, down the handle
    lim = math.radians(half_angle)
    pts = {}
    for y in range(int(ty - R - 3), int(ty + R + 3)):
        for x in range(int(tx - R - 3), int(tx + R + 3)):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            a = math.atan2(dx * uy - dy * ux, dx * ux + dy * uy)  # angle off the handle axis
            if abs(a) > lim:
                continue
            t = abs(a) / lim
            th = thick - (thick - 1.2) * t  # thick in the middle, pointed tips
            off = d - R
            if -th * 0.55 <= off <= th * 0.45:
                nx, ny = dx / d, dy / d
                light = -(nx + ny) / math.sqrt(2)  # outer face lit from the top-left
                if off > th * 0.45 - 1:
                    c = IRON[0] if light > 0.3 else IRON[1] if light > -0.3 else IRON[3]
                elif off < -th * 0.55 + 1:
                    c = IRON[3] if t < 0.85 else IRON[4]
                else:
                    c = IRON[1] if light > 0 else IRON[2]
                if t > 0.88:
                    c = IRON[3]
                pts[(x, y)] = c
    cv.stamp(set(pts), lambda x, y: pts[(x, y)], IRON_OUT)
    # socket where the handle enters the head
    for (x, y) in [(tx, ty + 1), (tx - 1, ty + 1), (tx, ty + 2)]:
        if (x, y) in pts:
            cv.set(x, y, IRON[4])


def handle(cv, x0, y0, n):
    h = {}
    for i in range(n):
        h[(x0 + i, y0 - i)] = WOOD[0] if i % 4 else WOOD[1]
        h[(x0 + i + 1, y0 - i)] = WOOD[2]
    cv.stamp(set(h), lambda x, y: h[(x, y)], CU_OUT)


def load_sprite(name="golem32.txt"):
    """Text grid next to this script: palette lines '<char> <#hex>', blank line, then the rows."""
    with open(os.path.join(_HERE, name), encoding="utf-8") as f:
        lines = [l.rstrip("\n") for l in f if not l.startswith("#")]
    sep = lines.index("")
    pal = {l[0]: C(l[2:9]) for l in lines[:sep]}
    rows = [r for r in lines[sep + 1:] if r]
    cv = Canvas(len(rows[0]), len(rows))
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch != ".":
                cv.set(x, y, pal[ch])
    return cv


def golem_icon():
    """32x32 icon golem: the pickaxe is drawn behind the sprite so the hand holds the handle,
    with the head centred on the handle tip (same pickaxe_head as the big golem)."""
    cv = Canvas(32, 32)
    handle(cv, 20, 23, 7)          # tip at (26, 17)
    pickaxe_head(cv, 27, 16, R=7.5, half_angle=62, thick=2.6)
    sp = load_sprite()
    for y in range(32):
        for x in range(32):
            c = sp.get(x, y)
            if c:
                cv.set(x, y, c)
    return cv


def upscale(src, k):
    cv = Canvas(src.w * k, src.h * k)
    for y in range(cv.h):
        for x in range(cv.w):
            c = src.get(x // k, y // k)
            if c:
                cv.set(x, y, c)
    return cv


# --------------------------------------------------------------------------- outputs
def make_banner(cfg):
    W, H = 266, 76  # x3 = 798 px
    cv = Canvas(W, H)
    pal = B.SKIES[cfg.get("sky", "workshop")]
    seed = cfg.get("seed", 7)
    B.sky(cv, pal, W / 2, 46, 90, seed=seed)
    B.mountains(cv, 70, 22, B.darken(pal[3], 0.85), B.darken(pal[6], 0.8), seed=seed + 4)
    B.mountains(cv, 77, 14, B.darken(pal[1], 0.9), B.darken(pal[3], 0.9), seed=seed - 2, rough=1.4)
    lines = cfg["title"]
    gap = 2
    side = 66  # "AUTOMATION" x2 = 132 px doit tenir entre les golems
    scales = B.fit_title(lines, W - 2 * side)
    top = max(4, (H - B.title_height(scales, gap)) // 2 - 1)
    B.draw_title(cv, lines, W // 2, top, scales, gap)
    g = upscale(golem_icon(), 2)  # même golem que l'icône, pixels 2x comme le titre
    cv.paste(g, 2, H - 2 - g.h)
    cv.paste(g, W - 2 - g.w, H - 2 - g.h, flip=True)
    B.frame(cv)
    return cv


def text_w(lines, scales):
    return max(B.text_width(l["text"], s) for l, s in zip(lines, scales))


def make_header(text, seed):
    cv = B.make_header(text, seed)
    # recolour the purple plate to the workshop slate
    swap = {C("#2b2140"): C("#24303e"), C("#221a35"): C("#1d2733"), C("#1a142a"): C("#171f29"),
            C("#130f20"): C("#11171f"), C("#3a2d55"): C("#2f3e4e")}
    for y in range(cv.h):
        for x in range(cv.w):
            c = cv.p[y][x]
            if c in swap:
                cv.p[y][x] = swap[c]
    return cv


def main():
    cfg_path = sys.argv[1]
    with open(cfg_path, encoding="utf-8") as f:
        cfg = json.load(f)
    out = os.path.dirname(os.path.abspath(cfg_path))
    only = set(sys.argv[sys.argv.index("--only") + 1].split(",")) if "--only" in sys.argv else None
    # header text uses the copper ramp
    gold = B.RAMPS["gold"]
    B.RAMPS["gold"] = B.RAMPS[cfg.get("header_ramp", "copper")]
    jobs = {
        # logo CurseForge/Modrinth : titre + engrenages, carré <= 512 px
        "logo": lambda: {"logo.png": (B.make_logo(cfg), None)},
        # logo du mod dans le jeu (simple_automation_logo.png) : le golem, 256 px
        "modlogo": lambda: {"mod_logo.png": (golem_icon(), 8)},
        "banner": lambda: {"banner.png": make_banner(cfg)},
        "headers": lambda: {f"header_{B.slug(t)}.png": make_header(t, i + 1)
                            for i, t in enumerate(cfg.get("headers", []))},
    }
    try:
        for key, job in jobs.items():
            if only and key not in only:
                continue
            for name, cv in job().items():
                cv, scale = cv if isinstance(cv, tuple) else (cv, None)
                im = cv.image(scale, B.LOGO_MAX if key in ("logo", "modlogo") else B.BANNER_MAX)
                B.save(im, os.path.join(out, name))
                print(f"{name:32s} {im.width}x{im.height}")
    finally:
        B.RAMPS["gold"] = gold


if __name__ == "__main__":
    main()

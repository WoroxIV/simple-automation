"""Simple Automation branding: copper golem + oxidized gear theme.

Reuses the shared generator (~/.claude/skills/mod-branding/branding.py) for fonts, skies, frames
and title layout, and swaps the sword/shield emblem for a copper golem holding a pickaxe.

    python branding/src/sa_branding.py branding/branding.json [--only logo,banner,title,headers]
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.expanduser("~/.claude/skills/mod-branding"))
import branding as B  # noqa: E402
from branding import C, Canvas, pick, N4, N8  # noqa: E402

# --------------------------------------------------------------------------- palette
CU = [C("#f6b08e"), C("#e3826c"), C("#c87456"), C("#a75a40"), C("#7f4130"), C("#5a2e22")]
CU_OUT = C("#2e1710")
OX = [C("#d8f0e8"), C("#8fd0b6"), C("#76b39c"), C("#5a9c84"), C("#3a6e5e"), C("#1b3830")]
OX_OUT = C("#0c1c18")
EYE = C("#1c2626")
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


# --------------------------------------------------------------------------- shapes
def rect(x0, y0, w, h):
    return {(x, y) for x in range(x0, x0 + w) for y in range(y0, y0 + h)}


def box_shade(mask, x, y, ramp=CU):
    """Top-left lit, bottom-right shaded block shading."""
    if (x, y - 1) not in mask:
        return ramp[1]
    if (x - 1, y) not in mask:
        return ramp[1] if y % 3 else ramp[2]
    if (x + 1, y) not in mask or (x, y + 1) not in mask:
        return ramp[4]
    if (x + 2, y) not in mask or (x, y + 2) not in mask:
        return ramp[3]
    return ramp[2]


def gear(cv, cx, cy, r, teeth=8, hole=None):
    hole = r * 0.42 if hole is None else hole
    pts = {}
    for y in range(int(cy - r - 2), int(cy + r + 3)):
        for x in range(int(cx - r - 2), int(cx + r + 3)):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            tooth = math.cos(a * teeth) > 0.25
            rr = r if tooth else r * 0.78
            if d <= rr and d >= hole:
                light = -(dx + dy) / (d + 0.01)
                if d > rr - 1.2:
                    c = OX[1] if light > 0.2 else OX[4]
                elif d < hole + 1.2:
                    c = OX[4] if light > 0.2 else OX[2]
                else:
                    c = pick([OX[1], OX[2], OX[3], OX[4]], 0.5 - light * 0.45, x, y)
                pts[(x, y)] = c
    cv.stamp(set(pts), lambda x, y: pts[(x, y)], OX_OUT)


def pickaxe_head(cv, tx, ty, span=6):
    """Iron head centred on the handle tip (tx, ty), perpendicular to a (1,-1) handle, ends curved down."""
    pts = {}
    for i in range(-span, span + 1):
        k = (i * i) // (2 * span)
        x, y = tx + i - k, ty + i + k
        far = abs(i) >= span - 1
        pts[(x, y)] = IRON[3] if far else IRON[1]
        pts[(x + 1, y)] = IRON[4] if far else IRON[2]
        if abs(i) < span - 2:
            pts[(x + 1, y + 1)] = IRON[3]
        if abs(i) < span - 1:
            pts[(x, y - 1)] = IRON[0] if i < 0 else IRON[1]
    cv.stamp(set(pts), lambda x, y: pts[(x, y)], IRON_OUT)
    for (x, y) in [(tx, ty), (tx + 1, ty), (tx, ty - 1)]:
        cv.set(x, y, IRON[3])


def handle(cv, x0, y0, n):
    h = {}
    for i in range(n):
        h[(x0 + i, y0 - i)] = WOOD[0] if i % 4 else WOOD[1]
        h[(x0 + i + 1, y0 - i)] = WOOD[2]
    cv.stamp(set(h), lambda x, y: h[(x, y)], CU_OUT)


def golem(cv, x0, y0, tool=True, flip=False, rust_seed=1):
    """Copper golem, 30 px wide (+ pickaxe up to 52), 45 px tall. (x0, y0) = top-left of its box."""
    g = Canvas(54, 46)
    ant = rect(14, 4, 3, 9)
    ball = rect(13, 1, 5, 4) - {(13, 1), (17, 1)}
    g.stamp(ant, lambda x, y: CU[1] if x == 14 else CU[3] if x == 16 else CU[2], CU_OUT)
    g.stamp(ball, lambda x, y: box_shade(ball, x, y), CU_OUT)
    body = rect(7, 28, 17, 11)
    legs = rect(8, 38, 6, 6) | rect(17, 38, 6, 6)
    g.stamp(legs, lambda x, y: box_shade(legs, x, y), CU_OUT)
    g.stamp(body, lambda x, y: box_shade(body, x, y), CU_OUT)
    plate = rect(11, 31, 9, 5)
    for (x, y) in plate:
        g.set(x, y, CU[4] if (x, y - 1) not in plate or x == 11 else CU[3])
    for (x, y) in rect(13, 33, 5, 2):
        g.set(x, y, CU[5])
    arm_l = rect(2, 29, 4, 10)
    arm_r = rect(25, 29, 4, 9)
    if tool:
        handle(g, 26, 39, 18)
        pickaxe_head(g, 44, 21, 6)
    g.stamp(arm_l, lambda x, y: box_shade(arm_l, x, y), CU_OUT)
    g.stamp(arm_r, lambda x, y: box_shade(arm_r, x, y), CU_OUT)
    if tool:  # fingers over the handle
        for (x, y) in [(27, 37), (28, 36)]:
            g.set(x, y, WOOD[1])
        g.set(26, 37, CU[3]); g.set(26, 36, CU[4])
    head = rect(1, 12, 29, 15)
    g.stamp(head, lambda x, y: box_shade(head, x, y), CU_OUT)
    for x in range(3, 28):
        g.set(x, 15, CU[4])
    for ex in (4, 18):
        for (x, y) in rect(ex, 17, 8, 5):
            g.set(x, y, EYE)
        g.set(ex + 6, 17, OX[0])
    nose = rect(14, 16, 3, 8)
    for (x, y) in nose:
        g.set(x, y, CU[1] if x == 14 else CU[3] if x < 16 else CU[4])
    g.set(14, 16, CU[0])
    for (x, y) in rect(14, 24, 3, 1):
        g.set(x, y, CU[5])
    rnd = random.Random(rust_seed)
    for (sx, sy) in [(3, 23), (4, 24), (26, 22), (27, 21), (9, 37), (20, 36), (21, 37), (3, 36)]:
        g.set(sx, sy, OX[3] if rnd.random() < 0.6 else OX[2])
    g.set(4, 23, OX[1])
    cv.paste(g, x0, y0, flip=flip)
    return g


# --------------------------------------------------------------------------- outputs
def rounded_bg(cv, cfg):
    N = cv.w
    tmp = Canvas(N, N)
    B.sky(tmp, B.SKIES[cfg.get("sky", "workshop")], N * 0.62, N * 0.4, N * 0.6, 0.05, 0.3, rays=False,
          seed=cfg.get("seed", 3))
    r = 7
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - min(max(x, r), N - 1 - r), y - min(max(y, r), N - 1 - r))
            if d <= r - 0.5:
                edge = d > r - 1.5 or x in (0, N - 1) or y in (0, N - 1)
                edge2 = (d > r - 2.5 or x in (1, N - 2) or y in (1, N - 2)) and not edge
                cv.set(x, y, CU_OUT if edge else CU[3] if edge2 else tmp.get(x, y))


def make_logo(cfg, bg=True):
    N = 60
    cv = Canvas(N, N)
    if bg:
        rounded_bg(cv, cfg)
    gear(cv, 45, 14, 10, teeth=8)
    gear(cv, 48, 47, 7.5, teeth=8, hole=3)
    golem(cv, 4, 8)
    return cv


def make_banner(cfg):
    W, H = 280, 76
    cv = Canvas(W, H)
    pal = B.SKIES[cfg.get("sky", "workshop")]
    seed = cfg.get("seed", 7)
    B.sky(cv, pal, W / 2, 46, 90, seed=seed)
    B.mountains(cv, 70, 22, B.darken(pal[3], 0.85), B.darken(pal[6], 0.8), seed=seed + 4)
    B.mountains(cv, 77, 14, B.darken(pal[1], 0.9), B.darken(pal[3], 0.9), seed=seed - 2, rough=1.4)
    lines = cfg["title"]
    gap = 2
    side = 62
    scales = B.fit_title(lines, W - 2 * side)
    top = max(4, (H - B.title_height(scales, gap)) // 2 - 1)
    B.draw_title(cv, lines, W // 2, top, scales, gap)
    gear(cv, 46, 20, 9, teeth=8)
    gear(cv, W - 47, 20, 9, teeth=8)
    golem(cv, 8, 25, rust_seed=2)
    golem(cv, W - 8 - 54, 25, flip=True, rust_seed=5)
    B.frame(cv)
    return cv


def make_title(cfg):
    lines = cfg["title"]
    gap = 1
    scales = [2] * len(lines)
    side = 26
    W = max(text_w(lines, scales) + 2 * side, 180)
    H = max(42, B.title_height(scales, gap) + 2)
    cv = Canvas(W, H)
    B.draw_title(cv, lines, W // 2, 2, scales, gap)
    gear(cv, 12, H // 2, 10)
    gear(cv, W - 13, H // 2, 10)
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
        "logo": lambda: {"logo.png": make_logo(cfg, True), "logo_transparent.png": make_logo(cfg, False)},
        "banner": lambda: {"banner.png": make_banner(cfg)},
        "title": lambda: {"title_logo.png": make_title(cfg)},
        "headers": lambda: {f"header_{B.slug(t)}.png": make_header(t, i + 1)
                            for i, t in enumerate(cfg.get("headers", []))},
    }
    try:
        for key, job in jobs.items():
            if only and key not in only:
                continue
            for name, cv in job().items():
                im = cv.image()
                B.save(im, os.path.join(out, name))
                print(f"{name:32s} {im.width}x{im.height}")
    finally:
        B.RAMPS["gold"] = gold


if __name__ == "__main__":
    main()

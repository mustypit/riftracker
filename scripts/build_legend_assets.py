#!/usr/bin/env python3
"""Turns Riftcodex legend cards into the app's legend library.

  python3 scripts/build_legend_assets.py legends_raw.json

legends_raw.json: a JSON list of Riftcodex card objects with classification.type == "Legend"
(scripts/fetch-riftcodex-legends.ps1 makes it; Riftcodex refuses the cloud dev machines, so it's
fetched from a home connection). For each champion this picks one legend card, downloads its image
from Riot's CDN, crops the artwork (no frame, name bar or rules text) and writes:

  app/src/main/assets/legends/<id>.jpg     the artwork, ~720 px
  app/src/main/assets/legends/legends.json name, title, set, domains, artist, image

Needs Pillow (pip install pillow). Legends missing from Riftcodex (e.g. a set it hasn't added yet)
stay in the app's built-in list in Legends.kt.
"""
import io, json, re, sys, urllib.request
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
import os
OUT = Path(os.environ.get("LEGENDS_OUT", ROOT / "app/src/main/assets/legends"))
PROMO_SETS = {"OPP", "PR", "JDG"}
DOMAINS = {"Fury", "Calm", "Mind", "Body", "Chaos", "Order"}
# Artwork area of a portrait Riftbound card, as fractions of width/height.
ART_BOX = (0.17, 0.03, 0.97, 0.64)
# Newest first; Riftcodex set codes.
SET_ORDER = ["RAD", "VEN", "UNL", "SFD", "OGN", "OGS"]


def champion(card):
    """'Vi - Piltover Enforcer (Signature)' -> 'Vi'."""
    return card["name"].split(" - ")[0].strip()


def title(card):
    m = re.match(r"^[^-]+ - (.+?)(?: \(.*\))?$", card["name"])
    return m.group(1).strip() if m else ""


def image_id(name):
    """Same rule as Legends.idOf in the app: 'Kai'Sa' -> 'kaisa', 'Lee Sin' -> 'lee_sin'."""
    return re.sub(r" +", "_", "".join(c for c in name.lower() if c.isalpha() or c == " ").strip())


def rank(card):
    """Lower is better: the plain printing from a main set, newest set first."""
    meta = card.get("metadata", {})
    special = sum(bool(meta.get(k)) for k in ("alternate_art", "overnumbered", "signature"))
    promo = card["set"]["set_id"] in PROMO_SETS
    set_pos = SET_ORDER.index(card["set"]["set_id"]) if card["set"]["set_id"] in SET_ORDER else len(SET_ORDER)
    return (promo, special, set_pos)


def pick(cards):
    """One legend card per champion."""
    by_champ = {}
    for c in cards:
        if c.get("classification", {}).get("type") != "Legend":
            continue
        by_champ.setdefault(champion(c), []).append(c)
    return {name: sorted(cs, key=rank)[0] for name, cs in sorted(by_champ.items())}


def art(url):
    req = urllib.request.Request(url, headers={"User-Agent": "RiftTracker legend importer (personal fan app)"})
    with urllib.request.urlopen(req, timeout=60) as r:
        im = Image.open(io.BytesIO(r.read())).convert("RGB")
    w, h = im.size
    x0, y0, x1, y1 = ART_BOX
    im = im.crop((int(w * x0), int(h * y0), int(w * x1), int(h * y1)))
    im.thumbnail((720, 720))
    return im


def main(raw_path):
    cards = json.loads(Path(raw_path).read_text(encoding="utf-8-sig"))
    if isinstance(cards, dict):  # a single card, or a Riftcodex page
        cards = cards.get("items", [cards])
    chosen = pick(cards)
    OUT.mkdir(parents=True, exist_ok=True)
    library = []
    for name, c in chosen.items():
        lid = image_id(name)
        entry = {
            "name": name,
            "title": title(c),
            "set": c["set"]["label"],
            "domains": [d for d in c["classification"].get("domain", []) if d in DOMAINS],
            "artist": c.get("media", {}).get("artist"),
            "image": None,
        }
        url = c.get("media", {}).get("image_url")
        if url and c.get("orientation", "portrait") == "portrait":
            try:
                art(url).save(OUT / f"{lid}.jpg", quality=82, optimize=True)
                entry["image"] = f"{lid}.jpg"
            except Exception as e:  # keep going; the app falls back to a colour tile
                print(f"  no art for {name}: {e}")
        library.append(entry)
        print(f"{name:14} {entry['set']:12} {'/'.join(entry['domains']):12} {entry['image'] or '-'}")
    (OUT / "legends.json").write_text(json.dumps(library, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{len(library)} legends -> {OUT}/legends.json")


if __name__ == "__main__":
    main(sys.argv[1])

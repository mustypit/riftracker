#!/usr/bin/env python3
"""Turns Riftcodex legend and battlefield cards into the app's libraries.

  python3 scripts/build_legend_assets.py --fetch            # download from Riftcodex (CI does this)
  python3 scripts/build_legend_assets.py legends_raw.json   # use a saved list

The saved list is a JSON list of Riftcodex card objects with classification.type == "Legend"
(scripts/fetch-riftcodex-legends.ps1 makes one at home; Riftcodex refuses the cloud dev machines).
The weekly GitHub workflow update-legends.yml runs --fetch. For each champion this picks one legend
card, downloads its image from Riot's CDN, crops the artwork (no frame, name bar or rules text) and
writes:

  app/src/main/assets/legends/<id>.jpg     the artwork, ~720 px
  app/src/main/assets/legends/legends.json name, title, set, domains, artist, image
  app/src/main/assets/battlefields/<id>.jpg + battlefields.json (name, set, artist, image)

Needs Pillow (pip install pillow). Legends missing from Riftcodex (e.g. a set it hasn't added yet)
stay in the app's built-in list in Legends.kt.
"""
import io, json, re, sys, urllib.request
from pathlib import Path

from PIL import Image

import os, time

ROOT = Path(__file__).resolve().parent.parent
OUT = Path(os.environ.get("LEGENDS_OUT", ROOT / "app/src/main/assets/legends"))
API = "https://api.riftcodex.com/cards"
UA = "RiftTracker legend importer (personal fan app; github.com/mustypit/riftracker)"
PROMO_SETS = {"OPP", "PR", "JDG"}
DOMAINS = {"Fury", "Calm", "Mind", "Body", "Chaos", "Order"}
# Artwork area of a portrait Riftbound card, as fractions of width/height.
ART_BOX = (0.17, 0.03, 0.97, 0.64)
# Newest first; Riftcodex set codes.
SET_ORDER = ["RAD", "VEN", "UNL", "SFD", "OGN", "OGS"]


def base_name(card):
    """Card name without a trailing '(Signature)' / '(Alternate Art)'."""
    return re.sub(r"\s*\([^)]*\)\s*$", "", card["name"]).strip()


def title(card):
    """'Vi - Piltover Enforcer (Signature)' -> 'Piltover Enforcer'; a title-only name is the title."""
    name = base_name(card)
    return name.split(" - ", 1)[1].strip() if " - " in name else name


def champion(card, champions, by_title):
    """
    The champion a legend card belongs to. Usually the part before ' - ' ('Vi - Piltover Enforcer');
    'Yordle, Kennen - …' -> the part that's also a tag (Kennen). Some printings carry only the title
    ('Master of Shadows'): then the champion tag, or the champion of another printing with that title.
    """
    name, tags = base_name(card), card.get("tags") or []
    if " - " in name:
        parts = [p.strip() for p in name.split(" - ", 1)[0].split(",")]
        return next((p for p in reversed(parts) if p in tags), parts[-1])
    return next((t for t in tags if t in champions), by_title.get(name.lower(), name))


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
    legends = [c for c in cards if c.get("classification", {}).get("type") == "Legend"]
    # First pass: champions and titles from the cards named 'Champion - Title'.
    named = [c for c in legends if " - " in base_name(c)]
    champions = {champion(c, set(), {}) for c in named}
    by_title = {title(c).lower(): champion(c, set(), {}) for c in named}
    by_champ = {}
    for c in legends:
        by_champ.setdefault(champion(c, champions, by_title), []).append(c)
    return {name: sorted(cs, key=rank)[0] for name, cs in sorted(by_champ.items())}


def get(url, tries=5):
    """GET with a few retries (both Riftcodex and the CDN occasionally hiccup)."""
    for i in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=60) as r:
                return r.read()
        except Exception as e:
            if i == tries - 1:
                raise
            print(f"  retry {i + 1} for {url}: {e}")
            time.sleep(3 * (i + 1))


def fetch_cards(types=("Legend", "Battlefield")):
    """Every legend and battlefield card on Riftcodex, page by page (100 cards per page)."""
    cards, page, pages = [], 1, 1
    while page <= pages:
        data = json.loads(get(f"{API}?size=100&page={page}"))
        pages = data.get("pages", 1)
        cards += [c for c in data.get("items", []) if c.get("classification", {}).get("type") in types]
        print(f"page {page} of {pages}: {len(cards)} legend/battlefield cards so far")
        page += 1
    return cards


def art(url, box=ART_BOX):
    im = Image.open(io.BytesIO(get(url))).convert("RGB")
    w, h = im.size
    x0, y0, x1, y1 = box
    im = im.crop((int(w * x0), int(h * y0), int(w * x1), int(h * y1)))
    im.thumbnail((720, 720))
    return im


MIN_LEGENDS = 20  # fewer than this from --fetch means something's off: change nothing
BF_OUT = OUT.parent / "battlefields"
# Artwork band of a landscape battlefield card: between the upside-down rules text and the name.
BF_ART_BOX = (0.04, 0.21, 0.96, 0.60)


def pick_battlefields(cards):
    """One battlefield card per name: the plain printing from a main set, newest set first."""
    by_name = {}
    for c in cards:
        if c.get("classification", {}).get("type") == "Battlefield":
            by_name.setdefault(base_name(c), []).append(c)
    return {name: sorted(cs, key=rank)[0] for name, cs in sorted(by_name.items())}


def write_index(folder, library):
    """Writes <folder>/<name>.json and removes art this script made last time that nothing uses now.
    Images never listed in the index (added by hand, e.g. for a set Riftcodex lacks) stay."""
    index = folder / f"{folder.name}.json"
    if index.exists():
        before = {e.get("image") for e in json.loads(index.read_text(encoding="utf-8")) if e.get("image")}
        for stale in before - {e["image"] for e in library if e["image"]}:
            (folder / stale).unlink(missing_ok=True)
            print(f"  removed {stale}")
    index.write_text(json.dumps(library, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{len(library)} -> {index}")


def save_art(folder, file_id, url, box):
    try:
        art(url, box).save(folder / f"{file_id}.jpg", quality=82, optimize=True)
        return f"{file_id}.jpg"
    except Exception as e:  # keep going; the app falls back to a colour tile
        print(f"  no art for {file_id}: {e}")
        return None


def main(source):
    if source == "--fetch":
        cards = fetch_cards()
        if len(pick(cards)) < MIN_LEGENDS:
            sys.exit(f"Only {len(pick(cards))} legends from Riftcodex; not touching the app's data.")
    else:
        cards = json.loads(Path(source).read_text(encoding="utf-8-sig"))
        if isinstance(cards, dict):  # a single card, or a Riftcodex page
            cards = cards.get("items", [cards])

    # Legends (skipped when the input has none, so a battlefields-only file doesn't wipe them)
    OUT.mkdir(parents=True, exist_ok=True)
    library = []
    for name, c in pick(cards).items():
        url = c.get("media", {}).get("image_url")
        image = save_art(OUT, image_id(name), url, ART_BOX) if url and c.get("orientation", "portrait") == "portrait" else None
        entry = {
            "name": name,
            "title": title(c),
            "set": c["set"]["label"],
            "domains": [d for d in c["classification"].get("domain", []) if d in DOMAINS],
            "artist": c.get("media", {}).get("artist"),
            "image": image,
        }
        library.append(entry)
        print(f"{name:14} {entry['set']:12} {'/'.join(entry['domains']):12} {entry['image'] or '-'}")
    if library:
        write_index(OUT, library)

    # Battlefields (skipped when the input has none, so a legends-only file doesn't wipe them)
    battlefields = pick_battlefields(cards)
    if battlefields:
        BF_OUT.mkdir(parents=True, exist_ok=True)
        bf_library = []
        for name, c in battlefields.items():
            url = c.get("media", {}).get("image_url")
            image = save_art(BF_OUT, image_id(name), url, BF_ART_BOX) if url and c.get("orientation") == "landscape" else None
            bf_library.append({"name": name, "set": c["set"]["label"], "artist": c.get("media", {}).get("artist"), "image": image})
            print(f"  battlefield {name:30} {c['set']['label']:12} {image or '-'}")
        write_index(BF_OUT, bf_library)


if __name__ == "__main__":
    main(sys.argv[1])

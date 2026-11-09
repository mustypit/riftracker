#!/usr/bin/env python3
"""Turns OpenRift's card catalog into the app's legend and battlefield libraries.

  python3 scripts/build_legend_assets.py --fetch        # download the catalog (CI does this)
  python3 scripts/build_legend_assets.py catalog.json   # use a saved catalog

OpenRift (openrift.app, free, no key) has every set, Radiance included. The cloud dev machines
can't reach it, so the weekly GitHub workflow update-legends.yml runs --fetch. For each champion
(and each battlefield) this picks the card's original normal printing: no overnumbered, alternate
art, signed, promo-stamped or special-distribution versions; the earliest English release; a main
set over a supplemental one released the same day. It downloads that image, crops the artwork (no
frame, name bar or rules text) and writes:

  app/src/main/assets/legends/<id>.jpg     the artwork, ~720 px
  app/src/main/assets/legends/legends.json name, title, set, domains, artist, image
  app/src/main/assets/battlefields/<id>.jpg + battlefields.json (name, set, artist, image)
  app/src/main/assets/cards/<slug>.webp + cards.json: every card (the card library), whole card

Catalog: GET https://openrift.app/api/v1/catalog -> {sets: [...], cards: {id: card}, printings:
{id: printing}, tagCategoryMap: {tag: category}, ...}. A card has name, types (["legend"],
["battlefield"], ...), domains, tags (the champion is the tag whose category is "legend"). A printing
has cardId, setId, language, artVariant, isOvernumbered, isSigned, markers, distributionChannels,
finish, size, artist, canonicalRank and images[0].imageId; the image is
https://openrift.app/media/cards/<imageId>-full.webp.

Needs Pillow (pip install pillow).
"""
import io, json, os, re, sys, time, urllib.request
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = Path(os.environ.get("LEGENDS_OUT", ROOT / "app/src/main/assets/legends"))
BF_OUT = OUT.parent / "battlefields"
CATALOG = "https://openrift.app/api/v1/catalog"
IMAGE = "https://openrift.app/media/cards/{}-full.webp"
UA = "RiftTracker legend importer (personal fan app; github.com/mustypit/riftracker)"
DOMAINS = {"fury", "calm", "mind", "body", "chaos", "order"}
# Artwork area of a portrait card, and of a landscape battlefield (the band between the upside-down
# rules text and the name), as fractions of width/height.
ART_BOX = (0.17, 0.03, 0.97, 0.64)
BF_ART_BOX = (0.04, 0.21, 0.96, 0.60)
MIN_LEGENDS = 20  # fewer than this means something's off: change nothing


def clean(s):
    """Straight apostrophes, as the app's names use ('K'Sante', 'Ornn's Forge')."""
    return s.replace("’", "'").strip()


def image_id(name):
    """Same rule as Legends.idOf in the app: 'Kai'Sa' -> 'kaisa', 'Lee Sin' -> 'lee_sin'."""
    return re.sub(r" +", "_", "".join(c for c in name.lower() if c.isalpha() or c == " ").strip())


def get(url, tries=5):
    for i in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=90) as r:
                return r.read()
        except Exception as e:
            if i == tries - 1:
                raise
            print(f"  retry {i + 1} for {url}: {e}")
            time.sleep(3 * (i + 1))


def as_list(d):
    """cards/printings come as {id: obj}; give each obj its id."""
    return [dict(v, id=v.get("id", k)) for k, v in d.items()] if isinstance(d, dict) else list(d)


def rank(p, sets):
    """Lower is better: the original normal printing."""
    s = sets.get(p.get("setId"), {})
    en = ((s.get("releases") or {}).get("EN") or {}).get("releasedAt")
    special = (
        bool(p.get("isOvernumbered")), p.get("artVariant", "normal") != "normal", bool(p.get("isSigned")),
        bool(p.get("markers")), bool(p.get("distributionChannels")), p.get("size", "standard") != "standard",
    )
    return (
        sum(special), p.get("language") != "EN", en is None, en or "", s.get("setType") != "main",
        p.get("finish") != "normal", p.get("canonicalRank") or 0, p.get("shortCode") or "",
    )


def champion(card, tag_map):
    tags = card.get("tags") or []
    return clean(next((t for t in tags if tag_map.get(t) == "legend"), tags[0] if tags else card["name"]))


def pick(catalog, kind):
    """{name: (card, printing, set)} with one printing per champion ('legend') or battlefield."""
    sets = {s["id"]: s for s in catalog["sets"]}
    cards = {c["id"]: c for c in as_list(catalog["cards"])}
    by_name = {}
    for p in as_list(catalog["printings"]):
        c = cards.get(p.get("cardId"))
        if not c or c.get("types") != [kind] or not p.get("images"):
            continue
        name = champion(c, catalog.get("tagCategoryMap", {})) if kind == "legend" else clean(c["name"])
        by_name.setdefault(name, []).append((c, p))
    out = {}
    for name, options in sorted(by_name.items()):
        c, p = min(options, key=lambda cp: rank(cp[1], sets))
        out[name] = (c, p, sets.get(p.get("setId"), {}))
    return out


def save_art(folder, file_id, image_id_, box, landscape):
    try:
        im = Image.open(io.BytesIO(get(IMAGE.format(image_id_)))).convert("RGB")
        w, h = im.size
        if (w > h) != landscape:
            print(f"  no art for {file_id}: unexpected orientation {w}x{h}")
            return None
        x0, y0, x1, y1 = box
        im = im.crop((int(w * x0), int(h * y0), int(w * x1), int(h * y1)))
        im.thumbnail((720, 720))
        im.save(folder / f"{file_id}.jpg", quality=82, optimize=True)
        return f"{file_id}.jpg"
    except Exception as e:  # keep going; the app falls back to a colour tile
        print(f"  no art for {file_id}: {e}")
        return None


def write_index(folder, library):
    """Writes <folder>/<folder>.json and removes art this script made last time that nothing uses now."""
    index = folder / f"{folder.name}.json"
    if index.exists():
        before = {e.get("image") for e in json.loads(index.read_text(encoding="utf-8")) if e.get("image")}
        for stale in before - {e["image"] for e in library if e["image"]}:
            (folder / stale).unlink(missing_ok=True)
            print(f"  removed {stale}")
    index.write_text(json.dumps(library, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{len(library)} -> {index}")


def main(source):
    catalog = json.loads(get(CATALOG) if source == "--fetch" else Path(source).read_bytes())
    legends = pick(catalog, "legend")
    if len(legends) < MIN_LEGENDS:
        sys.exit(f"Only {len(legends)} legends from OpenRift; not touching the app's data.")

    OUT.mkdir(parents=True, exist_ok=True)
    library = []
    for name, (c, p, s) in legends.items():
        image = save_art(OUT, image_id(name), p["images"][0]["imageId"], ART_BOX, landscape=False)
        library.append({
            "name": name, "title": clean(c["name"]), "set": s.get("name", ""),
            "domains": [d for d in c.get("domains", []) if d in DOMAINS],
            "artist": p.get("artist"), "image": image,
        })
        print(f"{name:14} {p.get('shortCode', ''):10} {s.get('name', ''):16} {'/'.join(library[-1]['domains']):12} {image or '-'}")
    write_index(OUT, library)

    battlefields = pick(catalog, "battlefield")
    if battlefields:
        BF_OUT.mkdir(parents=True, exist_ok=True)
        bf_library = []
        for name, (c, p, s) in battlefields.items():
            image = save_art(BF_OUT, image_id(name), p["images"][0]["imageId"], BF_ART_BOX, landscape=True)
            bf_library.append({"name": name, "set": s.get("name", ""), "artist": p.get("artist"), "image": image})
            print(f"  battlefield {name:30} {p.get('shortCode', ''):10} {s.get('name', ''):16} {image or '-'}")
        write_index(BF_OUT, bf_library)

    write_cards(catalog)


CARDS_OUT = OUT.parent / "cards"
CARD_WIDTH = 400  # px; the whole card (frame and text), for the card library
TOKEN = re.compile(r":rb_[a-z0-9_]+:")


def write_cards(catalog):
    """
    The card library: every card's original normal printing as assets/cards/cards.json, and its
    whole-card image as assets/cards/<slug>.webp. CARD_IMAGES_LIMIT=n (env) saves images for an
    even sample of n cards only (for trying things out).
    """
    sets = {s["id"]: s for s in catalog["sets"]}
    cards = {c["id"]: c for c in as_list(catalog["cards"])}
    best = {}
    for p in as_list(catalog["printings"]):
        c = cards.get(p.get("cardId"))
        if not c or not p.get("images"):
            continue
        if c["id"] not in best or rank(p, sets) < rank(best[c["id"]], sets):
            best[c["id"]] = p
    library, tokens = [], {}
    for cid, p in best.items():
        c, s = cards[cid], sets.get(p.get("setId"), {})
        text = p.get("printedRulesText") or ""
        for t in TOKEN.findall(text):
            tokens[t] = tokens.get(t, 0) + 1
        library.append({
            "slug": c["slug"], "name": clean(c["name"]), "type": (c.get("types") or ["other"])[0],
            "superTypes": c.get("superTypes") or [], "domains": c.get("domains") or [],
            "energy": c.get("energy"), "power": c.get("power"), "might": c.get("might"),
            "keywords": c.get("keywords") or [], "tags": [clean(t) for t in c.get("tags") or []],
            "text": text, "set": s.get("name", ""), "code": p.get("shortCode"), "rarity": p.get("rarity"),
            "artist": p.get("artist"), "maxCopies": c.get("maxCopiesOverride"), "banned": bool(c.get("bans")),
            "imageId": p["images"][0]["imageId"],
        })
    library.sort(key=lambda e: (e["set"], e["code"] or "", e["name"]))
    limit = int(os.environ.get("CARD_IMAGES_LIMIT", "0") or 0)
    chosen = library if not limit else library[:: max(1, len(library) // limit)][:limit]
    CARDS_OUT.mkdir(parents=True, exist_ok=True)
    for e in chosen:
        try:
            im = Image.open(io.BytesIO(get(IMAGE.format(e["imageId"])))).convert("RGB")
            if im.width > im.height:  # landscape battlefields: keep them landscape, same width budget
                im.thumbnail((CARD_WIDTH * 1.4, CARD_WIDTH))
            else:
                im.thumbnail((CARD_WIDTH, CARD_WIDTH * 1.4))
            im.save(CARDS_OUT / f"{e['slug']}.webp", quality=78, method=6)
            e["image"] = f"{e['slug']}.webp"
        except Exception as ex:
            print(f"  no image for {e['slug']}: {ex}")
    for e in library:
        e.setdefault("image", None)
        del e["imageId"]
    (CARDS_OUT / "cards.json").write_text(json.dumps(library, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    by_type = {}
    for e in library:
        by_type[e["type"]] = by_type.get(e["type"], 0) + 1
    print(f"{len(library)} cards -> {CARDS_OUT / 'cards.json'}, {sum(1 for e in library if e['image'])} images; by type {by_type}")
    print("rules-text symbols:", json.dumps(dict(sorted(tokens.items(), key=lambda kv: -kv[1]))))
    print("enums:", json.dumps({k: [x.get("slug") for x in v] if isinstance(v, list) else v for k, v in catalog.get("enums", {}).items()})[:3000])


if __name__ == "__main__":
    main(sys.argv[1])

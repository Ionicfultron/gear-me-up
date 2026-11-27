"""Download a small picture of each boss from the OSRS Wiki into the plugin's resources.

Usage (from tools/, needs Pillow):  uv run --with pillow python -I fetch_images.py research/npc_stats.json ../src/main/resources/com/gearmeup/npc

Each image is fitted into BOX x BOX pixels and saved as an optimised palette PNG named after the boss
(see slug()). Images already present are skipped unless --force is given.
"""
import io
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

from PIL import Image

API = "https://oldschool.runescape.wiki/api.php"
UA = "GearMeUpPlugin/1.0 (RuneLite plugin data build)"
BOX = 80


def slug(name):
    """Must match Boss.imageSlug() in the plugin."""
    return re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read()


def thumb_url(file_title):
    q = urllib.parse.urlencode({"action": "query", "format": "json", "titles": file_title, "prop": "imageinfo",
                                "iiprop": "url", "iiurlheight": BOX * 2})
    pages = json.loads(get(API + "?" + q))["query"]["pages"]
    info = next(iter(pages.values())).get("imageinfo")
    return info[0].get("thumburl") or info[0]["url"] if info else None


def main():
    stats = json.load(open(sys.argv[1], encoding="utf-8"))
    out_dir = sys.argv[2]
    force = "--force" in sys.argv
    os.makedirs(out_dir, exist_ok=True)
    total = 0
    for name, s in stats.items():
        path = os.path.join(out_dir, slug(name) + ".png")
        if not s.get("image") or (os.path.exists(path) and not force):
            continue
        url = thumb_url(s["image"])
        time.sleep(0.3)
        if not url:
            print("no image for", name)
            continue
        img = Image.open(io.BytesIO(get(url))).convert("RGBA")
        bbox = img.getbbox()
        if bbox:
            img = img.crop(bbox)
        img.thumbnail((BOX, BOX), Image.LANCZOS)
        img = img.quantize(colors=64, method=Image.Quantize.FASTOCTREE)
        img.save(path, optimize=True)
        total += os.path.getsize(path)
        print(name, img.size, os.path.getsize(path))
    print("done, %d KB written" % (total // 1024))


main()

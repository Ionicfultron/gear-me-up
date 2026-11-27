"""Fetch combat stats for every boss in bosses.json from the OSRS Wiki (infobox_monster bucket).

Usage (from tools/):  python -I fetch_stats.py ../src/main/resources/com/gearmeup/bosses.json research/npc_stats.json [--list]

Writes one entry per boss: the wiki page/version used plus hitpoints, defence and magic levels,
defence bonuses, size, attributes and elemental weakness. --list prints every version found so the
TARGETS table below can be tuned.
"""
import json
import sys
import time
import urllib.parse
import urllib.request

API = "https://oldschool.runescape.wiki/api.php"
UA = "GearMeUpPlugin/1.0 (RuneLite plugin data build)"
FIELDS = ["page_name", "page_name_sub", "name", "version_anchor", "default_version", "id", "hitpoints",
          "defence_level", "magic_level", "stab_defence_bonus", "slash_defence_bonus", "crush_defence_bonus",
          "magic_defence_bonus", "light_range_defence_bonus", "standard_range_defence_bonus",
          "heavy_range_defence_bonus", "attribute", "size", "elemental_weakness", "elemental_weakness_percent",
          "image", "flat_armour"]

# Boss name in the plugin -> (wiki page, version to prefer (substring of version_anchor) or None, label shown
# in the plugin when the stats are for a representative NPC rather than the boss itself).
TARGETS = {
    "Eclipse Moon": ("Eclipse Moon", "Regular", None),
    "The Hueycoatl": ("The Hueycoatl", "Normal", None),
    "Amoxliatl": ("Amoxliatl", "Post-quest", None),
    "Araxxor": ("Araxxor", "In combat", None),
    "Yama": ("Yama", "Normal", None),
    "Doom of Mokhaiotl": ("Doom of Mokhaiotl", "Delve 1", "vs delve 1"),
    "Scurrius": ("Scurrius", "Solo", None),
    "Brutus": ("Brutus", "Post-quest", None),
    "Demonic Brutus": ("Demonic Brutus", "Brutus", None),
    "Shellbane gryphon": ("Shellbane gryphon", "Post-quest", None),
    "Archive Guardian": ("Archive Guardian", "Flying", "vs flying form"),
    "Ocellus": ("Ocellus", "Phase 1", "vs phase 1"),
    "Vet'ion": ("Vet'ion", "Normal", None),
    "Calvar'ion": ("Calvar'ion", "Normal", None),
    "Abyssal Sire": ("Abyssal Sire", "Phase 2", "vs phase 2"),
    "Alchemical Hydra": ("Alchemical Hydra", "Serpentine", "vs green phase"),
    "Dusk": ("Dusk", "Second form", "vs second form"),
    "Kraken": ("Kraken", "Kraken", None),
    "Zulrah": ("Zulrah", "Serpentine", "vs green form"),
    "Kalphite Queen": ("Kalphite Queen", "Airborne", "vs phase 2"),
    "Duke Sucellus": ("Duke Sucellus", "Post-quest, Awake", None),
    "The Leviathan": ("The Leviathan", "Post-quest", None),
    "Vardorvis": ("Vardorvis", "Post-quest", None),
    "The Whisperer": ("The Whisperer", "Post-quest", None),
    "Phantom Muspah": ("Phantom Muspah", "Ranged", "vs ranged form"),
    "TzKal-Zuk": ("TzKal-Zuk", "Normal", None),
    "Chambers of Xeric": ("Great Olm", "Head (Normal)", "vs Great Olm (head)"),
    "Theatre of Blood": ("Verzik Vitur", "Normal mode, Phase 3", "vs Verzik (phase 3)"),
    "Tombs of Amascut": ("Tumeken's Warden", "Active", "vs Tumeken's Warden"),
    "Great Olm": ("Great Olm", "Head (Normal)", "vs the head"),
    "Verzik Vitur": ("Verzik Vitur", "Normal mode, Phase 3", "vs phase 3"),
    "Tumeken's Warden / Elidinis' Warden": ("Tumeken's Warden", "Active", "vs Tumeken's Warden"),
    "Zebak": ("Zebak", "Normal", None),
    "Kephri": ("Kephri", "Aggressive", None),
    "Tormented Demon": ("Tormented Demon", None, None),
}
SKIP = {"Any monster (generic)"}
# Values the wiki gives as a range (so the bucket stores nothing). Starting values are used.
OVERRIDES = {
    "Vardorvis": {"defenceLevel": 215},
}


def query(page):
    q = "bucket('infobox_monster').select(%s).where('page_name','%s').limit(20).run()" % (
        ",".join("'%s'" % f for f in FIELDS), page.replace("'", "\\'"))
    url = API + "?" + urllib.parse.urlencode({"action": "bucket", "format": "json", "query": q})
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        data = json.load(r)
    if "error" in data:
        raise RuntimeError(data["error"])
    return data.get("bucket", [])


def choose(rows, prefer):
    if prefer:
        for r in rows:
            if prefer.lower() in (r.get("page_name_sub") or "").lower():
                return r
    for r in rows:
        if "default_version" in r:
            return r
    return rows[0] if rows else None


def to_int(v):
    try:
        return int(v)
    except (TypeError, ValueError):
        return 0


def main():
    bosses = json.load(open(sys.argv[1], encoding="utf-8"))["bosses"]
    listing = "--list" in sys.argv
    out, missing = {}, []
    for b in bosses:
        name = b["name"]
        if name in SKIP:
            continue
        page, prefer, label = TARGETS.get(name, (name, None, None))
        rows = query(page)
        time.sleep(0.3)
        if listing:
            print(name, "->", page, [r.get("version_anchor") for r in rows])
        r = choose(rows, prefer)
        if not r:
            missing.append(name)
            continue
        out[name] = {
            "page": page,
            "version": r.get("version_anchor") or "",
            "label": label,
            "ids": [to_int(i) for i in (r.get("id") or []) if str(i).strip().isdigit()],
            "image": (r.get("image") or [None])[0],
            "hitpoints": to_int(r.get("hitpoints")),
            "defenceLevel": to_int(r.get("defence_level")),
            "magicLevel": to_int(r.get("magic_level")),
            "stab": to_int(r.get("stab_defence_bonus")),
            "slash": to_int(r.get("slash_defence_bonus")),
            "crush": to_int(r.get("crush_defence_bonus")),
            "magic": to_int(r.get("magic_defence_bonus")),
            "rangedLight": to_int(r.get("light_range_defence_bonus")),
            "rangedStandard": to_int(r.get("standard_range_defence_bonus")),
            "rangedHeavy": to_int(r.get("heavy_range_defence_bonus")),
            "flatArmour": to_int(r.get("flat_armour")),
            "size": to_int(r.get("size")) or 1,
            "attributes": [a.lower() for a in (r.get("attribute") or [])],
            "elementalWeakness": (r.get("elemental_weakness") or "").lower() or None,
            "elementalWeaknessPercent": to_int(r.get("elemental_weakness_percent")),
        }
        out[name].update(OVERRIDES.get(name, {}))
    with open(sys.argv[2], "w", encoding="utf-8") as f:
        json.dump(out, f, indent=1, ensure_ascii=False)
    print(len(out), "bosses with stats; missing:", missing)


main()

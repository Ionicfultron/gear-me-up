"""Converts the wiki research files into the plugin's bosses.json.

usage (from the tools folder):
  python -I convert.py knowledge.json ../src/main/resources/com/gearmeup/bosses.json research/*.json
"""
import json, re, sys

CATEGORY = {}
def cat(name, *bosses):
    for b in bosses:
        CATEGORY[b.lower()] = name

cat("Slayer bosses", "Abyssal Sire", "Alchemical Hydra", "Cerberus", "Dusk", "Dawn", "Kraken",
    "Thermonuclear smoke devil", "Skotizo", "Araxxor", "Shellbane gryphon", "Tormented Demon")
cat("God Wars Dungeon", "General Graardor", "Commander Zilyana", "Kree'arra", "K'ril Tsutsaroth", "Nex")
cat("Wilderness", "Callisto", "Artio", "Vet'ion", "Calvar'ion", "Venenatis", "Spindel", "Chaos Elemental",
    "Chaos Fanatic", "Crazy archaeologist", "Scorpia", "King Black Dragon", "Revenant maledictus")
cat("Classic bosses", "Vorkath", "Zulrah", "Corporeal Beast", "Dagannoth Rex", "Dagannoth Prime", "Dagannoth Supreme",
    "Kalphite Queen", "Giant Mole", "Sarachnis", "The Nightmare", "Phosani's Nightmare", "Obor", "Bryophyta",
    "Hespori", "Deranged archaeologist", "The Mimic", "Scurrius")
cat("Barrows", "Ahrim the Blighted", "Karil the Tainted", "Dharok the Wretched", "Guthan the Infested",
    "Torag the Corrupted", "Verac the Defiled")
cat("Desert Treasure II", "Duke Sucellus", "The Leviathan", "Vardorvis", "The Whisperer")
cat("Varlamore", "Blue Moon", "Eclipse Moon", "Blood Moon", "Sol Heredit", "The Hueycoatl", "Amoxliatl",
    "Eldric the Ice King", "Branda the Fire Queen", "Yama", "Doom of Mokhaiotl")
cat("Quest & other", "Phantom Muspah", "Brutus", "Demonic Brutus")
cat("Minigames", "TzTok-Jad", "TzKal-Zuk")
cat("Raids", "Chambers of Xeric", "Theatre of Blood", "Tombs of Amascut")
cat("Raid bosses", "Great Olm", "Verzik Vitur", "Tumeken's Warden / Elidinis' Warden", "Akkha", "Zebak", "Kephri", "Ba-Ba")
cat("Fractured Archive", "Archive Guardian", "Ezekiel", "Bork", "Moia", "Dreadnoth the Jellied", "Dagon'hai Elder",
    "Xarth", "Ocellus")

# Common nicknames so the panel search finds bosses by what players call them.
ALIASES = {
    "Kalphite Queen": ["kq", "kalphite"], "King Black Dragon": ["kbd"], "Corporeal Beast": ["corp"],
    "Dagannoth Rex": ["dks", "dk", "kings"], "Dagannoth Prime": ["dks", "dk", "kings"], "Dagannoth Supreme": ["dks", "dk", "kings"],
    "Dusk": ["gg", "grotesque guardians", "gargoyle"], "Dawn": ["gg", "grotesque guardians", "gargoyle"],
    "Thermonuclear smoke devil": ["thermy", "smoke devil"], "Cerberus": ["cerb", "hellhound"],
    "Alchemical Hydra": ["hydra", "alchy"], "Abyssal Sire": ["sire"], "Vorkath": ["vork"], "Zulrah": ["zul", "snake"],
    "General Graardor": ["bandos", "graardor"], "Commander Zilyana": ["sara", "saradomin", "zily"],
    "Kree'arra": ["arma", "armadyl", "kree"], "K'ril Tsutsaroth": ["zammy", "zamorak", "kril"], "Nex": ["zaros"],
    "TzTok-Jad": ["jad", "fight caves", "fire cape"], "TzKal-Zuk": ["zuk", "inferno", "infernal cape"],
    "Chambers of Xeric": ["cox", "raids 1"], "Theatre of Blood": ["tob", "raids 2"], "Tombs of Amascut": ["toa", "raids 3"],
    "Great Olm": ["olm", "cox"], "Verzik Vitur": ["verzik", "tob"], "Tumeken's Warden / Elidinis' Warden": ["wardens", "toa"],
    "Akkha": ["toa"], "Zebak": ["toa"], "Kephri": ["toa"], "Ba-Ba": ["baba", "toa"],
    "The Nightmare": ["nm", "nightmare"], "Phosani's Nightmare": ["pnm", "phosani"], "Phantom Muspah": ["muspah"],
    "Duke Sucellus": ["duke", "dt2"], "The Leviathan": ["levi", "dt2"], "Vardorvis": ["vard", "dt2"], "The Whisperer": ["whisp", "dt2"],
    "Vet'ion": ["vetion"], "Calvar'ion": ["calvarion"], "Revenant maledictus": ["rev", "revenant"], "Chaos Elemental": ["chaos ele"],
    "Crazy archaeologist": ["crazy arch"], "Deranged archaeologist": ["deranged arch"], "Giant Mole": ["mole"],
    "Sol Heredit": ["colosseum", "fortis", "sol"], "Blue Moon": ["moons", "perilous moons"], "Eclipse Moon": ["moons", "perilous moons"],
    "Blood Moon": ["moons", "perilous moons"], "The Hueycoatl": ["huey"], "Eldric the Ice King": ["royal titans", "titans"],
    "Branda the Fire Queen": ["royal titans", "titans"], "Doom of Mokhaiotl": ["doom", "delve"], "Tormented Demon": ["td", "tds"],
    "Ahrim the Blighted": ["barrows"], "Karil the Tainted": ["barrows"], "Dharok the Wretched": ["barrows"],
    "Guthan the Infested": ["barrows"], "Torag the Corrupted": ["barrows"], "Verac the Defiled": ["barrows"],
    "Archive Guardian": ["fractured archive"], "Ezekiel": ["fractured archive"], "Bork": ["fractured archive"],
    "Moia": ["fractured archive"], "Dreadnoth the Jellied": ["fractured archive"], "Dagon'hai Elder": ["fractured archive"],
    "Xarth": ["fractured archive"], "Ocellus": ["fractured archive"],
}

SLOTS = {"head", "cape", "amulet", "weapon", "body", "shield", "legs", "gloves", "boots", "ring", "ammo"}
STYLE_IMMUNITY = re.compile(r"^(melee|ranged|magic)")
# Handled elsewhere (slayer checkbox) or only good as a full set, so never a plain per-slot preference.
DROP_FROM_PREFERRED = re.compile(r"slayer helmet|black mask|void (knight|ranger|mage|melee)|elite void", re.I)

RENAMES = {"ascension crossbows": "Ascension crossbow"}

def clean_list(items):
    out, seen = [], set()
    for i in items or []:
        if not i or not isinstance(i, str):
            continue
        i = RENAMES.get(i.strip().lower(), i)
        k = i.strip().lower()
        if k not in seen:
            seen.add(k)
            out.append(i.strip())
    return out

# Plugin Hub rules forbid combat prayer recommendations, so tips never say which prayer to use.
PRAYER_ADVICE = re.compile(r"\bpray\b|switch prayer|pray(ing)? against", re.I)

def strip_prayer_advice(text):
    sentences = re.split(r"(?<=\.)\s+", text.strip())
    kept = []
    for sentence in sentences:
        if not PRAYER_ADVICE.search(sentence):
            kept.append(sentence)
            continue
        body = sentence.rstrip(".")
        # Drop only the clauses that give prayer advice, keeping the rest of the sentence.
        clauses = re.split(r";\s*|,\s*(?=(?:and |then )?pray\b)|\s+and\s+(?=pray\b)|(?<=\))\s+and\s+|\s+and\s+(?=(?:wear|attack|dodge|step|kill|keep|use)\b)", body, flags=re.I)
        clauses = [c.strip(" ,") for c in clauses if c.strip(" ,") and not PRAYER_ADVICE.search(c)]
        if clauses:
            s = "; ".join(clauses)
            kept.append(s[0].upper() + s[1:] + ".")
    return " ".join(kept)

def slot_map(m, drop):
    out = {}
    for slot, items in (m or {}).items():
        slot = slot.lower()
        if slot not in SLOTS:
            continue
        items = clean_list(items)
        if drop:
            items = [i for i in items if not DROP_FROM_PREFERRED.search(i)]
        if items:
            out[slot] = items
    return out

def convert(b):
    name = b["name"]
    setups = []
    for s in b.get("setups") or []:
        weapons = clean_list(s.get("weapons"))
        setup = {"label": s.get("label") or s["style"].title(), "style": s["style"].upper()}
        if weapons:
            setup["weapons"] = weapons
        pref = slot_map(s.get("preferred"), True)
        if pref:
            setup["preferred"] = pref
        req = slot_map(s.get("required"), False)
        if req:
            setup["required"] = req
        if s.get("notes"):
            notes = strip_prayer_advice(s["notes"])
            if notes:
                setup["notes"] = notes
        setups.append(setup)
    if not setups:
        return None

    out = {"name": name, "category": CATEGORY.get(name.lower(), "Other")}
    if name in ALIASES:
        out["aliases"] = ALIASES[name]
    attrs = [a.lower() for a in (b.get("attributes") or [])]
    if attrs:
        out["attributes"] = attrs
    imm = [i for i in (b.get("immuneTo") or []) if STYLE_IMMUNITY.match(i.lower())]
    if imm:
        out["immuneTo"] = imm
    ew = b.get("elementalWeakness") or {}
    if ew.get("element") and ew["element"] != "none":
        out["elementalWeakness"] = ew["element"].lower()
        if ew.get("percent"):
            out["elementalWeaknessPercent"] = int(ew["percent"])
    d = b.get("defenceBonuses") or {}
    defence = {k: int(v) for k, v in d.items() if isinstance(v, (int, float))}
    if defence and any(defence.values()):
        out["defence"] = defence
    if b.get("slayerLevel"):
        out["slayerLevel"] = int(b["slayerLevel"])
    if b.get("slayerOnly"):
        out["slayerOnly"] = True
    out["setups"] = setups
    return out

def apply_stats(boss, st):
    """Overlay the wiki's combat stats (from fetch_stats.py) onto a converted boss."""
    keys = ["stab", "slash", "crush", "magic", "rangedLight", "rangedStandard", "rangedHeavy"]
    boss["defence"] = {k: st[k] for k in keys}
    attrs = boss.get("attributes", [])
    for a in st.get("attributes", []):
        if a not in attrs:
            attrs.append(a)
    if attrs:
        boss["attributes"] = attrs
    if st.get("elementalWeakness"):
        boss["elementalWeakness"] = st["elementalWeakness"]
        boss["elementalWeaknessPercent"] = st["elementalWeaknessPercent"]
    boss["combat"] = {k: st[k] for k in ["hitpoints", "defenceLevel", "magicLevel", "size", "flatArmour"]}
    if st.get("label"):
        boss["combat"]["target"] = st["label"]
    if st.get("ids"):
        boss["npcIds"] = st["ids"]


def main():
    knowledge = json.load(open(sys.argv[1], encoding="utf-8"))
    stats = {}
    args = sys.argv[3:]
    if "--stats" in args:
        i = args.index("--stats")
        stats = json.load(open(args[i + 1], encoding="utf-8"))
        args = args[:i] + args[i + 2:]
    bosses, names = [], set()
    for path in args:
        for b in json.load(open(path, encoding="utf-8")):
            c = convert(b)
            if c and c["name"].lower() not in names:
                names.add(c["name"].lower())
                bosses.append(c)
    bosses = knowledge.pop("bossesBefore", []) + bosses
    missing = [b["name"] for b in bosses if b["category"] == "Other"]
    if missing:
        print("No category for:", missing)
    for b in bosses:
        if b["name"] in stats:
            apply_stats(b, stats[b["name"]])
    no_stats = [b["name"] for b in bosses if "combat" not in b and b["name"] != "Any monster (generic)"]
    if stats and no_stats:
        print("No combat stats for:", no_stats)
    knowledge["bosses"] = bosses
    with open(sys.argv[2], "w", encoding="utf-8") as f:
        json.dump(knowledge, f, indent=1, ensure_ascii=False)
    print(len(bosses), "bosses written")

main()

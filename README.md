# Gear Me Up

A RuneLite plugin for Old School RuneScape. Search for a boss and get the best setup you can make from the
items you own (what you're wearing, what's in your inventory and what's in your bank), with its DPS, the
offensive prayer and potion to use, and the runes for magic.

## How it works

- A helmet icon in the RuneLite sidebar opens the panel.
- **Search:** type a boss name or nickname (kq, cerb, tob, zuk...). Suggestions appear as you type; pick one
  with the mouse or the arrow keys and Enter. Press the down arrow in an empty box to browse every boss.
- The boss's picture, weaknesses, hitpoints and Defence appear at the top.
- **Prayer** and **Boost** (Auto by default) choose the offensive prayer and potion used in the numbers:
  - Auto prayer uses the best one you've unlocked for each style: Zeal/Piety/Chivalry (or Ultimate Strength +
    Incredible Reflexes), Rigour/Deadeye/Eagle Eye, Augury/Mystic Vigour/Mystic Might. It reads your Prayer level
    and the Rigour, Augury, Deadeye, Mystic Vigour and Zeal unlocks, and Knight Waves for Chivalry/Piety.
  - Auto boost uses the best potion you own for each style (super combat, ranging, magic potions, hearts, brews).
    Overloads count inside the Chambers of Xeric and smelling salts inside the Tombs of Amascut.
  - "Current levels" uses your levels as they are right now instead.
  - Only offensive prayers are covered. RuneLite's Plugin Hub rules don't allow protection prayer advice.
- **Find best setup** works out every style. The headline shows the best DPS overall, and the
  **Melee / Ranged / Magic** tabs (with **Slash / Crush / Stab** under Melee) show each one:
  DPS, max hit, accuracy, the attack style to use, attack speed and prayer bonus; the gear laid out like the
  in-game equipment screen (border colour shows where each item is: green equipped, amber inventory, orange
  bank; hover for the name); the prayer and potion icons; and for magic, the spell and runes.
- Bosses with several setups for one style (e.g. raid rooms) get a **Setup** picker in that tab.
  Styles the boss is immune to are greyed out.
- The plugin remembers your bank per account. **Open your bank once** so it knows what's in it; "Your items"
  at the bottom shows when it was last seen. Results update live as you equip, bank or level up.
- **Show only this gear in bank** filters your bank to the tab you're viewing, using RuneLite's Bank Tags plugin
  (which must be enabled). Click again to stop.

### Advanced options

- **Pick weapon by DPS** (on): tries every weapon you own for the style and keeps the highest DPS. Off: uses the
  wiki's recommended order for the boss.
- **Include bank / Include inventory**: limit where items can come from.
- Plugin settings also have **Ignored items**: comma-separated names never to suggest (e.g. `Dharok, Void`).

### The DPS maths

It follows the OSRS Wiki DPS calculator (same multipliers, order and rounding): prayers, potions, attack
styles, Salve amulet and slayer helmet, demonbane (with each demon's vulnerability), dragonbane, keris,
inquisitor's, obsidian, crystal armour, Twisted bow, Scythe of vitur, Osmumten's fang, Colossal blade, powered
staves, Tumeken's shadow, tomes, Chaos gauntlets, elemental weaknesses, flat armour, Corporeal Beast, Kraken
and Zulrah damage rules, and the 200 damage cap.

Not modelled: special attacks, enchanted bolt effects, Void, Wilderness weapons and other set effects.
Boss stats are for one form when a boss has several (e.g. Verzik phase 3, Zulrah green); the info line says which.

### Choosing armour and other slots

For each slot the plugin takes the first item you own from these lists, in order:
1. Items the boss requires (anti-dragon shield, facemask, etc.)
2. Slayer helmet / black mask, if "On slayer task" is ticked. Skipped when wearing a Salve, since they don't stack.
3. The boss's own wiki list
4. Monster-type items (demonbane, dragonbane, Salve vs undead, Keris vs kalphites...)
5. An elemental tome, only for magic with a weapon that casts standard spells
6. General best-in-slot lists for the style
7. Otherwise, the best stats for the style

Ammo must suit the weapon, crystal armour is only used with a crystal bow, and League, LMS, Deadman and
Gauntlet items are never suggested.

### Limitations

- It doesn't check level or quest requirements for gear. If it suggests something you can't wear yet, add it
  to **Ignored items**.
- If Chivalry/Piety isn't detected but you have it, pick it in the Prayer box.
- Boss tips are a quick guide, not a full strategy. They never say which protection prayer to use.

## Running it

You need JDK 11 or newer.

```
./gradlew run
```

On Windows you can also run `.\run-dev.ps1`, which finds your JDK and calls `gradlew.bat run`. Jagex accounts need
the one-time setup from [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts) to log
in to the development client. `./gradlew test` runs the DPS tests.

## Boss data

89 bosses in 12 categories, researched from the OSRS Wiki in November 2026: Slayer, God Wars, Wilderness, classic
bosses, Barrows, Desert Treasure II, Varlamore, Fractured Archive, minigames, all four raids and the raid bosses.

Everything is generated from the files in `tools/`:

- `tools/knowledge.json`: monster-type items, tomes, slayer helms, weapon tiers, armour tiers and the generic
  "Any monster" entry.
- `tools/research/*.json`: per-boss setups, weaknesses and tips.
- `tools/research/npc_stats.json`: combat stats from the wiki, made by `fetch_stats.py` (version choices are in
  its `TARGETS` table).
- `src/main/resources/com/gearmeup/npc/*.png`: boss pictures from the wiki, made by `fetch_images.py`.
- `tools/convert.py`: builds `bosses.json`. It assigns categories and nicknames, removes prayer advice from tips,
  and merges in the combat stats.

To refresh everything (from `tools/`):

```
python -I fetch_stats.py ../src/main/resources/com/gearmeup/bosses.json research/npc_stats.json
uv run --with pillow python -I fetch_images.py research/npc_stats.json ../src/main/resources/com/gearmeup/npc
python -I convert.py knowledge.json ../src/main/resources/com/gearmeup/bosses.json research/varlamore_new.json research/gwd_wildy.json research/slayer_classic.json research/dt2_raids.json --stats research/npc_stats.json
```

Item names match case- and space-insensitively by "contains", so `"Slayer helmet (i)"` also matches coloured
slayer helmets.

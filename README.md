# Gear Me Up

A RuneLite plugin for Old School RuneScape. Pick a boss, press **Gear me up!**, and get the best
setup you can make from the items you own: what you're wearing, what's in your inventory, and
what's in your bank.

## How it works

- A helmet icon in the RuneLite sidebar opens the panel.
- Choose a **boss** and a **setup** (some bosses have several, e.g. Zulrah Magic/Ranged, the raids).
- Each slot shows the item to use and where it is: **Equipped**, **Inventory** or **Bank**.
- Red lines flag problems, e.g. a missing anti-dragon shield at Vorkath, or a trident that needs charging.
- The plugin remembers your bank per account. **Open your bank once** so it knows what's in it.
  It refreshes every time you open the bank again.
- The result updates live as you equip or bank items.
- **Magic setups show a spell box.**
  - **Autocast weapons:** it shows the best spell for your Magic level and current spellbook, using the boss's elemental weakness if it has one. Demonbane is used vs demons with a Purging staff/Kodai/etc., and Ancients when you're on that spellbook and the weapon can autocast them. It lists the runes per cast, how many you own (bank + inventory + equipped, counting combination and Sunfire runes), and roughly how many casts that covers. Runes your staff or tome provides are marked **free**. If you need a different spellbook, it says so. Rune pouch contents aren't counted.
  - **Powered staves and charged weapons** (tridents, Sanguinesti, Shadow, Eye of Ayak, Zorya's tome, sceptres): it shows what one charge costs and how many charges your runes cover.
  - Spell data (levels, runes, max hits, charge costs, rune-providing staves, autocast rules) is in `src/main/resources/com/gearmeup/spells.json`, verified against the OSRS Wiki.
- **Show only this gear in bank** filters your bank to just the suggested items, using RuneLite's
  Bank Tags plugin, which must be enabled. It applies straight away if the bank is open, and again each
  time you open it, until you click the button again to stop. Switching to another bank tab also
  clears it until the bank is next opened.

### Choosing items

1. **Boss preferences:** hand-picked items for that boss, best first (Dragon hunter crossbow + Salve
   amulet(ei) at Vorkath, Emberlight/Arclight vs demons, spears at Corp, ...). The first one you own wins.
2. **Style favourites:** for ranged and magic, weapons whose real damage beats their stats
   (Twisted bow, Bow of faerdhinen, blowpipe, tridents, Sanguinesti staff, Tumeken's shadow).
3. **Stats:** everything else is scored for the combat style:
   - Melee: strength, then the matching stab/slash/crush accuracy
   - Ranged: ranged strength, then ranged accuracy
   - Magic: magic damage %, then magic accuracy

   Small extras count too: prayer bonus and defence.

Weapons are scaled by attack speed. Two-handers are compared against your best one-hander plus
shield. Ranged weapons only get ammo they can fire (bolts for crossbows, arrows for bows, nothing
for the blowpipe or crystal bows).

### Settings

- **Include bank / Include inventory:** limit where items can come from.
- **Ignored items:** comma-separated names never to suggest (e.g. `Dharok, Void`).
- **On slayer task** (checkbox in the panel): prefers your slayer helmet or black mask, imbued for ranged/magic.
- **Category** (drop-down in the panel): narrows the boss list.
- **Search boss** (box at the top of the panel): filters every boss as you type, ignoring case, spaces and
  punctuation ("kril" finds K'ril). It also knows common nicknames: kq, kbd, dks, gg, thermy, cerb, corp, vork,
  bandos/sara/arma/zammy, jad, zuk, cox/tob/toa, nm/pnm, huey, moons, titans and more. Press Enter to gear up for
  the top match. Nicknames live in `ALIASES` in `tools/convert.py`.

### Limitations

- It doesn't check level or quest requirements. If it suggests something you can't wear yet, add it to **Ignored items**.
- Void, Obsidian and Inquisitor set effects aren't scored. Void is left out of the lists because it only works as a full set.
- Demonbane strength varies per demon (e.g. weaker at Duke Sucellus); the plugin just prefers demonbane weapons.
- Boss tips are a quick guide, not a full strategy. Check the OSRS Wiki for the details.
- Tips never say which protection prayer to use: RuneLite's Plugin Hub rules don't allow combat prayer
  recommendations. `tools/convert.py` strips any that appear in the research files.

## Running it

You need JDK 11 or newer.

**IntelliJ IDEA (easiest):** open this folder as a Gradle project. Then run
`src/test/java/com/gearmeup/GearMeUpPluginTest.java`, adding `-ea` to the VM options. RuneLite starts
in developer mode with the plugin already loaded.

**Command line:** the Gradle wrapper downloads everything it needs the first time.

```
./gradlew run
```

On Windows you can also run `.\run-dev.ps1`, which finds your JDK and calls `gradlew.bat run`. Jagex accounts need
the one-time setup from [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts) to log
in to the development client.

## Boss data

There are 89 bosses in 12 categories, researched from the OSRS Wiki in November 2026. They cover Slayer, God Wars, Wilderness, classic bosses, Barrows, Desert Treasure II, Varlamore (Moons of Peril, Hueycoatl, Royal Titans, Yama, Doom, etc.), minigames, all four raids and the individual raid bosses.

Each boss records:
- **Weaknesses:** its monster type (demon, dragon, undead, kalphite...), elemental weakness, defence by style, immunities and Slayer requirement. These show in the panel under the boss name.
- **Setups:** one per style the wiki recommends, each with the wiki's best-first weapon list, any boss-specific items (Salve, anti-dragon shield, tomes...), required items and short tips.

For each slot the plugin takes the first item you own from these lists, in order:
1. Items the boss requires (anti-dragon shield, facemask, etc.)
2. Slayer helmet / black mask, if "On slayer task" is ticked. It's skipped when wearing a Salve, since they don't stack.
3. The boss's own wiki list
4. Monster-type items, e.g. Emberlight/Arclight/Scorching bow/Purging staff vs demons, Dragon hunter lance/crossbow/wand vs dragons, Salve vs undead, Keris vs kalphites
5. An elemental tome, only for magic with a weapon that casts standard spells
6. General best-in-slot lists for the style
7. Otherwise, the best stats for the style

Extra rules:
- Ammo must suit the weapon. Dragon arrows only go with the Twisted/Dark/Venator/Scorching bow, dragon bolts not with a Rune crossbow, Ascension crossbows take only ascension bolts, and Bow of faerdhinen/blowpipe/Webweaver bow need no ammo.
- Crystal armour is only used (and then preferred) with a Bow of faerdhinen or Crystal bow.
- League, LMS, Deadman and Gauntlet-only items are never suggested.

### Editing the data

`src/main/resources/com/gearmeup/bosses.json` is generated. Edit the sources in `tools/`:

- `tools/knowledge.json`: monster-type items, tomes, slayer helms, weapon tiers, general armour tiers and the generic "Any monster" entry.
- `tools/research/*.json`: per-boss research (setups, defences, weaknesses, notes).
- `tools/convert.py`: builds `bosses.json`. It assigns categories, drops slayer-helm and Void pieces from general lists, and normalises names.

Regenerate after editing:

```
cd tools
python -I convert.py knowledge.json ../src/main/resources/com/gearmeup/bosses.json research/varlamore_new.json research/gwd_wildy.json research/slayer_classic.json research/dt2_raids.json
```

Item names match case- and space-insensitively by "contains", so `"Slayer helmet (i)"` also matches coloured slayer helmets, and `"Salve amulet (e)"` matches `Salve amulet(e)`.

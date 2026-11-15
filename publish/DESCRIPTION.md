# Simple Automation

**Put your copper golems to work.** Mark out a zone, drop a job controller in it, and every copper golem inside starts digging, chopping, smelting, farming or tending animals for you. No power, no machines to upgrade: the tool you hand a golem decides how well it works.

Made for NeoForge 1.21.11. Works in survival from the start: everything is crafted from copper, a chest and a bit of redstone.

---

## Getting started (in 1 minute)

1. **Craft two Border Markers** (lightning rod + redstone gives 2) and place them at **two opposite corners** of the area you want to work on. Zones can be up to 64 × 64 blocks (configurable).
2. **Place a job controller** inside the zone, or at most 2 blocks outside it. Lines on the ground show the zone.
3. **Put a chest (or any storage) next to the controller.** That is where the golems bring everything they collect.
4. **Hire golems:** build a copper golem inside the zone, or bring an existing one on a **lead**. It turns into a worker of that zone.
5. **Give it a tool:** right-click the golem with the tool. Right-click with an empty hand to take the tool back.

Right-click the controller at any time: it tells you the zone size, how many golems work or sit idle, and what is missing (a tool, a chest, a pickaxe for the stone layer...).

---

## The tool is everything

A golem works like a player holding the same tool:

- **Speed** comes from the tool tier and **Efficiency**.
- **Fortune** and **Silk Touch** change the drops, exactly like for you.
- **Durability** is used up; a broken tool leaves the golem empty-handed.
- **Modded tools work too**: paxels, multitools and modded pickaxes, axes, shovels, hoes, shears and swords are recognised through the standard tags and NeoForge tool abilities.

A golem holding something useless for its job shows a **"?"** above its head.

---

## The six jobs

| Controller | Golem holds | What happens |
|---|---|---|
| **Quarry** | pickaxe, shovel or nothing | Digs the zone layer by layer down to bedrock. Shovels and bare hands take dirt, sand and gravel; pickaxes take stone and ores. Golems work together along the same digging front. |
| **Lumber Yard** | axe | Fells whole trees in one go (mangroves included), collects what falls and replants saplings from the drops or the chest. Huge crimson and warped fungi are felled too (the stems; the canopy stays) and replanted on their own nylium with fungi from the chest; put bone meal in the chest and the golems grow them back. |
| **Forge** | nothing or a shovel | Tends every furnace, blast furnace and smoker in the zone: shares the input and fuel fairly between them, gives each one only the fuel it needs, and empties the results. With a shovel, golems stoke the fire so furnaces cook faster. |
| **Crusher** | pickaxe | Takes ore blocks from the chest, places them in the zone and mines them for 50% more loot than the same pickaxe would give. Fortune counts, Silk Touch is ignored: mine ores with Silk Touch, crush them with Fortune III. |
| **Farm** | nothing or a hoe | Harvests ripe crops and replants them, sows seeds from the chest on empty farmland, picks sweet berries and ripe cocoa (up to 12 blocks up jungle trunks). Pumpkin and melon stems keep a free spot for their fruit. |
| **Barn** | animal food, shears, a bucket, a brush or a weapon | Food: breeds the animals that eat it (food taken from the chest), up to a limit per species (10 by default, babies included). Shears: shears sheep, mooshrooms and other shearable animals (modded ones too; a sheared mooshroom becomes a cow, like with a player). Bucket: milks cows, mooshrooms and goats into empty buckets from the chest. Brush: brushes armadillos for scutes. Sword, axe, mace or trident: culls the extra adults above the limit (never babies, named, leashed or tamed animals). Eggs and drops lying in the zone go to the chest, and experience comes out as crystals. |

Tips:
- **Quarry:** golems place cobblestone under themselves over caves, lava and water, and seal lava next to the blocks they dig. Put cobblestone in the chest; if there is none they still place it. The quarry waits on the stone layer until someone has a pickaxe.
- **Barn:** fence the zone in. The controller warns you when animals wander out.
- **Crusher:** leave some free cells with a solid floor inside the zone, at the markers' height: that is where golems set the ores down.

---

## Golem care

- **Oxidation:** like vanilla copper golems, workers slowly oxidize and **slow down**. A fully oxidized golem freezes.
- **Wax** a golem with honeycomb to stop oxidation, **scrape** it by sneaking and right-clicking with an axe.
- **Lightning** striking a zone (or up to 2 blocks around it) de-oxidizes all its golems. A lightning rod in the zone helps.
- **Free a golem:** sneak and right-click it with an empty hand. It becomes a normal copper golem again, with its name, its tool returned to you and the flower on its antenna. Bring it back on a lead to re-hire it.
- **Breaking a controller** releases its golems next to it. Place a controller back near them and they are re-hired at once. Nothing is lost: tools and stored items drop, even with the `blockDrops` game rule off.

Idle golems never stand around in the void: they walk next to the closest busy golem, or come back to the controller, and get back to work as soon as there is something to do.

---

## Copper Pipe, Wrench and Pipe Filter

One pipe, no tiers, instant transfer.

- **Copper Pipe** (8 per craft: copper, glass, copper). Pipes connect to any storage, including modded ones, and to controllers.
- **Wrench:** right-click a pipe arm facing a container to switch it between **insert**, **extract** and **disabled**. Right-click the arm between two pipes to separate or join them. Sneak and right-click to pick a pipe up instantly.
- Extracting arms pull items from their container and send them to the inserting arms of the network, in turns. If nothing can take an item, it stays where it is: pipes never drop or lose anything.
- **Pipe Filter:** right-click in the air to open it. Click items into its 9 slots (they are not consumed), **right-click a slot to cycle through its tags** ("all ores", "all ingots"...) and switch between **whitelist** and **blacklist**. Right-click a pipe arm with the filter to fit it; right-click the arm with an empty hand to take it back. On an extracting arm it filters what is pulled out; on an inserting arm, what may go in.

---

## Recipes

All controllers use the same shape: copper ingots around, a **chest** in the middle, **redstone** at the bottom and the job's item at the top:

| Controller | Top item |
|---|---|
| Quarry | iron pickaxe |
| Lumber Yard | iron axe |
| Forge | furnace |
| Crusher | grindstone |
| Farm | iron hoe |
| Barn | hay bale |

- Border Marker ×2: lightning rod + redstone (shapeless)
- Copper Pipe ×8: copper ingot, glass, copper ingot
- Wrench: 4 copper ingots (Y shape)
- Pipe Filter: paper over a copper ingot over a hopper

With JEI installed, every block and item also has an information page.

---

## Compatibility

- **Jade:** look at a controller to see its job, status, workers (busy, idle, missing a tool) and stored items; look at a pipe to see the mode and filter of each side.
- **JEI:** information pages for every block and item, and you can drag items from JEI straight into a Pipe Filter.
- **Claim and protection mods** (FTB Chunks, Open Parties and Claims...): golems ask permission like a player would before breaking or placing blocks and before shearing or breeding animals. Protected spots are skipped.
- **Modded content:** modded storage (any block with an item inventory), ores (`c:ores`), logs and saplings, crops, seeds (`c:seeds`), shears, swords, animals and furnaces that extend the vanilla ones are all supported.
- **Modpack tags:**
  - `simple_automation:golem_blacklist` (blocks): golems never break these.
  - `simple_automation:crusher_blacklist` (items): ores the crusher leaves alone.
  - `simple_automation:farm_crops` (blocks): extra crops for the farm (harvested at max age, then replanted).
  - `simple_automation:farm_seeds` (items): extra seeds the farm sows on empty farmland (only when it can also harvest the plant they grow).

---

## Server friendly

- Golems inside a zone are **not entities**: the controller simulates them and clients only draw them. Fifty golems cost about as much as one block entity.
- A **global block budget** caps how many blocks all golems of the server break per tick. Work slows down instead of lagging.
- When the chests are full the zone **pauses**: nothing is dropped on the ground.
- Everything is configurable in `simple_automation-server.toml` (world `serverconfig` folder): zone size, golems per zone, block budget, work speed, walk speed, crusher bonus, barn animal limit, pipe speed.

---

## FAQ

**My golem does nothing.** Right-click the controller: it tells you why (no tool, wrong tool, chest full, no pickaxe for stone...). A "?" over a golem means it holds the wrong item.

**Can I use it on an existing world?** Yes. Worlds from Simple Automation 1.x are not compatible: the old machines are gone.

**A copper golem I already had is not hired.** Only newly built golems are hired on their own. Bring an existing golem into the zone on a lead.

**Is there a Fabric version?** Not for now: NeoForge only.

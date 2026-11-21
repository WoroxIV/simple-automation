## 2.2.0

New jobs (Barn, Fishery, Guard Post, Enchanter, Workshop), beekeeping, experience crystals and a reliable Lumber Yard.

### Fixed
- Lumber Yard: a bee nest on a tree reaches the chest even when the axe breaks during the felling. Before, a broken axe could destroy the nest and release angry bees, or leave it floating.
- Lumber Yard: a 2x2 dark oak or pale oak growing across the edge of the zone is replanted with another species right away. Before, a single dark oak sapling was planted, which never grows.
- Lumber Yard: trees felled before this update wait the full `lumberFallbackSeconds` before another species is planted.
- Barn: a culling hit refused by the animal no longer wears the weapon.
- Fishery: looking for open water never loads the chunk next to a pond.
- Breaking a controller drops the experience waiting for the next crystal as orbs, instead of losing it.
- Workshop: a Recipe Card keeps its exact result. A card for swiftness tipped arrows never uses a poison lingering potion from the chest. Smithing still keeps the wear and enchantments of the upgraded item.

### Changed
- Enchanter: the table now keeps its offer like it does for a player. The same item at the same table always costs the same and gets the same enchantments, until something is enchanted. Golems no longer reroll until they get a cheap level: they wait for enough experience, like you would.
- New project logo and description images.


- Experience Crystal (new item): the experience earned by golems never drops as orbs anymore. The controller stores it and puts one crystal (10 experience points) in its chests for every 10 points. Right-click to use one crystal, sneak to use the whole stack; Mending repairs as usual. Clicking the controller (and Jade) shows the experience waiting for the next crystal.
- Golems also take what they need (saplings, food, buckets…) from the items waiting in the controller, not only from the chests.
- Quarry, Crusher, Lumber Yard and Farm: ores and other blocks that give experience when mined now give it to the controller too (coal, lapis, redstone, sculk…), like a player with the same tool. Silk Touch gives none, as in vanilla; the Crusher counts the experience once, without the bonus.
- Forge: the smelting experience goes to the controller and comes out as crystals, fractions included (no more orbs above the controller).
- Crusher: each ore now gives 50% more than mining it with the same pickaxe (`crusherMultiplier`, 1.5 by default), instead of keeping the best of 4 rolls. This helps without Fortune too: 1.5 raw iron per ore with a plain pickaxe, 3.3 on average with Fortune III. Silk Touch pickaxes are accepted and Silk Touch is ignored, so the quarry's pickaxe works. The `crusherRolls` setting is replaced by `crusherMultiplier`.
- Farm: melon and pumpkin stems are never broken anymore (a fully grown stem was taken for a ripe crop). Pitcher and torchflower sprouts are left alone too.
- Barn Controller (new): replaces the Sheepfold and Ranch controllers, which are removed. What each golem does depends on what it holds: animal food breeds, shears shear, a bucket milks cows, mooshrooms and goats (empty buckets taken from the chests), a brush brushes armadillos for scutes, and a sword, axe, mace or trident culls the adults above the limit. Eggs (blue and brown ones too) and other drops are collected, and the experience from culling and breeding comes out as crystals, without orbs. Recipe: copper ingots, chest and redstone with a hay bale on top. The `ranchMaxAnimals` setting is renamed `barnMaxAnimals` (per species, babies included).
- Fishery Controller (new): golems holding a fishing rod stand on the bank and fish in the still water of the zone (one water block each, up to 5 blocks from the bank). They use the vanilla fishing loot: Luck of the Sea and Lure work, and treasure only bites in open water (at least 5×5 and 2 deep with air above), like for a player. Each catch gives 1 to 6 experience as crystals and wears the rod by one point. Other mods see the catches (`ItemFishedEvent`). Recipe: copper ingots, chest and redstone with a fishing rod on top.
- Guard Post Controller (new): golems holding a sword, an axe, a mace or a trident strike the hostile mobs in the zone like a player: one full-strength hit per attack cooldown of the weapon, with its damage and enchantments (Sharpness, Smite, Fire Aspect, Knockback...), and one point of wear per hit. Kills count as player kills, so Looting works and rare drops can fall. Drops go to the chests and experience comes out as crystals. Bosses, named and leashed mobs and the Warden are left alone (tag `simple_automation:guard_blacklist`). Mobs cannot hurt golems. Recipe: copper ingots, chest and redstone with an iron sword on top.
- Enchanter Controller (new): golems holding a book work at an enchanting table placed in the zone, with the power of the bookshelves around it (vanilla rules, up to 15). They always take the third option, the best one, with random enchantments and no choice, like a player. Each enchanting costs 3 lapis lazuli from the chests plus the experience of 3 levels at the required level (at level 30: 306 experience, 31 crystals), taken from the controller's stored experience first, then from experience crystals in the chests (the change goes back to the controller). Every unenchanted enchantable item and every book in the chests gets enchanted, one at a time. Recipe: copper ingots, chest and redstone with an enchanting table on top.
- Workshop Controller and Recipe Card (new): golems holding a Recipe Card work at the crafting tables, smithing tables, stonecutters and brewing stands of the zone. Right-click a station with a blank card to set it to that kind of station, then put one item in each slot (they are not consumed; drag items from JEI too): the card shows the result and its tooltip lists the station and the ingredients. At the stonecutter, click the result to pick another one. The golem takes the materials from the chests (any item the recipe accepts at that place, the card's own item first, as long as the result is the same), uses the nearest station of its kind and puts the result and the leftovers (empty buckets...) in the chests. At a brewing stand the real block brews, vanilla 20 seconds: the golem loads up to 3 base potions, the ingredient and blaze powder, then empties the finished potions; one golem per stand. Chain cards through the chests, for example "water to awkward" and "awkward to strength" on two golems. Anvil, grindstone and loom are not supported. Recipes: Workshop Controller is copper ingots, chest and redstone with a crafting table on top; Recipe Card is paper + copper ingot.
- Farm: beekeeping. A golem holding shears collects honeycombs from full beehives and bee nests (up to 12 blocks above the markers); a golem holding an empty glass bottle fills honey bottles, taking the empty bottles from the chests. The bees stay calm and the hive goes back to empty. Golems with a hoe (or bare-handed) keep tending the crops.
- Lumber Yard: trees are replanted reliably.
  - The leaves fall with the tree: their saplings, sticks and apples go straight to the chests instead of waiting for the leaves to decay. The axe does not wear out on leaves.
  - Each tree is replanted with its own species. 2x2 trees are replanted as a square of 4 saplings of the same species, never a mix. Dark oak and pale oak wait for 4 saplings.
  - If the right sapling is still missing after `lumberFallbackSeconds` (60 by default, 0 = never), another species is planted (the one you have the most of). A big spruce or jungle tree without 4 saplings gets a single one.
  - Bee nests on a felled tree no longer stay floating: they fall with it and reach the chests whole, with their bees and honey, ready to be placed somewhere else.
  - Nether fungi are replanted on the matching nylium next to the stump when the nylium under the stem has turned back into netherrack (vanilla does this under any block). Before, the fungus was silently never replanted.
  - Mangroves growing on roots are replanted with a propagule from the chests on the mud next to the roots (mangrove leaves give no propagules).
  - Clicking the controller tells you how many felled trees are waiting for saplings. With `lumberWaitForSaplings` turned on, golems stop felling new trees until they are replanted.

## 2.1.0

Fixes and improvements from playtesting.

- Golems freed by breaking a controller are recruited again as soon as a controller is placed back near them, no lead needed. Golems freed by hand (sneak with an empty hand) still need a lead. Freed golems reappear around the controller, never at the bottom of a quarry, and keep their name and the flower on their antenna.
- Golems with nothing to do no longer stand still or float: they walk next to the closest busy golem, or back to the controller, and turn around as soon as work shows up again. A golem holding the wrong item shows a "?" above its head, and the controller tells you how many golems are idle.
- Quarry:
  - Golems dig bare-handed too (dirt, sand, gravel, slowly), next to pickaxe and shovel golems. Everyone works along the same digging front and nobody waits while there is a block they can harvest.
  - Golems fall back onto solid ground after each block, and cobblestone is placed under them over caves, lava and water. Chests, plants and other blocks under them are no longer overwritten.
  - With no pickaxe in the crew (a broken pickaxe, bare-handed golems), the quarry now waits on the stone layer instead of skipping it. Clicking the controller tells you it needs a pickaxe.
  - When the quarry is finished, every golem comes back up to the controller.
- Crusher: golems place each ore on a free spot of the zone and mine it with their pickaxe, cracks included. Ores left placed (golem removed, server restart) are found and mined first. Idle golems no longer block the crushing spots. If a claim refuses the golems, the spot is skipped and no more ores are taken from the chest until the problem is fixed.
- Forge: input and fuel are shared between all the furnaces of the same kind, and each furnace only gets the fuel it needs for what it is cooking.
- Pipe Filter (new item): right-click in the air to set up to 9 items, with a whitelist or blacklist button. Right-click an item slot to cycle through its tags (for example "all ores" or "all ingots"). Put it on a pipe arm facing a container with a right-click, and take it back with an empty-handed right-click. Breaking the pipe gives the filters back.
- Pipes connect to controllers and an extracting pipe can empty the controller's buffer.
- Farm: sows seeds from the chests on empty farmland, pumpkin and melon seeds included (the farmland next to a stem stays free for its fruit).
- Lumber Yard: mangroves and trees growing on roots are now detected.
- Ranch: breeding counts food across all the chest stacks. Sheepfold and Ranch warn you when animals escape an unfenced zone.
- Nothing is lost when a controller or pipe is broken, even with the blockDrops game rule turned off.
- Lighter network traffic: the client only receives what it draws.
- Only newly built (or spawned) copper golems are hired without a lead. Before, any wild copper golem near a zone was hired after a server restart. Golems from older saves need a lead once.
- Ranch: a golem holding food that no animal of the zone eats now shows the "?". Axes, maces and tridents cull like swords.
- Sheepfold and Ranch: work speed follows the tool (Efficiency, faster modded shears) and the `workTimeMultiplier` setting. The Sheepfold shears every shearable animal (sheep, mooshrooms, modded ones), but no longer snow golems or bogged.
- Farm: picks sweet berries and ripe cocoa (up to 12 blocks above the markers on jungle trunks), and handles modded crops listed in the `simple_automation:farm_crops` tag and seeds in `simple_automation:farm_seeds` (plus `c:seeds`). Seeds from `farm_seeds` are only sown when their plant has an age the farm can harvest. Farmland where a claim refuses planting is skipped for 30 seconds.
- Lumber Yard: fells huge crimson and warped fungi (stems only, the canopy stays) and replants fungi from the chest on the matching nylium. Fungi only grow with bone meal: the controller uses bone meal from the chest on the fungi it planted.
- Crusher: ores in the `simple_automation:crusher_blacklist` item tag are left alone.

### Compatibility
- Claim and protection mods: golems now ask permission before placing blocks (quarry cobblestone, saplings, seeds) and before shearing or breeding, not only before breaking.
- Modded tools: paxels, drills, hammers and multitools are recognised by what they can do, not only by the vanilla tags.
- Jade (optional): job, status, workers and stored items on controllers; mode and filter of each pipe side.
- JEI (optional): an information page for every block and item, and items can be dragged from JEI into a Pipe Filter.
- New mod logo.

## 2.0.0

Complete rewrite. Worlds from 1.x are not compatible: the old machines are gone.

- Copper golems do the work. Mark a zone with two Border Markers, place a job controller inside it (or up to 2 blocks around), then build a copper golem in the zone or bring one on a lead.
- The tool a golem holds decides how well it works: speed, Efficiency, Fortune, Silk Touch and durability all come from the tool. Modded tools work too.
- Seven jobs, one controller each:
  - Quarry: digs layer by layer down to bedrock with pickaxes or shovels, puts cobblestone under the golems over lava, water or caves, and seals lava with cobblestone.
  - Lumber Yard: fells whole trees at once with axes, picks up what falls and replants saplings.
  - Forge: golems tend the vanilla furnaces of the zone. With a shovel they stoke them, so the furnaces cook faster (fuel per item stays vanilla).
  - Crusher: golems crush ore blocks with their pickaxe. The loot is rolled several times and the best result is kept, so Fortune III almost always gives the maximum.
  - Farm: harvests ripe crops bare-handed or with a hoe and replants them.
  - Sheepfold: shears sheep with shears.
  - Ranch: breeds animals with the food the golem holds, up to a limit, and culls the extra adults with a sword.
- Items go into the chests next to the controller. When they are full, the zone pauses and nothing is dropped.
- Golems oxidize like vanilla ones and slow down; wax them with honeycomb, scrape them with an axe while sneaking. Lightning in a zone de-oxidizes its golems.
- One Copper Pipe, instant and fast. The Wrench separates two pipes or switches a container side between insert, extract and disabled.
- Server friendly: golems inside a zone are not entities, a global block budget caps the work per tick, and protection mods are respected.

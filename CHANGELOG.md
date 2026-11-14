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

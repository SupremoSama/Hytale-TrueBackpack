# True Backpack (+ Quivers | Helipack | HeadLight)

Transform backpacks into fully wearable equipment with immersive utility features, expanded storage options, and seamless gameplay integration.

> **Requires [Custom Inventory](https://github.com/SupremoSama/Hytale-CustomInventory)** (`>=0.3.0 <0.4.0`). Install both mods.

## Features

### Wearable Backpacks

* Backpacks are equipped in the **Backpack** slot of Custom Inventory's **Gear** panel
* Access backpack storage using the original in-game backpack interface
* Fully compatible with all armor sets
* Supports quick swapping between backpacks
* Press the **Use** key while holding a backpack to equip it instantly (an equipped backpack swaps into your hand)
* Functions similarly to portable shulker-style containers: contents stay inside the backpack item

### Backpack Sizes

| Backpack | Slots |
| --- | --- |
| Fiber Bag | 3 |
| Side Backpack | 6 |
| Small Backpack | 9 |
| Medium Backpack | 18 |
| Big Backpack | 27 |
| Extra Big Backpack | 36 (upgradable to 45 and 54) |

### Backpack Workbench

Craft, upgrade and personalize your backpacks at the **Apprentice** and **Master** Backpack Workbenches.

* **Craft & Upgrade**: craft new backpacks or upgrade the one you are holding or wearing to the next tier. Contents, name and appearance are kept
* **Personalize**: rename your backpack, change its appearance, and paint it with a live preview
* Some recipes are unlocked by restoring Memories

Personalize your backpack anywhere with:

```
/transmog
```

### Quiver

A quiver cosmetic automatically appears whenever arrows are present in your inventory. It sits on your backpack when one is equipped.

Toggle the quiver at any time using:

```
/togglecosmetic quiver
```

### Equipment Visibility

Show or hide your backpack and hat with the **eye** beside their slot in the Gear panel. Each item is controlled independently, and hiding an item never removes it or its effects. Your choice is saved between sessions.

### Placeable Backpacks

Backpacks can be placed directly in the world and used as portable containers.

Controls:

* **Secondary Key** → Place backpack on the ground
* **Use Key** → Open backpack
* Break the backpack to pick it back up

### Backpack ↔ Chest Transfer System

Quickly transfer items between backpacks and containers.

While crouching and holding a backpack, interact with a container:

* **Primary Click** → Move backpack contents into the chest
* **Secondary Click** → Pull items from the chest into the backpack
* **Use Click** → Transfer matching items already stored in the chest into the backpack

### HeadLight

The HeadLight (Torch Bandana) is a simple wearable light source equipped in the **Hat** slot of the Gear panel. Its durability slowly drains while worn.

Perfect for:

* Mining
* Building in dark areas
* Exploring caves without constantly holding torches

### Helipack

The Helipack is a specialized backpack variant focused on mobility and vertical exploration.

Features

* Equipped in the same Backpack slot
* Fully compatible with armor
* Always visible while equipped, so you can see it working
* Powered by charcoal stored in the Helipack: by default it consumes 5 charcoal every 10 seconds while flying

Flight Controls

* Double tap the jump key quickly
* Hold the second jump press to begin flying

## Commands

| Command | Description |
| --- | --- |
| `/togglecosmetic quiver` | Show or hide the quiver |
| `/transmog` | Open the backpack personalization workbench |
| `/reloadbackpack` | Reload the backpack configuration (admin) |
| `/sethelipackfuel` | Change the Helipack fuel type and consumption (admin) |
| `/setbackpackmodel <player> <model> <texture>` / `clear` | Override the backpack model shown for a player (admin) |

## Compatibility

* Compatible with all armor sets
* Built on Custom Inventory's shared equipment system, so other equipment mods can be used alongside it without their models overwriting each other
* Designed to work seamlessly with existing gameplay mechanics

## Warning

⚠️ **Important:** This mod makes irreversible changes to backpack data. Installing the mod without preparation may result in item loss.

Changes Made by the Mod

* Disables default backpack unlocking
* Removes the original "Unlock Backpack" item
* Deletes all existing backpack upgrades
* May remove items stored inside upgraded vanilla backpacks

Recommended Before Installing

* Backup your world
* Empty all backpacks before installation

Updating from an older version: backpacks and hats you were wearing move to the Gear panel automatically, and if you had hidden your backpack or hat, it stays hidden.

## Credits

Built using the Hytale Java plugin template originally developed by UpcraftLP and later refined by Kaupenjoe. Special thanks to both for their foundational work and contributions to the modding community.

## Source Code

Interested in contributing or creating your own version of the project?

GitHub: [SupremoSama/Hytale-TrueBackpack](https://github.com/SupremoSama/Hytale-TrueBackpack)

Try the mod: [CurseForge](https://www.curseforge.com/hytale/mods/true-backpack)

True Backpack (+ Quiver & Helipack) Team

## Development

### CustomInventory dependency

TrueBackpack requires `SupremoSama:CustomInventory` version `>=0.3.0 <0.4.0`.
Install both mod JARs. Hytale validates the required dependency and loads
CustomInventory first; TrueBackpack does not provide an independent inventory UI.

In this workspace, `settings.gradle.kts` includes the sibling
`../Hytale-CustomInventory` build. `compileOnly` compiles against its public API;
the dependency is not bundled inside TrueBackpack. Run
`gradlew.bat build` to compile both projects without publishing anything.

Run `gradlew.bat runServer` (or the IDE's `runServer` task) to launch with both
mods. `prepareDevMods` builds and stages their JARs in `build/dev-mods`, which
`runServer` loads through Hytale's dependency-aware mod loader. This also loads
their UI assets and avoids duplicate API classes from mixing loose development
classes with plugin JARs. Your existing `run/mods` files are preserved, and
CustomInventory remains a separate required mod when shipping.
After editing source code or assets, restart `runServer` to rebuild the staged
JARs.
The launch also ignores the invalid `HytaleServer.aot.config` cache argument
selected by hytale-mod 0.8.1.

The workbench is an `InventoryContent` hosted with
`CustomInventoryPlugin.openView`. Each opening creates a fresh backpack content
controller and `InventoryUiExtension`. The extension uses stable
`InventoryElementId` values to set the title/content layout and mount its own
navigation and bench panels. CustomInventory owns the player/inventory panels,
slot interactions, controller integration, subscriptions and page lifecycle.
TrueBackpack owns recipes, progression, upgrades and paint/name/appearance
previews. The bench still uses Hytale's native
`SimpleCraftingWindow` backend for queues, validation and material transactions.

On dismissal, TrueBackpack cancels its bench checks and paint preview tasks,
detaches the crafting update callback and releases the preview session. Plugin
shutdown closes its hosted views through the public context and removes its
outbound tooltip registration. Ordinary inventory updates preserve unsaved
personalization fields and their focus. Crafting controls also stay
mounted during updates; recipe rows rebuild only when the recipe list changes,
and upgrade controls rebuild only when their target changes.

### Equipment and visibility

CustomInventory's `EquipmentManager` owns the hat and backpack slots, equip
notifications, each slot's saved visibility and the player-model rendering, so
other equipment mods never overwrite TrueBackpack's models (or vice versa).
TrueBackpack registers its items and listeners (`ExtraEquipmentIntegration`),
describes each item's model, and applies the item-specific effects: backpack
capacity and contents, hat light and helipack animations. The quiver is a
namespaced `PlayerModel` attachment.
Players show or hide a backpack or hat with the eye beside its Gear slot; each
eye is independent. Helipacks stay visible, so their eye is shown as locked.
The workbench no longer has a Visibility tab, and `/togglecosmetic` now only
toggles the quiver, which follows carried arrows rather than an equipment slot.
Hidden backpack/hat preferences from older saves are moved to the Gear eyes
once, on the player's next join.

Runtime validation requires an actual Hytale client: open/reopen both benches
and `/transmog`, craft and upgrade, edit and save customization, switch tabs,
close while a preview is pending, remove the bench, change worlds, and repeat
with two players. Test mouse and gamepad input in the shared inventory panels.
The shared-source directory is reference-only and is never built or modified.

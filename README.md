# Hytale True Backpack

A mod that allows backpacks to function more naturally and as originally intended, built using the Hytale Java plugin template.  
Originally created by UpcraftLP and slightly modified by Kaupenjoe.  
Special thanks to both authors.

I’m sharing these files because I’m not very familiar with Java or Java IDEs, but I wanted to make backpacks work in a more immersive and intuitive way.  
The repository is open for improvements and contributions.

Try the mod: https://www.curseforge.com/hytale/mods/true-backpack

## Original Goals

- Make backpacks wearable
- Use the game’s native UI instead of interacting with backpacks like chests
- Drop all backpack items on death, if the game settings are configured to lose items.
- Remove backpack upgrade recipes so only backpack equipment is used
- Make backpacks behave similarly to shulker boxes (Minecraft)
- Add an extra inventory slot so players can equip both armor and a backpack

## Current Features

- Backpacks are wearable.
- Backpacks open using the original in-game UI.
- Added the Giant Backpack (36 slots).
- Backpacks drop on death, if the game settings are configured to lose items.
- Backpacks function similarly to Minecraft Shulker Boxes.
- Backpacks can be placed on the ground and used as containers.
- Original game recipes have been removed and are no longer craftable.


## CustomInventory dependency

TrueBackpack requires `SupremoSama:CustomInventory` version `>=0.2.0 <0.3.0`.
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
TrueBackpack owns recipes, progression, upgrades, paint/name/appearance previews
and cosmetic controls. The bench still uses Hytale's native
`SimpleCraftingWindow` backend for queues, validation and material transactions.

On dismissal, TrueBackpack cancels its bench checks and paint preview tasks,
detaches the crafting update callback and releases the preview session. Plugin
shutdown closes its hosted views through the public context and removes its
outbound tooltip registration. Ordinary inventory updates preserve unsaved
personalization fields and their focus. Crafting and visibility controls also stay
mounted during updates; recipe rows rebuild only when the recipe list changes,
and upgrade controls rebuild only when their target changes.

Runtime validation requires an actual Hytale client: open/reopen both benches
and `/transmog`, craft and upgrade, edit and save customization, switch tabs,
close while a preview is pending, remove the bench, change worlds, and repeat
with two players. Test mouse and gamepad input in the shared inventory panels.
The shared-source directory is reference-only and is never built or modified.

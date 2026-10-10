# Player-render eye controls

The four eyes beside equipped armor were passive `Group` elements with no event bindings.
They now use `Button` elements, dispatch `ToggleArmorVisibility`, and update the native
`PlayerSettings` component. These armor eyes now live in CustomInventory's player panel.

Backpack and hat visibility is not part of this flow: it is CustomInventory's saved
per-slot state, set with the eyes beside the Gear slots (see CustomInventory's
`docs/equipment-api.md`). The workbench Visibility tab has been removed.

The handler follows the native `GamePacketHandler.handleSyncPlayerPreferences` flow:
change the relevant hide flag, preserve the other settings, then mark the armor component's
equipment outdated. This lets normal equipment synchronization update rendering and lets
CustomInventory's player-model renderer (`PlayerModelRenderer.OnPlayerSettingsChange`)
rebuild cosmetic attachments.
No armor items, defense values, container transactions or other tabs change.

`ArmorVisibilityOption.ALL`, `HELMET_ONLY` and `NONE` are enforced both in the button state
and in the server handler. Eyes are absent for empty slots; forbidden eyes are disabled.

## Preference lifetime

This changes the server's current render settings. Native preferences are owned by the
client: `SyncPlayerPreferences` is client-to-server only, and `PlayerSettings` is not a
persisted component. A reconnect or a subsequent native preference synchronization can
replace the server values. The mod cannot update the client's saved native preferences
through the available public protocol. This patch deliberately retains that native ownership.

## Verification

Run `./gradlew.bat compileJava verifyBackpackArmorVisibility verifyBackpackWorkbenchInventory jar --offline`.
The armor verifier checks show/hide, world policy, unchanged unrelated preferences and
immutability against the installed engine classes. The existing inventory verifier checks
14 transaction, equipment-filter and UI-codec scenarios.

Client checks still needed: equip each armor type, click its eye twice, observe the player
preview and world model (including another player), verify defense stays constant, test
the three world policies, and navigate Personalize / Craft-Upgrade afterwards.
Server tests do not establish successful client rendering or input delivery.

All changes are in TrueBackpack; `hytale-shared-source` is read-only.

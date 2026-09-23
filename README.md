# CustomLights

Client-side mod for Fabric, NeoForge and Forge (1.20.1 to 26.2, see [Building and testing](#building-and-testing)) for colored lights with shapes, volumetric atmosphere, looping animations, a live editor, lights on items, blocks, particles, entities and model bones (GeckoLib, ModelEngine, item models) and an optional Paper/Spigot plugin that syncs and saves lights for every player.

Lights are drawn in screen space from the depth buffer after the world and the first person hand are rendered, so they light blocks, entities, players, held items and the hand alike. See [Performance](#performance) for how many lights stay cheap.

## How a light is shaped

- **radius**: the size of the lit shape. Everything inside it is fully lit (flat).
- **distance**: how far the light blurs out past the shape. `0` gives a hard edge.
- **intensity**: brightness (0 or more). Dark colors (`black`, `shadow` or any dim color) darken what they light instead.
- **atmosphere**: 0-10, the light scattered by the air (beams, glows).
- **luminosity**: 0-10, how hot the middle of the light burns. It holds a white core around the light's centre, bigger and brighter the higher it goes, and eases back to the light's own colour further out. At 10 the core is white and about twice the radius across.
- **color**: `#RRGGBB`, a name (`white warm cold red crimson orange lava gold yellow lime green teal aqua cyan blue navy purple magenta pink soul shadow black`) or `party`, which cycles every color.
- **yaw / pitch**: where the light points (yaw 0 = south, pitch 90 = down, -90 = up). The shape's own Y axis follows it.
- **stretch x y z**: scales the shape on its own axes (Y = where it points), from -64 to 64; negative values flip it.
- **pivot x y z**: the point the stretch grows from, on those same axes and in unstretched blocks (0 0 0 = the middle of the light). Put it at the bottom of a beam and the beam only grows upwards.
- **angle**: how wide cones, spotlights, flashlights and sirens open (1-170 degrees). Their length comes from the radius.

| Shape | Lit shape for a radius `r` |
|---|---|
| `sphere` | Ball of radius `r` |
| `pad` | Thin disc of radius `r` |
| `cone` | Wide lamp: tip at the light, `2r` long, opening by its angle (53° by default) |
| `inverted_cone` | Wide at the light, narrowing to a tip after `2r` |
| `spotlight` | Narrow projector `6r` long (20°), with a hot centre, fading along its length |
| `flashlight` | A real torch `12r` long (45°): bright centre, soft rim, faint spill around it, dimmer the further it goes; `distance` softens the rim |
| `siren` | Warning light: a glowing core and two opposite beams `16r` long (30°); add the `rotation` animation to make them sweep |
| `beam` | Column of radius `r`, `16r` long, closing into a point over its last quarter |
| `ring` | Thin ring of radius `r` |
| `box` | Box reaching `r` from its centre to each face |
| `star`, `cross`, `triangle` | Thin flat shapes of size `r` |
| `fire` | Flame `4r` tall that leans, licks and closes into a point, hottest at its base |
| `portal` | Ring of radius `r` that ripples as it turns, with a sheet inside it; it stands up by default |
| `lightning` | Bolt `10r` long that redraws its kinks several times a second |
| `aurora` | Curtain `2r` wide and `4r` tall that waves along its width and thins out towards the top |
| `ripple` | Pool of radius `r` on the ground whose rim rises and falls in a wave running outwards |
| `plasma` | Ball of radius `r` whose surface boils |
| `tornado` | Funnel `8r` tall, narrow where it touches the ground and turning as it widens |
| `singularity` | Accretion disc of radius `r` wound into a spiral around a core nothing comes out of |
| `rune` | Summoning circle of radius `r` on the ground: rings and marks turning against each other |
| `flare` | Star of radius `r` whose spikes grow and pull back in |
| `mist` | Low bank of fog of radius `r` whose top rolls slowly |
| `comet` | Burning head with a tail `7r` long that wavers and fades out behind it |
| `laser_door` | Flat doorway of beams `2r` wide and `2.4r` tall, framed, with rungs creeping upwards |

Every shape has a ready-made look (radius, distance, intensity, atmosphere, angle and, for `beam`, a longer stretch) that the editor applies when you pick the shape (smaller on objects).

`fire`, `portal`, `lightning`, `aurora`, `ripple`, `plasma`, `tornado`, `singularity`, `rune`, `flare`, `mist`, `comet`, `laser_door` and `siren` move on their own: their silhouette changes in the shader every frame, each light on its own clock so two fires never burn in step. They are written in yellow in the editor's shape list, bring their own colour and switch their animations on when you pick them (the portal turns and breathes, the bolt strikes, the curtain drifts, the pool pulses, the siren sweeps; the flame and the plasma ball need none, their shape already moves). Everything they set is an ordinary value, so you can change or drop any of it afterwards.

## Commands (client)

`/customlight`. IDs are names (`letters numbers _ - . + :`). Coordinates accept `~` and `~1.5`; whole numbers on x/z are centred on the block like `/tp`.

```mcfunction
/customlight set <id> <x> <y> <z> <shape> <color> <radius> <distance> <intensity> <atmosphere> <fade_in> <fade_out> [yaw] [pitch]
/customlight follow <id> <player|uuid|tag:<tag>|@self> <offX> <offY> <offZ> <shape> <color> <radius> <distance> <intensity> <atmosphere> <fade_in> <fade_out> [yaw pitch] [visible_firstperson]
/customlight remove <id|all|flashlight> [fade_out]
/customlight move <id> <x> <y> <z> <smooth|linear|step|bounce> <time>
/customlight model_id <id> <x> <y> <z> <shape> <color> <radius> <opacity> <light> <fade_in> <fade_out> [distance] [yaw] [pitch]
/customlight model_remove <id|all> [fade_out]
/customlight flashlight <id> <player|uuid|tag:<tag>|@self> <color> <scale> <distance> [intensity] [atmosphere] [camera_delay] [angle]
/customlight flashlight off
/customlight animate <id> <preset[+preset...]> [time] [amount]
/customlight animate <id> add <preset> [time] [amount]
/customlight animate <id> remove <preset>
/customlight animate <id> color_transition <color1> <ticks1> <color2> <ticks2> [...]
/customlight animate <id> size <radius> <distance> <time>
/customlight animate <id> animation_radius <radius> [time]
/customlight animate <id> stop [all|preset|color|size|radius]
/customlight stretch <id> <x> <y> <z>
/customlight pivot <id> <x> <y> <z>
/customlight angle <id> <degrees>
/customlight luminosity <id> <amount>
/customlight seethrough <id> <true|false>
/customlight group add <group> [lights...]
/customlight group remove <group> [lights...]
/customlight group modify <group> on|off|animations on|animations off|rename <name>
/customlight group list
/customlight copy [id]
/customlight paste [light]
/customlight list
/customlight editor [id]
/customlight toggle
/customlight quality <low|medium|high|ultra>
/customlight stats
```

- **fade_in / fade_out / time**: ticks. `remove` without a fade uses the light's own fade out.
- **follow**: the light stays on the target and hides while it is not loaded. `tag:<tag>` lights every entity with that scoreboard tag (singleplayer on its own; on servers the plugin sends the tagged entities, since clients never receive tags). `visible_firstperson false` hides the light from the followed player while they are in first person; everyone else (and that player in third person) still sees it.
- **flashlight**: a light with the `flashlight` shape that shines from its holder's eyes, where they look. The holder is a player name, an entity UUID, `tag:<tag>` or `@self` (you), and Tab completes them. Each flashlight has its own id, so you can have as many as you want, put them in groups, remove them with `remove <id>` and keep them with the world. `camera_delay` (0-5 ticks) makes your own beam lag behind the camera, `angle` (default 40) sets how wide it opens, and `flashlight off` takes every flashlight out.
- **set/follow/model_id** on an existing id keep its animations, stretch and (for the same shape) angle.
- **group**: `add` creates a group (and puts the listed lights in it), `remove` without lights deletes the group (its lights stay) and with lights takes them out, `modify` turns the group's lights on or off, stops or plays their animations, or renames it.
- **move**: takes the light from where it is (A) to the given point (B) in `time` ticks (20 ticks = 1 second), with `~` keeping an axis. The four ways of getting there: `smooth` leaves and arrives with no jolt at all, `linear` keeps one speed, `step` makes eight little hops, and `bounce` arrives fast and bounces a few times before settling. Only a command: the editor does not show it.
- **quality**: how sharp and smooth the lights are (resolution and atmosphere samples); every visible light is drawn at every level (saved in `config/customlights/settings.json`). `medium` is the default.
- **stats**: lights drawn last frame; run it twice to also see the GPU time of each light pass.

### Copy and paste

`/customlight copy [id]` copies lights (all of them without an id) as one `/customlight paste {...}` command, like FogTools. `/customlight paste` without text reads the clipboard, so long lights work even past the 256 character chat limit. The editor's **Copy command** button copies the same thing.

```text
/customlight paste {(type:static,id:lamp,pos:[0.5,70,8.5],shape:cone,color:warm,radius:3,distance:1.5,intensity:2,atmosphere:4,fadein:10,fadeout:10,yaw:0,pitch:90,stretch:[1,1,1],presets:[pulse:60:0.9],colors:[warm:20,magenta:40],size:[4,2,40],radiuspulse:[3,60])}
```

Cones, flashlights and sirens also take `angle:<degrees>`. `type` is `static`, `follow` (`target`, `offset:[x,y,z]`, `firstperson`), `model` (`pos` is the offset from the bone) or `flashlight` (`delay`, and `target` when someone else holds it). Several lights go one after another: `{(...)(...)}`. Only `id` is required.

### Animations

All animations loop until `animate <id> stop`. They also work on model lights (by model id).

- Presets: `circle random orbit figure8 spiral bounce sway float zigzag firefly` (movement), `pulse flicker strobe breathe heartbeat candle lightning glitch` (intensity), `rainbow disco police` (color), `sweep scan wobble pendulum` (direction).
- Stage presets for spotlights, cones, flashlights and sirens, all starting from where the beam starts: `ballyhoo` (figure eight), `search` (looks around at random), `chase` (glides between four aim points), `grow` (the beam grows out and pulls back in), `shoot` (shoots out, stays, fades), `iris` (opens and closes) and `rotation` (turns around its own axis, clockwise; made for `siren`).
- Every preset is continuous: loops never jump back, random ones never pause, blinks and color changes blend.
- Each preset has its own `time` (ticks of one loop) and `amount`: the radius in blocks around the light's position for movement presets, the depth for `pulse`/`breathe`, the strength for `flicker`, the on-time fraction for `strobe`, the saturation for `rainbow`, colors per loop for `disco`, degrees for the direction and stage presets (`rotation`: degrees per loop, 360 by default) and how far `grow` pulls back.
- `animate <id> circle+pulse 60` replaces the presets (combine with `+`); `add` adds or updates one and keeps the rest; `remove` removes one. Values left out keep the current ones.
- `animation_radius <radius> [time]`: without a time sets the radius of every movement preset; with a time their radius pulses to the new one every `time` ticks.
- `color_transition`: any number of colors, each with the ticks it takes to blend into the next one (the last blends back into the first).
- `size`: pulses radius and distance between the light's own values and the given ones.

## Editor

Key **J** (configurable) or `/customlight editor [id]`; J closes it again. You can keep walking while it is open.

- Only the left button interacts; the mouse wheel only scrolls.
- Sliders change by dragging from wherever you click (Shift = fine, Ctrl = coarse); double click one to type a value. The ⟲ button next to each one puts its default back.
- Coordinates change by dragging sideways over them; click them to type.
- **Ctrl+S** saves, **Ctrl+Z** undoes and **Ctrl+Y** (or Ctrl+Shift+Z) redoes, on the Light and Objects tabs.
- **Gizmo**: the light being edited has Axiom-style handles in the world. Drag the red/green/blue arrows to move it along an axis, the white square to move it freely, the green ring to turn it (yaw), the red ring to tilt it (pitch), the orange knob outside the rings to aim it, the yellow diamond to change its radius, the cyan squares (on the other side of the arrows) to stretch it and the purple diamond to move the pivot the stretch grows from. The handle closest to the mouse is the one you grab. Hold Ctrl to snap (half blocks, 15°).
- **Right click** held over the world turns the camera; **F5** changes the camera view (to see follow lights on yourself).
- Radius and distance sliders change in proportion to their value, so small lights are as easy to tune as big ones.

**Light** tab: one light. Pick the type (static, follow, flashlight) and the shape from the drop-down lists; picking a shape loads its ready-made look. **Player position** / **Player view** place the light and **Player camera position** points it where you look. Changing the ID of a saved light and saving makes a copy with the new ID (the original stays). Follow lights start as a sphere, have a list with you, the online players and the entity you are looking at (by UUID); in the Target field, Tab completes a player name or the UUID of the entity you look at, and show what they found ("On you", "On player ...", "Not found nearby"). **Stretch: On** shows the X/Y/Z stretch and the pivot it grows from, **Angle** shows for cones, flashlights and sirens, and **Animations: None** hides the presets (each with its own time and amount), the color transition (as many colors as you want, each with its time) and the size pulse. The flashlight has **Target** (you, or like a follow target a player, the entity you look at or `tag:<tag>`, with Tab completion) and **Camera delay** instead of animations. **Through blocks: No** makes solid blocks stop the light: it only reaches what it can see, half a block at a time, so slabs and stairs cast the shadow of their real shape (the glow in the air is shadowed too). Those lights are drawn at full resolution, so the shadow lands on the exact pixel the block covers. The bottom bar shows the paste command with **Save**, **Copy command**, **New** and **Close**.

**Saved lights** tab: every light here, the flashlight too, with Edit, Copy and Del, in groups. **Disable all** / **Enable all** turns every saved light off or on, **+ New group** makes a group (click a group name to rename it), and each group can be folded, turned on or off, made still (animations off) or deleted (its lights stay). Server lights can be dragged into groups too (the group is kept on this client, even when the server sends the light again). Drag a light by its name onto another group (or onto "No group") to move it. Groups are kept per world.

**History** (top right, like Custom Waypoints): the last 8 lights and object lights the editor added, overwrote or deleted, with the time. Click one to open that version in the editor; **Restore** saves it back. Kept in `config/customlights/history.json`.

**Copy props** / **Paste props** (bottom bar) copy the look of a light (shape, color, size, angle, stretch, animations...) and paste it onto another one, which keeps its own ID and position.

**Objects** tab: lights for kinds of objects, saved in `config/customlights/objects.json` (no copy/paste):

- **Edit object** / **Presets** switch between editing one object and managing presets.
- **Item** (optionally only with one custom model data), **Block**, **Particle**, **Entity**, **Special** or **Bone**. Search the list (with icons; mods are included, search `modid:` to see only one mod) and click an object. Typing a full id that is not in the list (`mymod:thing`) offers **+ Use** it anyway. New object lights start small, as objects are.
- **Display** (items only): like Blockbench, pick a context (1st person right/left, 3rd person right/left, head, ground, item frame), turn the light on or off for it and move it in pixels; the preview shows the item as in that context. Each context keeps its own light settings, so first person can look different from third person (hover the buttons for their full names). By default only first person is on.
- The object shows in front of you with its light (items and blocks turn slowly, entities stand still facing you, particles are spawned), and every changed value shows at once, also on the real objects around you. **Preview here** moves the preview to where you are looking, and the gizmo moves the light's offset (for items, the pixel offset of the display context being edited).
- **Offset** moves the light from the object (from the item centre, the block centre, the particle, the middle of the entity, or the bone).
- **Bone** shows the name to give the bone in Blockbench: `customlights_<id>`.
- **Save** stores it, **Delete** removes it, and **Saved objects** lists them.

**Set to everyone** shows at the top of the Light and Objects tabs on servers with the CustomLights plugin, when you may edit the server's lights (operators, permission `customlights.admin`). While it is on, **Save** sends the light or object light to every player (and the server keeps it for players who join later), **Delete** takes it away for everyone, a server light being edited follows your changes live, and **Copy command** copies the `/lightplugin paste @a ...` command. While it is off everything stays on your client. In the Presets view, **Share** puts a preset in every player's preset list (shown as `server/<name>`, **Unshare** takes it back), and **Load** / **Unload** with Set to everyone on add or remove the preset's objects for everyone.

## Object lights

- **Items** carry the light inside their model, so it moves with the item like a part of it, in the display contexts it is turned on for (first person by default).
- **Blocks** light up where they are placed, so any block can be a light. Only blocks with a face in the open count. Up to 64 block lights are drawn within 112 blocks: the nearest chunks get one light per block, and only when there are too many are blocks merged (per 4-block cell, then per 16-block section, furthest first). A merged light is the block light swept over the box its blocks fill, so it looks like the blocks together, not like a blob.
- **Particles** carry their light while they live; nearby ones merge the same way (up to 32 lights).
- **Entities** carry their light while they are drawn; it turns with the entity. Crowds merge (up to 32 lights).
- **Special**:
  - `beacon_beam` (tinted by the beam color), `end_gateway_beam` (while a gateway shoots its beam) and `end_crystal_beam` (from the crystal to the block it points at);
  - `conduit` (active conduits) and `lit_blocks` (every lit furnace, smoker, campfire, candle, redstone lamp... any block that is lit);
  - `burning_entity`, `glowing_entity` (tinted by team color), `charged_creeper`, `enchanted_item` (dropped enchanted items), `enchanted_gear` (entities holding or wearing enchanted items);
  - groups: `hostile_mobs`, `animals`, `players`, `projectiles`, `bosses`, `item_entities`.
- **Bones**: any model part named `customlights_<id>` is hidden and the light of the bone `<id>` is drawn where the part is, following its animation. `/customlight model_id <id> ...` sets the same light from a command (and the server plugin sends its own).
  - **GeckoLib** (optional): name a bone `customlights_<id>` in Blockbench. The light sits on the bone pivot and turns with the bone. Works on entities, blocks, items and armor.
  - **Item models / ModelEngine**: in a Java item model exported from Blockbench, a group (bone) named `customlights_<id>` is removed from the model and becomes a light at the group pivot. A model *file* named `customlights_<id>.json` (like the per-bone item models ModelEngine generates) is not drawn and becomes a light at its centre.
  - `x y z` of `model_id` offset the light from the part (in blocks, part space). `opacity` 0-1 is the atmosphere, `light` the intensity. Without `distance` the blur equals the radius.

Static JSON *block* models placed in the world are not scanned for bones (use a Block object light instead).
Lights held in first person keep almost no atmosphere, so they don't fog your own view.

### Presets

The **Presets** view of the Objects tab keeps named lists of object lights in `config/customlights/presets/<name>.json`:

- **Save all objects** stores every saved object light under the name; **Add this object** adds the one being edited.
- Each preset in the list can be **Load**ed (its objects are added), **Unload**ed (its objects are taken out) or deleted (click Del twice).
- Below, the objects of the selected preset can be edited or removed one by one.
- **Open presets folder** opens the folder, to share presets.

## Performance

- The normals and depth of the scene are worked out once per frame, and all the light work runs at half resolution (a quarter on `low`); the atmosphere runs at a quarter (an eighth on `low`, half on `ultra`). One final pass puts it on the frame, estimating the surfaces' own color only where there is light.
- Light adds up and darkness combines separately, so dark lights and bright ones overlap without flickering, in any order.
- Lights outside the view, hidden behind terrain (in no chunk section the game is drawing), or smaller than a pixel or two, are skipped, so lights you cannot see cost nothing. Every other light is drawn at every quality level (up to 256 per frame); lights covering a big part of the screen only get fewer atmosphere samples.
- Each light only touches the screen area of its own box (tight for beams and cones), and pixels outside its reach stop after one read.
- Blocks are indexed a few milliseconds per tick at most, only when a rule for them exists, and merged as described above.
- `/customlight quality low` roughly halves the cost again; `/customlight toggle` turns every light off.

## Storage

Client lights are saved per world in `config/customlights/worlds/<world>.json`, the quality level in `config/customlights/settings.json`, and belong to the dimension they were created in. Object lights (and bones) are shared by every world in `config/customlights/objects.json`; an old `models.json` is moved there automatically. Server lights are never saved on the client.

## Compatibility

- **Sodium**: supported. Lights hidden in chunk sections Sodium does not draw are skipped too.
- **Iris / shader packs**: supported. With a shader pack on, the light is added on top of the shader's image as it is (the shader already lights the scene its own way), and nothing is taken from the shadow pass.
- The development game (`./gradlew :<version>:runClient`) comes with that version's Sodium and Iris, and Complementary Reimagined goes in `versions/<version>/run/shaderpacks`; `-PnoCompat=true` runs without Sodium and Iris.
- GeckoLib bones work on the versions GeckoLib publishes for (1.20.1 to 1.21.1); model markers inside baked block and item models need 1.21.4 or older.

## Server plugin

`CustomlightsPLUGIN` (Paper/Spigot 1.20.1+): `/lightplugin` (alias `/lpl`, permission `customlights.admin`) takes the same arguments with a target after the sub command:

```mcfunction
/lightplugin set <@a|player|selector> <id> <x> <y> <z> <shape> <color> <radius> <distance> <intensity> <atmosphere> <fade_in> <fade_out> [yaw] [pitch]
/lightplugin follow <targets> <id> <player|uuid|tag:<tag>|@selector> <offX> <offY> <offZ> <shape> ... [yaw pitch] [visible_firstperson]
/lightplugin move <targets> <id> <x> <y> <z> <smooth|linear|step|bounce> <time>
/lightplugin remove <targets|all> <id|all> [fade_out]   (/lightplugin remove all: every light of everyone)
/lightplugin model_id <targets> <id> <x> <y> <z> <shape> <color> <radius> <opacity> <light> <fade_in> <fade_out> [distance] [yaw] [pitch]
/lightplugin model_remove <targets> <id|all> [fade_out]
/lightplugin animate <targets> <id> <preset[+preset]> [time] [amount] | add ... | remove ... | color_transition <c1> <t1> <c2> <t2>... | size ... | animation_radius ... | stop
/lightplugin stretch <targets> <id> <x> <y> <z>
/lightplugin pivot <targets> <id> <x> <y> <z>
/lightplugin angle <targets> <id> <degrees>
/lightplugin seethrough <targets> <id> <true|false>
/lightplugin object list | object remove <key|all>
/lightplugin preset list | preset remove <name|all>
/lightplugin group <targets> list | add <group> [ids...] | remove <group> [ids...] | modify <group> on|off|animations <on|off>
/lightplugin paste <targets> {(...)}
/lightplugin flashlight <targets> <id> <holder> <color> <scale> <distance> [intensity] [atmosphere] | off
/lightplugin luminosity <targets> <id> <amount>
/lightplugin list [targets]
/lightplugin reload
```

- `all` works as a target like `@a`. `remove <targets> all` (or just `/lightplugin remove all`) removes every light, no ids needed, and never complains about a light called "all"; with `@a` it also removes the players' own copies and tells the clients to drop every light of this server. Object lights have their own `object remove all`.
- Every argument completes with Tab: targets and player names (`@a`, `all`, selectors), light and model ids (plus a free name like `light1` for new ones), what a follow light can follow (players, `tag:`, nearby entity UUIDs), shapes, colors (and `#RRGGBB`), `~` coordinates, group names, animation presets, object keys, shared preset names, `true`/`false`, and sensible numbers for sizes, fades, times and angles.
- `group` names the group its lights show in on the clients (the editor lists it), and `modify` turns that group on or off, or stops its animations, for the players. A light a player dragged into a group on their own client keeps their choice.
- `@a` saves the light for everyone: it is sent to everyone online right away and to every player who joins later.
- A player name or selector (`@p`, `@a[distance=..10]`...) saves it for each of those players only.
- `paste` takes what the client editor or `/customlight copy` copied (the server cannot read your clipboard, so paste the text).
- Everything is saved in `plugins/CustomLights/lights.json`. On join the player receives the full current list, so a light removed while they were offline is gone for them too.
- `~` coordinates start at the command sender (player, command block), otherwise at the only target player or the main world spawn. Static lights are tied to that world's dimension.
- Flashlights are saved like any other light: they come back when players join, go in groups and are removed by id.
- Players with the mod say hello when they join: the plugin sends them everything (lights, model lights, object lights and shared presets) and tells them whether they may edit from the editor (checked again every second, so op / deop applies at once). Players who join later get what was set while they were away, and what was removed is gone for them.
- Object lights and presets sent from the editor are for everyone; `object` and `preset` list and remove them.

## API for other mods

Everything lives in one class, `com.cotii.customlights.api.CustomLights`. It is client side: call it from client code only
(client initializer, client tick, key bindings, client networking handlers). The lights you make are the player's own, so they
are saved with the world, show up in the editor and can go in groups. Put your mod id in the light id so it never clashes with
someone else's.

### Depending on it

The jar is on Modrinth, which serves a Maven repository, so a Fabric project can pull it straight in:

```gradle
repositories {
    maven { url = "https://api.modrinth.com/maven" }
}

dependencies {
    // Compile against it without forcing your users to install it
    modCompileOnly "maven.modrinth:customlights:1.0.2"
    // Use modImplementation instead if you also want it in your dev runtime
}
```

If you would rather keep a copy in the repository, drop the jar in `libs/` and use
`modCompileOnly files("libs/customlights-1.21.1-1.0.2.jar")`. On NeoForge the jar goes in as a normal dependency
(`compileOnly files("libs/customlights-neoforge-1.21.1-1.0.2.jar")`), and on Forge with
`compileOnly fg.deobf(files("libs/customlights-forge-1.20.1-1.0.2.jar"))`. The API is the same class on every loader.

With `modCompileOnly` the mod stays optional at runtime, so check it is installed before you touch the API:

```java
private static final boolean LIGHTS = FabricLoader.getInstance().isModLoaded("customlights");
```

Or make it a real dependency in `fabric.mod.json`:

```json
"depends": { "customlights": ">=1.0.2" }
```

### Making lights

```java
import com.cotii.customlights.api.CustomLights;

// A lamp standing in the world
CustomLights.light("mymod:lamp")
        .at(100.5, 70, 8.5)
        .shape("sphere")
        .color(0xFF8800)
        .radius(3).blur(1.5f).intensity(2).atmosphere(3)
        .add();

// A campfire with the look and the animations the editor gives it, only bigger
CustomLights.light("mymod:bonfire")
        .at(12.5, 64, -30.5)
        .style("fire")
        .radius(1.4f)
        .luminosity(4)
        .add();

// A portal ring that follows the player, fading in over half a second
CustomLights.light("mymod:aura")
        .follow("@self", 0, 1, 0)
        .style("portal")
        .color(0x40C0FF)
        .fade(10, 10)
        .add();

CustomLights.remove("mymod:lamp");
```

`light(id)` starts from the light that already has that id when there is one, so calling it again and calling `add()` updates it
instead of making a second one. `shape(name)` only swaps the shape; `style(name)` also applies that shape's ready-made look and,
for the shapes that move on their own, its colour and its animations. `animation(preset, ticks, amount)` adds one animation by
name (`flicker`, `pulse`, `breathe`, `rotation`, `strobe`, `rainbow`, `lightning`, `sway`, and the rest of the names the commands
take).

### A flashlight on a custom item

A flashlight is a follow light with the `flashlight` shape, pointing where its holder looks, so this is all it takes. This
example turns one on and off when the player uses your item, on Fabric:

```java
package com.example.torchmod;

import com.cotii.customlights.api.CustomLights;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;

public class TorchModClient implements ClientModInitializer {
    private static final String LIGHT_ID = "torchmod:flashlight";
    private static boolean on;

    @Override
    public void onInitializeClient() {
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack held = player.getItemInHand(hand);
            if (level.isClientSide && held.is(TorchMod.FLASHLIGHT_ITEM)) {
                toggle();
            }
            return InteractionResultHolder.pass(held);
        });
        // Put it out if the player stops holding the item or leaves the world
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (on && (client.player == null || !client.player.getMainHandItem().is(TorchMod.FLASHLIGHT_ITEM))) {
                turnOff();
            }
        });
    }

    private static void toggle() {
        if (on) {
            turnOff();
        } else {
            turnOn();
        }
    }

    private static void turnOn() {
        if (!CustomLights.ready()) {
            return;
        }
        CustomLights.light(LIGHT_ID)
                .follow("@self", 0, 0, 0)   // "@self" is the player; a name, an entity UUID or tag:<tag> also work
                .style("flashlight")        // shape, reach, angle and rim of the editor's flashlight
                .color(0xFFF3D0)
                .radius(2.5f)               // the beam reaches twelve times this
                .intensity(2.8f)
                .atmosphere(1.5f)
                .cameraDelay(2)             // the beam lags a couple of ticks behind the camera, like a real torch
                .throughBlocks(false)       // walls stop it
                .fade(4, 6)
                .add();
        on = true;
    }

    private static void turnOff() {
        CustomLights.remove(LIGHT_ID, 6);
        on = false;
    }
}
```

Give the light to something else by passing a player name, an entity UUID or `tag:<tag>` to `follow`, for example
`.follow("tag:lit", 0, 1.2, 0)` for every entity carrying that tag.

### The rest of the class

- `ready()`: false before the player is in a world, or if the mod's shaders failed to build. Check it before adding lights.
- `ids()`, `exists(id)`, `remove(id)`, `remove(id, fadeOut)`.
- `shapes()` and `animatedShapes()`: the names `shape(...)` and `style(...)` take.
- `setEnabled(on)`, `enabled()`, `drawnLastFrame()`.
- On the builder: `at`, `follow`, `shape`, `style`, `color`, `radius`, `blur`, `intensity`, `atmosphere`, `luminosity`,
  `direction`, `angle`, `stretch`, `pivot`, `fade`, `throughBlocks`, `group`, `animation`, `clearAnimations`, `cameraDelay`,
  `visibleFirstPerson`, then `add()`, or `moveTo(x, y, z, easing, ticks)` to slide a light that is already there.


## Building and testing

One project builds every supported game version (Stonecutter): 1.20.1, 1.20.4, 1.21.1, 1.21.4, 1.21.5, 1.21.8, 1.21.11 and 26.2. Each version has its own tasks and jar in `versions/<version>/build/libs`:

```bash
./gradlew :1.21.1:build
./gradlew :1.20.1:runClient
```

1.20.x build with Java 17, 1.21.x with Java 21 and 26.x with Java 25. The shared sources are written for 1.21.1; what differs lives in a few classes (`compat/Compat`, `compat/DevCompat`, `compat/GuiCanvas`, `render/Gl`, `network/SyncNetwork` and some mixins):

- `src/versioned/<overlay>`: replacement classes shared by several versions, listed in `version_overlays` in `versions/<version>/gradle.properties` (later overlays win).
- `versions/<version>/src`: classes and resources (mixin lists) only that version uses.
- `versions/<version>/source-rewrites.txt`: plain renames (`Old -> New`, one per line) applied to a copy of the sources before compiling, for names Mojang or Fabric changed (1.21.11 and 26.2).

**NeoForge** builds every version the Fabric one does, in `neoforge/<version>` (ModDevGradle, one Gradle build per version). They compile from the same layers as the Fabric build, including that version's overlays and its `source-rewrites.txt`, so the shared code never forks. The Fabric API calls go to small stand-ins in `neoforge/shared` fed by NeoForge's events (client commands keep their Tab completion), and each version adds only what NeoForge changed there: the render and tick hooks, the plugin channels and, on 1.21.11 and 26.2, the newer render context. NeoForge 1.20.1 is the fork of Forge, so it builds through the Forge script pointed at `net.neoforged:forge`.

```bash
./neoforge/1.21.1/gradlew -p neoforge/1.21.1 build
```

**Forge 1.20.1 and 1.20.4** have their own builds in `forge/<version>` (ForgeGradle, Java 17), from the same sources and the `legacy-1.20` overlay, with the same kind of stand-ins in `forge/shared`. Install `forge/<version>/build/libs/customlights-forge-<version>-1.0.2.jar` (MixinExtras is inside):

```bash
./forge/1.20.1/gradlew -p forge/1.20.1 build
```

Sodium and Iris are recognised on Fabric and NeoForge, and so are Embeddium and Oculus, their 1.20.x ports.

Two things the Fabric build has that NeoForge and Forge do not: lights read out of item models (Blockbench groups), because neither loader has an equivalent of Fabric's model loading hook, and the editor's floating object preview on 1.21.11 and 26.2, because their render events do not hand out the frame's drawing queue. Everything else is the same on every loader.

`./gradlew :<version>:runDevtest` opens a superflat test world, builds a scene, places lights with the real commands and saves screenshots to `run/screenshots`, then measures the frame rate and GPU time of each pass with many lights (`-PdevtestQuit=true` closes the game afterwards).

## License

CC0.

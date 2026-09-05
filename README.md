![CreateSecurityBannerTitle](https://cdn.modrinth.com/data/cached_images/310c1aba571065c6114b1fbda2a762fcafa9fb5c.png)
<center><h3>Introducing working CCTV for Minecraft</h3></center>

<p align="center">
  <a href="https://github.com/Kombucha-Works/Create-Security/issues">Report a bug</a>
  &nbsp;·&nbsp;
  <a href="https://github.com/Kombucha-Works/Create-Security">Source</a>
  &nbsp;·&nbsp;
  <a href="https://modrinth.com/mod/create-fabric">Requires Create</a>
</p>

---

Create: Security adds security cameras, monitor walls and alarms that actually work. The displays render the world live, so what you see on a monitor is the real view from that camera. Set up a network, build yourself a proper security room, and finally find out who keeps stealing from your vaults.

Then lock the place down. Keycard readers and code locks on every door, obsidian containers nobody else can break, and kinetic turrets that rise out of the floor when someone gets too close.

---

![overview of the mod](https://cdn.modrinth.com/data/cached_images/3b5b7daccb850e7784494b9a7192e62ce8e940a7.jpeg)

<h2 align="center">Cameras</h2>


Place a camera and it starts watching. When a player walks into its view cone (16 blocks, 90 degrees), it puts out a full strength redstone signal. Wire that into doors, alarms, trapdoors above lava, whatever you feel is appropriate. Cameras only open for the player who placed them, so nobody can tamper with your setup.

<h2 align="center">Monitors</h2>


Camera Displays placed next to each other merge into one big screen automatically. A single monitor by the front door is fine, but the mod really comes alive once you build a full video wall and sit there watching your friends dig around your base.

There is also a handheld Portable Camera Display. Instead of frequencies, you shift-click a camera to bind it (shift-click again to unbind). Right-click with it in hand to open the live feed, and use the arrows to flip between all your bound cameras.

<h2 align="center">Frequencies</h2>


Cameras and displays link wirelessly through item frequencies, the same way Create's redstone links work. Put any two items in the slots and that combination is your channel. Place a Camera Link next to a display, match the frequency, and the feed shows up. You can run as many separate networks as you like without them interfering, and frequencies are saved with the world.

<h2 align="center">Fisheye Lens</h2>


Stonecut a Fisheye Lens and slot it into a camera to widen the view from 70 to 150 degrees. Handy for covering a whole courtyard with one camera instead of four.

<h2 align="center">Player Lists and Alarms</h2>


A Player List item lets a camera or turret watch for specific players only, or the other way around, ignore your friends and trigger on everyone else. Pair the camera with the Alarm block, and you've got a full security system set up.

The Alarm mounts on a wall and needs something to hang off. Power it and it blinks, throws red sparks off the lens and runs a looping siren until the signal stops.



<h2 align="center">Turrets</h2>


The Turret is a Create kinetic block. Connect a shaft underneath it and give it rotational force, and it sits there looking like a blank copycat block until something walks into range. Then the telescopic mast rises, the gun swings down from vertical, and it starts tracking.

Everything about it scales with your shaft speed. A faster drive means it deploys quicker, traverses quicker and fires quicker. Cut the power and the turret freezes exactly where it is rather than politely retracting.

Use it with an empty hand to open it up. Any full block applied to the base disguises it permanently, so you can hide one in a wall and forget it is there.

<h3 align="center">Configuration</h3>

<div align="center">

| Control | What it does |
|---|---|
| **Ammo slot** | The rounds it fires |
| **Barrel slot** | Decides the gun, its fire rate and what ammo it accepts |
| **Player List slot** | Restricts it to specific players, or everyone except them |
| **Target Players** | On or off |
| **Target Mobs** | Off · Hostile only · All mobs |
| **Health Filter** | Off · Only above threshold · Only below threshold |
| **Extend distance** | 1 to 60 blocks — when the mast rises and it starts tracking |
| **Shoot distance** | 1 to 60 blocks — when it actually opens fire |
| **Health threshold** | 1 to 500 HP — pairs with the Health Filter |

</div>

Everything starts switched **off** and at minimum range, so a freshly placed turret ignores the world until you configure it. That is deliberate; no accidentally shooting yourself the moment you power it.

Leave the barrel slot empty, and the turret still rises and tracks; it just has no gun and cannot shoot. Handy if you only want something menacing that follows people around.

<h3 align="center">Barrels</h3>

<div align="center">

| Barrel | Rate | Notes |
|---|---|---|
| **Shotgun Barrel** | 40 rpm | Twin bore, fires two rounds at once, wide spread |
| **Automatic Barrel** | 120 rpm | Accurate, magazine fed |
| **Minigun Barrel** | 4000 rpm | Six barrels that spin up while firing. One round item feeds 25 shots |
| **Rocket Barrel** | 30 rpm | Slow launcher tube. The rounds break blocks |

</div>

<h3 align="center">Ammo</h3>

Each barrel takes its own family and nothing else, so you cannot feed slugs into a minigun.

<div align="center">

| Barrel | Ammo |
|---|---|
| Shotgun | 12 Gauge Slug · 12 Gauge Buckshot · 12 Gauge Incendiary Buckshot |
| Automatic | Regular 5.56mm · Incendiary 5.56mm |
| Minigun | Regular 7.62×51mm |
| Rocket | Rocket · HE Rocket |

</div>

Slugs hit hardest in a single round, buckshot sprays pellets, incendiary rounds set what they hit on fire, and the two rocket types explode on impact — the HE Rocket about as hard as TNT. Rockets damage terrain, so think about where you point one.

Raise a shield and it soaks the shot completely, but it wears out fast; twenty rounds and it is gone, whatever the barrel. Do not expect to walk up to a minigun behind a shield.

Bullets punch straight through glass, shattering it on the way, and the turret targets through windows. If you build a glass-walled control room, the turret can still defend it from the outside.



<h2 align="center">Locks and Keycards</h2>


A Keycard Reader can be mounted on doors, trapdoors, chests, ender chests, barrels, furnaces, smokers, blast furnaces, and shulker boxes. Program it with a Keycard, and only that card opens it. The little LED on the front glows green while whatever it guards is open, and red again once it closes.

Doors opened with a keycard close themselves after two seconds, and tapping the card again shuts them straight away.
- **Keycard** — dyeable, gets its own ID the first time you hold it
- **Universal Keycard** — opens every armed reader, whatever it was programmed with (Creative Mode Item)
- **Keycard Editor** — an anvil-shaped bench that clones one card's ID onto another and recolours it. The source card is never consumed, so it is a copier
- **Code Lock** — mounts exactly like a reader, but opens with a four digit keypad instead of a card. Get the code wrong and it shocks you, dealing damage

Break a shulker box that has a reader on it and the reader stays attached to the dropped item, so you never lose the lock on a box full of loot.

<h2 align="center">Obsidian Security Blocks</h2>


Obsidian versions of every block a reader mounts on: door, trapdoor, chest, ender chest, barrel, furnace, smoker and blast furnace. They have bedrock-grade blast resistance, ignore the wither and the dragon, and only the player who placed one can break it. Everyone else cannot even crack the surface.

The obsidian door and trapdoor never open by hand. With no lock fitted, they respond to redstone, like an iron door. Fit a reader or a code lock, and redstone stops working, so the card or the code is the only way through.

Put a keycard reader on an obsidian chest, and that is about as locked as a container gets.



<h2 align="center">Getting Started</h2>

**Cameras:** Place a camera, put two items in its frequency slots, build a display wall, place a Camera Link next to it, and set the same two items. Done; the feed is live.

**Turrets:** The turret and all four barrels are built on a Create assembly line: a depot, a belt, deployers, and presses. Check the recipe in JEI, feed the base item along the belt, and let the machine do the work. Once you have one, drop it down, run a shaft into the bottom, load a barrel and matching ammo, then open it up and set your ranges.

A lot of blocks also have Ponder scenes, so if you get stuck, just hold W while hovering over the item and the game will show you what to do.




<h2 align="center">Requirements</h2>

<div align="center">

| Dependency                                                | Version     |
|-----------------------------------------------------------|-------------|
| Minecraft                                                 | 1.20.1      |
| Fabric Loader                                             | 0.18.0+     |
| [Fabric API](https://modrinth.com/mod/fabric-api)                                            | Latest      |
| [Create (Fabric)](https://modrinth.com/mod/create-fabric) | Any Version |
| [GeckoLib](https://modrinth.com/mod/geckolib)             | Any Version |

</div>

<p align="center">Fabric only. Works in singleplayer and on dedicated servers.</p>

---

<h2 align="center">FAQ</h2>

<details>
<summary><b>Does it work with shaders?</b></summary>

No, our mod hooks deep into the rendering pipeline of Minecraft, and our current renderer conflicts with Iris' renderer, which causes either a White or Black screen. We are addressing this issue, and we will hopefully manage to fix it.
</details>
<details>
<summary><b>Does it lag?</b></summary>

Each visible display renders an extra view of the world, so a giant wall of monitors is not free. A handful of screens is fine on most PCs. If you build a 50 screen control room, that is on you. 

</details>

<details>
<summary><b>Do turrets grief my base?</b></summary>

Only the Rocket Barrel does. Rockets and HE Rockets explode on impact and break blocks, so keep them pointed away from anything you like. Every other barrel leaves the world alone, apart from glass, which all bullets shatter.

</details>

<details>
<summary><b>My turret will not shoot.</b></summary>

Check, in order: it needs rotational force from a shaft or motor underneath, configured targeting filters, a barrel fitted, ammo that matches that barrel, and a shoot distance high enough to reach the target. Both range sliders start at 1 block, so a fresh turret really will let someone walk right up to it.

</details>

<details>
<summary><b>Can I use it in my modpack?</b></summary>

Yes! We love seeing the mod in modpacks. Free modpacks on platforms we officially publish on (currently just Modrinth) are fine without asking. Anything else needs written permission; see the <a href="https://github.com/Kombucha-Works/Create-Security/blob/main/LICENSE">license</a> for details.

</details>

<details>
<summary><b>Other Minecraft versions?</b></summary>

Only 1.20.1 right now. Ports to other versions are planned.

</details>

<details>
<summary><b>Contact?</b></summary>

Shoot us an email at <a href="mailto:kombuchamods@gmail.com">kombuchamods@gmail.com</a>.

</details>

---

<p align="center">
Found a bug or have an idea? <a href="https://github.com/Kombucha-Works/Create-Security/issues">Open an issue on GitHub.</a>
<br><br>
Made by <b>SuaveHickory500</b> and <b>Hardstone</b> · Kombucha-Protection-License v1.5
</p>

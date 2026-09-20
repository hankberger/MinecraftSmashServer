# Fighter kits

These are implemented server mechanics for the five-fighter roster. All work with a vanilla Java client. Values are initial tuning, not a claim of competitive balance.

## Shared controls

- **A/D:** movement and forward aim. No sprint modifier required.
- **Left click:** forward light while grounded; neutral aerial with no direction held in the air.
- **W/S + left click:** up/down light. W/S take priority over A/D.
- **Right click:** primary special. **F:** secondary special, on the ground or in the air. Uses Minecraft's Swap Item With Offhand binding, so rebinding that control changes this key too. **S + right click** remains an alternate input.
- **Space:** jump → double jump → class recovery, with a fresh press for each action. Tap/hold the jumps for short/full height; holding Space does not chain actions. W does not change the sequence. Landing restores the existing air options.
- **W + right click:** direct recovery shortcut, including before the double jump. Recovery spends remaining air options until landing. The sequence adds no new boosts or extra lift.
- **S:** crouch the fighter model, platform drop or fast fall. Release to stand. Villager uses a dip and head bow. Press S and attack together to claim the short attack chord before dropping.
- **Shift:** shield. In the air it is a brief guard, once per landing, retaining gravity and drift.

Attacks have one short input buffer, remembering direction and facing for 150 ms. Melee keeps its facing during startup/contact. Damage percentages below are additions to the opponent's percentage. Recovery spends remaining jump/recovery options until landing. None of the kit interactions refund those options.

## Steve — spacing and explosive setups

Steve wants blade distance, then a committed finishing hit. His hilt is deliberately weak; running directly into an opponent is less effective than controlling the space in front of them.

| Move | Input | Behavior |
|---|---|---|
| Sword Swipe / Air Slash | Forward light | 4% inside 0.95 blocks; 7% through the middle; 9% from 1.95 blocks out. Tip contact also launches harder. Distance is measured between fighters, within the actual curved hit area. |
| Overhead Cut / Rising Cut | Up light | 6%; a short overhead sword arc for catching jumps. |
| Shovel Lift | Ground down light | 5%; pops an opponent upward. A real hit opens a faster Pickaxe Smash follow-up. |
| Anvil Drop | Air down light | Releases a visible, falling anvil beneath Steve for 10% and downward launch. It carries some drift, stops on terrain, and hits one opponent. Steve remains free after the attack's recovery. |
| Sword Spin | Neutral air | 6%; covers close approaches on either side. |
| Pickaxe Smash | Primary special | 15%, or 18% at its outer sweet spot from 2.15 blocks. Startup is normally 4 ticks; a shovel confirm reduces it to 2, with a minimum delay to respect the victim's hit immunity. |
| TNT Toss | Secondary special | Tosses a bouncing TNT with a 30-tick fuse and a 2.25-block blast radius. 16%; strong launch. One TNT at a time, with a 40-tick use cooldown. |
| Piston Pop | Recovery | Strong vertical rise with 6% contact damage and modest horizontal steering. |

**Setups:** shovel into pickaxe; cover the ground with TNT and catch the resulting jump with an overhead; drift over a landing opponent and drop an anvil.

**Counterplay:** shield the sword to recoil Steve, approach inside the blade, or punish a missed pickaxe. Light melee can bat TNT in the attack's direction, transferring ownership and KO credit. The original thrower can be hit by the returned TNT. Batting never resets its fuse. Smoke and a flashing block/radius ring warn of detonation; terrain blocks the blast and is never destroyed.

## Alex — contact chains and movement mix-ups

Alex gets the fastest run and strongest air steering, but is light and has short reach. Her reward comes from choosing a follow-up after contact rather than repeating one attack through shields.

| Move | Input | Behavior |
|---|---|---|
| Quick Slash → Cross Cut → Launch Kick | Successive ground forward lights after contact | 5% → 4% → 7%. Later hits step Alex forward; the third launches upward. Each link requires an unblocked hit and another click within the confirm window. The finisher cannot immediately loop back into the opener. |
| Passing Cut | Air forward light | 6%; a quick forward cut while drifting. |
| Flick Slash / Scissor Kick | Up light | 4% / 5%; short, quick overhead attacks. |
| Low Cut | Ground down light | 4%; a low advancing slide with a crouched pose. |
| Heel Cut | Air down light | 6%; dives forward and down. An unblocked hit bounces Alex upward once per landing, opening a new aerial approach without restoring air resources. |
| Twisting Cut | Neutral air | 4%; quick coverage around Alex that can confirm into Dash Cut. |
| Dash Cut | Primary special | 12%; forward burst. An unblocked light can cancel into it. Hitting shortens recovery; hitting a shield stops the dash and leaves at least 10 ticks to punish. One aerial dash per landing. |
| Wind Step | Secondary special | A quick backward hop, with no damage or invulnerability. Once per landing, with a 26-tick use cooldown. Does not spend or restore double jump/recovery. |
| Wind Vault | Recovery | Angled rise with the strongest horizontal steering, 5% contact damage. |

**Setups:** choose between the ground chain and an earlier Dash Cut; slide under a jump; dive onto an opponent, bounce and drift into a nair; Wind Step out of an anticipated swing and re-enter.

**Counterplay:** block the opener or dash, attack the landing after a missed dive, and use reach against Alex's short blade. Wind Step is movement, so attacks can still hit it. Whiffs have no early cancel and pressure does not reset her air resources.

## Zombie — heavy pressure and a shield-beating grab

Zombie runs and steers more slowly but is heavier, with slower claws and meaningful punishment for a wrong defensive read.

| Move | Input | Behavior |
|---|---|---|
| Claw Sweep / Raking Claws | Forward light | 9% / 8%; broad, slower claws. A ground swipe takes 6 ticks to start and 18 ticks before another normal action. |
| Grave Uppercut / Sky Rake | Up light | 10% / 9%; close overhead launch. The grounded uppercut has strong vertical finishing power. |
| Grave Fissure | Ground down light | A travelling, low dirt fissure for 6%. Travels about 6 blocks, can hit multiple distinct opponents, stops at walls and platform edges, and has limited setup launch. |
| Grave Stomp | Air down light | 11%; short downward claw/stomp coverage that can spike an opponent below. |
| Flailing Claws | Neutral air | 9%; slower, wider coverage around Zombie. |
| Grave Slam | Primary special | Ground: 18% shockwave, briefly absorbs one light of at most 9% during windup while still taking damage. Air: committed downward plunge, 22% on landing, then landing recovery. No aerial armor. |
| Hungry Grab | Secondary special | 10%; a short 6-tick windup grab that bypasses shielding, throws the victim behind Zombie and removes up to 6% from Zombie's own damage. One victim per grab, 26-tick commitment, 30-tick use cooldown. Works in the air. |
| Grave Rise | Recovery | Strong vertical rise, little horizontal travel, 8% contact damage. |

**Setups:** fissure pressures the floor, uppercut catches a jump, and Hungry Grab answers a shield held against the heavy claws. A grab near an edge can reverse the opponent's position. Landing Slam threatens clustered fighters.

**Counterplay:** jump over the fissure, backstep or jump the short grab and punish its miss. Slam armor starts after the first startup tick, lasts only until impact and works once. A special, a light above 9%, or the next hit interrupts it. Air Slam must reach the floor to hit; it cannot be cancelled into recovery.

## Skeleton — arrows, spacing and a committed finisher

Skeleton's basic attack is now an actual projectile. Quick shots buy space; charged shots reward an accurate read. Skeleton is light, and stronger attacks require time or an opening.

| Move | Input | Behavior |
|---|---|---|
| Quick Shot | Forward light | 5%; a real arrow with moderate speed, gravity and an 18-tick lifetime. Limited knockback/stun makes it a poke rather than the main KO tool. Up to two quick arrows can be active. |
| Sky Shot | Up light | 5%; an upward diagonal arrow that arcs back down. It stops on the underside of platforms rather than hitting through them. |
| Retreating Sweep | Ground down light | 4%; a low bone sweep that steps backward to make room. |
| Descending Shot | Air down light | 5%; shoots diagonally down to cover approaches beneath Skeleton. |
| Bone Spin | Neutral air | 5%; close defensive coverage when someone gets inside the arrows. |
| Bow Shot / Power Shot | Hold, then release primary special | Native bow pose; charging reduces movement. Charge controls arrow speed, range and damage (5–10%). Gravity remains active. Full draw at 20 ticks produces a critical arrow with strong, damage-scaling finishing launch. Cannot start while an owned arrow remains active. |
| Scatter Shot | Secondary special | Three short-lived arrows in a fan for 7% total per opponent, with slight backward recoil. Only one pellet can hurt each opponent. Requires no owned arrows active; 26-tick use cooldown. |
| Bone Vault | Recovery | Vertical escape with modest steering; no attack damage. |

**Setups:** quick arrow to establish distance, low sweep when approached, Sky Shot against a jump, then a charged shot aimed at the opponent's landing. Scatter covers a nearby approach but loses its arrows quickly.

**Counterplay:** use shield and platforms against arrows, vary approach height, and punish a long draw. Quick arrows and scatter have capped launch and short stun. All arrows use swept collision and stop at terrain; neither charging nor firing grants invulnerability. Villager's active nair can reflect them.

## Villager — objects, traps and remote pressure

Villager sets up space instead of chasing every opponent. Each object has a different job, and the opponent can dismantle the setup.

| Move | Input | Behavior |
|---|---|---|
| Parcel Toss | Forward light | 5%; throws an emerald parcel on a shallow, falling path. Two can be active. Limited launch. A parcel that reaches Villager's own bell bats it forward instead of disappearing harmlessly. |
| Parcel Lift / Overhead Delivery | Up light | 6%; short gold overhead strike to catch someone jumping over a trap. |
| Sapling Snare | Ground down light | Plants one visible sapling ahead. It grows after 14 ticks, triggers on an enemy entering its small area for 7% and an upward launch, then disappears. Expires after 120 ticks. |
| Flowerpot Drop | Air down light | Drops a visible flowerpot with gravity for 8% and downward launch. Two can be active; terrain stops them. |
| Parcel Twirl | Neutral air | 6%; gold defensive twirl. Arrows crossing its active area reverse direction and ownership, including damage/KO credit. It does not reflect every object type. |
| Bell Toss / Bell Ring | Primary special | Throws one bell. After landing it takes 8 ticks to arm; right-click then rings it for 12% around the bell. It automatically rings 60 ticks after arming. Melee or parcels can reposition it; it must land and arm again. Ringing also grows owned saplings within 3 blocks early. |
| Golem Shove | Secondary special | Summons a native iron golem 2.2 blocks ahead, visibly winds up for 9 ticks, then punches for 15% with strong horizontal launch. Fixed position and a 48-tick use cooldown; one golem at a time. |
| Firework Float | Recovery | A gentler sustained float with steering, no attack damage. |

**Setups:** plant near a landing and throw parcels to influence the approach; ring a nearby bell to activate the sapling sooner; use Golem Shove where the opponent will be instead of where they were. Bat the bell from a distance to relocate pressure. Reflect an arrow while drifting through a nair.

**Counterplay:** melee and enemy arrows/parcels break saplings. Enemy melee/arrows also destroy bells, starting their replacement cooldown. The golem misses point-blank opponents, cannot punch through terrain and disappears if Villager is interrupted before the attack commits. Saplings do not consume themselves on protected respawning fighters. Objects are finite and are removed on KO, reset, disconnect or match cleanup.

## Implementation and verification

`FighterMoves` defines move data; `CombatState` handles timing, confirms, armor and guard. `BattleObjects` owns native arrows and bells; `KitObjects` owns finite props and summons. `KitState` tracks secondary-special cooldowns and per-landing movement budgets. The server validates all hits and never changes terrain for these attacks. Effects use existing vanilla entities, particles and sounds.

Unit checks cover input/chain timing, shield bypass, spacing, air budgets and launch roles. The dedicated native `RosterClientTest` uses mouse/keyboard inputs for each kit and fixtures for counterplay, object ownership, cleanup and terrain collisions. The same run retains the movement, melee precision and match regressions. Multiplayer feel, matchup balance and readability at different camera/FOV settings still need human playtests.

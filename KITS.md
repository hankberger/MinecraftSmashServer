# Fighter kits

These are implemented server mechanics for the five-fighter roster. All work with a vanilla Java client. Values are initial tuning, not a claim of competitive balance.

## Shared controls

- **A/D:** movement and forward aim at 0.38 blocks/tick (24% below the previous base speed). No sprint modifier required. Alex is 8% faster, with 12% stronger air steering.
- **Left click:** forward light while grounded; neutral aerial with no direction held in the air.
- **W/S + left click:** up/down light. W/S take priority over A/D.
- **Hold/release right click:** charge/fire the primary special. Quick taps retain baseline power; a longer hold improves its class-specific reward. **F:** immediate secondary special, on the ground or in the air. Uses Minecraft's Swap Item With Offhand binding, so rebinding that control changes this key too. **S + right click** remains an alternate secondary input.
- **Space:** jump → double jump → class recovery, with a fresh press for each action. Tap/hold the jumps for short/full height; holding Space does not chain actions. W does not change the sequence. Landing restores the existing air options.
- **W + right click:** direct recovery shortcut, including before the double jump. Recovery spends remaining air options until landing. The sequence adds no new boosts or extra lift.
- **At a ledge:** hold toward the stage to climb, Space to jump, S to drop. Descending fighters auto-grab; S bypasses it. Only the first grab before landing has brief protection. Two grabs maximum, with a regrab delay and a two-second hang limit. Hanging/jumping from a ledge does not refill air resources; landing does.
- **S:** crouch the fighter model, platform drop or fast fall. Release to stand. Villager uses a dip and head bow. S has a 250 ms attack grace period. A successful S + attack claims that press: no platform drop or fast-fall until S is released and pressed again. Fast-fall accelerates gradually, with a 1.45 blocks/tick limit; ordinary falling caps at 1.20. Existing spikes are preserved.
- **Shift:** shield. In the air it is a brief guard, once per landing, retaining gravity and drift.

Attacks have one short input buffer, remembering direction and facing for 150 ms. Melee keeps its facing during startup/contact. Damage percentages below are additions to the opponent's percentage. Recovery spends remaining jump/recovery options until landing. None of the kit interactions refund those options.

Primary charges can turn while held and lock facing on release. Grounded charges plant the fighter: no walking, jumping or platform drops. Air charges brake horizontal drift by 0.10 blocks/tick each tick and disable steering, while gravity and fast-fall continue. Full charge never auto-fires. Hits interrupt the charge; Shift cancels it with eight ticks before shielding. An air jump or recovery can discard the charge to escape, using the existing air budget. Charge is discarded on cancel, KO, leaving or switching hotbar slot. Buffered clicks remember a release that happened before the next action could start. The non-bow specials retain baseline power for the first three ticks, preserving quick taps. Native arm poses and compact weapon props communicate the windup: a raised pickaxe, a braced sword, both Zombies bracing with raised arms, a native bow draw, or a bell rotating around its handle. Steve and Alex use their actual held weapons, so the client keeps them attached through movement, turning and release. The bell retains a fixed size, follows the living model's three-tick interpolation, and disappears on release or interruption; it adds no hitbox or extra reach. A six-segment HUD meter and a single full-charge chime supplement the animation.

## Steve — spacing and explosive setups

Steve wants blade distance, then a committed finishing hit. His hilt is deliberately weak; running directly into an opponent is less effective than controlling the space in front of them.

| Move | Input | Behavior |
|---|---|---|
| Sword Swipe / Air Slash | Forward light | 4% inside 0.95 blocks; 7% through the middle; 9% from 1.95 blocks out. Tip contact also launches harder. Distance is measured between fighters, within the actual curved hit area. |
| Overhead Cut / Rising Cut | Up light | 6%; a short overhead sword arc for catching jumps. |
| Shovel Lift | Ground down light | 5%; pops an opponent upward. A real hit opens a faster Pickaxe Smash follow-up. |
| Anvil Drop | Air down light | Releases a visible, falling anvil beneath Steve for 10% and downward launch. It carries some drift, stops on terrain, and hits one opponent. Steve remains free after the attack's recovery. |
| Sword Spin | Neutral air | 6%; covers close approaches on either side. |
| Pickaxe Smash | Hold/release primary | Tap: 14%, or 17% at the outer sweet spot from 2.15 blocks. Full charge at 18 ticks: 22% / 25%, stronger launch and shield pressure. Charging spends the normal windup, leaving one tick before a fully charged release connects. Shovel confirms still accelerate quick follow-ups. |
| TNT Toss | Secondary special | Tosses a bouncing TNT that explodes immediately on an opponent or uses a 30-tick fuse after a miss, with a 2.25-block blast radius. 16%; strong launch. One TNT at a time, with a 40-tick use cooldown. |
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
| Dash Cut | Hold/release primary | Tap: 11%, four-tick burst. Full charge at 12 ticks: 15%, six-tick burst with more speed and launch. An unblocked light can cancel into the charge. Hitting shortens recovery; shielding stops the dash and leaves at least 10 ticks to punish. One aerial dash per landing, spent on release. |
| Wind Step | Secondary special | A quick backward hop, with no damage or invulnerability. Once per landing, with a 26-tick use cooldown. Does not spend or restore double jump/recovery. |
| Wind Vault | Recovery | Angled rise with the strongest horizontal steering, 5% contact damage. |

**Setups:** choose between the ground chain and an earlier Dash Cut; slide under a jump; dive onto an opponent, bounce and drift into a nair; Wind Step out of an anticipated swing and re-enter.

**Counterplay:** block the opener or dash, attack the landing after a missed dive, and use reach against Alex's short blade. Wind Step is movement, so attacks can still hit it. Whiffs have no early cancel and pressure does not reset her air resources.

## Zombie — a coordinated duo

Zombie now fights alongside a real baby zombie. Quick claws set up delayed partner attacks, and the baby can cover space without moving its owner into danger. The old slam, fissure and shield-bypassing bite are replaced.

| Move | Input | Behavior |
|---|---|---|
| One-Two Claws / Air Claws | Forward light | 6%; three-tick startup, twelve-tick recovery. The baby follows ten ticks later with a short advancing swipe for 3%. Each can hit an opponent once. |
| Tag-Team Uppercut / Sky Rake | Up light | 6%; a short upward starter, echoed by the baby. |
| Ankle Swipe / Double Stomp | Down light | 5% grounded / 8% airborne. The baby echoes the same direction; a centered aerial stomp can spike. |
| Buddy Spin | Neutral air | 5%; close coverage on both sides, with a delayed 3% partner spin. |
| Double Trouble | Hold/release primary | 12% on tap / 18% at 16 ticks, followed by a 6–9% baby swipe. Works on the ground or in the air without forcing a dive. Both raise their arms while bracing. No armor. |
| Buddy Toss | Secondary special | Throws the baby forward in an arc for one 9% contact hit, then it returns to following. Has a 45-tick cooldown and requires the partner to be available. Shield stops it. |
| Buddy Boost | Recovery | Vertical rise with 8% contact. Brings a living partner alongside the owner; does not revive a knocked-out partner or restore air options. |

The baby follows using stage collision and gravity, jumps after its owner, and drops from platforms to regroup. It only attacks when commanded, never from idle proximity. A hit on the owner interrupts an upcoming follow-up. The baby has 18 health, can be staggered, and takes five seconds to regroup after being defeated or falling out of bounds; it waits for its owner to land safely. It shares close-range shielding and spawn protection, but has no independent stocks. Owner KO, reset, disconnect and match end clean it up. A new stock restores it.

**Setups:** claws into the delayed swipe; an overhead followed by a second catch; toss the baby toward an anticipated landing while the owner guards space. Separation is a tradeoff: the baby attacks from its own visible position, so a follow-up is not guaranteed.

**Counterplay:** shield the sequence, hit the owner before the echo, or knock out the baby to create a solo-Zombie window. The partner cannot attack while disabled or generate automatic damage by standing nearby.

## Skeleton — melee spacing and charged arrows

Skeleton can now defend its space with a bone instead of needing to shoot at point-blank range. Strong horizontal melee knockback and Scatter Retreat make room to draw the bow.

| Move | Input | Behavior |
|---|---|---|
| Bone Swing / Heel Kick | Forward light | 7%; melee with 2.6-block reach and strong horizontal push. |
| Bone Jab / Up Kick | Up light | 6%; short overhead melee to catch approaching jumps. |
| Retreating Sweep | Ground down light | 5%; a quick low sweep that steps backward. |
| Heel Drop | Air down light | 7%; downward melee. |
| Bone Spin | Neutral air | 5%; defensive coverage on both sides. |
| Bow Shot | Hold/release primary | Native-looking arrow with charge-scaled speed, range and damage (5–10%). Full draw takes 20 ticks; gravity and terrain collision apply. Grounded draw locks position. Full arrows can finish at high damage. |
| Scatter Retreat | Secondary special | Three short-range arrows sharing one hit per target for 8%, plus a six-tick backward movement. Three-tick startup and 26-tick cooldown. Requires no owned arrow in flight. |
| Bone Vault | Recovery | Vertical escape with modest steering; no attack damage. |

**Setups:** push back with the bone, retreat while covering a pursuit, then charge an arrow toward a landing. Mix up draw duration so opponents cannot always approach during the same timing window.

**Counterplay:** shield arrows, vary approach height, and punish a committed draw or predictable retreat. The bone is melee and can be shielded; retreat has no invulnerability. All arrows use swept collision and stop on terrain; Villager's nair can reflect them.

## Villager — objects, traps and remote pressure

Villager sets up space instead of chasing every opponent. Each object has a different job, and the opponent can dismantle the setup.

| Move | Input | Behavior |
|---|---|---|
| Axe Sweep / Air Chop | Forward light | 8%; close melee with a visible wooden axe and 2.4-block reach. Can bat an owned bell on contact. |
| Overhead Lift / Rising Chop | Up light | 7%; short gold overhead strike to catch someone jumping over a trap. |
| Sapling Snare | Ground down light | Plants one visible sapling ahead. It grows after 14 ticks, triggers on an enemy entering its small area for 7% and an upward launch, then disappears. Expires after 120 ticks. |
| Flowerpot Drop | Air down light | Drops a visible flowerpot with gravity for 8% and downward launch. Two can be active; terrain stops them. |
| Parcel Twirl | Neutral air | 6%; gold defensive twirl. Arrows crossing its active area reverse direction and ownership, including damage/KO credit. It does not reflect every object type. |
| Bell Toss / Bell Ring | Hold/release primary | Tap throws a nearby bell; charging up to 16 ticks throws it farther and raises its eventual ring from 12% to 18%, with stronger launch. A thrown bell immediately rings on opponent contact. After a miss, landing takes 8 ticks to arm. A new hold/release rings an armed bell and can raise its charge further. It automatically rings 60 ticks after arming, even while Villager holds another charge. Melee repositions it without changing its charge; it must land and arm again. Ringing also grows owned saplings within 3 blocks early. |
| Golem Shove | Secondary special | Summons a native iron golem 2.2 blocks ahead, visibly winds up for 9 ticks, then punches for 15% with strong horizontal launch. Fixed position and a 40-tick use cooldown; one golem at a time. |
| Firework Float | Recovery | A gentler sustained float with steering, no attack damage. |

**Setups:** plant near a landing and use axe spacing to influence the approach; ring a nearby bell to activate the sapling sooner; use Golem Shove where the opponent will be instead of where they were. Bat the bell to relocate pressure. Reflect an arrow while drifting through a nair.

**Counterplay:** melee and enemy arrows/parcels break saplings. Enemy melee/arrows also destroy bells, starting their replacement cooldown. The golem misses point-blank opponents, cannot punch through terrain and disappears if Villager is interrupted before the attack commits. Saplings do not consume themselves on protected respawning fighters. Objects are finite and are removed on KO, reset, disconnect or match cleanup.

## Implementation and verification

`FighterMoves` defines move data; `CombatState` handles timing, confirms and guard. `BattleObjects` owns native arrows and bells; `KitObjects` owns finite props and summons. `ZombieCompanions` owns the vulnerable partner, delayed inputs, movement and return lifecycle. `KitState` tracks secondary-special cooldowns and per-landing movement budgets. The server validates all hits and never changes terrain for these attacks. Effects use existing vanilla entities, particles and sounds.

Unit checks cover input/chain timing, shielding, spacing, air budgets and launch roles. The dedicated native `RosterClientTest` uses mouse/keyboard inputs for each kit and fixtures for counterplay, object ownership, cleanup and terrain collisions. `PlaytestClientTest` covers the packed party flow with a second player, controller visibility, lobby rescue, native melee/down inputs, impact explosives, retreat and companion lifecycle. Multiplayer feel, matchup balance and readability at different camera/FOV settings still need human playtests.

# Points

Points are a persistent, earnable currency for cosmetics and future fighter unlocks. They are separate from damage percentage and matchmaking skill. All five fighters remain free; optional outfits can now be bought in character select.

| Result | Points |
|---|---:|
| Finish a 1v1 or 4 Player match | 50 |
| Win that match | +25 |
| Draw | 50 per player |
| Practice, sandbox, cancelled countdown, interrupted match | 0 |
| Leave or disconnect while still fighting | 0 for that player |

A qualifying match lasts at least **30 seconds of active round time**, and a player must have at least **5 seconds of active participation** before elimination. Movement/directional/shield input, charging, and a one-second window after a successfully started attack count; hits are not required. Countdown and spectating do not count. Short matches and inactive players receive no points, with the reason shown on the result screen. This deters instant quit/rematch loops and entirely idle accounts; it is not bot detection or protection against coordinated accounts that simulate play.

A player eliminated normally can leave before the match ends and still receive the completion reward if they participated. Qualified rematches with the same friends pay normally. KOs and damage do not add currency. Reward amounts live in `PointRules`; prices and qualification thresholds live in `EconomyRules`.

The lobby action bar and the character picker's separate wallet below the controls show the current balance. The picker labels spendable currency as credits. Results show the earned amount and updated balance. `/smash points` shows the full balance and lifetime points earned in the lobby. UI messages do not use chat. Balances follow Minecraft account UUIDs, including name changes.

## Persistence and delivery

`<level-name>/smash/points.db` is a SQLite database inside each backend's existing persistent volume. The deployed Compose configuration uses `/data/smash-network/smash/points.db`. **The lobby database is the authoritative wallet for the entire network.** Arena databases hold undelivered match results only. Standalone `PLAY.cmd` keeps its own database under that instance's world; it does not share production balances.

At match completion, an arena writes the full result to its durable delivery queue. The proxy sends it to the lobby independently of its temporary match assignments. The lobby atomically records the result, all player rewards, and the updated accounts, then acknowledges it. Only then may the arena delete the pending result. Pending results survive arena/proxy restarts and delay deployment drains until delivered. A receipt keyed by match ID and player UUID prevents duplicate payments; conflicting results with the same ID are rejected. All database work runs outside the Minecraft tick thread. UI balances update only after a committed write.

Accounts store current balance, lifetime earned, completed matches and wins. The append-only reward ledger preserves the amount awarded at the time, even if later balancing changes the reward rules. Purchases debit balance, grant permanent ownership, and equip the outfit in one SQLite transaction. A unique player/skin key prevents a second charge on retries. Lifetime earnings and match receipts remain unchanged. There is no player-accessible administrative grant endpoint.

Database schema version remains 1, with additive wardrobe and economy measurement tables so image rollback retains purchases and spent balances. Control protocol is 6 and requires a coordinated proxy/backend release. Older stored results without timing evidence retain their original rewards; already committed receipts are never recalculated. Driver: pinned SQLite JDBC 3.50.3.0, bundled into the backend JAR. Corrupt or newer-version databases fail startup instead of resetting players to zero.

## Levels and XP

An account starts at **Level 1** and earns permanent XP from qualifying 1v1 and 4 Player matches. XP is separate from credits, damage and skill rating. Spending credits, buying credits or subscribing never changes XP, and levels do not increase combat power.

| Result | XP |
|---|---:|
| Finish a match, including a loss or draw | 100 |
| Win | +40 |
| Each KO, up to four rewarded KOs per match | +15 |

The existing 30-second match / 5-second participation qualification applies. Early forfeits, inactive players and practice award no XP. Normal elimination qualifies, including leaving after the last stock. A win with three KOs earns 185 XP; a loss with two KOs earns 130. No daily caps or streak penalties are applied.

The first level costs 100 XP, then each following level costs 50 more, capped at **1,500 XP per level** from Level 29 onward. Level 5 requires 700 lifetime XP, Level 10 requires 2,700, and Level 25 requires 16,200. Milestone titles/colors are Rookie (1), Brawler (5), Contender (10), Challenger (25), All-Star (50), and Legend (100). These are account identity milestones, with no gameplay advantage or promised item unlocks.

The winner stage displays a personal reward card: committed XP counts upward, the progress bar fills with rising pickup sounds, level boundaries pause for a gold pulse/chime/particle burst, and milestone titles get an additional flourish. The result controls stay available during the reveal. Opening the same result again shows the final receipt without repeating its celebration. Players can leave immediately; skipping the animation never forfeits XP. Short/inactive/forfeited rounds display their exclusion reason instead of a fake reward.

In the lobby, the native XP bar and number show account progress, the action bar shows the exact fraction, and Tab shows a colored level beside each name. **Your Level** in hotbar slot 8 or `/smash level` opens the current tier, next level, next milestone and lifetime XP. Arena backends do not display a guessed level: this first version exposes account progression on the authoritative lobby and results stage.

Additive `level_accounts`, `level_rounds`, and immutable `level_receipts` tables live in the same `points.db`. XP, credits and rankings commit in the same transaction. Each match ID is settled once, including offline/eliminated players. Existing settled results are backfilled once in settlement order, so earlier play counts; the same historical no-evidence eligibility rule as credits applies. Backfill does not run a parade of old level-up animations. Arena outbox retries, restarts and rollback-era results use the existing delivery path; no new protocol or data volume is needed. Database failures leave the card at **Saving XP...**, never at a fabricated success.

`LevelRules` owns rewards, the curve and tiers; `Levels` owns persistent receipts. Unit tests cover thresholds, bounded bonuses, exclusions, duplicate/conflicting results, payment separation, rollback, historical migration and multi-level reveals. `runClientGameTest -PlevelsTests -PdedicatedTests` finishes a real duel and checks native animation, cursor stability, different viewport sizes, dismissal/cleanup, lobby progress, last-result reopening and persistent reload.

## Rankings

Rankings are earned match statistics, separate from the wallet and purchases. Weekly wins resets Monday at 00:00 UTC; All-time wins and KOs never reset. 1v1 and 4 Player matches share these boards. A player must satisfy the existing completion/participation rules above; forfeited, inactive and short-match results do not contribute. A draw awards no win, but its qualifying KOs count. Equal scores share competition ranks (1, 1, 3); name then UUID only orders tied rows. Zero-score players appear as Unranked.

The same authoritative lobby `points.db` stores additive ranking tables. Each result's rankings and credit receipt commit atomically, keyed by match UUID, so arena retries and restarts cannot count twice. Existing settled match history is imported once using its recorded settlement date; old results without timing evidence retain the historical eligibility rule. Pending arena results count when the lobby first settles them, including the week of settlement if delivery was delayed. Store deliveries never enter rankings. Back up the existing SQLite database as before; no new volume or protocol change is required. These leaderboards measure earned wins/KOs, not skill rating, and do not change matchmaking.

## Cosmetics

Use the arrows beneath the fighter grid, above the game-mode row, to preview a skin on the live stage. **Unlock 1,500** opens a purchase confirmation with the credit price and remaining balance; confirming permanently unlocks and equips it. Your first cosmetic purchase receives a one-time **50% discount (750 credits)**. Owned skins show **Equip**; the active skin shows **Equipped**. An unaffordable selection shows **Need …** with the exact shortfall. Defaults are always free. While previewing a locked or unequipped skin, **Play with [equipped skin]** restores the owned outfit and queues normally without a purchase. Skin changes clear your ready state, and queuing replaces these controls until you cancel.

| Fighter | Alternate | Appearance |
|---|---|---|
| Steve | Lumberjack | Red flannel shirt, dark jeans and casual shoes |
| Alex | Gardener | Cream shirt, green overalls and a violet flower hair clip |
| Zombie | Dune | Husk, with a matching baby husk companion |
| Skeleton | Frost | Stray's icy eyes and tattered cloak |
| Villager | Desert | Native desert robes and headwrap |

Every current alternate has a standard price of **1,500 points**. The future elaborate-cosmetic tier is **3,000**, and the future class-unlock baseline is **5,000**. These are catalog defaults, not newly added content: the five starter classes are still free, and this release adds no locked classes or mastery rewards. Future classes should be available to try in practice before purchase.

The first-purchase discount applies across all fighters, only to a successful purchase, and never renews after restarts, default equip, or failed purchases. Existing 250-point purchases remain owned at their original recorded cost; those accounts have already made their first purchase. A fresh price check and the debit/ownership/equip transaction prevent races or stale quotes from spending more than the displayed price. Lifetime earnings and all existing balances are retained.

These are visual-only outfits: kits, movement, damage and collision sizes stay the same. Class portraits remain the recognizable default faces. Steve and Alex use clothing textures from the existing server pack, with no equipped armor. Their internal `diamond` and `scout` IDs are retained so existing purchases and equipped skins automatically receive the new looks. The catalog is server-owned (`Cosmetics`); price, class compatibility and ownership never come from the client.

Ownership and one equipped skin per fighter live beside the wallet. The lobby snapshots the committed outfit into the match ticket; arenas and winner stages use that snapshot. Rematches retain your equipped look. Standalone and network wallets are separate. The existing resource pack gains the skin controls, with no extra install or client mod.

## Wallet / store handoff

The wallet sits below the picker, separate from party membership and skin controls. Its **Store >** button opens the existing branded credit and membership cards with the real balance and membership status. Opening it clears your ready vote; it is unavailable during queue/transfer. Close and Escape return to the same fighter, skin preview and stage. The **Need …** skin control is disabled until affordable; it no longer redirects into a Get Points dialog.

Set `SMASH_STORE_URL` in the Docker host's `.env` (or standalone environment) to override the configured storefront. Compose passes it to the lobby. The Store uses the same URL validation, credit and membership routes, and Minecraft external-link confirmation as the lobby Store pedestal. No promotional chat is sent. Browsing never grants or spends credits.

This is a storefront handoff, **not payment processing**. Before enabling a real checkout, implement and verify authenticated, idempotent fulfillment and refund handling against the authoritative lobby wallet, with an online purchase history. Review [Minecraft's server monetization guidelines](https://www.minecraft.net/en-us/usage-guidelines): purchases must not grant a competitive advantage. Revisit paid currency eligibility before introducing gameplay class unlocks; the current purchasable catalog contains cosmetics only.

## Operations and verification

Run `python3 deploy/economy_report.py` on the Docker host to read the authenticated, read-only `/economy` admin route. It reports measured match duration, points per player-match hour, first-purchase match minutes, earners with no cosmetic purchase, and reward-exclusion counts. No player identifiers are returned. Measures count completed multiplayer round time up to each player's elimination/departure; they exclude lobby/queue time, practice, incomplete matches, and old results without evidence. They are **not wall-clock session retention measurements**. With no samples, rate/median fields are omitted. New purchases and measured rounds are saved transactionally with their wallet changes and survive restarts; duplicate result delivery does not duplicate measurements.

Keep the lobby and arena data volumes across releases. Image rollback must not roll back balances. Back up the lobby database and any arena delivery queues using a SQLite-aware backup, or stop the network before copying `points.db` and any accompanying `points.db-wal` / `points.db-shm` files. A live copy of only `points.db` can omit recent transactions. Normal image redeployments preserve these files; deleting volumes or replacing a world deletes its points data too.

Unit tests cover wins/losses/draws/forfeits, replayed and conflicting results, transaction rollback, restart persistence, concurrent purchases, insufficient funds, invalid class/skin combinations and outbox acknowledgment. `runClientGameTest -PpointsTests -PdedicatedTests` exercises match rewards. `runClientGameTest -PcosmeticsTests -PdedicatedTests` checks native mouse purchases, all five models, unchanged collision sizes, equip/default switching, persistence, queue tickets, combat, rematches and winner models. CI's `check_points.py` delivers results from both arena containers, buys an outfit, retries the purchase, and checks wallets and wardrobes again after the full-network rollback/restart test. Its purchase fixture requires the private control secret and `SMASH_TEST_CONTROL=true`; production leaves test controls disabled.

The current deployment has one lobby account authority. Multiple lobbies will need a shared account service or transactional database before scaling that tier. Completed rewards persist; an abrupt process failure before its completion record reaches disk can still lose that uncommitted result. In-progress matches do not survive arena crashes.

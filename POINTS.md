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

The lobby action bar and character picker's sidebar show the current balance. Results show the earned amount and updated balance. `/smash points` shows the full balance and lifetime points earned in the lobby. UI messages do not use chat. Balances follow Minecraft account UUIDs, including name changes.

## Persistence and delivery

`<level-name>/smash/points.db` is a SQLite database inside each backend's existing persistent volume. The deployed Compose configuration uses `/data/smash-network/smash/points.db`. **The lobby database is the authoritative wallet for the entire network.** Arena databases hold undelivered match results only. Standalone `PLAY.cmd` keeps its own database under that instance's world; it does not share production balances.

At match completion, an arena writes the full result to its durable delivery queue. The proxy sends it to the lobby independently of its temporary match assignments. The lobby atomically records the result, all player rewards, and the updated accounts, then acknowledges it. Only then may the arena delete the pending result. Pending results survive arena/proxy restarts and delay deployment drains until delivered. A receipt keyed by match ID and player UUID prevents duplicate payments; conflicting results with the same ID are rejected. All database work runs outside the Minecraft tick thread. UI balances update only after a committed write.

Accounts store current balance, lifetime earned, completed matches and wins. The append-only reward ledger preserves the amount awarded at the time, even if later balancing changes the reward rules. Purchases debit balance, grant permanent ownership, and equip the outfit in one SQLite transaction. A unique player/skin key prevents a second charge on retries. Lifetime earnings and match receipts remain unchanged. There is no player-accessible administrative grant endpoint.

Database schema version remains 1, with additive wardrobe and economy measurement tables so image rollback retains purchases and spent balances. Control protocol is 6 and requires a coordinated proxy/backend release. Older stored results without timing evidence retain their original rewards; already committed receipts are never recalculated. Driver: pinned SQLite JDBC 3.50.3.0, bundled into the backend JAR. Corrupt or newer-version databases fail startup instead of resetting players to zero.

## Cosmetics

Use the arrows beneath the fighter grid to preview a skin on the live stage. **Buy - 1,500 Points** permanently unlocks and equips it. Your first cosmetic purchase receives a one-time **50% discount (750 points)**, shown on the button. Owned skins show **Equip**; the active skin shows **Equipped**. Defaults are always free. Locked previews cannot enter matchmaking; equip a skin or return to the current one first. Skin changes clear your ready state, and queuing replaces these controls until you cancel.

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

## Operations and verification

Run `python3 deploy/economy_report.py` on the Docker host to read the authenticated, read-only `/economy` admin route. It reports measured match duration, points per player-match hour, first-purchase match minutes, earners with no cosmetic purchase, and reward-exclusion counts. No player identifiers are returned. Measures count completed multiplayer round time up to each player's elimination/departure; they exclude lobby/queue time, practice, incomplete matches, and old results without evidence. They are **not wall-clock session retention measurements**. With no samples, rate/median fields are omitted. New purchases and measured rounds are saved transactionally with their wallet changes and survive restarts; duplicate result delivery does not duplicate measurements.

Keep the lobby and arena data volumes across releases. Image rollback must not roll back balances. Back up the lobby database and any arena delivery queues using a SQLite-aware backup, or stop the network before copying `points.db` and any accompanying `points.db-wal` / `points.db-shm` files. A live copy of only `points.db` can omit recent transactions. Normal image redeployments preserve these files; deleting volumes or replacing a world deletes its points data too.

Unit tests cover wins/losses/draws/forfeits, replayed and conflicting results, transaction rollback, restart persistence, concurrent purchases, insufficient funds, invalid class/skin combinations and outbox acknowledgment. `runClientGameTest -PpointsTests -PdedicatedTests` exercises match rewards. `runClientGameTest -PcosmeticsTests -PdedicatedTests` checks native mouse purchases, all five models, unchanged collision sizes, equip/default switching, persistence, queue tickets, combat, rematches and winner models. CI's `check_points.py` delivers results from both arena containers, buys an outfit, retries the purchase, and checks wallets and wardrobes again after the full-network rollback/restart test. Its purchase fixture requires the private control secret and `SMASH_TEST_CONTROL=true`; production leaves test controls disabled.

The current deployment has one lobby account authority. Multiple lobbies will need a shared account service or transactional database before scaling that tier. Completed rewards persist; an abrupt process failure before its completion record reaches disk can still lose that uncommitted result. In-progress matches do not survive arena crashes.

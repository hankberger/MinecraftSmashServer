# Points

Points are a persistent, earnable currency for future cosmetics and fighter unlocks. They are separate from damage percentage and matchmaking skill. This release adds earning and balances; it does not add a shop, purchases, or locked fighters.

| Result | Points |
|---|---:|
| Finish a 1v1 or 4 Player match | 50 |
| Win that match | +25 |
| Draw | 50 per player |
| Practice, sandbox, cancelled countdown, interrupted match | 0 |
| Leave or disconnect while still fighting | 0 for that player |

A player eliminated normally can leave before the match ends and still receive the completion reward. Every completed rematch earns a new reward. KOs and damage do not add currency, so fighting weaker opponents or repeatedly damaging a cooperative player does not inflate that match's payout. Reward amounts live in `PointRules`.

The lobby action bar and character picker's sidebar show the current balance. Results show the earned amount and updated balance. `/smash points` shows the full balance and lifetime points earned in the lobby. UI messages do not use chat. Balances follow Minecraft account UUIDs, including name changes.

## Persistence and delivery

`world/smash/points.db` is a SQLite database inside each backend's existing persistent volume. **The lobby database is the authoritative wallet for the entire network.** Arena databases hold undelivered match results only. Standalone `PLAY.cmd` keeps its own database under that instance's world; it does not share production balances.

At match completion, an arena writes the full result to its durable delivery queue. The proxy sends it to the lobby independently of its temporary match assignments. The lobby atomically records the result, all player rewards, and the updated accounts, then acknowledges it. Only then may the arena delete the pending result. Pending results survive arena/proxy restarts and delay deployment drains until delivered. A receipt keyed by match ID and player UUID prevents duplicate payments; conflicting results with the same ID are rejected. All database work runs outside the Minecraft tick thread. UI balances update only after a committed write.

Accounts store current balance, lifetime earned, completed matches and wins. The append-only reward ledger preserves the amount awarded at the time, even if later balancing changes the reward rules. Future purchases should debit the current balance and grant an unlock in one transaction, with an idempotent purchase ID. Lifetime earnings and match receipts must remain unchanged. No purchase or administrative grant endpoint is exposed to players in this release.

Database schema version is 1; control protocol is 4 and requires a coordinated proxy/backend release. Driver: pinned SQLite JDBC 3.50.3.0, bundled into the backend JAR. Corrupt or newer-version databases fail startup instead of resetting players to zero.

## Operations and verification

Keep the lobby and arena data volumes across releases. Image rollback must not roll back balances. Back up the lobby database and any arena delivery queues using a SQLite-aware backup, or stop the network before copying `points.db` and any accompanying `points.db-wal` / `points.db-shm` files. A live copy of only `points.db` can omit recent transactions. Normal image redeployments preserve these files; deleting volumes or replacing a world deletes its points data too.

Unit tests cover wins/losses/draws/forfeits, replayed and conflicting results, transaction rollback, restart persistence and outbox acknowledgment. `runClientGameTest -PpointsTests -PdedicatedTests` exercises actual match completion, packed UI rewards, leaving after elimination, practice exclusion and account reload. CI's `check_points.py` delivers results from both real arena containers, verifies duplicate delivery, and checks balances again after the full-network rollback/restart test.

The current deployment has one lobby account authority. Multiple lobbies will need a shared account service or transactional database before scaling that tier. Completed rewards persist; an abrupt process failure before its completion record reaches disk can still lose that uncommitted result. In-progress matches do not survive arena crashes.

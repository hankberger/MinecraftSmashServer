# Discord reward integration

The lobby exposes authenticated `POST /discord/reward` through the private proxy admin API. This is independent of the sandbox store gate. It accepts only Brawl Party's guild ID and a fixed 750-credit bonus:

```json
{"id":"discord:1554281966197538908:123456789012345678","player":"01234567-89ab-cdef-0123-456789abcdef","credits":750}
```

The UUID comes from verified Minecraft website sign-in. Discord membership is verified by the sibling `ringshift` website; never expose the game admin secret or this endpoint to a player or browser. The outbound `ringshift/scripts/discord-bridge.py` delivers verified rewards and acknowledges only after commit.

`discord_rewards` in the lobby points database records the receipt, player UUID and Discord ID. Each is unique, so lost responses, retries, and changing one of the linked accounts cannot produce extra credits. Wallet and receipt commit atomically. Discord credits increment balance and lifetime earned; they do not increment XP, matches, wins or membership. Preserve these rows and the normal points backups.

Both lobby and proxy must run this version. Deploy during maintenance using the existing release procedure; arena-only rolling updates do not update these endpoints. The account page remains disabled until the Discord application is configured. See `../ringshift/DISCORD-REWARDS.md` for the remaining setup and live acceptance test. Automated coverage is `DiscordRewardTest` plus website and bridge tests.

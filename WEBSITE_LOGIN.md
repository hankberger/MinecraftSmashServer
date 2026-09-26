# Website sign-in

In the lobby, `/smash login` opens a private code dialog. Click the code to copy it, then open the website and enter the Minecraft Java username and code at `/account`. Use within four minutes; each code is accepted once. Request another code if it expires or was already used.

Set `SMASH_WEBSITE_KEY` to the same independent 64-character lowercase hex secret configured as `MINECRAFT_LOGIN_KEY` on the website. Put it in the deployment host's ignored `.env`, never Git. Compose exposes it only to the authenticated lobby. Without the key, or on an offline standalone server, the command displays an unavailable message. Website links use the origin of `SMASH_STORE_URL` (or the default Ringshift site).

Codes use purpose-bound HMAC-SHA256 over stable UUID, UTC epoch minute and a random nonce. The website verifies the current minute and previous four and transactionally prevents reuse. Keep host clocks synchronized. The API never creates operator access, changes queue state, spends credits or exposes payment records. The website keeps its current audience restriction.

Run `./gradlew.bat --gradle-user-home ../smash_arena/.gradle-user-home build` and `runClientGameTest -PdedicatedTests -PloginTests` to test the code generator and native copy dialog. The paired website has replay, expiry, throttling, session and CSRF tests.

#!/bin/sh
set -eu
mkdir -p /data/plugins
cp /opt/smash/smash-proxy.jar /data/plugins/smash-proxy.jar
cp /opt/smash/velocity.toml /data/velocity.toml
if [ "${SMASH_ONLINE_MODE:-true}" = "false" ]; then
  if [ "${SMASH_ALLOW_OFFLINE_TEST:-false}" != "true" ]; then
    echo "Offline identities require the explicit local test configuration." >&2
    exit 1
  fi
  sed -i 's/^online-mode = true/online-mode = false/; s/^force-key-authentication = true/force-key-authentication = false/; s/^login-ratelimit = 3000/login-ratelimit = 0/' /data/velocity.toml
fi
exec java -Xms256M -Xmx512M --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -jar /opt/smash/velocity.jar

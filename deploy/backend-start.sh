#!/bin/sh
set -eu
mkdir -p /data/mods
# Fixed names guarantee that a rollback replaces the same three application files.
cp /opt/smash/mods/smash-vanilla.jar /data/mods/smash-vanilla.jar
cp /opt/smash/mods/fabric-api.jar /data/mods/fabric-api.jar
cp /opt/smash/mods/forwarder.jar /data/mods/forwarder.jar
exec /start

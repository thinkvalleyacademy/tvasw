#!/bin/bash
# Local backup of the database and contact-form entries (H2 must be stopped for a safe copy)
set -euo pipefail
umask 077

cd ~/apps/tvasw
DEST="backup/daily/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$DEST"

trap 'systemctl --user start tvasw-backend' EXIT
systemctl --user stop tvasw-backend
cp data/tvaswdb.mv.db "$DEST/"
[ ! -f client_queries/queries.json ] || cp client_queries/queries.json "$DEST/"

# Keep the 14 newest
ls -1d backup/daily/2* | head -n -14 | xargs -r rm -r --

#!/usr/bin/env bash
set -euo pipefail
APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
CHROME_BIN="$(command -v google-chrome || command -v chromium || command -v chromium-browser || true)"
if [[ -z "$CHROME_BIN" ]]; then echo "需要先安装 Google Chrome 或 Chromium" >&2; exit 1; fi
mkdir -p "$HOME/.local/share/applications" "$HOME/.config/systemd/user"
sed "s|@APP_DIR@|$APP_DIR|g" "$APP_DIR/ubuntu/anubis.service.in" > "$HOME/.config/systemd/user/anubis.service"
sed -e "s|@APP_DIR@|$APP_DIR|g" -e "s|@CHROME@|$CHROME_BIN|g" "$APP_DIR/ubuntu/anubis.desktop.in" > "$HOME/.local/share/applications/anubis.desktop"
chmod +x "$HOME/.local/share/applications/anubis.desktop"
systemctl --user daemon-reload
systemctl --user enable --now anubis.service
echo "Anubis 已安装。可从应用菜单启动，数据服务运行在 http://127.0.0.1:8080"

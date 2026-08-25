#!/usr/bin/env bash
set -euo pipefail
systemctl --user disable --now anubis.service 2>/dev/null || true
rm -f "$HOME/.config/systemd/user/anubis.service" "$HOME/.local/share/applications/anubis.desktop"
systemctl --user daemon-reload
echo "应用入口已卸载；保险箱密文数据仍保留在项目 server-data/ 中。"

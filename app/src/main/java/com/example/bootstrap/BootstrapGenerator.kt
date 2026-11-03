package com.example.bootstrap

object BootstrapGenerator {

    fun generateFullScript(publicKey: String): String {
        return """
#!/data/data/com.termux/files/usr/bin/bash
set -e

echo "=========================================="
echo "⚡ SHΞN™Termutoxic Automatic Bootstrap ⚡"
echo "=========================================="

echo "[1/6] Updating package repositories..."
pkg update -y && pkg upgrade -y

echo "[2/6] Installing core tools & SSH..."
pkg install -y openssh curl wget git python nodejs jq termux-api cloudflared gh proot-distro || true

echo "[3/6] Configuring Termux external access..."
mkdir -p ~/.termux
touch ~/.termux/termux.properties
if ! grep -q "allow-external-apps" ~/.termux/termux.properties; then
    echo "allow-external-apps=true" >> ~/.termux/termux.properties
fi
termux-reload-settings || true

echo "[4/6] Setting up SSH Authorized Keys & Server..."
mkdir -p ~/.ssh
chmod 700 ~/.ssh
touch ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys

if ! grep -q "${publicKey.take(30)}" ~/.ssh/authorized_keys; then
    echo "${publicKey}" >> ~/.ssh/authorized_keys
fi

# Configure sshd config if needed
pkill sshd || true
sshd -p 8022

echo "[5/6] Requesting Termux storage permission..."
termux-setup-storage || true

echo "[6/6] Creating SHΞN environment directory..."
mkdir -p ~/.config/shen
chmod 700 ~/.config/shen

echo "=========================================="
echo "✅ SHEN_BOOTSTRAP_SUCCESS_8022"
echo "=========================================="
""".trimIndent()
    }

    fun generateOneLinerCommand(publicKey: String): String {
        val escapedKey = publicKey.replace("\"", "\\\"")
        return "pkg update -y && pkg install -y openssh curl && mkdir -p ~/.ssh ~/.termux ~/.config/shen && chmod 700 ~/.ssh && echo \"$escapedKey\" >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys && echo \"allow-external-apps=true\" >> ~/.termux/termux.properties && termux-reload-settings && pkill sshd || true && sshd -p 8022 && echo \"SHEN_BOOTSTRAP_SUCCESS_8022\""
    }
}

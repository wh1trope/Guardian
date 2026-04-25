<div align="center">
  <h1>🛡️ Guardian AntiCrash</h1>
  <p><strong>A high-performance, comprehensive exploit and crash protection plugin for Minecraft 1.20+</strong></p>

  [![Java](https://img.shields.io/badge/Java-17%2B-blue?logo=java)](https://adoptium.net/)
  [![Spigot](https://img.shields.io/badge/Spigot-1.20.1%2B-orange?logo=spigot)](https://www.spigotmc.org/)
  [![License](https://img.shields.io/badge/License-GPLv3-green.svg)](https://www.gnu.org/licenses/gpl-3.0)
  [![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/whitrope/Guardian/pulls)

  <br>
  <strong>⭐ If you find this project useful, please consider giving it a star! It helps the project grow! ⭐</strong>
</div>

---

## 📖 Overview

**Guardian** is a robust security plugin designed to protect Minecraft servers against a wide array of packet-level crashes, exploit attempts, and lag machines. Built specifically for Spigot/Paper 1.20+, it intercepts and validates packets asynchronously, neutralizing threats before they can impact your server's performance.

## 🤔 Why Guardian? (The Motivation)

Currently, the Minecraft market is flooded with paid AntiCrash and AntiExploit plugins that are often overpriced, poorly optimized, or simply ineffective. **We believe that fundamental server security should not be hidden behind a paywall.** 

**Guardian is engineered from an attacker's perspective.** Developed by the creator of the market's most popular crash client - Ayakashi. This plugin leverages deep, inside knowledge of exactly how exploits are discovered, weaponized, and executed. Who better to defend a server than someone who knows exactly how to break it?

Guardian was created to provide a **free, open-source, and superior alternative** to premium plugins. We want to set a new standard for server protection, ensuring every server owner has access to top-tier security without spending a dime.

## 🚀 Features

### 🛡️ CrashShield
Protects against the most common and advanced crash methods:
- **String & Payload Exploits:** Blocks oversized packets, plugin-message flooding, and invalid characters (Zalgo, zero-width, ASCII control chars).
- **NBT & Item Exploits:** Deep-scans NBT data in packets to block exponential expansion attacks (e.g., nested crossbows/shulkers) and restricts illegal enchants, oversized books, and invalid stack sizes.
- **Inventory & Movement:** Rejects invalid slot interactions, out-of-bounds creative slots, NaN rotations, and forged player positions.

### ⚡ PacketGuard & ActivityGuard
Advanced rate-limiting to prevent spam and server overload:
- Dynamic limits for window clicks, chat, command suggestions, sign updates, and interactions.
- Blocks `KeepAlive` and `VehicleMove` floods.
- Prevents container-close and resource-pack spam.
- Strictly bounds total packets per second (PPS) and bandwidth limits.

### ⚙️ ExploitBlocker & LagControl
Maintains stable TPS during gameplay:
- Caches piston and entity actions, blocking lag machines.
- Optionally blocks heavily exploited vanilla items like lecterns, bundles, specific books, and armor stands.

### 🌐 ConnectionGuard
Defends against botting attacks during the login phase:
- Limits login attempts per IP.
- Validates and enforces length/character constraints for usernames.
- Blocks blacklisted username patterns automatically.

## 📥 Installation

1. Download the latest `Guardian-1.0.jar` from the [Releases](https://github.com/whitrope/Guardian/releases) page.
2. Place the `.jar` file into your server's `plugins` folder.
3. Restart your server.
4. Modify the `plugins/Guardian/config.yml` to fit your server's needs (default values are optimized for standard survival/factions servers).
5. Use `/guardian` in-game for administrative commands.

## 🤝 Contributing & Pull Requests

**We want to build this together with the community!** The project is fully open-source because we want to constantly improve it.

Whether you are fixing a bug, adding a new exploit patch, or optimizing the code - **Pull Requests are highly encouraged and warmly welcomed!** 

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/NewFeature`)
3. Commit your changes (`git commit -m 'Add some NewFeature'`)
4. Push to the branch (`git push origin feature/NewFeature`)
5. Open a Pull Request

If you have ideas for new features or find bypasses, please open an **Issue** so we can discuss it!

## 🛠️ Building from Source

To build Guardian locally, you'll need JDK 17+ and Gradle.

```bash
git clone https://github.com/whitrope/Guardian.git
cd Guardian
./gradlew build
```
The compiled jar will be available in the `build/libs` directory.

## 📝 License

Guardian is licensed under the [GNU General Public License v3.0](LICENSE).

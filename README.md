# What Could Have Been for RuneLite

What Could Have Been (WCHB) is a just-for-fun alternate loot journey for Old School RuneScape. Every real drop delivered by Dink is rolled again against the relevant drop table to answer one question: **what could you have received instead?**

The reroll is fictional. WCHB never changes your real items, account, odds, or gameplay.

## What the RuneLite plugin does

The plugin is a native RuneLite companion for [WCHB.net](https://wchb.net). It provides:

- a movable in-game overlay for the latest real drop and WCHB reroll;
- a minimal single-line layout by default, with a configurable full layout and size;
- a short dice reveal when a new reroll arrives;
- enhanced reveals for WCHB big-value, unique, and pet results; and
- a live sidebar feed containing the eight most recent rerolls.

Dink remains solely responsible for detecting loot and sending it to WCHB. This plugin does not duplicate Dink, inspect gameplay to collect drops, automate actions, or influence combat.

### Why Dink?

[Dink](https://runelite.net/plugin-hub/show/dink) is an established RuneLite Plugin Hub notifier that already handles loot detection across ordinary NPCs, bosses, raids, clues, and reward chests. WCHB deliberately relies on Dink's maintained event delivery instead of reproducing its game-data collection inside this plugin.

The essential Dink setup is short:

1. Install Dink from RuneLite's Plugin Hub.
2. Open Dink settings and find **Webhook Overrides**.
3. Paste the private WCHB webhook into the **Loot** override. Existing Discord destinations can remain under **Primary Webhook URLs**.
4. Set **Loot: Enabled** to `ON`, **Min Loot Value** to `1`, **Include PK Loot** to `OFF`, **Include Clue Loot** to `ON`, and **Send Image** to `OFF`.
5. Kill a low-level NPC such as a chicken or goblin. The WCHB panel should confirm the connection within a few seconds.

The complete guide and progress checklist remain available inside the plugin after onboarding.

## The full WCHB experience

The plugin keeps the in-client experience intentionally light. WCHB.net provides the larger fictional profile, including:

- the complete rerolled loot history and WCHB bank;
- an equipment manager built from fictional items;
- player profiles and leaderboards; and
- social and sharing features.

## First-time setup

1. Install the plugin and open the WCHB sidebar.
2. Enable **Connect to WCHB** in the plugin settings and accept RuneLite's required third-party network warning.
3. Select **Create profile**. No email address or password is required.
4. Copy the private webhook URL into Dink.
5. The first Dink event associates the temporary profile with that character and begins its fictional loot journey.
6. Optionally claim the profile on WCHB.net later to access the full website.

Already use WCHB.net? Select **Connect existing account** instead, sign in on WCHB.net, and approve the connection. The plugin also remembers character connections locally so it can reconnect the correct profile on later launches.

Installing the plugin alone does not collect loot. Dink only sends events after the player deliberately configures the generated webhook.

## Data and privacy

For a connected profile, WCHB stores the data required to create and display the fictional experience:

- OSRS display name and detected/reported account type;
- loot details delivered through the private Dink webhook;
- generated rerolls, fictional bank value, and loot history; and
- a one-way hash of the plugin installation token used to reconnect the plugin.

RuneLite stores the connection token locally in its configuration. The plugin also keeps a local character-to-token mapping in RuneLite's data directory so the correct WCHB profile can be restored when switching characters. Treat generated webhook URLs and connection tokens as private.

An email address is only associated with the profile if the player later signs in and claims it. WCHB does not receive or store Jagex credentials, passwords, bank PINs, chat messages, nearby-player information, or gameplay inputs. WCHB does not add the connecting IP address to the player profile or application entities; infrastructure providers may process ordinary security and operational logs.

Network access is disabled by default. The plugin only contacts WCHB after **Connect to WCHB** is enabled. While the player is logged in and connected, the initial feed is fetched over HTTPS and new rerolls are delivered through a WCHB WebSocket. The live connection is stopped when the player logs out or disables the setting.

## Configuration

- **Minimal overlay** is enabled by default.
- **Overlay size** ranges from 60% to 140%.
- **Unlock overlay movement** temporarily enables dragging; RuneLite stores the chosen location for that RuneLite profile.
- **Show overlay** can hide the in-game card without disconnecting the sidebar feed.

For setup help, visit [wchb.net/setup](https://wchb.net/setup). For plugin bugs or feature requests, use this repository's [issue tracker](https://github.com/wchbdotnet/wchb-runelite/issues).

## Development

This repository is the source for the RuneLite Plugin Hub submission. The plugin uses RuneLite-native overlays and Swing panels and targets the standard Java Plugin Hub build.

```powershell
.\gradlew.bat clean test
.\gradlew.bat run
```

## License

BSD 2-Clause. See [LICENSE](LICENSE).

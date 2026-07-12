# Rewards

Rewards are defined per-template under `rewards:`, keyed by **placement** (`1`, `2`, `3`, ...) plus a special participation tier keyed `0`. `RewardManager.distribute()` is called once when a tournament ends, on the main thread, after final standings are computed.

## Reward Tiers

```yaml
rewards:
  1:
    money: 5000.0
    commands: ["eco give {player} 500"]
    items:
      - material: DIAMOND
        amount: 3
        display-name: "&bFirst Place Diamond"
  2:
    money: 2500.0
  3:
    money: 1000.0
  0:                    # participation tier
    enabled: true        # required — omitted or false = tier is skipped for everyone
    commands: ["eco give {player} 50"]
```

- Numeric keys `1`, `2`, `3`, ... match exact final placement. A player ranked 4th gets nothing from a template with only tiers `1`–`3` defined (unless a participation tier exists).
- Key `0` (or the string key `participation`) is the **participation tier** — every single participant receives it, regardless of rank, *in addition to* their placement reward if they placed. It only applies if `enabled: true` is set inside it; this is the one tier that requires an explicit opt-in flag.
- `money`, `commands`, and `items` are all optional within a tier; any can be omitted or empty.

## What Gets Applied

For each tier a player qualifies for:

1. **Money** — deposited via Vault (`economy.depositPlayer`). If the player is offline, Vault's offline-player deposit is used instead so rewards aren't lost. Money is skipped entirely (with a one-time startup log) if Vault isn't installed.
2. **Commands** — each command string has `{player}` replaced with the player's name and `{place}` replaced with their placement (`0` for the participation tier), then dispatched from console (`Bukkit.dispatchCommand(Bukkit.getConsoleSender(), ...)`).
3. **Items** — only given if the player is currently online (no offline item storage). `player.getInventory().addItem()` is used; any items that don't fit are dropped naturally at the player's feet rather than lost.

Money and item rewards additionally send the player a chat confirmation message (`"You received $X for placing #N!"` / participating).

Reward distribution is marked in the database (`reward_given` flag on `tournament_scores`) asynchronously per player, so history queries can show whether a past reward was actually paid out.

## Item Rewards

Each item entry supports:

```yaml
items:
  - material: DIAMOND        # must match a valid Bukkit Material name, or the entry is skipped
    amount: 3                # default 1
    display-name: "&bFirst Place Diamond"   # optional, supports & color codes
```

Next: [Leaderboards & GUIs](leaderboards.md)

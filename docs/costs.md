# Skill purchase costs

A skill rank can require points from several trees, Minecraft experience points, inventory items, or nested alternatives between them. Costs are separate from purchase prerequisites: both must pass, and disabling a purchased skill does not refund its price.

Use the in-game editor's **Costs** field to build typed entries and nested **AND**/**OR** groups. The node details show the resolved expression, current raw Minecraft XP and available tree point balances. The server evaluates the complete expression again when buying a rank.

## Defining a price

`costs` is an inherited setting. It can appear in the global defaults at `data/mastery/mastery/settings/defaults.json`, a tree at `data/<namespace>/mastery/trees/<id>.json`, a spell binding, or a node at `data/<namespace>/mastery/nodes/<id>.json`. More specific settings replace the complete cost expression; two expressions are never merged into a mixed logical group.

When `costs` is absent or resolves to `null`, the existing node `cost` is charged from the owning tree. Literal `"default"` inherits the parent value. Use `null` to restore the legacy single-tree cost under a parent that defines custom costs.

A single price needs no group:

```json
{
  "costs": {
    "type": "points",
    "amount": 2
  }
}
```

Omitting `tree` means the node's own tree. An empty `tree` string has the same meaning. A complete mixed example is:

```json
{
  "costs": {
    "or": [
      {
        "and": [
          {"type": "points", "tree": "mastery:fire", "amount": 2},
          {"type": "points", "tree": "mastery:ice", "amount": 1}
        ]
      },
      {
        "and": [
          {"type": "experience", "amount": 100},
          {"type": "item", "item": "minecraft:diamond", "amount": 1}
        ]
      }
    ]
  }
}
```

Each rank costs either 2 Fire Points plus 1 Ice Point, or 100 raw Minecraft XP plus a diamond. This replaces the node's legacy numeric `cost`; it does not charge that price again.

| Cost type | Selector | Resource consumed |
| --- | --- | --- |
| `points` | Optional `tree` | Points belonging to that tree. |
| `experience` | None | Raw Minecraft XP points, not proficiency XP and not experience levels. |
| `item` | Exactly one `item` or `item_tag` | Matching stacks in inventory, hotbar and offhand. Worn armor is excluded. |

Every leaf requires a nonnegative integer `amount` from 0 to 1,000,000,000. Zero is free. Exact item IDs must exist in the installed registry, including zero-amount leaves and unused alternatives. An item tag uses its resource ID without `#`; for example, `{"type":"item","item_tag":"minecraft:planks","amount":4}`. Item components are not selectors: all matching items count regardless of name or other components, and unconsumed stack contents retain their components.

An AND group pays every child. An OR group chooses one child. Groups can contain other groups, with a maximum of 32 nested groups and 128 total entries. Groups must be nonempty and must contain exactly one of `and` or `or`. A group cannot also contain a typed leaf.

## Depth-based inflation

The inherited `cost_depth_percent` defaults to `0`. It adds a fraction of the base amount per dependency depth. A leaf can override it with `depth_percent`.

```json
{
  "cost_depth_percent": 0.1,
  "costs": {
    "or": [
      {"type": "points", "amount": 1, "depth_percent": 0},
      {"type": "experience", "amount": 100}
    ]
  }
}
```

Placed on a tree, this makes its nodes cost either 1 point from that tree or Minecraft XP that increases by 10% of 100 per dependency depth. Root nodes have depth 0. Depth follows the longest dependency path, including nested prerequisite groups; moving nodes around the visual map does not change it.

Each leaf is charged as:

```text
ceil(amount × (1 + dependency_depth × depth_percent))
```

At depth 3 the example costs 1 tree point or 130 Minecraft XP. At depth 1, a base cost of 1 with a 10% increase becomes 2 because payment uses whole units. Rounding happens on each leaf before repeated demands are added. Decimal percentages use exact decimal ceiling, avoiding accidental overcharges such as rounding 130.00000000000003 to 131.

Percentages accept 0–100. The final amount of each leaf must fit a signed 32-bit integer; oversized resolved prices fail validation. Rank does not separately multiply the purchase price: buying each rank pays the same resolved expression unless a later data edit changes it.

## Choosing and paying a branch

Mastery chooses the first fully affordable alternative in authored order. It evaluates the whole expression before taking anything. If an earlier OR choice prevents a later AND requirement from being paid, it backtracks to another OR choice.

Repeated demands accumulate. Two leaves asking for 2 Fire Points require 4 points, even when they appear in different nested groups. Item allocation also accounts for overlap: an early `minecraft:planks` tag cost will not consume the only oak plank needed by a later exact `minecraft:oak_planks` cost when another plank is available.

The planner allows at most 4,096 expansion states. An expression that exceeds this bound returns an error without taking resources; simplify deeply multiplied alternatives if the editor reports this condition.

Prerequisites, rank limits, exclusions, book gates, world-tier restrictions and purchase chronology are checked before payment. The server then selects a complete resource plan, computes the resulting XP state, removes all resources and increments the rank. An insufficient balance or invalid expression leaves points, XP, inventory and rank unchanged. Resource removal is a purchase transaction, so it uses exact XP setters rather than XP-gain events that another mod could cancel or multiply halfway through a charge.

Minecraft experience is calculated from the player's actual level and progress bar using the vanilla cumulative XP curve. The `totalExperience` bookkeeping field can be stale after commands and is not used to decide affordability. After a successful XP payment it is synchronized with the actual remaining XP. Normal Minecraft experience synchronization updates the player's bar.

Administrative rank commands continue to bypass costs. Resetting a tree or all Mastery progression does not reimburse spent items, Minecraft XP or points from other trees.

## Automatic proficiency caps

A tree's point schedule must support costs that draw from its currency, including nodes owned by other trees. The derived budget sums every node's maximum point demand in that currency for every rank. Within an AND group it sums child demands; within an OR group it takes the largest child demand for that currency. Depth inflation is included.

The result is floored at the previous budget from that tree's owned `cost × max_rank` values. This retains proficiency progression for trees whose custom prices use only items or Minecraft XP. The derived cap also accommodates the highest explicit `level` gate on the tree's own nodes. Existing point award intervals, point amounts, milestone awards, formulas and world-tier limits still apply. An entirely externally funded tree with no point-award schedule retains its existing zero-budget leveling behavior unless an explicit node level gate requires a higher cap.

This budget is deliberately sufficient for every authored choice; buying cheaper OR alternatives does not lower the tree's saved cap. It is not a record of which branch each player paid.

## Integration and verification

`CostResolver.forNode` returns the inherited expression and its resolved depth settings. `CostPlanner` is a pure planner over point balances, raw XP and inventory descriptors. `CostService.affordable` supplies the client preview; `CostService.purchase` supplies the server transaction. Other server integrations should use that purchase entry point when a real player's XP or items are involved.

The original `ProgressionService.purchase` overload remains available for pure point-only progression code and tests. It cannot spend a real player's inventory or Minecraft XP. A payment-hook overload validates progression first, then invokes a server-side payer exactly once before granting the rank.

Tests cover nested backtracking, duplicate resource demands, exact-item/tag overlap, depth rounding, inherited replacement, cross-tree point budgets, stale Minecraft XP bookkeeping, failed-purchase atomicity and prerequisites checked before payment. Live client interaction remains a separate validation step.

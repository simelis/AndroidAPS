# Food-based eCarbs

Open **Manage → Food** (or the Food QuickLaunch shortcut). The add button is on the left, with space below the list to keep it clear of the final row.

1. For foods entered in grams, enter nutrients **per 100 g**. Existing gram/kilogram portions are converted for display without changing their nutrients in the background. Saving an edited gram food stores it as `portion = 100`, `unit = g`. The database stores whole-gram nutrients, so conversion rounding is shown for review before saving. Wizard calculations on an unedited entry retain the original precision.
2. Foods measured in pieces, cups, millilitres or other units remain supported. Enter their portion, unit and nutrients for that portion. Their calculator opens the ordinary wizard with the saved carbs and food name, without a portion-weight card or automatic protein/fat rule. No density or piece weight is assumed. Switching between grams and other units clears nutrient inputs so the correct values must be entered for the new basis.
3. In the food editor, enable **Save an eCarbs rule for this food**, choose a calculation method, and set delay and duration. Both protein and fat must be entered; use zero when absent. No factors or duration are selected for a new rule. Duration must be 1–10 hours; delay may be 0–720 minutes after the wizard's carb time.
4. The editor shows the formula's carbohydrate equivalent per 100 g. You can adjust this value by hand and save it with the food. A manual value is a fixed equivalent per 100 g; selecting **Use calculated amount** returns to the formula. Changing protein/fat or formula factors resets the manual value.
5. Select the food's calculator and enter **Your portion weight** in grams. No meal weight is assumed. The wizard scales carbs, protein, fat and the saved eCarbs rule by weight. A configured rule applies automatically. The resulting carb/eCarb grams and timing remain editable for this meal; those edits do not overwrite the food's rule. Editing the wizard's separate carbs field changes only meal carbs.
6. Review the normal wizard confirmation before submitting. Food mode uses all entered meal carbs for the immediate wizard calculation and schedules additional eCarbs through the existing eCarbs path. It replaces the bread/dumpling/pizza split while enabled. You can turn off the saved rule for one meal or disable it in the food editor to remove the reusable rule.

Rules are stored with the food record and synced in the `aapsFoodECarbs` extension on Nightscout food documents. Meal portion weights and one-meal overrides are never saved.

## Nightscout synchronization

Stock Nightscout API v3 supports food documents with extension fields; no server modification is required. Normal AAPS upload settings and Nightscout food write permissions still apply. This branch previously disabled food uploads; its food queue is now enabled. Updates use `PATCH /api/v3/food/{identifier}` with only mutable food fields, preserving unrelated server fields and avoiding immutable timestamps. Empty identifiers are rejected before any request. Failed food writes keep their queue cursor for retry.

- The versioned extension includes the method, custom factors, delay, duration and optional manual eCarbs per 100 g. Restoring a food on another updated AAPS device restores its rule independently of the local database ID.
- Incoming older food records with the extension missing or null retain the local extension and queue a repair upload. Changing standard nutrients still works. An explicit `enabled: false` extension disables the rule on other updated devices; omission never means disable.
- Rule edits carry an `updatedAt` revision timestamp. Stale downloaded rules cannot replace a newer local adjustment; the retained rule is queued for upload. Concurrent edits use the later timestamp, so devices should keep their clocks synchronized. Unrelated nutrient edits do not advance the rule revision.
- Database version 37 adds a nullable metadata column without replacing existing foods. The former device-local preference rules migrate into their existing food rows before food reads, downloads or uploads. The old preference remains as a recovery copy and never overrides a present rule or explicit disable.
- Unknown extension versions are preserved and remain inactive. Unrelated food edits preserve them. Editing this custom rule requires an updated AAPS client; Nightscout's standard food editor does not expose these controls. An older editor can strip the extension from the server; an updated device retaining the food repairs it on its next food sync. Avoid clearing all updated local copies before that repair reaches Nightscout.

Compatibility is checked against the stock [Nightscout collection routes](https://github.com/nightscout/cgm-remote-monitor/blob/master/lib/api3/generic/collection.js) and [validation](https://github.com/nightscout/cgm-remote-monitor/blob/master/lib/api3/shared/operationTools.js). No live user's Nightscout database is modified by the tests.

## Calculation choices

Let `P` and `F` be grams of protein and fat in the entered portion. Round only the final carbohydrate-equivalent result to the nearest whole gram. A saved manual equivalent instead uses `manualGramsPer100g × portionWeight / 100` before rounding.

| Method | Additional eCarbs |
| --- | --- |
| Custom factors | `P × proteinPercent / 100 + F × fatPercent / 100` |
| Original FPU reference | `(4 × P + 9 × F) / 10` |
| Modified FPU reference | `(4 × P + 9 × F) / 20` |

These are carbohydrate-equivalent estimates for the existing eCarbs mechanism, not measurements of glucose produced by digestion. The formula selector does not select a complete published insulin regimen or its timing. Original FPU methods have been associated with increased hypoglycemia; the [modified-FPU reference study](https://doi.org/10.1186/s12986-023-00757-w) involved adults using injections. Neither is offered as a validated pediatric AAPS dosing method. Factors and timing need an individual plan with the diabetes team. An increased profile also changes insulin delivery and can overlap with the effect of eCarbs.

## Validation

- New foods have no eCarbs rule. A valid saved rule applies automatically when that food is selected.
- Unknown or negative protein/fat values cannot enable food mode.
- Immediate carbs and additional eCarbs must each fit the configured carb limit. The master rechecks additional eCarbs and timing for manual wizard requests, including requests from paired clients.
- For gram foods, a positive gram portion and a known weight basis are required. Invalid food settings or an unavailable food entry disable submission. Invalid saved JSON or settings fall back to unconfigured values. Other units use the ordinary wizard with the food's saved carbs.
- Recalculation invalidates the previous result. A meal with only eCarbs can be confirmed, and a consumed confirmation cannot be submitted again.

Automated coverage includes formula arithmetic, gram scaling, legacy nutrient conversion, other-unit editing and wizard fallback, manual equivalents, database and Nightscout serialization, missing-extension preservation and repair, explicit disabling, legacy preference migration, real SQLite upgrade/reopening, food upload routing and failure retry, exclusive use of food mode versus old presets, request forwarding and timing, missing/deleted food data, UI controls, master constraints and eCarbs-only confirmation. Pump delivery, a live Nightscout deployment and glucose outcomes have not been tested with this feature.

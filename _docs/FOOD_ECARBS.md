# Food-based eCarbs

Open **Manage → Food** (or the Food QuickLaunch shortcut). The add button is on the left, with space below the list to keep it clear of the final row.

1. Edit a food and enter its nutrients **per 100 g**. Existing gram/kilogram portions are converted for display without background database writes. Saving an edited food stores it as `portion = 100`, `unit = g`. The database stores whole-gram nutrients, so conversion rounding is shown for review before saving. Wizard calculations on an unedited entry retain the original precision.
2. For entries measured in pieces, cups, millilitres or other units, enter the weight of the **original portion** before conversion. No density or piece weight is assumed.
3. In the food editor, enable **Save an eCarbs rule for this food**, choose a calculation method, and set delay and duration. Both protein and fat must be entered; use zero when absent. No factors or duration are selected for a new rule. Duration must be 1–10 hours; delay may be 0–720 minutes after the wizard's carb time.
4. The editor shows the formula's carbohydrate equivalent per 100 g. You can adjust this value by hand and save it with the food. A manual value is a fixed equivalent per 100 g; selecting **Use calculated amount** returns to the formula. Changing protein/fat or formula factors resets the manual value.
5. Select the food's calculator and enter **Your portion weight** in grams. No meal weight is assumed. The wizard scales carbs, protein, fat and the saved eCarbs rule by weight. A configured rule applies automatically. The resulting carb/eCarb grams and timing remain editable for this meal; those edits do not overwrite the food's rule. Editing the wizard's separate carbs field changes only meal carbs.
6. Review the normal wizard confirmation before submitting. Food mode uses all entered meal carbs for the immediate wizard calculation and schedules additional eCarbs through the existing eCarbs path. It replaces the bread/dumpling/pizza split while enabled. You can turn off the saved rule for one meal or disable it in the food editor to remove the reusable rule.

Rules are local to this device and keyed by the food's database ID. Meal portion weights and one-meal overrides are never saved.

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
- A positive gram portion and a known weight basis are required. Invalid food settings or an unavailable food entry disable submission. Invalid saved JSON or settings fall back to unconfigured values.
- Recalculation invalidates the previous result. A meal with only eCarbs can be confirmed, and a consumed confirmation cannot be submitted again.

Automated coverage includes formula arithmetic, gram scaling, legacy nutrient conversion, explicit weights for other units, settings persistence under newly assigned food IDs, manual equivalents, exclusive use of food mode versus old presets, request forwarding and timing, missing/deleted food data, UI controls, master constraints and eCarbs-only confirmation. Pump delivery and glucose outcomes have not been tested with this feature.

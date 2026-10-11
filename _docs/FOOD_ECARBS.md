# Food-based eCarbs

Open **Manage → Food** (or the Food QuickLaunch shortcut). The add button is on the left, and the list has space at the bottom so the button does not cover the last row.

## Using it

1. **Foods in grams** are entered **per 100 g**. Older foods with a gram or kilogram portion are shown converted to 100 g. Nothing is written to the database until you save. When you save, the food is stored as `portion = 100`, `unit = g`. The database keeps whole grams, so the editor shows the rounded values before you save. The wizard uses the original values of a food that was not saved again.
2. **Foods in other units** (pieces, cups, ml, ...) still work. Enter the portion, unit and nutrients for that portion. Their calculator button opens the normal wizard with the food's carbs and name. There is no portion weight card and no eCarbs rule, because AAPS never guesses the weight of a piece or a cup. Changing the unit between grams and another unit clears the nutrients, so you enter them again for the new basis.
3. **eCarbs rule.** In the food editor, turn on **Save an eCarbs rule for this food**. Choose a method, a delay and a duration. Protein and fat must both be entered (use 0 for none). A new rule has no factors and no duration, so you must choose them. Duration is 1–10 h. Delay is 0–720 min after the wizard carb time.
4. The editor shows the formula result per 100 g. You can change this value by hand. A hand value is a fixed number of eCarbs per 100 g; **Use calculated amount** goes back to the formula. Changing protein, fat or the factors clears the hand value.
5. **In the wizard**, tap the food's calculator button and enter **Your portion weight** in grams. No weight is filled in for you. The wizard scales carbs, protein, fat and the rule by the weight. A saved rule is used automatically. You can still change the eCarbs grams and timing for this meal; this does not change the saved rule. Changing the wizard's carbs field changes only the meal carbs.
6. Check the normal wizard confirmation before you submit. In food mode, all meal carbs go into the wizard calculation now, and the extra eCarbs are scheduled with the existing eCarbs function. Food mode replaces the bread/dumpling/pizza split. You can turn the rule off for one meal, or turn it off in the food editor to remove it.

Portion weights and changes made for one meal are never saved.

## Storage and Nightscout

- The rule is stored in the food row in the database (`eCarbsRuleJson`, database version 37). It is read into a typed `FoodECarbsRule`. A rule that cannot be read, or is not valid, is treated as no rule.
- The rule is kept in AAPS only. It is not sent to Nightscout, and food edits are not uploaded.
- Foods still download from Nightscout. A food that you save, delete or restore in AAPS is marked `isLocallyEdited`. A Nightscout download never changes that row again, so your edits, your rule and your deletes are kept. Foods you never touched in AAPS still follow Nightscout.

## Calculation

`P` and `F` are grams of protein and fat in your portion. Only the final result is rounded to whole grams. A hand value uses `manualGramsPer100g × portionWeight / 100` before rounding.

| Method | Extra eCarbs |
| --- | --- |
| Custom factors | `P × proteinPercent / 100 + F × fatPercent / 100` |
| Original FPU reference | `(4 × P + 9 × F) / 10` |
| Modified FPU reference | `(4 × P + 9 × F) / 20` |

These are estimates for the existing eCarbs function, not a measure of the glucose made by digestion. The method choice is not a full treatment plan and does not set timing. Original FPU methods have been linked to more hypoglycemia. The [modified-FPU study](https://doi.org/10.1186/s12986-023-00757-w) was done with adults on injections. Neither method is a tested dosing method for children in AAPS. Factors and timing must come from a personal plan made with the diabetes team. A raised profile also changes insulin delivery and can add to the effect of eCarbs.

## Checks

- New foods have no rule. A valid saved rule is used automatically when the food is chosen.
- Food mode cannot be turned on when protein or fat is unknown or negative.
- Meal carbs and extra eCarbs must each be within the carb limit. The master checks the eCarbs amount and timing again for manual wizard requests, also when they come from a paired client.
- A gram food needs a positive portion weight. Invalid food values, or a food that cannot be loaded, block submit.
- A new calculation removes the old result. A meal with only eCarbs can be confirmed. A confirmation that was already used cannot be submitted again.

Pump delivery and glucose results have not been tested with this feature.

# Food-based eCarbs

Open **Manage → Food**, or add **Food** to the QuickLaunch toolbar (Configure QuickLaunch → Treatment).

**Favourites.** Tap the star on a food, or the star in its editor (applied on Save), to mark it, and tap the **Favourites** chip to see only those. The chip stays on until you turn it off. Favourites are kept on this phone only, as food IDs, and are not exported, because after a reinstall the same IDs would point at other foods.

## Using it

1. **Amount and nutrients.** A food is the nutrients of an **Amount** in a **Unit**: 100 g from a label, 3 pieces, 250 ml, a 30 g slice. A new food starts as 100 g. For a unit that is a weight (g, gram, grams or kg), no nutrient can weigh more than the food; the editor says so, because 30 g carbs in a 25 g slice is a typo. AAPS never guesses the weight of a piece or a cup.
2. **Enter the amount every time** (on for new foods). On: the wizard starts with an empty amount, so you weigh or count the food every time. Off: it starts at the saved amount, so a meal you usually eat the same way (3 pieces) is taken as it is. Either way the wizard asks for the **Amount eaten** in the food's own unit (steps of 0.5 for small amounts like pieces, 10 for larger ones, up to 20 times the saved amount), and the carbs, protein, fat and eCarbs scale with it.
3. **eCarbs formula.** Any food can have one. In the food editor, turn on **Save an eCarbs formula for this food**. Choose a method, a delay and a duration. Duration is 1–10 h. Delay is 0–720 min after the wizard carb time. The first formula has no factors and no duration, so you must choose them. After that, a food without a formula starts from the last formula saved on this phone (without its fixed amount); the switch stays off until you turn it on. The explanations of the switches and the methods are behind ⓘ buttons.
   - **Nutrients.** A formula needs only the nutrients it uses. A fixed amount needs neither. Custom factors need the nutrients whose factor is above 0. The FPU methods need protein or fat; an unknown one is counted as 0 g, which gives fewer eCarbs, and the editor and wizard say so. A blank value means unknown, so enter 0 for none.
   - **Carbs counted now.** 50–100 %, default 100 %. The rest of the meal carbs is not dropped: it is added to the eCarbs, like the wizard's pizza option. For example 80 % of 50 g counts 40 g in the bolus now and adds 10 g to the eCarbs.
4. The editor shows the formula result for the saved amount (for example "for 100 g"). You can change this value by hand. A hand value is a fixed number of eCarbs for the saved amount; **Use calculated amount** goes back to the formula. Changing the amount, the unit, protein, fat, the method or the factors clears the hand value.
5. **In the wizard**, tap the food's calculator button. When the food asks for the amount every time, the **Amount eaten** is empty and has the focus, and the problem text shows only after an amount is entered; otherwise it starts at the saved amount. The wizard scales carbs, protein, fat and the formula by the amount eaten ÷ the saved amount. A saved formula is used automatically. The formula is a fourth choice next to bread, dumplings and pizza (food icon), selected automatically and disabled when the food lacks a nutrient it needs; picking bread, dumplings or pizza turns it off. A food without a saved formula has no such choice, only a short note. The card shows the amount and the meal nutrients; the nutrients appear only after an amount is entered. The carbs field stays the whole meal; a line under it shows the carbs in the bolus now and the eCarbs later, for dumplings and pizza too. The ⓘ button explains the scaling, names the formula and tells where to change the eCarbs. While the formula is on, the wizard inputs have an **eCarbs** row next to carb time with the amount and timing; its **Change** button lets you change them for this meal; this does not change the saved formula. Changing the wizard's carbs field changes only the meal carbs.
6. Check the normal wizard confirmation before you submit. In food mode, the carbs counted now go into the wizard calculation (all of them at 100 %), and the eCarbs are scheduled with the existing eCarbs function. Food mode replaces the bread/dumpling/pizza split. To leave the formula out of one meal, choose bread, dumplings or pizza instead; to remove it, turn it off in the food editor.

The amount eaten and changes made for one meal are never saved with the food. They are saved in the note of the meal in the treatment history (bolus calculation, carbs, user entries and Nightscout), for example "Pizza: 250 g. Modified FPU (200 kcal): 80% of 50 g carbs now, eCarbs 30 g after 60 min over 3 h". A note you typed follows it.

## Storage

- The formula is stored in the food row in the database (`eCarbsRuleJson`, database version 38) and synced as the `aapsECarbsRule` field of the Nightscout food and read into a typed `FoodECarbsRule`. A formula that cannot be read, or is not valid, is treated as no formula. The fixed amount is for the saved amount of the food.
- **Enter the amount every time** is the `askAmount` column (also database version 38) and the `aapsAskAmount` field in Nightscout. The 37 to 38 migration sets it the way foods were used before: a food in g, gram, grams or kg is asked every time, any other starts at its portion. A food from Nightscout without the field (made in the web editor) gets the same rule.
- **Sync.** The formula and the amount setting go with the food changes that AAPS uploads, and a download takes them over like the other fields. A missing field in an update keeps the old value on the server, so "no formula" is sent as `aapsECarbsRule: null`. The Nightscout web editor removes unknown fields when it saves a food, so edit foods with a formula in AAPS.

## Calculation

`P` and `F` are grams of protein and fat in the meal (scaled by the amount eaten ÷ the saved amount); an unknown one is 0. Only the final result is rounded to whole grams. With carbs counted now below 100 %, the carbs now are rounded down and the rest is added to these eCarbs. A fixed amount (`manualGrams` in the JSON) is used as `fixed × amount eaten / saved amount` before rounding.

| Method | Extra eCarbs |
| --- | --- |
| Custom factors | `P × proteinPercent / 100 + F × fatPercent / 100` |
| Original FPU reference | `(4 × P + 9 × F) / 10` |
| Modified FPU reference | `(4 × P + 9 × F) / 20` |

These are estimates for the existing eCarbs function, not a measure of the glucose made by digestion. The method choice is not a full treatment plan and does not set timing. Original FPU methods have been linked to more hypoglycemia. The [modified-FPU study](https://doi.org/10.1186/s12986-023-00757-w) was done with adults on injections. Neither method is a tested dosing method for children in AAPS. Factors and timing must come from a personal plan made with the diabetes team. A raised profile also changes insulin delivery and can add to the effect of eCarbs.

## Checks

- New foods have no formula. A valid saved formula is used automatically when the food is chosen.
- Food mode cannot be turned on when the food lacks a nutrient its formula needs. A negative value counts as unknown.
- Meal carbs and eCarbs (including carbs moved from the bolus) must each be within the carb limit. The master checks the eCarbs amount and timing again for manual wizard requests, also when they come from a paired client.
- The amount eaten must be positive and at most 20 times the saved amount. In the editor, the saved amount must be positive, and for a weight unit no nutrient may weigh more than it. Invalid food values, or a food that cannot be loaded, block submit.
- Food values that cannot be calculated remove the old result. A meal with only eCarbs can be confirmed.

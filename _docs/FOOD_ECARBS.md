# Food-based eCarbs

Open **Manage → Food**, or add **Food** to the QuickLaunch toolbar (Configure QuickLaunch → Treatment). The add button is on the right, and the list has space at the bottom so the button does not cover the last row.

**Favourites.** Tap the star on a food, or the star in its editor (applied on Save), to mark it, and tap the **Favourites** chip to see only those. The chip stays on until you turn it off. Favourites are kept on this phone only, as food IDs, and are not exported, because after a reinstall the same IDs would point at other foods. Dragging inside the editor only scrolls it; drag the handle at the top (or use Cancel, Back or a tap outside) to close it.

## Using it

1. **Nutrients per 100 g** is a switch in the food editor (on for new foods). When on, you enter the nutrients of 100 g, and the wizard asks how many grams you eat. Older foods with a gram or kilogram portion are shown converted to 100 g. Nothing is written to the database until you save. When you save, the food is stored as `portion = 100`, `unit = g`. The database keeps whole grams, so the editor shows the rounded values before you save. The wizard uses the original values of a food that was not saved again.
2. **Foods with a usual amount** (switch off): enter the amount you usually eat and its unit, for example 3 pieces, 250 ml or 30 g (a slice), and the nutrients of that amount. The wizard asks for the **Amount eaten** in the same unit, starting at that usual amount, so a usual meal is taken as it is. Change it for a different meal (steps of 0.5 for small counts like pieces, 10 for larger amounts, up to 20 times the usual amount); the carbs, protein, fat and eCarbs scale with it. For a food where the amount changes every time, saving the nutrients of 1 piece is easiest. AAPS never guesses the weight of a piece or a cup. Flipping the switch keeps the entered numbers and asks you to check them, because they now mean something else; only a fixed eCarbs amount is cleared.
3. **eCarbs formula.** Any food can have one. In the food editor, turn on **Save an eCarbs formula for this food**. Choose a method, a delay and a duration. Duration is 1–10 h. Delay is 0–720 min after the wizard carb time. The first formula has no factors and no duration, so you must choose them. After that, a food without a formula starts from the last formula saved on this phone (without its fixed amount); the switch stays off until you turn it on. The explanations of the switches and the methods are behind ⓘ buttons.
   - **Nutrients.** A formula needs only the nutrients it uses. A fixed amount needs neither. Custom factors need the nutrients whose factor is above 0. The FPU methods need protein or fat; an unknown one is counted as 0 g, which gives fewer eCarbs, and the editor and wizard say so. A blank value means unknown, so enter 0 for none.
   - **Carbs counted now.** 50–100 %, default 100 %. The rest of the meal carbs is not dropped: it is added to the eCarbs, like the wizard's pizza option. For example 80 % of 50 g counts 40 g in the bolus now and adds 10 g to the eCarbs.
4. The editor shows the formula result per 100 g (or per saved amount). You can change this value by hand. A hand value is a fixed number of eCarbs per 100 g (or per saved amount); **Use calculated amount** goes back to the formula. Changing protein, fat or the factors clears the hand value.
5. **In the wizard**, tap the food's calculator button. For a food per 100 g, enter **Your portion weight** in grams: no weight is filled in for you, the weight field has the focus when the wizard opens, and the problem text shows only after a weight is entered. For any other food, the **Amount eaten** starts at the amount saved with the food. The wizard scales carbs, protein, fat and the formula by the weight or amount. A saved formula is used automatically. The formula is a fourth choice next to bread, dumplings and pizza (food icon), selected automatically and disabled when the food lacks a nutrient it needs; picking bread, dumplings or pizza turns it off. A food without a saved formula has no such choice, only a short note. The card shows the weight or amount and the meal nutrients; for a food per 100 g, the nutrients appear only after a weight is entered. The carbs field stays the whole meal; a line under it shows the carbs in the bolus now and the eCarbs later, for dumplings and pizza too. The ⓘ button explains the scaling, names the formula and tells where to change the eCarbs. While the formula is on, the wizard inputs have an **eCarbs** row next to carb time with the amount and timing; its **Change** button lets you change them for this meal; this does not change the saved formula. Changing the wizard's carbs field changes only the meal carbs.
6. Check the normal wizard confirmation before you submit. In food mode, the carbs counted now go into the wizard calculation (all of them at 100 %), and the eCarbs are scheduled with the existing eCarbs function. Food mode replaces the bread/dumpling/pizza split. To leave the formula out of one meal, choose bread, dumplings or pizza instead; to remove it, turn it off in the food editor.

The weight or amount eaten and changes made for one meal are never saved.

## Storage and Nightscout

- The formula is stored in the food row in the database (`eCarbsRuleJson`, database version 37) and read into a typed `FoodECarbsRule`. A formula that cannot be read, or is not valid, is treated as no formula. The fixed amount keeps its JSON name `manualGramsPer100g`, also when it is per portion.
- How a food is counted is the `byWeight` column (also database version 37) and the `aapsByWeight` field in Nightscout. A food without the field (made in the Nightscout web editor, or by an older version) is weighed when its unit is g, gram, grams or kg. The 36 to 37 migration uses the same rule for existing foods.
- Food changes made in AAPS or AAPSClient are uploaded to Nightscout (API v3) when **Upload food changes** is on in the Nightscout client settings, and the formula goes with them as the `aapsECarbsRule` field. It is off by default: a phone with a version of AAPS without this feature would take over uploaded food changes without the formula, so turn it on only when all your phones run the same version with this feature. An older version reads a newer formula without the parts it does not know, and if it uploads that food again, those parts are lost on every phone (for example **Carbs counted now** goes back to 100 %). On the main AAPS phone, **Upload data to NS** must be on too. When off, food changes stay on this phone, and turning it on later uploads them. This needs no change on the Nightscout server; it was tested against Nightscout master.
- A food that you save, delete or restore is marked `isLocallyEdited`. Until that change is uploaded, a Nightscout download does not change the food, because the Nightscout copy is older. After the upload, the mark is removed (without a new change, so nothing is uploaded twice) and changes from other devices apply again. This also keeps a full sync, which uploads everything again, from overwriting newer Nightscout values with old local ones. If Nightscout rejects the upload, the food upload stops there and tries again on the next sync, so the local change is never counted as uploaded.
- Deletes reach the other devices through the Nightscout food history (and the live socket). The plain food list hides deleted foods, so the first load (and the first load after a full sync) reads the whole history, not only changes from now on.
- If a food was deleted on another device and you restore or edit it here, it is added to Nightscout again as a new food.

### Nightscout behaviour this depends on

- An update must send only the fields that can change. Nightscout refuses `date`, `app`, `utcOffset`, `identifier` and some other fields in an update when they differ from what it stored. Foods made in the Nightscout web editor have no `date` or `app`, so sending them always fails with 400. This is why food update looked broken before.
- A missing field in an update keeps the old value on the server, so "no formula" is sent as `aapsECarbsRule: null`.
- The food download cursor is its own preference (`ns_food_last_loaded_srv_modified`). It is not in the saved Nightscout sync state (`V3LastModified`), because older AAPS versions read that state strictly and would fail on a key they do not know.
- Nightscout names the collection `food`. AAPS used to listen for `foods`, so live food changes never arrived; that is fixed.
- Foods made or edited in the Nightscout web editor (API v1) have no change time, so they are not in the history. AAPS still reads the full food list every 5th time to find them. The web editor also removes `aapsECarbsRule` when it saves a food, so edit foods with rules in AAPS.

Run the live test against a throwaway Nightscout only, because it writes foods:

```
NS_TEST_URL=http://127.0.0.1:1337 NS_TEST_TOKEN=<token> NS_TEST_API_SECRET=<secret> ./gradlew :core:nssdk:jvmTest --tests "*NightscoutFoodLiveTest"
```

## Going back to a version without this feature

This feature uses database version 37. A version 36 build cannot read it: Room finds no way back, so it drops every table and starts with an empty database.

For testing on top of 4.0.0-beta1 there is a fallback build: [branch `v4.0-beta1-fallback`](https://github.com/simelis/AndroidAPS/tree/v4.0-beta1-fallback), version name `4.0.0-beta1-fallback`. It is 4.0.0-beta1 plus one step back from 37 to 36: it rebuilds the food table with only the version 36 columns and keeps all other data. Build it from the same fork, so it is signed with the same key and installs over the test build without losing data. From the fallback, normal builds upgrade as usual.

The food formulas are not lost if **Upload food changes** was on: they are on Nightscout and come back when this feature is installed again.

## Calculation

`P` and `F` are grams of protein and fat in the meal (scaled by the weight or amount eaten); an unknown one is 0. Only the final result is rounded to whole grams. With carbs counted now below 100 %, the carbs now are rounded down and the rest is added to these eCarbs. A fixed amount (`manualGramsPer100g` in the JSON) is used as `fixed × weight / 100` for a food per 100 g, and as `fixed × amount eaten / saved amount` for any other food, before rounding.

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
- A food per 100 g needs a positive weight, any other food a positive amount (at most 20 times its saved amount). Invalid food values, or a food that cannot be loaded, block submit.
- A new calculation removes the old result. A meal with only eCarbs can be confirmed. A confirmation that was already used cannot be submitted again.

Pump delivery and glucose results have not been tested with this feature.

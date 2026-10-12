# Food sync with Nightscout

Foods sync between AAPS, AAPSClient and Nightscout through Nightscout API v3. No change on the Nightscout server is needed.

## How it works

- **Downloads.** Changes made through API v3 (by AAPS and AAPSClient) are read from the food history, which also has deleted foods. The plain food list hides deleted foods, so the first load (and the first load after a full sync) reads the whole history, not only changes from now on. The cursor is `food` in the saved sync state (`V3LastModified`). Only the history read moves it; a websocket frame does not. Live changes and deletes also arrive through the websocket.
- **Uploads.** Food changes made in AAPS or AAPSClient are uploaded like other data. In AAPS, **Upload data to NS** must be on.
- **Local edits.** A food that you save, delete or restore is marked `isLocallyEdited` (database version 37). Until that change is uploaded, a Nightscout download does not change the food, because the Nightscout copy is older. After the upload, the mark is removed (without a new change, so nothing is uploaded twice) and changes from other devices apply again. This also keeps a full sync, which uploads everything again, from overwriting newer Nightscout values with old local ones.
- **Rejected uploads.** If Nightscout rejects an upload (400), the food upload stops there and the cursor stays, so the local change is not counted as uploaded.
- **Restore after a delete.** If a food was deleted on another device and you restore or edit it here, Nightscout answers 410 Gone, and the food is added to Nightscout again as a new food.

## Nightscout behaviour this depends on

- An update must send only the fields that can change. Nightscout refuses `date`, `app`, `utcOffset`, `identifier` and some other fields in an update when they differ from what it stored. Foods made in the Nightscout web editor have no `date` or `app`, so sending the whole food fails with 400.
- A missing field in an update keeps the old value on the server, so a removed value is sent as `null`.
- Nightscout names the collection `food`, not `foods`.
- The Nightscout web editor writes foods through API v1, so these foods have no change time and are not in the API v3 history. AAPS reads the full food list every 5th time to find them.

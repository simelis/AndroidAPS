package app.aaps.core.nssdk.remotemodel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Timestamp of last modification of every collection
 *
 **/
@Serializable
data class LastModified(
    @SerialName("collections") val collections: Collections
) {

    @Serializable
    data class Collections(

        @SerialName("devicestatus") var devicestatus: Long = 0, // devicestatus collection
        @SerialName("entries") var entries: Long = 0,           // entries collection
        @SerialName("profile") var profile: Long = 0,           // profile collection
        @SerialName("treatments") var treatments: Long = 0,     // treatments collection
        // Not a timestamp: counts food loads, so the full food list is read every 5th time (see
        // LoadFoodsRunner). Nightscout names the collection "food", so the server never fills this.
        @SerialName("foods") var foods: Long = 0,
        @SerialName("food") var food: Long = 0,                 // food collection
        @SerialName("settings") var settings: Long = 0          // settings collection
    )

    fun set(colName: String, value: Long) {
        when (colName) {
            "devicestatus" -> collections.devicestatus = value
            "entries"      -> collections.entries = value
            "profile"      -> collections.profile = value
            "treatments"   -> collections.treatments = value
            "food"         -> collections.food = value
            "settings"     -> collections.settings = value
        }
    }
}

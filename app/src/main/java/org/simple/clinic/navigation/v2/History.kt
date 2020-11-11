package org.simple.clinic.navigation.v2

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class History(val keys: List<ScreenKey>) : Parcelable {

  fun add(screenKey: ScreenKey): History {
    return copy(keys = keys + screenKey)
  }

  fun withoutLast(): History {
    require(keys.size > 1) { "Cannot remove last when there is only one key left" }

    return copy(keys = keys.dropLast(1))
  }
}

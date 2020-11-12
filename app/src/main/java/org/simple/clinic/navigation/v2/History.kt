package org.simple.clinic.navigation.v2

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class History(val keys: List<ScreenKey>) : Parcelable {

  init {
    if (keys.isEmpty()) throw IllegalStateException("Require at least 1 key!")
  }

  fun add(screenKey: ScreenKey): History {
    return copy(keys = keys + screenKey)
  }

  fun withoutLast(): History {
    require(keys.size > 1) { "Cannot remove last when there is only one key left" }

    return copy(keys = keys.dropLast(1))
  }

  fun removeUntil(key: ScreenKey): History {
    val newKeys = keys.dropLastWhile { it != key }

    return copy(keys = newKeys)
  }

  fun top(): ScreenKey {
    return keys.last()
  }
}

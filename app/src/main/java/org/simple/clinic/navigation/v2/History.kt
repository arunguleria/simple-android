package org.simple.clinic.navigation.v2

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class History(private val keys: List<ScreenKey>) : Parcelable

package org.simple.clinic.navigation.v2

import androidx.annotation.IdRes
import androidx.fragment.app.FragmentManager

class Router(
    private val history: History,
    private val fragmentManager: FragmentManager,
    @IdRes private val containerId: Int
) {

  constructor(
      history: List<ScreenKey>,
      fragmentManager: FragmentManager,
      @IdRes containerId: Int
  ) : this(
      history = History(history),
      fragmentManager = fragmentManager,
      containerId = containerId
  )

  constructor(
      initialScreenKey: ScreenKey,
      fragmentManager: FragmentManager,
      @IdRes containerId: Int
  ) : this(
      history = listOf(initialScreenKey),
      fragmentManager = fragmentManager,
      containerId = containerId
  )
}

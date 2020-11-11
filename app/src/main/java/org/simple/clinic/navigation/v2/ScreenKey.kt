package org.simple.clinic.navigation.v2

import android.os.Bundle
import android.os.Parcelable
import androidx.fragment.app.Fragment

abstract class ScreenKey(): Parcelable {

  companion object {
    private const val ARGS_KEY = "org.simple.clinic.navigation.v2.ScreenKey.ARGS_KEY"
  }

  open val fragmentTag: String
    get() = javaClass.name

  open val type: ScreenType = ScreenType.FullScreen

  protected abstract fun instantiateFragment(): Fragment

  fun createFragment(): Fragment {
    return instantiateFragment().apply {
      val args = Bundle()
      args.putParcelable(ARGS_KEY, this@ScreenKey)
      this.arguments = args
    }
  }

  enum class ScreenType {
    FullScreen,
    Modal
  }
}

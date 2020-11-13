package org.simple.clinic.navigation.test

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import kotlinx.android.parcel.Parcelize
import kotlinx.android.synthetic.main.screen_navigation_second.*
import org.simple.clinic.R
import org.simple.clinic.navigation.v2.HandlesBack
import org.simple.clinic.navigation.v2.Router
import org.simple.clinic.navigation.v2.ScreenKey
import org.simple.clinic.util.unsafeLazy

class SecondScreen : Fragment(R.layout.screen_navigation_second), HandlesBack {

  private val router: Router by unsafeLazy {
    (requireActivity() as NavigationTestActivity).router
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    nextScreenButton.setOnClickListener { router.push(ThirdScreen.Key()) }
  }

  override fun onBackPressed(): Boolean {
    router.push(ConfirmationDialog.Key())
    return true
  }

  @Parcelize
  class Key : ScreenKey() {

    override fun instantiateFragment(): Fragment {
      return SecondScreen()
    }
  }
}

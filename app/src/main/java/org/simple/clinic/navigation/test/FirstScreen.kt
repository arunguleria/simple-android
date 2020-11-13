package org.simple.clinic.navigation.test

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import kotlinx.android.parcel.Parcelize
import kotlinx.android.synthetic.main.screen_navigation_first.*
import org.simple.clinic.R
import org.simple.clinic.navigation.v2.Router
import org.simple.clinic.navigation.v2.ScreenKey
import org.simple.clinic.util.unsafeLazy

class FirstScreen : Fragment(R.layout.screen_navigation_first) {

  private val router: Router by unsafeLazy {
    (requireActivity() as NavigationTestActivity).router
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    nextScreenButton.setOnClickListener { router.push(SecondScreen.Key()) }
  }

  @Parcelize
  object Key : ScreenKey() {

    override fun instantiateFragment(): Fragment {
      return FirstScreen()
    }
  }
}

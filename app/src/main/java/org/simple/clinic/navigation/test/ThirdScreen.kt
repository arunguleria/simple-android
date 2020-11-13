package org.simple.clinic.navigation.test

import android.os.Bundle
import android.os.Parcelable
import android.view.View
import androidx.fragment.app.Fragment
import kotlinx.android.parcel.Parcelize
import kotlinx.android.synthetic.main.screen_navigation_third.*
import org.simple.clinic.R
import org.simple.clinic.navigation.v2.ExpectsResult
import org.simple.clinic.navigation.v2.Router
import org.simple.clinic.navigation.v2.ScreenKey
import org.simple.clinic.navigation.v2.ScreenResult
import org.simple.clinic.navigation.v2.Succeeded
import org.simple.clinic.util.unsafeLazy

class ThirdScreen : Fragment(R.layout.screen_navigation_third), ExpectsResult {

  private val router: Router by unsafeLazy {
    (requireActivity() as NavigationTestActivity).router
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    confirmScreenButton.setOnClickListener { router.pushExpectingResult(EnterText, TextEntrySheet.Key()) }
  }

  override fun onScreenResult(requestType: Parcelable, result: ScreenResult) {
    if (requestType == EnterText && result is Succeeded) {
      val enteredText = TextEntrySheet.readEnteredText(result)

      thirdScreenLabel.text = thirdScreenLabel.text.toString() + " : " + enteredText
    }
  }

  @Parcelize
  class Key : ScreenKey() {

    override fun instantiateFragment(): Fragment {
      return ThirdScreen()
    }
  }

  @Parcelize
  object EnterText : Parcelable
}

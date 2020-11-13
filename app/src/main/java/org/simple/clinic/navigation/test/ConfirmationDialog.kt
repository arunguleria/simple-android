package org.simple.clinic.navigation.test

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import kotlinx.android.parcel.Parcelize
import org.simple.clinic.navigation.v2.Router
import org.simple.clinic.navigation.v2.ScreenKey
import org.simple.clinic.util.unsafeLazy

class ConfirmationDialog: DialogFragment() {

  private val router: Router by unsafeLazy {
    (requireActivity() as NavigationTestActivity).router
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    return AlertDialog.Builder(requireContext())
        .setTitle("Confirm")
        .setMessage("Are you sure you want to go back?")
        .setPositiveButton("Yes") { _, _ -> router.popUntil(FirstScreen.Key) }
        // Needed to inform the backstack about the dismissal of the fragment
        .setNegativeButton("No") { _, _ -> router.pop() }
        .create()
  }

  @Parcelize
  class Key: ScreenKey() {
    override fun instantiateFragment(): Fragment {
      return ConfirmationDialog()
    }

    override val type: ScreenType
      get() = ScreenType.Modal
  }
}

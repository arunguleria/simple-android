package org.simple.clinic.navigation.test

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.simple.clinic.R
import org.simple.clinic.navigation.v2.Router

class NavigationTestActivity: AppCompatActivity() {

  lateinit var router: Router

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_navigation_test)
    router = Router(FirstScreen.Key, supportFragmentManager, R.id.content)
  }

  override fun onBackPressed() {
    if (!router.onBackPressed()) {
      super.onBackPressed()
    }
  }
}

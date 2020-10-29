package org.simple.clinic.introvideoscreen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import com.jakewharton.rxbinding3.view.clicks
import com.zhuinden.simplestack.Backstack
import io.reactivex.Observable
import io.reactivex.rxkotlin.cast
import kotlinx.android.synthetic.main.screen_intro_video.view.*
import org.simple.clinic.R
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.di.injector
import org.simple.clinic.mobius.MobiusDelegate
import org.simple.clinic.platform.crash.CrashReporter
import org.simple.clinic.registration.register.RegistrationLoadingScreenKey
import org.simple.clinic.util.unsafeLazy
import javax.inject.Inject
import javax.inject.Named

class IntroVideoScreen(
    context: Context,
    attrs: AttributeSet
) : ConstraintLayout(context, attrs), UiActions {

  @Inject
  lateinit var backstack: Backstack

  @Inject
  @Named("training_video_youtube_id")
  lateinit var youTubeVideoId: String

  @Inject
  lateinit var introVideoEffectHandler: IntroVideoEffectHandler.Factory

  @Inject
  lateinit var crashReporter: CrashReporter

  private val events: Observable<IntroVideoEvent> by unsafeLazy {
    Observable
        .mergeArray(
            videoClicks(),
            skipClicks()
        )
        .compose(ReportAnalyticsEvents())
        .cast<IntroVideoEvent>()
  }

  private val mobiusDelegate by unsafeLazy {
    MobiusDelegate.forView(
        events,
        IntroVideoModel.default(),
        IntroVideoUpdate(),
        introVideoEffectHandler.create(this).build()
    )
  }

  override fun onFinishInflate() {
    super.onFinishInflate()
    if (isInEditMode) return

    context.injector<IntroVideoScreenInjector>().inject(this)

    // Hard-coding to show this simple video view exists because, as of now,
    // we are not sure if we will have variations of this training video.
    // We should make the title, duration and video thumbnail configurable in order to improve this.
    introVideoSubtitle.text = resources.getString(R.string.simple_video_duration, "5:07")
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    mobiusDelegate.start()
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    mobiusDelegate.stop()
  }

  override fun openVideo() {
    openYoutubeLinkForSimpleVideo()
  }

  override fun openHome() {
    backstack.goTo(RegistrationLoadingScreenKey())
  }

  private fun videoClicks(): Observable<IntroVideoEvent> {
    val clicksFromVideoImage = introVideoImageView.clicks().map { VideoClicked }
    val clicksFromWatchVideoButton = watchVideoButton.clicks().map { VideoClicked }

    return clicksFromVideoImage
        .mergeWith(clicksFromWatchVideoButton)
        .cast()
  }

  private fun skipClicks(): Observable<IntroVideoEvent> {
    return skipButton
        .clicks()
        .map { SkipClicked }
  }

  private fun openYoutubeLinkForSimpleVideo() {
    val packageManager = context.packageManager
    val appUri = "vnd.youtube:$youTubeVideoId"
    val webUri = "http://www.youtube.com/watch?v=$youTubeVideoId"

    val resolvedIntent = listOf(appUri, webUri)
        .map { Uri.parse(it) }
        .map { Intent(Intent.ACTION_VIEW, it) }
        .firstOrNull { it.resolveActivity(packageManager) != null }

    if (resolvedIntent != null) {
      context.startActivity(resolvedIntent)
    } else {
      crashReporter.report(ActivityNotFoundException("Unable to play simple video because no supporting apps were found."))
    }
  }
}

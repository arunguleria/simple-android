package org.simple.clinic.registration.name

import android.animation.LayoutTransition
import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.RelativeLayout
import com.jakewharton.rxbinding3.widget.editorActions
import com.jakewharton.rxbinding3.widget.textChanges
import com.zhuinden.simplestack.Backstack
import io.reactivex.Observable
import io.reactivex.rxkotlin.ofType
import kotlinx.android.synthetic.main.screen_registration_name.view.*
import org.simple.clinic.R
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.di.injector
import org.simple.clinic.mobius.MobiusDelegate
import org.simple.clinic.navigation.ScreenKeyProvider
import org.simple.clinic.registration.pin.RegistrationPinScreenKey
import org.simple.clinic.user.OngoingRegistrationEntry
import org.simple.clinic.util.unsafeLazy
import javax.inject.Inject

class RegistrationFullNameScreen(
    context: Context,
    attrs: AttributeSet
) : RelativeLayout(context, attrs), RegistrationNameUi, RegistrationNameUiActions {

  @Inject
  lateinit var backstack: Backstack

  @Inject
  lateinit var screenKeyProvider: ScreenKeyProvider

  @Inject
  lateinit var effectHandlerFactory: RegistrationNameEffectHandler.Factory

  private val events by unsafeLazy {
    Observable
        .merge(
            nameTextChanges(),
            doneClicks()
        )
        .compose(ReportAnalyticsEvents())
        .share()
  }

  private val delegate: MobiusDelegate<RegistrationNameModel, RegistrationNameEvent, RegistrationNameEffect> by unsafeLazy {
    val uiRenderer = RegistrationNameUiRenderer(this)

    val screenKey = screenKeyProvider.provide<RegistrationNameScreenKey>(this)

    MobiusDelegate.forView(
        events = events.ofType(),
        defaultModel = RegistrationNameModel.create(screenKey.registrationEntry),
        update = RegistrationNameUpdate(),
        effectHandler = effectHandlerFactory.create(this).build(),
        init = RegistrationNameInit(),
        modelUpdateListener = uiRenderer::render
    )
  }

  override fun onFinishInflate() {
    super.onFinishInflate()
    if (isInEditMode) {
      return
    }
    context.injector<Injector>().inject(this)

    backButton.setOnClickListener { backstack.goBack() }

    cardViewContentLayout.layoutTransition.setDuration(200)
    cardViewContentLayout.layoutTransition.setStagger(LayoutTransition.CHANGE_APPEARING, 0)
    cardViewContentLayout.layoutTransition.setStagger(LayoutTransition.CHANGE_DISAPPEARING, 0)
    cardViewContentLayout.layoutTransition.setStagger(LayoutTransition.CHANGING, 0)

    post { fullNameEditText.requestFocus() }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    delegate.start()
  }

  override fun onDetachedFromWindow() {
    delegate.stop()
    super.onDetachedFromWindow()
  }

  override fun onSaveInstanceState(): Parcelable? {
    return delegate.onSaveInstanceState(super.onSaveInstanceState())
  }

  override fun onRestoreInstanceState(state: Parcelable?) {
    super.onRestoreInstanceState(delegate.onRestoreInstanceState(state))
  }

  private fun nameTextChanges() =
      fullNameEditText
          .textChanges()
          .map(CharSequence::toString)
          .map { it.trim() }
          .map(::RegistrationFullNameTextChanged)

  private fun doneClicks() =
      fullNameEditText
          .editorActions() { it == EditorInfo.IME_ACTION_DONE }
          .map { RegistrationFullNameDoneClicked() }

  override fun showEmptyNameValidationError() {
    validationErrorTextView.visibility = View.VISIBLE
    validationErrorTextView.text = resources.getString(R.string.registrationname_error_empty_name)
  }

  override fun hideValidationError() {
    validationErrorTextView.visibility = View.GONE
  }

  override fun openRegistrationPinEntryScreen(registrationEntry: OngoingRegistrationEntry) {
    backstack.goTo(RegistrationPinScreenKey(registrationEntry))
  }

  interface Injector {
    fun inject(target: RegistrationFullNameScreen)
  }
}

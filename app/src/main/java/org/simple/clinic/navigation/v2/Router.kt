package org.simple.clinic.navigation.v2

import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction

/**
 * Class that maintains a history of screens and is used to perform
 * the backstack changes.
 *
 * Code related to fragment transactions is mostly repurposed from https://github.com/Zhuinden/simple-stack-extensions/blob/0bbf8fd045cece22c838b62243674fb7fe450aa0/fragments/src/main/java/com/zhuinden/simplestackextensions/fragments/DefaultFragmentStateChanger.java.
 **/
class Router(
    private var history: History,
    private val fragmentManager: FragmentManager,
    @IdRes private val containerId: Int
) {

  constructor(
      history: List<ScreenKey>,
      fragmentManager: FragmentManager,
      @IdRes containerId: Int
  ) : this(
      history = History(history.map(::Normal)),
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

  // Used for posting screen results
  private val handler = Handler(Looper.getMainLooper())

  init {
    executeStateChange(history, Direction.Replace, null)
  }

  fun push(screenKey: ScreenKey) {
    val newHistory = history.add(Normal(screenKey))

    executeStateChange(newHistory, Direction.Forward, null)
  }

  fun pushExpectingResult(
      requestType: Parcelable,
      key: ScreenKey
  ) {
    val newHistory = history.add(ExpectingResult(requestType, key))

    executeStateChange(newHistory, Direction.Forward, null)
  }

  fun pop() {
    val newHistory = history.withoutLast()

    executeStateChange(newHistory, Direction.Backward, null)
  }

  fun popWithResult(result: ScreenResult) {
    val newHistory = history.withoutLast()

    executeStateChange(newHistory, Direction.Backward, result)
  }

  fun popUntil(key: ScreenKey) {
    val newHistory = history.removeUntil(key)

    executeStateChange(newHistory, Direction.Backward, null)
  }

  fun onBackPressed(): Boolean {
    val currentTop = history.top()
    val fragment = fragmentManager.findFragmentByTag(currentTop.key.fragmentTag)

    require(fragment != null) { "Could not find fragment for [$currentTop]" }

    return if (fragment !is HandlesBack) {
      if (history.requests.size > 1) {
        pop()
        true
      } else {
        false
      }
    } else {
      val handled = fragment.onBackPressed()

      if (handled) {
        true
      } else {
        if (history.requests.size > 1) {
          pop()
          true
        } else {
          false
        }
      }
    }
  }

  private fun executeStateChange(
      newHistory: History,
      direction: Direction,
      screenResult: ScreenResult?
  ) {
    checkNotMainThread()

    fragmentManager.executePendingTransactions()

    val transaction = fragmentManager
        .beginTransaction()
        .disallowAddToBackStack()

    transaction.setTransition(when (direction) {
      Direction.Forward -> FragmentTransaction.TRANSIT_FRAGMENT_OPEN
      Direction.Backward -> FragmentTransaction.TRANSIT_FRAGMENT_CLOSE
      Direction.Replace -> FragmentTransaction.TRANSIT_FRAGMENT_FADE
    })

    val currentNavRequests = history.requests
    val newNavRequests = newHistory.requests
    val newTop = newNavRequests.last()
    val currentTop = currentNavRequests.last()
    val beforeNewTop = if (newNavRequests.size > 1) newNavRequests[newNavRequests.lastIndex - 1] else null

    // Remove old fragments if they are no longer present in the new set of screens
    currentNavRequests.forEach { navRequest ->
      val fragment = fragmentManager.findFragmentByTag(navRequest.key.fragmentTag)

      if (fragment != null) {
        if (!newNavRequests.contains(navRequest)) {
          transaction.remove(fragment)
        } else if (fragment.isShowing) {
          if (navRequest == newTop) {
            if (currentTop.key.type != ScreenKey.ScreenType.Modal) {
              transaction.detach(fragment)
            }
          } else {
            transaction.detach(fragment)
          }
        }
      }
    }

    newNavRequests.forEach { navRequest ->
      var fragment = fragmentManager.findFragmentByTag(navRequest.key.fragmentTag)
      if (navRequest == newTop) {
        if (navRequest.key.type == ScreenKey.ScreenType.FullScreen) {
          if (fragment != null) {
            if (fragment.isRemoving) { // fragments are quirky, they die asynchronously. Ignore if they're still there.
              transaction.replace(containerId, navRequest.key.createFragment(), navRequest.key.fragmentTag)
            } else if (fragment.isDetached) {
              transaction.attach(fragment)
            }
          } else {
            fragment = navRequest.key.createFragment() // create and add new top if did not exist
            transaction.add(containerId, fragment, navRequest.key.fragmentTag)
          }
        } else if (navRequest.key.type == ScreenKey.ScreenType.Modal) {
          if (fragment != null) {
            if (fragment.isRemoving) { // fragments are quirky, they die asynchronously. Ignore if they're still there.
              transaction.add(navRequest.key.createFragment(), navRequest.key.fragmentTag)
            } else if (fragment.isDetached) {
              transaction.attach(fragment)
            }
          } else {
            fragment = navRequest.key.createFragment() // create and add new top if did not exist
            transaction.add(fragment, navRequest.key.fragmentTag)
          }
        }
      } else {
        if (newTop.key.type == ScreenKey.ScreenType.FullScreen) {
          if (fragment != null && fragment.isShowing) {
            transaction.detach(fragment)
          }
        } else if (newTop.key.type == ScreenKey.ScreenType.Modal) {
          // Last but one key should not be detached since the topmost key is a modal
          // and we want the previous screen to be visible
          if (navRequest != beforeNewTop && fragment != null && fragment.isShowing) {
            transaction.detach(fragment)
          } else if (navRequest == beforeNewTop) {
            if (fragment == null) {
              fragment = navRequest.key.createFragment()
              transaction.replace(containerId, fragment, navRequest.key.fragmentTag)
            } else {
              if (fragment.isRemoving) {
                transaction.add(navRequest.key.createFragment(), navRequest.key.fragmentTag)
              } else {
                transaction.attach(fragment)
              }
            }
          }
        }
      }
    }

    transaction.commitNow()

    history = newHistory

    val newTopNavRequest = history.top()
    if(currentTop is ExpectingResult && screenResult != null) {
      val targetFragment = fragmentManager.findFragmentByTag(newTopNavRequest.key.fragmentTag)

      require(targetFragment != null) { "Could not find fragment for key: [${newTopNavRequest.key}]" }
      require(targetFragment is ExpectsResult) { "Key [${newTopNavRequest.key}] was pushed expecting results, but fragment [${targetFragment.javaClass.name}] does not implement [${ExpectsResult::class.java.name}]!" }

      handler.post { targetFragment.onScreenResult(currentTop.requestType, screenResult) }
    }
  }

  private fun checkNotMainThread() {
    if (Looper.getMainLooper() !== Looper.myLooper()) {
      throw RuntimeException("Can only execute navigation state changes on the UI thread! Current thread is [${Thread.currentThread().name}].")
    }
  }
}

private val Fragment.isShowing: Boolean
  get() = !isDetached

private val Fragment.isNotShowing: Boolean
  get() = isDetached

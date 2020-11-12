package org.simple.clinic.navigation.v2

import android.os.Looper
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

  init {
    executeStateChange(history, Direction.Replace)
  }

  fun push(screenKey: ScreenKey) {
    val newHistory = history.add(screenKey)

    executeStateChange(newHistory, Direction.Forward)
  }

  fun pop() {
    val newHistory = history.withoutLast()

    executeStateChange(newHistory, Direction.Backward)
  }

  fun popUntil(key: ScreenKey) {
    val newHistory = history.removeUntil(key)
    
    executeStateChange(newHistory, Direction.Backward)
  }

  fun onBackPressed(): Boolean {
    val currentTop = history.top()
    val fragment = fragmentManager.findFragmentByTag(currentTop.fragmentTag)

    require(fragment != null) { "Could not find fragment for [$currentTop]" }

    return if (fragment !is HandlesBack) {
      if (history.keys.size > 1) {
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
        if (history.keys.size > 1) {
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
      direction: Direction
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

    val currentScreenKeys = history.keys
    val newScreenKeys = newHistory.keys
    val newTop = newScreenKeys.last()
    val beforeNewTop = if (newScreenKeys.size > 1) newScreenKeys[newScreenKeys.lastIndex - 1] else null

    // Remove old fragments if they are no longer present in the new set of screens
    currentScreenKeys.forEach { key ->
      val fragment = fragmentManager.findFragmentByTag(key.fragmentTag)

      if (fragment != null) {
        if (!newScreenKeys.contains(key)) {
          transaction.remove(fragment)
        } else if (fragment.isShowing) {
          transaction.detach(fragment)
        }
      }
    }

    newScreenKeys.forEach { key ->
      var fragment = fragmentManager.findFragmentByTag(key.fragmentTag)
      if (key == newTop) {
        if (key.type == ScreenKey.ScreenType.FullScreen) {
          if (fragment != null) {
            if (fragment.isRemoving) { // fragments are quirky, they die asynchronously. Ignore if they're still there.
              transaction.replace(containerId, key.createFragment(), key.fragmentTag)
            } else if (fragment.isDetached) {
              transaction.attach(fragment)
            }
          } else {
            fragment = key.createFragment() // create and add new top if did not exist
            transaction.add(containerId, fragment, key.fragmentTag)
          }
        } else if (key.type == ScreenKey.ScreenType.Modal) {
          if (fragment != null) {
            if (fragment.isRemoving) { // fragments are quirky, they die asynchronously. Ignore if they're still there.
              transaction.add(key.createFragment(), key.fragmentTag)
            } else if (fragment.isDetached) {
              transaction.attach(fragment)
            }
          } else {
            fragment = key.createFragment() // create and add new top if did not exist
            transaction.add(fragment, key.fragmentTag)
          }
        }
      } else {
        if (newTop.type == ScreenKey.ScreenType.FullScreen) {
          if (fragment != null && fragment.isShowing) {
            transaction.detach(fragment)
          }
        } else if (newTop.type == ScreenKey.ScreenType.Modal) {
          // Last but one key should not be detached since the topmost key is a modal
          // and we want the previous screen to be visible
          if (key != beforeNewTop && fragment != null && fragment.isShowing) {
            transaction.detach(fragment)
          } else if (key == beforeNewTop) {
            if (fragment == null) {
              fragment = key.createFragment()
              transaction.replace(containerId, fragment, key.fragmentTag)
            } else {
              if (fragment.isNotShowing) {
                transaction.attach(fragment)
              } else if (fragment.isRemoving) {
                transaction.add(key.createFragment(), key.fragmentTag)
              }
            }
          }
        }
      }
    }

    transaction.commitNow()

    history = newHistory
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

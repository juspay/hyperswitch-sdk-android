package io.hyperswitch.react

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object HyperFragmentManager {

    // Thread-safe collections; all mutations happen on the main thread via mainHandler,
    // but public query methods (isActive, isPending, getFragment) can be called from any thread.
    private val fragmentRegistry = ConcurrentHashMap<String, Fragment>()
    private val pendingTags = ConcurrentHashMap.newKeySet<String>()
    private val debounceHandlers = ConcurrentHashMap<String, Runnable>()
    private val mainHandler = Handler(Looper.getMainLooper())

    // Monotonically increasing counter for stable, collision-free view IDs.
    private val idCounter = AtomicInteger(0x00F00001)

    private const val DEBOUNCE_MS = 300L

    /**
     * Request to add/replace a fragment. Debounced per tag —
     * rapid successive calls for the same tag collapse into one.
     */
    fun addOrReplace(
        activity: FragmentActivity,
        container: FrameLayout,
        fragment: Fragment,
        tag: String,
        addToBackStack: Boolean = true,
        debounce: Boolean = true
    ) {
        if (debounce) {
            scheduleAddOrReplace(activity, container, fragment, tag, addToBackStack)
        } else {
            commitAddOrReplace(activity, container, fragment, tag, addToBackStack)
        }
    }

    private fun scheduleAddOrReplace(
        activity: FragmentActivity,
        container: FrameLayout,
        fragment: Fragment,
        tag: String,
        addToBackStack: Boolean
    ) {
        // Cancel any previously scheduled runnable for this tag
        debounceHandlers[tag]?.let { mainHandler.removeCallbacks(it) }

        // Mark as pending so callers can check
        pendingTags.add(tag)

        val runnable = Runnable {
            pendingTags.remove(tag)
            debounceHandlers.remove(tag)
            if (!activity.isFinishing && !activity.isDestroyed) {
                commitAddOrReplace(activity, container, fragment, tag, addToBackStack)
            }
        }

        debounceHandlers[tag] = runnable
        mainHandler.postDelayed(runnable, DEBOUNCE_MS)
    }

    private fun commitAddOrReplace(
        activity: FragmentActivity,
        container: FrameLayout,
        fragment: Fragment,
        tag: String,
        addToBackStack: Boolean
    ) {
        val fm: FragmentManager = activity.supportFragmentManager

        // Remove existing fragment with the same tag if present, including its back-stack entry
        // so that onDestroy is called and the React tree is fully released.
        val existing = fm.findFragmentByTag(tag)
        if (existing != null) {
            fm.popBackStackImmediate(tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            fragmentRegistry.remove(tag)
        }

        if (container.id == View.NO_ID) {
            container.id = generateStableId()
        }

        val tx = fm.beginTransaction().add(container.id, fragment, tag)
        if (addToBackStack) tx.addToBackStack(tag)
        tx.commitAllowingStateLoss()

        fragmentRegistry[tag] = fragment
    }

    /**
     * Cancel any pending debounced creation for this tag.
     */
    fun cancelPending(tag: String) {
        debounceHandlers[tag]?.let { mainHandler.removeCallbacks(it) }
        debounceHandlers.remove(tag)
        pendingTags.remove(tag)
    }

    fun remove(activity: FragmentActivity, tag: String) {
        cancelPending(tag)
        val fm = activity.supportFragmentManager
        val fragment = fm.findFragmentByTag(tag)
        if (fragment != null) {
            // Pop from back stack if the fragment was added with addToBackStack=true.
            // This is a no-op when the fragment was added without a back-stack entry.
            fm.popBackStackImmediate(tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            // If still attached (was never on the back stack), remove it directly.
            if (fragment.isAdded) {
                fm.beginTransaction().remove(fragment).commitAllowingStateLoss()
            }
        }
        fragmentRegistry.remove(tag)
    }

    fun removeAll(activity: FragmentActivity) {
        debounceHandlers.keys.toList().forEach { cancelPending(it) }
        val fm = activity.supportFragmentManager
        fragmentRegistry.keys.toList().forEach { tag ->
            if (fm.findFragmentByTag(tag) != null) {
                fm.popBackStackImmediate(tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            }
        }
        fragmentRegistry.clear()
    }

    /** True if a debounced commit is scheduled but not yet executed */
    fun isPending(tag: String): Boolean = pendingTags.contains(tag)

    fun isActive(tag: String): Boolean = fragmentRegistry.containsKey(tag)

    fun getFragment(tag: String): Fragment? = fragmentRegistry[tag]

    /** Generates a unique, valid view ID using an atomic counter. */
    internal fun generateStableId(): Int = idCounter.getAndIncrement()
}

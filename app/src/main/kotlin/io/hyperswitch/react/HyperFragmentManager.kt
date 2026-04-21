package io.hyperswitch.react

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager

object HyperFragmentManager {

    private val fragmentRegistry = mutableMapOf<String, Fragment>()
    private val pendingTags = mutableSetOf<String>()       // tags currently being debounced
    private val debounceHandlers = mutableMapOf<String, Runnable>() // pending runnables
    private val mainHandler = Handler(Looper.getMainLooper())

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

        // Remove existing fragment with the same tag if present
        val existing = fm.findFragmentByTag(tag)
        if (existing != null) {
            fm.beginTransaction()
                .remove(existing)
                .commitAllowingStateLoss()
            fm.executePendingTransactions()
//      fm.popBackStackImmediate(tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            fragmentRegistry.remove(tag)
        }

        if (container.id == View.NO_ID) {
            container.id = generateStableId(tag)
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
        fm.findFragmentByTag(tag)?.let {
            fm.beginTransaction().remove(it).commitAllowingStateLoss()
            // popBackStackImmediate is synchronous and also removes the back stack entry,
            // so the fragment goes through the full lifecycle: onDestroyView → onDestroy.
            // Plain remove() on a back-stacked fragment only calls onDestroyView, leaving
            // the fragment instance (and its React tree) alive.
//      fm.popBackStackImmediate(tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
        fragmentRegistry.remove(tag)
    }

    fun removeAll(activity: FragmentActivity) {
        debounceHandlers.keys.toList().forEach { cancelPending(it) }
        val fm = activity.supportFragmentManager
        fragmentRegistry.keys.toList().forEach { tag ->
            fm.findFragmentByTag(tag)?.let {
                fm.beginTransaction().remove(it).commitAllowingStateLoss()
//        fm.popBackStackImmediate(tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            }
        }
        fragmentRegistry.clear()
    }

    /** True if a debounced commit is scheduled but not yet executed */
    fun isPending(tag: String): Boolean = pendingTags.contains(tag)

    fun isActive(tag: String): Boolean = fragmentRegistry.containsKey(tag)

    fun getFragment(tag: String): Fragment? = fragmentRegistry[tag]

    internal fun generateStableId(tag: String): Int {
        return (tag.hashCode() and 0x000FFFFF) or 0x00F00000
    }
}
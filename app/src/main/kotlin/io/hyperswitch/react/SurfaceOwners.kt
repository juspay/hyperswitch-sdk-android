package io.hyperswitch.react

import android.view.View
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.UiThreadUtil
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.common.UIManagerType
import io.hyperswitch.R
import java.lang.ref.WeakReference

/** Owner of a prefetch surface: receives the JS reply to an updateIntent round trip. */
fun interface UpdateIntentReplyTarget {
    fun onUpdateIntentReply(eventType: String, resultJson: String)
}

/**
 * Every React surface is reconciled by its root tag. The mounting layer already
 * maps a root tag to the surface's root view, so the owner of a surface is kept
 * on that view and nothing is registered anywhere else.
 */
internal object SurfaceOwners {

    /**
     * Marks [view], a surface's root view, as owned by [owner]; null clears it. The reference
     * is weak: the host keeps every running surface's root view alive, and that must not keep
     * a session, or the Activity it holds, alive in turn.
     */
    fun attach(view: View, owner: Any?) {
        view.setTag(R.id.hs_surface_owner, owner?.let { WeakReference(it) })
    }

    /** Every surface the SDK starts tags its root view; a view without a tag has no owner. */
    fun ownerOf(view: View): Any? =
        (view.getTag(R.id.hs_surface_owner) as? WeakReference<*>)?.get()

    /** Resolves rootTag → root view → owner. [block] always runs on the UI thread; null when unknown. */
    fun resolve(rct: ReactApplicationContext, rootTag: Int, block: (Any?) -> Unit) {
        UiThreadUtil.runOnUiThread {
            val owner = if (rootTag <= 0) {
                null
            } else {
                try {
                    UIManagerHelper.getUIManager(rct, UIManagerType.FABRIC)
                        ?.resolveView(rootTag)
                        ?.let(::ownerOf)
                } catch (_: Exception) {
                    null
                }
            }
            block(owner)
        }
    }
}

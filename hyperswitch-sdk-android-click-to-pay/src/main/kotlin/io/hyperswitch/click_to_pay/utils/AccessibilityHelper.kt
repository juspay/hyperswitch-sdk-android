package io.hyperswitch.click_to_pay.utils

import android.view.View
import android.view.ViewGroup

/**
 * Helper class for managing accessibility settings during modal operations.
 * Allows hiding views from accessibility services while preserving original settings for restoration.
 */
class AccessibilityHelper {
    
    private val originalAccessibility = HashMap<View, Int>()
    
    /**
     * Sets modal accessibility mode by hiding all views except the target view.
     * This implementation correctly handles view hierarchy by not hiding ancestors
     * of the target view, preventing the target from becoming inaccessible.
     *
     * @param root The root ViewGroup to start the traversal from
     * @param targetView The view that should remain accessible
     */
    fun setModalMode(root: ViewGroup, targetView: View) {
        val ancestors = HashSet<View>()
        var parent = targetView.parent
        while (parent is View) {
            ancestors.add(parent as View)
            parent = parent.parent
        }
        hideViewsRecursively(root, targetView, ancestors)
    }
    
    /**
     * Recursively hides views from accessibility services while preserving
     * the target view and its ancestor chain.
     *
     * @param currentView The current view being processed
     * @param targetView The view that should remain accessible
     * @param ancestors Set of ancestor views that must not be hidden
     */
    private fun hideViewsRecursively(
        currentView: View,
        targetView: View,
        ancestors: HashSet<View>
    ) {
        if (currentView == targetView) {
            return
        }

        if (ancestors.contains(currentView)) {
            if (currentView is ViewGroup) {
                for (i in 0 until currentView.childCount) {
                    hideViewsRecursively(currentView.getChildAt(i), targetView, ancestors)
                }
            }
        } else {
            if (!originalAccessibility.containsKey(currentView)) {
                originalAccessibility[currentView] = currentView.importantForAccessibility
            }
            currentView.importantForAccessibility =
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }
    
    /**
     * Restores the original accessibility settings for all modified views.
     * This method should be called when modal accessibility mode is no longer needed.
     */
    fun restore() {
        for ((view, originalValue) in originalAccessibility) {
            view.importantForAccessibility = originalValue
        }
        originalAccessibility.clear()
    }
}

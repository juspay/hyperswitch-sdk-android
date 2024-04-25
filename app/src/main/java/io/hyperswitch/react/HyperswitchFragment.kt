package io.hyperswitch.react

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.facebook.react.*
import com.facebook.react.modules.core.PermissionAwareActivity

open class HyperswitchFragment : ReactFragment(),
    PermissionAwareActivity {

    private var originalSoftInputMode: Int = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

    // Override the onCreateView method to set soft input mode
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Get the original soft input mode
        originalSoftInputMode = activity?.window?.attributes?.softInputMode ?: WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        // Set soft input mode to adjust resize
        setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // Call the superclass method to create the view
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    // Method to set soft input mode
    private fun setSoftInputMode(inputMode: Int) {
        // Check if the original soft input mode is different from the current mode
        if(originalSoftInputMode != WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        // Set the soft input mode
            activity?.window?.setSoftInputMode(inputMode)
    }

    // Override the onResume method to set soft input mode
    override fun onResume() {
        super.onResume()
        // Set soft input mode to adjust resize
        setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    // Override the onPause method to restore original soft input mode
    override fun onPause() {
        super.onPause()
        // Restore original soft input mode
        setSoftInputMode(originalSoftInputMode)
    }

    // Override the onDestroy method to restore original soft input mode
    override fun onDestroy() {
        super.onDestroy()
        // Restore original soft input mode
        setSoftInputMode(originalSoftInputMode)
    }

    // Override the onHiddenChanged method to handle soft input mode based on fragment visibility
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)

        // Check if the fragment is hidden
        if (hidden) {
            // Restore original soft input mode
            setSoftInputMode(originalSoftInputMode)
        } else {
            // Set soft input mode to adjust resize
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    // Nested Builder class for creating instances of HyperswitchFragment
    class Builder: ReactFragment.Builder()
}

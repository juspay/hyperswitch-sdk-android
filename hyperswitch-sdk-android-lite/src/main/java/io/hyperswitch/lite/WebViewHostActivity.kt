package io.hyperswitch.lite

import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity

class WebViewHostActivity : FragmentActivity() {
    
    private lateinit var rootContainer: FrameLayout
    private var keyboardListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configure window as floating to isolate from background
        window.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawableResource(android.R.color.transparent)
            
            // Add dim behind the floating window
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes.dimAmount = 0.5f
        }
        
        // Create root container
        rootContainer = FrameLayout(this).apply {
            id = android.R.id.content
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Keep transparent background
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        setContentView(rootContainer)

        val requestBody = intent.getStringExtra("requestBody")

        if (supportFragmentManager.findFragmentByTag("webFragment") == null) {
            val fragment = WebViewFragment()

            supportFragmentManager.beginTransaction()
                .add(rootContainer.id, fragment, "webFragment")
                .commitNow()
            
            requestBody?.let {
                fragment.setRequestBody(it)
            }
        }
        
        // No need for custom keyboard handling with floating window
        
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val fragment = supportFragmentManager.findFragmentByTag("webFragment") as? WebViewFragment
                if (fragment?.onBackPressed() != true) {
                    finish()
                }
            }
        })
    }
    
}

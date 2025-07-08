package io.hyperswitch.lite

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity

class WebViewHostActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requestBody = intent.getStringExtra("requestBody")

        if (supportFragmentManager.findFragmentByTag("webFragment") == null) {
            val fragment = WebViewFragment()

            supportFragmentManager.beginTransaction()
                .add(android.R.id.content, fragment, "webFragment")
                .commitNow()
            
            requestBody?.let {
                fragment.setRequestBody(it)
            }
        }
        
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

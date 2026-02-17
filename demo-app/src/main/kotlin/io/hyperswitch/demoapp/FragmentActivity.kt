package io.hyperswitch.demoapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler

class FragmentActivity : AppCompatActivity(), DefaultHardwareBackBtnHandler {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fragment)

        // Load PaymentFragment if not already loaded
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, PaymentFragment.newInstance())
                .commit()
        }
    }

    override fun invokeDefaultOnBackPressed() {
        super.onBackPressed()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Delegate back press to fragment first
        val fragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        if (fragment is DefaultHardwareBackBtnHandler) {
            fragment.invokeDefaultOnBackPressed()
        } else {
            super.onBackPressed()
        }
    }
}
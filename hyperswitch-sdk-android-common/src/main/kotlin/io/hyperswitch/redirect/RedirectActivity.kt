package io.hyperswitch.redirect

import android.app.Activity
import android.content.Intent
import org.greenrobot.eventbus.EventBus

class RedirectActivity : Activity() {
    override fun onResume() {
        super.onResume()
        EventBus.getDefault().post(
            RedirectEvent(
                intent.data?.toString() ?: "chrome tabs activity closed",
                "cancel",
                false
            )
        )
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

class RedirectEvent(val message: String?, val resultType: String, val isError: Boolean)
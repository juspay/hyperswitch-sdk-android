package io.hyperswitch.demo_app_lite

import android.app.Activity
import android.os.Bundle
import android.view.View
import io.hyperswitch.click_to_pay.ClickToPaySession

class ClickToPayExample: Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.c2p_activity)

        var clickToPaySession = ClickToPaySession(this)

        findViewById<View>(R.id.initialize).setOnClickListener {
//            clickToPaySession.initialise()
        }
    }
}
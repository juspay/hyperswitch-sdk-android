package io.hyperswitch.lite.react

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

class RedirectActivity : AppCompatActivity() {

    // Override onResume method to handle redirection logic
    override fun onResume() {
        super.onResume()

        // Get app link data from intent
        val appLinkData = intent.data

        // Finish the current activity
        finish()
    }

    // Override onNewIntent to update the intent when a new intent is received
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // Set the new intent
    }
}

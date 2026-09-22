package io.hyperswitch.pmm

import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import com.facebook.react.runtime.ReactSurfaceImpl
import com.facebook.react.runtime.ReactSurfaceView
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.pmm.react.PMMProtocol
import io.hyperswitch.pmm.react.PaymentMethodManagementEventTarget
import io.hyperswitch.pmm.react.PaymentMethodManagementRuntime
import io.hyperswitch.react.SurfaceOwners
import io.hyperswitch.react.parsePaymentResult

/**
 * The PMM sheet: one `hyperPMM` root of type `paymentMethodsManagement` on the PMM
 * host. A plain activity over a manually driven surface — the result comes back
 * through the surface's owner, never through an activity result.
 */
internal class PaymentMethodManagementActivity : AppCompatActivity() {

    private var surface: ReactSurfaceImpl? = null
    private var surfaceView: ReactSurfaceView? = null

    /** What the bundle says about this root lands here. */
    private val events = object : PaymentMethodManagementEventTarget {
        override fun onPmmExit(resultJson: String, reset: Boolean) {
            PaymentMethodManagementResultBus.deliver(parsePaymentResult(resultJson))
            finish()
        }

        /* Sheets resolve only on exit; a widget's confirm refusals do not apply here. */
        override fun onPmmNonTerminalResult(result: PaymentResult) = Unit

        override fun onPmmEvent(eventType: String, payload: Map<String, Any?>) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val runtime = PaymentMethodManagementRuntime.get(application)
        val surface = ReactSurfaceImpl(this, PMMProtocol.COMPONENT, intent.getBundleExtra(EXTRA_PROPS) ?: Bundle())
        val view = ReactSurfaceView(this, surface)
        SurfaceOwners.attach(view, events)
        surface.attachView(view)
        surface.attach(runtime.reactHost)
        this.surface = surface
        this.surfaceView = view
        /* The host runs JS only while resumed: it follows this sheet for its lifetime. */
        runtime.follow(this)
        setContentView(view)
        surface.start()

        onBackPressedDispatcher.addCallback(this) {
            PaymentMethodManagementResultBus.deliver(PaymentResult.Canceled("cancelled"))
            finish()
        }
    }

    override fun onDestroy() {
        surface?.let { surface ->
            this.surface = null
            surfaceView?.let { SurfaceOwners.attach(it, null) }
            surfaceView = null
            surface.stop()
            surface.detach()
        }
        /* If the OS tears the sheet down before JS could reply, the merchant is not
         * left waiting; a delivery already made is not repeated. */
        PaymentMethodManagementResultBus.deliver(PaymentResult.Canceled("cancelled"))
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PROPS = "props"
    }
}

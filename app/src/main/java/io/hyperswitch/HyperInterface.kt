package io.hyperswitch

import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler

interface HyperInterface : DefaultHardwareBackBtnHandler {
    override fun invokeDefaultOnBackPressed() {}
}
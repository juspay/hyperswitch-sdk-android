package io.hyperswitch.react

import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage

/**
 * Base class implemented by the host (:app) module to supply the list of
 * autolinked React Packages. Keeping this in :core lets :core remain
 * RN-version-agnostic while still initialising a ReactInstanceManager.
 */
abstract class HyperReactPackageProvider {
    abstract fun getPackages(host: ReactNativeHost): List<ReactPackage>
}

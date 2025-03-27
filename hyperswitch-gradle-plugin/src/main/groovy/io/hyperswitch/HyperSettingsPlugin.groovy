package io.hyperswitch

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings

class HyperSettingsPlugin implements Plugin<Settings> {
    void apply(Settings settings) {
        settings.dependencyResolutionManagement.repositories {
            maven {
                url 'https://maven.juspay.in/hyper-sdk'
            }
        }
    }
}

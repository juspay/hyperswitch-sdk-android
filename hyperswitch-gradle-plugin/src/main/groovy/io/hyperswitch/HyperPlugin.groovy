package io.hyperswitch

import org.gradle.api.Plugin
import org.gradle.api.Project

class HyperPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.plugins.withId('com.android.application') {
            project.dependencies {
                implementation 'io.hyperswitch:hyperswitch-sdk-android:1.1.1'
            }

            try {
                if (project.android) {
                    project.android.buildTypes.debug.manifestPlaceholders += [applicationName: "io.hyperswitch.react.MainApplication"]
                    project.android.buildTypes.release.manifestPlaceholders += [applicationName: "io.hyperswitch.react.MainApplication"]
                    project.android.packagingOptions.jniLibs.useLegacyPackaging = true

                    project.android.packagingOptions {
                        exclude "lib/**/libfabricjni.so"
                        exclude "lib/**/libreact_codegen_rncore.so"
                        exclude "lib/**/librninstance.so"
                        exclude "lib/**/librrc_view.so"
                        exclude "lib/**/libreact_nativemodule_dom.so"
                        exclude "lib/**/librrc_textinput.so"
                        exclude "lib/**/libreact_nativemodule_core.so"
                        exclude "lib/**/libreact_render_core.so"
                        exclude "lib/**/libreact_render_uimanager_consistency.so"
                        exclude "lib/**/libnative-imagetranscoder.so"
                        exclude "lib/**/librrc_image.so"
                        exclude "lib/**/libeb90.so"
                        exclude "lib/**/libhermesinstancejni.so"
                        exclude "lib/**/libreact_newarchdefaults.so"
                        exclude "lib/**/libturbomodulejsijni.so"
                        exclude "lib/**/libreact_render_componentregistry.so"
                        exclude "lib/**/libjscexecutor.so"
                        exclude "lib/**/libuimanagerjni.so"
                        exclude "lib/**/libreact_nativemodule_defaults.so"
                        exclude "lib/**/libreact_performance_timeline.so"
                        exclude "lib/**/libmapbufferjni.so"
                        exclude "lib/**/librrc_legacyviewmanagerinterop.so"
                        exclude "lib/**/libjscinstance.so"
                        exclude "lib/**/libreact_nativemodule_featureflags.so"
                        exclude "lib/**/libreact_nativemodule_microtasks.so"
                        exclude "lib/**/libreact_render_mapbuffer.so"
                        exclude "lib/**/libreact_render_observers_events.so"
                        exclude "lib/**/libreact_render_imagemanager.so"
                        exclude "lib/**/libreact_render_graphics.so"
                        exclude "lib/**/libreact_render_element.so"
                        exclude "lib/**/libjsijniprofiler.so"
                        exclude "lib/**/libnative-filters.so"
                    }
                }
            } catch (ignored) {
                project.logger.warn("Failed to apply custom configurations", ignored)
            }

        }
    }
}

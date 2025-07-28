package io.hyperswitch

import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project


class HyperPluginExtension {
    String sdkVersion = null

    boolean enablePaypal = false
    boolean enableKount = false
    boolean enableScanCard = false
    boolean enableKlarna = false
    boolean enableNetcetera3ds = false
    boolean enableSamsungPay = false

    String paypalVersion
    String kountVersion
    String scanCardVersion
    String klarnaVersion
    String netcetera3dsVersion
    String samsungPayVersion
}
class HyperPlugin implements Plugin<Project> {

    void apply(Project project) {
        def extension = project.extensions.create('hyperswitch', HyperPluginExtension)
        project.plugins.withId('com.android.application') {
            try {
                project.repositories {
                    maven {
                        url 'https://maven.juspay.in/hyper-sdk'
                    }
                }
                project.gradle.rootProject.allprojects { p ->
                    p.repositories {
                        maven {
                            url 'https://maven.juspay.in/hyper-sdk'
                        }
                    }
                }
            } catch (ignored) {
                project.logger.warn(
                        "\n" +
                                "⚠️ Build was configured to prefer settings repositories over project repositories\n\n" +
                                "   If you haven't manually configured the SDK yet, follow these steps:\n" +
                                "   🔹 In settings.gradle file:\n" +
                                "      • Apply HyperSettingsPlugin `plugins { id('io.hyperswitch.settings.plugin') version '0.1.1' }`\n" +
                                "       OR \n" +
                                "      • Add `maven { url 'https://maven.juspay.in/hyper-sdk' }`\n")
            }

            def optionalDepVersions = loadOptionalDepVersions(project)
            String highestSdkVersion = findHighestVersion(optionalDepVersions.keySet())
            project.logger.warn("Highest SDK version found: ${highestSdkVersion ?: 'None'}")

            String sdkVersionToUse = extension.sdkVersion ?:highestSdkVersion
            if (!sdkVersionToUse) {
                throw new GradleException("No SDK version specified or found. Cannot continue.")
            }
            project.logger.warn("Using SDK version: ${sdkVersionToUse}")

            def compatibleVersions = optionalDepVersions[sdkVersionToUse]
            if (!compatibleVersions && highestSdkVersion) {
                compatibleVersions = optionalDepVersions[highestSdkVersion]
                project.logger.warn("No compatible versions found for ${sdkVersionToUse}, using versions from ${highestSdkVersion}")
            }
            compatibleVersions = compatibleVersions ?: [:]
            project.logger.warn("Compatible versions for dependencies: ${compatibleVersions}")

            def DEP_MAP = [
                    paypal       : "react-native-hyperswitch-paypal",
                    kount        : "react-native-hyperswitch-kount",
                    scanCard     : "react-native-hyperswitch-scancard",
                    klarna       : "react-native-klarna-inapp-sdk",
                    netcetera3ds : "react-native-hyperswitch-netcetera-3ds",
                    samsungPay   : "react-native-hyperswitch-samsung-pay"
            ]

            project.afterEvaluate {
                project.dependencies {
                    implementation "io.hyperswitch:hyperswitch-sdk-android:${sdkVersionToUse}"
                    project.logger.warn("✅ Added core SDK: io.hyperswitch:hyperswitch-sdk-android:${sdkVersionToUse}")
                    [
                            [name: "paypal", enabled: extension.enablePaypal, version: extension.paypalVersion],
                            [name: "kount", enabled: extension.enableKount, version: extension.kountVersion],
                            [name: "scanCard", enabled: extension.enableScanCard, version: extension.scanCardVersion],
                            [name: "klarna", enabled: extension.enableKlarna, version: extension.klarnaVersion],
                            [name: "netcetera3ds", enabled: extension.enableNetcetera3ds, version: extension.netcetera3dsVersion],
                            [name: "samsungPay", enabled: extension.enableSamsungPay, version: extension.samsungPayVersion]
                    ].each { dep ->
                        if (dep.enabled) {
                            def artifact = DEP_MAP[dep.name]
                            def recommendedVersion = compatibleVersions[dep.name]

                            if (dep.version?.trim()) {
                                if (recommendedVersion && dep.version != recommendedVersion) {
                                    project.logger.warn("⚠️ Using custom version ${dep.version} for ${artifact} which may be incompatible with SDK version ${sdkVersionToUse}. Recommended version is ${recommendedVersion}")
                                }
                                implementation "io.hyperswitch:$artifact:${dep.version}"
                                project.logger.warn("✅ Added optional dep $artifact:${dep.version}")
                            }
                            else if (recommendedVersion) {
                                implementation "io.hyperswitch:$artifact:$recommendedVersion"
                                project.logger.warn("✅ Added optional dep $artifact:$recommendedVersion")
                            }
                            else {
                                project.logger.warn("⚠️ No compatible version found for $artifact with SDK $sdkVersionToUse and no custom version specified. Dependency will not be added.")
                            }
                        }
                    }
                }
            }

            try {
                if (!project.hasProperty("android")) {
                    project.logger.warn("⚠️ 'android' block not found in project. Plugin will not configure Android-specific settings.")
                }
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
                project.logger.warn("Failed to apply custom configurations")
            }
        }
    }

    private static Map loadOptionalDepVersions(Project project) {
        InputStream input = HyperPlugin.classLoader.getResourceAsStream("optional-dep-versions.json")
        if (input != null) {
            try {
                def json = new JsonSlurper().parse(input)
                return json instanceof Map ? json : [:]
            } catch (Exception e) {
                project.logger.error("⚠️ Error parsing optional-dep-versions.json: ${e.message}. No optional deps will be auto-applied.")
                return [:]
            }
        } else {
            project.logger.warn("⚠️ optional-dep-versions.json not found in resources. No optional deps will be auto-applied.")
            return [:]
        }
    }
    private static String findHighestVersion(Set<String> versions) {
        return versions ? versions.iterator().next() : null
    }
}
package io.hyperswitch

import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.util.regex.Pattern

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
            project.logger.info("Available SDK versions: ${optionalDepVersions.keySet()}")
            project.logger.info("Highest SDK version found: ${highestSdkVersion ?: 'None'}")

            String sdkVersionToUse = determineSdkVersion(extension, highestSdkVersion, project)
            def compatibleVersions = getCompatibleVersions(optionalDepVersions, sdkVersionToUse, highestSdkVersion, project)

            addCoreSdk(project, sdkVersionToUse)
            project.afterEvaluate {
                addOptionalDependencies(project, extension, compatibleVersions, sdkVersionToUse)
            }
            configureAndroidSettings(project)
        }
    }

    private static String determineSdkVersion(HyperPluginExtension extension, String highestSdkVersion, Project project) {
        String sdkVersionToUse = null
        
        // Priority: explicit extension version > highest available version
        if (extension.sdkVersion?.trim()) {
            sdkVersionToUse = extension.sdkVersion.trim()
            project.logger.info("Using explicitly specified SDK version: ${sdkVersionToUse}")
        } else if (highestSdkVersion) {
            sdkVersionToUse = highestSdkVersion
            project.logger.info("Using highest available SDK version: ${sdkVersionToUse}")
        }
        
        if (!sdkVersionToUse) {
            throw new GradleException("❌ No SDK version specified or found in optional-dep-versions.json. Please specify sdkVersion in hyperswitch extension or ensure optional-dep-versions.json is available.")
        }
        
        return sdkVersionToUse
    }

    private static Map getCompatibleVersions(Map optionalDepVersions, String sdkVersionToUse, String highestSdkVersion, Project project) {
        def compatibleVersions = optionalDepVersions[sdkVersionToUse]
        
        if (!compatibleVersions) {
            project.logger.warn("⚠️ No compatible versions found for SDK version ${sdkVersionToUse}")
            
            if (highestSdkVersion && highestSdkVersion != sdkVersionToUse) {
                compatibleVersions = optionalDepVersions[highestSdkVersion]
                if (compatibleVersions) {
                    project.logger.warn("⚠️ Falling back to compatible versions from highest SDK version ${highestSdkVersion}")
                    project.logger.warn("⚠️ This may cause compatibility issues. Consider using SDK version ${highestSdkVersion} instead.")
                }
            }
        }
        
        compatibleVersions = compatibleVersions ?: [:]
        project.logger.info("Compatible versions for dependencies: ${compatibleVersions}")
        return compatibleVersions
    }

    private static void addCoreSdk(Project project, String sdkVersionToUse) {
        project.dependencies {
            implementation "io.hyperswitch:hyperswitch-sdk-android:${sdkVersionToUse}"
        }
        project.logger.info("✅ Added core SDK: io.hyperswitch:hyperswitch-sdk-android:${sdkVersionToUse}")
    }

    private static void addOptionalDependencies(Project project, HyperPluginExtension extension, Map compatibleVersions, String sdkVersionToUse) {
        def DEP_MAP = [
                paypal       : "react-native-hyperswitch-paypal",
                kount        : "react-native-hyperswitch-kount",
                scanCard     : "react-native-hyperswitch-scancard",
                klarna       : "react-native-klarna-inapp-sdk",
                netcetera3ds : "react-native-hyperswitch-netcetera-3ds",
                samsungPay   : "react-native-hyperswitch-samsung-pay"
        ]

        def dependencies = [
                [name: "paypal", enabled: extension.enablePaypal, customVersion: extension.paypalVersion],
                [name: "kount", enabled: extension.enableKount, customVersion: extension.kountVersion],
                [name: "scanCard", enabled: extension.enableScanCard, customVersion: extension.scanCardVersion],
                [name: "klarna", enabled: extension.enableKlarna, customVersion: extension.klarnaVersion],
                [name: "netcetera3ds", enabled: extension.enableNetcetera3ds, customVersion: extension.netcetera3dsVersion],
                [name: "samsungPay", enabled: extension.enableSamsungPay, customVersion: extension.samsungPayVersion]
        ]

        dependencies.each { dep ->
            // Auto-enable if version is specified but not explicitly enabled
            boolean shouldEnable = dep.enabled
            if (!dep.enabled && dep.customVersion?.trim()) {
                shouldEnable = true
                project.logger.warn("ℹ️ Auto-enabling ${dep.name} because version '${dep.customVersion}' was specified")
            }
            
            project.logger.warn("Processing dependency ${dep.name}: enabled=${shouldEnable}, customVersion='${dep.customVersion}'")
            
            if (shouldEnable) {
                processDependency(project, dep, DEP_MAP, compatibleVersions, sdkVersionToUse)
            }
        }
    }

    private static void processDependency(Project project, Map dep, Map DEP_MAP, Map compatibleVersions, String sdkVersionToUse) {
        def artifact = DEP_MAP[dep.name]
        def recommendedVersion = compatibleVersions[dep.name]
        def customVersion = dep.customVersion?.trim()
        
        project.logger.info("Processing dependency: ${dep.name}")
        project.logger.info("  - Artifact: ${artifact}")
        project.logger.info("  - Custom version: ${customVersion ?: 'None'}")
        project.logger.info("  - Recommended version: ${recommendedVersion ?: 'None'}")

        String versionToUse = null
        String warningMessage = null

        if (customVersion) {
            // Case 1: Custom version specified
            versionToUse = customVersion
            if (recommendedVersion && customVersion != recommendedVersion) {
                warningMessage = "⚠️ WARNING: Using custom version ${customVersion} for ${artifact} which differs from recommended version ${recommendedVersion} for SDK ${sdkVersionToUse}. This may cause compatibility issues."
            } else if (!recommendedVersion) {
                warningMessage = "⚠️ Using custom version ${customVersion} for ${artifact}. No recommended version available for SDK ${sdkVersionToUse}."
            }
        } else if (recommendedVersion) {
            // Case 2: No custom version, but recommended version available
            versionToUse = recommendedVersion
        } else {
            // Case 3: No custom version and no recommended version
            project.logger.error("❌ Cannot add ${artifact}: No custom version specified and no recommended version found for SDK ${sdkVersionToUse}")
            project.logger.error("   Either specify a custom version in the hyperswitch extension or ensure optional-dep-versions.json contains compatible versions.")
            return
        }

        if (versionToUse) {
            try {
                project.dependencies {
                    implementation "io.hyperswitch:${artifact}:${versionToUse}"
                }
                project.logger.info("✅ Added optional dependency ${artifact}:${versionToUse}")
                
                if (warningMessage) {
                    project.logger.warn(warningMessage)
                }
            } catch (Exception e) {
                project.logger.error("❌ Failed to add dependency ${artifact}:${versionToUse}: ${e.message}")
            }
        }
    }

    private static boolean isVersionCompatible(String customVersion, String recommendedVersion) {
        return customVersion == recommendedVersion
    }

    private static void configureAndroidSettings(Project project) {
        try {
            if (!project.hasProperty("android")) {
                project.logger.warn("⚠️ 'android' block not found in project. Plugin will not configure Android-specific settings.")
                return
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
                
                project.logger.info("✅ Android configuration applied successfully")
            }
        } catch (Exception e) {
            project.logger.error("❌ Failed to apply Android configurations: ${e.message}")
        }
    }

    private static Map loadOptionalDepVersions(Project project) {
        InputStream input = HyperPlugin.classLoader.getResourceAsStream("optional-dep-versions.json")
        if (input != null) {
            try {
                def json = new JsonSlurper().parse(input)
                if (json instanceof Map && !json.isEmpty()) {
                    project.logger.info("✅ Successfully loaded optional dependency versions for ${json.keySet().size()} SDK versions")
                    return json
                } else {
                    project.logger.warn("⚠️ optional-dep-versions.json is empty or invalid format")
                    return [:]
                }
            } catch (Exception e) {
                project.logger.error("❌ Error parsing optional-dep-versions.json: ${e.message}")
                return [:]
            } finally {
                try {
                    input.close()
                } catch (Exception ignored) {}
            }
        } else {
            project.logger.warn("⚠️ optional-dep-versions.json not found in plugin resources")
            return [:]
        }
    }

    private static String findHighestVersion(Set<String> versions) {
        if (!versions || versions.isEmpty()) {
            return null
        }
        return versions.iterator().next()
    }

}
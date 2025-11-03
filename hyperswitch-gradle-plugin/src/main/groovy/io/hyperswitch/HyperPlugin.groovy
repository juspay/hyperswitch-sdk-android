package io.hyperswitch

import org.gradle.api.Plugin
import org.gradle.api.Project

enum HyperFeature {
    SCANCARD('juspay-tech_react-native-hyperswitch-scancard', 'rnlibVersion'),
    NETCETERA('juspay-tech_react-native-hyperswitch-netcetera-3ds', 'rnlibVersion')

    final String artifactId
    final String versionProperty

    HyperFeature(String artifactId, String versionProperty) {
        this.artifactId = artifactId
        this.versionProperty = versionProperty
    }

    static HyperFeature fromString(String name) {
        String normalizedName = name.replaceAll('([a-z])([A-Z])', '$1_$2').toUpperCase()
        try {
            return valueOf(normalizedName)
        } catch (IllegalArgumentException e) {
            return null
        }
    }
}

class HyperPluginExtension {
    String sdkVersion = null
    List<Object> features = []

    void features(List<Object> featureList) {
        this.features = featureList
    }

    void setFeatures(List<Object> featureList) {
        this.features = featureList
    }
}

class HyperPlugin implements Plugin<Project> {
    static final String FALLBACK_SDK_VERSION = "+"

    void apply(Project project) {
        def extension = project.extensions.create('hyper', HyperPluginExtension)
        
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

            project.afterEvaluate {
                String sdkVersionToUse = extension.sdkVersion ?: getVersionFromGradleProperties(project, 'sdkVersion') ?: getVersionFromResources('sdkVersion') ?: FALLBACK_SDK_VERSION
                project.dependencies {
                    implementation "io.hyperswitch:hyperswitch-sdk-android:${sdkVersionToUse}"
                }

                if (extension.features) {
                    extension.features.each { feature ->
                        HyperFeature hyperFeature = null
                        
                        if (feature instanceof HyperFeature) {
                            hyperFeature = feature
                        } else if (feature instanceof String) {
                            hyperFeature = HyperFeature.fromString(feature)
                        }

                        if (hyperFeature != null) {
                            String featureVersion = getVersionFromGradleProperties(project, hyperFeature.versionProperty) ?: 
                                                   getVersionFromResources(hyperFeature.versionProperty) ?: 
                                                   FALLBACK_SDK_VERSION
                            
                            project.dependencies {
                                implementation "io.hyperswitch:${hyperFeature.artifactId}:${featureVersion}"
                            }
                            
                            project.logger.info("Added Hyperswitch feature: ${hyperFeature.artifactId}:${featureVersion}")
                        } else {
                            project.logger.warn("Unknown Hyperswitch feature: ${feature}")
                        }
                    }
                }
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
                project.logger.warn("Failed to apply custom configurations")
            }
        }
    }

    private static String getVersionFromGradleProperties(Project project, String propertyName) {
        try {
            if (project.hasProperty(propertyName)) {
                return project.property(propertyName)
            }
        } catch (Exception ignored) {
        }
        return null
    }

    private static String getVersionFromResources(String propertyName) {
        try {
            InputStream inputStream = HyperPlugin.class.getResourceAsStream("/version.properties")
            if (inputStream != null) {
                Properties properties = new Properties()
                properties.load(inputStream)
                inputStream.close()
                return properties.getProperty(propertyName, null)
            }
        } catch (Exception ignored) {
        }
        return null
    }
}

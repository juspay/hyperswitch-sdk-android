package io.hyperswitch

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import org.gradle.api.Project

class HyperPluginIntegrationTest extends Specification {

    Project project

    def setup() {
        project = ProjectBuilder.builder().build()
        project.pluginManager.apply('com.android.application')
        project.pluginManager.apply('io.hyperswitch.plugin')
    }

    def "plugin should create hyper extension"() {
        expect:
        project.extensions.findByName('hyper') != null
        project.extensions.findByName('hyper') instanceof HyperPluginExtension
    }

    def "plugin should add maven repository"() {
        when:
        project.evaluate()

        then:
        project.repositories.any { it.url.toString().contains('maven.juspay.in/hyper-sdk') }
    }

    def "plugin should add main SDK dependency when no features specified"() {
        when:
        project.extensions.hyper.sdkVersion = '1.2.5'
        project.evaluate()

        then:
        def dependencies = project.configurations.implementation.dependencies
        dependencies.any { 
            it.group == 'io.hyperswitch' && 
            it.name == 'hyperswitch-sdk-android' && 
            it.version == '1.2.5' 
        }
    }

    def "plugin should add feature dependencies when features are specified as strings"() {
        when:
        project.extensions.hyper.features = ['scancard', 'netcetera']
        project.extensions.hyper.sdkVersion = '1.2.5'
        project.ext.rnlibVersion = '1.0.0'
        project.evaluate()

        then:
        def dependencies = project.configurations.implementation.dependencies
        
        // Main SDK
        dependencies.any { 
            it.group == 'io.hyperswitch' && 
            it.name == 'hyperswitch-sdk-android' 
        }
        
        // Scancard feature
        dependencies.any { 
            it.group == 'io.hyperswitch' && 
            it.name == 'juspay-tech_react-native-hyperswitch-scancard' 
        }
        
        // Netcetera feature
        dependencies.any { 
            it.group == 'io.hyperswitch' && 
            it.name == 'juspay-tech_react-native-hyperswitch-netcetera-3ds' 
        }
    }

    def "plugin should add feature dependencies when features are specified as enums"() {
        when:
        project.extensions.hyper.features = [HyperFeature.SCANCARD, HyperFeature.NETCETERA]
        project.extensions.hyper.sdkVersion = '1.2.5'
        project.ext.rnlibVersion = '1.0.0'
        project.evaluate()

        then:
        def dependencies = project.configurations.implementation.dependencies
        
        dependencies.any { 
            it.group == 'io.hyperswitch' && 
            it.name == 'juspay-tech_react-native-hyperswitch-scancard' 
        }
        
        dependencies.any { 
            it.group == 'io.hyperswitch' && 
            it.name == 'juspay-tech_react-native-hyperswitch-netcetera-3ds' 
        }
    }

    def "plugin should use version from gradle properties"() {
        when:
        project.ext.sdkVersion = '1.5.0'
        project.ext.rnlibVersion = '2.0.0'
        project.extensions.hyper.features = ['scancard']
        project.evaluate()

        then:
        def dependencies = project.configurations.implementation.dependencies
        
        dependencies.any { 
            it.group == 'io.hyperswitch' && 
            it.name == 'hyperswitch-sdk-android' && 
            it.version == '1.5.0' 
        }
        
        dependencies.any { 
            it.group == 'io.hyperswitch' && 
            it.name == 'juspay-tech_react-native-hyperswitch-scancard' && 
            it.version == '2.0.0' 
        }
    }

    def "plugin should use fallback version when no version specified"() {
        when:
        project.extensions.hyper.features = ['scancard']
        project.evaluate()

        then:
        def dependencies = project.configurations.implementation.dependencies
        
        dependencies.any { 
            it.group == 'io.hyperswitch' && 
            it.name == 'hyperswitch-sdk-android' && 
            it.version == '+' 
        }
    }

    def "plugin should handle empty features list"() {
        when:
        project.extensions.hyper.features = []
        project.extensions.hyper.sdkVersion = '1.2.5'
        project.evaluate()

        then:
        def dependencies = project.configurations.implementation.dependencies
        
        // Only main SDK should be added
        dependencies.any { 
            it.group == 'io.hyperswitch' && 
            it.name == 'hyperswitch-sdk-android' 
        }
        
        // No feature dependencies
        !dependencies.any { 
            it.name == 'juspay-tech_react-native-hyperswitch-scancard' 
        }
    }

    def "plugin should handle invalid feature names gracefully"() {
        when:
        project.extensions.hyper.features = ['scancard', 'invalidFeature', 'netcetera']
        project.ext.rnlibVersion = '1.0.0'
        project.evaluate()

        then:
        def dependencies = project.configurations.implementation.dependencies
        
        // Valid features should be added
        dependencies.any { 
            it.name == 'juspay-tech_react-native-hyperswitch-scancard' 
        }
        
        dependencies.any { 
            it.name == 'juspay-tech_react-native-hyperswitch-netcetera-3ds' 
        }
        
        // Invalid feature should be ignored (no exception thrown)
        notThrown(Exception)
    }

    def "plugin should override sdkVersion from extension"() {
        when:
        project.ext.sdkVersion = '1.0.0'
        project.extensions.hyper.sdkVersion = '2.0.0'
        project.evaluate()

        then:
        def dependencies = project.configurations.implementation.dependencies
        
        dependencies.any { 
            it.group == 'io.hyperswitch' && 
            it.name == 'hyperswitch-sdk-android' && 
            it.version == '2.0.0' 
        }
    }

    def "plugin should handle mixed string and enum features"() {
        when:
        project.extensions.hyper.features = ['scancard', HyperFeature.NETCETERA]
        project.ext.rnlibVersion = '1.0.0'
        project.evaluate()

        then:
        def dependencies = project.configurations.implementation.dependencies
        
        dependencies.any { 
            it.name == 'juspay-tech_react-native-hyperswitch-scancard' 
        }
        
        dependencies.any { 
            it.name == 'juspay-tech_react-native-hyperswitch-netcetera-3ds' 
        }
    }
}

package io.hyperswitch

import spock.lang.Specification

class HyperPluginExtensionTest extends Specification {

    def "extension should have default values"() {
        when:
        def extension = new HyperPluginExtension()

        then:
        extension.sdkVersion == null
        extension.features == []
    }

    def "extension should accept sdkVersion"() {
        when:
        def extension = new HyperPluginExtension()
        extension.sdkVersion = '1.2.5'

        then:
        extension.sdkVersion == '1.2.5'
    }

    def "extension should accept features as list of strings"() {
        when:
        def extension = new HyperPluginExtension()
        extension.features = ['scancard', 'netcetera']

        then:
        extension.features == ['scancard', 'netcetera']
    }

    def "extension should accept features as list of enums"() {
        when:
        def extension = new HyperPluginExtension()
        extension.features = [HyperFeature.SCANCARD, HyperFeature.NETCETERA]

        then:
        extension.features == [HyperFeature.SCANCARD, HyperFeature.NETCETERA]
    }

    def "extension should accept mixed list of strings and enums"() {
        when:
        def extension = new HyperPluginExtension()
        extension.features = ['scancard', HyperFeature.NETCETERA]

        then:
        extension.features == ['scancard', HyperFeature.NETCETERA]
    }

    def "extension should accept empty features list"() {
        when:
        def extension = new HyperPluginExtension()
        extension.features = []

        then:
        extension.features == []
    }

    def "setFeatures method should work"() {
        when:
        def extension = new HyperPluginExtension()
        extension.setFeatures(['scancard'])

        then:
        extension.features == ['scancard']
    }

    def "features method should work"() {
        when:
        def extension = new HyperPluginExtension()
        extension.features(['netcetera'])

        then:
        extension.features == ['netcetera']
    }

    def "extension should allow updating features multiple times"() {
        when:
        def extension = new HyperPluginExtension()
        extension.features = ['scancard']
        extension.features = ['netcetera']

        then:
        extension.features == ['netcetera']
    }
}

package io.hyperswitch

import spock.lang.Specification
import spock.lang.Unroll

class HyperFeatureTest extends Specification {

    @Unroll
    def "HyperFeature enum should have correct artifact ID for #feature"() {
        expect:
        feature.artifactId == expectedArtifactId

        where:
        feature                      | expectedArtifactId
        HyperFeature.SCANCARD        | 'juspay-tech_react-native-hyperswitch-scancard'
        HyperFeature.NETCETERA       | 'juspay-tech_react-native-hyperswitch-netcetera-3ds'
    }

    @Unroll
    def "HyperFeature enum should have correct version property for #feature"() {
        expect:
        feature.versionProperty == expectedVersionProperty

        where:
        feature                      | expectedVersionProperty
        HyperFeature.SCANCARD        | 'rnlibVersion'
        HyperFeature.NETCETERA       | 'rnlibVersion'
    }

    @Unroll
    def "fromString should convert '#input' to #expected"() {
        expect:
        HyperFeature.fromString(input) == expected

        where:
        input           | expected
        'scancard'      | HyperFeature.SCANCARD
        'SCANCARD'      | HyperFeature.SCANCARD
        'ScanCard'      | HyperFeature.SCANCARD
        'netcetera'     | HyperFeature.NETCETERA
        'NETCETERA'     | HyperFeature.NETCETERA
        'Netcetera'     | HyperFeature.NETCETERA
    }

    def "fromString should return null for invalid feature name"() {
        expect:
        HyperFeature.fromString('invalidFeature') == null
        HyperFeature.fromString('clickToPay') == null
        HyperFeature.fromString('') == null
        HyperFeature.fromString(null) == null
    }

    def "all HyperFeature values should be accessible"() {
        when:
        def values = HyperFeature.values()

        then:
        values.length == 2
        values.contains(HyperFeature.SCANCARD)
        values.contains(HyperFeature.NETCETERA)
    }

    def "HyperFeature should have unique artifact IDs"() {
        when:
        def artifactIds = HyperFeature.values().collect { it.artifactId }

        then:
        artifactIds.size() == artifactIds.unique().size()
    }

    def "both features should use rnlibVersion"() {
        expect:
        HyperFeature.SCANCARD.versionProperty == 'rnlibVersion'
        HyperFeature.NETCETERA.versionProperty == 'rnlibVersion'
    }

    def "enum should have exactly 2 features"() {
        expect:
        HyperFeature.values().length == 2
    }
}

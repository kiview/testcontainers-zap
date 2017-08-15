package de.gdaag.wss.redpanda.testcontainerszap

import spock.lang.Specification

/**
 * TODO: Documentation
 */

class SpiderScannerSpec extends Specification {

    def "scans httpd"() {
        expect:
        SpiderScanner.scan("httpd:2.2", 80, "httpd", "(?s).*resuming normal operations.*")
    }
}

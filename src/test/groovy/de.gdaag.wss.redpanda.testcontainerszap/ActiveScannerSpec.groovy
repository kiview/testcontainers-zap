package de.gdaag.wss.redpanda.testcontainerszap

import spock.lang.Specification

/**
 * TODO: Documentation
 */

class ActiveScannerSpec extends Specification {

    def "scans httpd"() {
        expect:
        ActiveScanner.scan("httpd:2.2", 80, "httpd", "(?s).*resuming normal operations.*")
    }
}

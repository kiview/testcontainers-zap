package de.gdaag.wss.redpanda.testcontainerszap

import spock.lang.Specification

class BaselineScannerSpec extends Specification {

    def "scans httpd"() {
        expect:
        BaselineScanner.scan("httpd:2.2", 80, "httpd", "(?s).*resuming normal operations.*")
    }
}

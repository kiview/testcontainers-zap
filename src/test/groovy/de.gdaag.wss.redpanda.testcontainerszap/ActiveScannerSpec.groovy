package de.gdaag.wss.redpanda.testcontainerszap

import spock.lang.Specification

class ActiveScannerSpec extends Specification {

    def "scans httpd"() {
        expect:
        ActiveScanner.scan("httpd:2.2", 80, "httpd", "(?s).*resuming normal operations.*")
    }


    def "scans dvsba"() {
        expect:
        ActiveScanner.scan("dvsba:latest", 8080, "dvsba", "(?s).*Started DvsbaApplication.*")
    }

}

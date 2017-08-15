package de.gdaag.wss.redpanda.testcontainerszap

import groovy.json.JsonSlurper
import org.testcontainers.containers.FixedHostPortGenericContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.LogMessageWaitStrategy

/**
 * TODO: Documentation
 */
class SpiderScanner {
    static void spiderScan(String testImage, int port, String alias, String waitLog) {

        String hostNetworkId = System.getenv("TEST_NETWORK_NAME")

        def zapNetworkAlias = "zap"
        FixedHostPortGenericContainer zap = new FixedHostPortGenericContainer("owasp/zap2docker-stable:latest")
                .withNetworkAliases(zapNetworkAlias)
                .withCommand("zap.sh", "-daemon", "-port", "8090", "-host", "0.0.0.0", "-config", "api.disablekey=true", "-config", "api.addrs.addr.name=.*", "-config", "api.addrs.addr.regex=true")
                .waitingFor(new LogMessageWaitStrategy().withRegEx(".*ZAP is now listening.*\\s")) as FixedHostPortGenericContainer


        GenericContainer containerUnderTest = new GenericContainer(testImage)
                .withNetworkAliases(alias)
                .withExposedPorts(port)
                .waitingFor(new LogMessageWaitStrategy().withRegEx(waitLog))

        def zapNetwork
        if (!hostNetworkId) {
            zapNetwork = Network.newNetwork()
            zap
                    .withFixedExposedPort(8090, 8090)
                    .withExposedPorts(8090)
                    .withNetwork(zapNetwork)
            containerUnderTest.withNetwork(zapNetwork)
        } else {
            zap.withNetworkMode(hostNetworkId)
            containerUnderTest.withNetworkMode(hostNetworkId)
        }

        zap.start()
        containerUnderTest.start()

        def slurper = new JsonSlurper()

        String zapUrl = "http://${!hostNetworkId ? zap.getContainerIpAddress() : zapNetworkAlias}:8090"

        def scanResponse = slurper.parse(new URL("$zapUrl/JSON/spider/action/scan/?url=http://$alias:$port"))
        String scanId = scanResponse.scan

        def scanStatus = slurper.parse(new URL("$zapUrl/JSON/spider/view/status/?scanId=$scanId"))

        while (scanStatus.status != "100") {
            sleep(500)
            scanStatus = slurper.parse(new URL("$zapUrl/JSON/spider/view/status/?scanId=$scanId"))
        }


        def pscanRecords = slurper.parse(new URL("$zapUrl/JSON/pscan/view/recordsToScan/"))
        while (pscanRecords.recordsToScan != "0") {
            sleep(500)
            pscanRecords = slurper.parse(new URL("$zapUrl/JSON/pscan/view/recordsToScan/"))
        }


        def alerts = slurper.parse(new URL("$zapUrl/JSON/core/view/alerts/"))
        println alerts.alerts

        zap.stop()
        containerUnderTest.stop()
        if (zapNetwork) {
            zapNetwork.close()
        }
    }
}

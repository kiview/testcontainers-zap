package de.gdaag.wss.redpanda.testcontainerszap

import groovy.json.JsonSlurper
import org.testcontainers.containers.FixedHostPortGenericContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.ToStringConsumer
import org.testcontainers.containers.wait.LogMessageWaitStrategy

import java.time.Duration

class Main {

    static void main(String[] args) {


        def image = "docker.repository.corp.gdaag.de/red-panda/featuretron-ng:latest"
        def port = 8080
        def networkAlias = "featuretron"
        def waitLogMessage = ".*Started Application.*\\s"

        spiderScan(image, port, networkAlias, waitLogMessage)
        baselineScan(image, port, networkAlias, waitLogMessage)

        System.exit(0)
    }

    static void baselineScan(String testImage, int port, String alias, String waitLog) {
        def zapNetwork = Network.newNetwork()

        ToStringConsumer logConsumer = new ToStringConsumer()

        GenericContainer zap = new GenericContainer("owasp/zap2docker-stable:latest")
                .withCommand("zap-baseline.py", "-t", "http://$alias:$port")
                .withNetwork(zapNetwork)
                .waitingFor(new LogMessageWaitStrategy().withRegEx("FAIL-NEW.*\\s"))
                .withLogConsumer(logConsumer)
                .withStartupTimeout(Duration.ofMinutes(3))

        GenericContainer featuretron = new GenericContainer(testImage)
                .withNetwork(zapNetwork)
                .withNetworkAliases(alias)
                .withExposedPorts(port)
                .waitingFor(new LogMessageWaitStrategy().withRegEx(waitLog))

        featuretron.start()
        zap.start()

        println logConsumer.toUtf8String()

        zap.stop()
        featuretron.stop()
        zapNetwork.close()
    }

    static void spiderScan(String testImage, int port, String alias, String waitLog) {
        def zapNetwork = Network.newNetwork()
        GenericContainer zap = new FixedHostPortGenericContainer("owasp/zap2docker-stable:latest")
                .withFixedExposedPort(8090, 8090)
                .withExposedPorts(8090)
                .withCommand("zap.sh", "-daemon", "-port", "8090", "-host", "0.0.0.0", "-config", "api.disablekey=true", "-config", "api.addrs.addr.name=.*", "-config", "api.addrs.addr.regex=true")
                .withNetwork(zapNetwork)
                .waitingFor(new LogMessageWaitStrategy().withRegEx(".*ZAP is now listening.*\\s"))

        GenericContainer featuretron = new GenericContainer(testImage)
                .withNetwork(zapNetwork)
                .withNetworkAliases(alias)
                .withExposedPorts(port)
                .waitingFor(new LogMessageWaitStrategy().withRegEx(waitLog))

        zap.start()
        featuretron.start()

        def slurper = new JsonSlurper()

        String zapUrl = "http://${zap.getContainerIpAddress()}:8090"

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
        featuretron.stop()
        zapNetwork.close()
    }


}

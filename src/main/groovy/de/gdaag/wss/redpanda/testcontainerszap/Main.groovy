package de.gdaag.wss.redpanda.testcontainerszap

import groovy.json.JsonSlurper
import org.testcontainers.containers.FixedHostPortGenericContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.ToStringConsumer
import org.testcontainers.containers.wait.LogMessageWaitStrategy

import java.time.Duration

class Main {


    public static final String ENV_IMAGE = "IMAGE"
    public static final String ENV_PORT = "PORT"
    public static final String ENV_WAIT_LOG_MESSAGE ="WAIT_LOG_MESSAGE"

    static void main(String[] args) {


        def image = System.getenv(ENV_IMAGE)
        def port = Integer.parseInt(System.getenv(ENV_PORT) ?: "8080")
        def waitLogMessage = System.getenv(ENV_WAIT_LOG_MESSAGE)

        def networkAlias = "app_under_test"

        zapActiveScan(image, port, networkAlias, waitLogMessage)
        spiderScan(image, port, networkAlias, waitLogMessage)
        baselineScan(image, port, networkAlias, waitLogMessage)

        System.exit(0)
    }

    static void zapActiveScan(String testImage, int port, String alias, String waitLog) {
        String hostNetworkId = System.getenv("TEST_NETWORK_NAME")

        def zapNetworkAlias = "zap"
        FixedHostPortGenericContainer zap = new FixedHostPortGenericContainer("owasp/zap2docker-stable:latest")
                .withExposedPorts(8090)
                .withCommand("zap-x.sh", "-daemon", "-port", "8090", "-host", "0.0.0.0", "-config", "api.disablekey=true", "-config", "api.addrs.addr.name=.*", "-config", "api.addrs.addr.regex=true")
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
        String targetUrl = "http://$alias:$port/"

        // Spider scan
        def scanResponse = slurper.parse(new URL("$zapUrl/JSON/spider/action/scan/?url=$targetUrl"))
        String scanId = scanResponse.scan

        def scanStatus = slurper.parse(new URL("$zapUrl/JSON/spider/view/status/?scanId=$scanId"))

        while (scanStatus.status != "100") {
            sleep(500)
            println "Spider Progess: " + scanStatus.status + "%"
            scanStatus = slurper.parse(new URL("$zapUrl/JSON/spider/view/status/?scanId=$scanId"))
        }

        // Active scan
        def ascan = slurper.parse(new URL("$zapUrl/JSON/ascan/action/scan/?url=$targetUrl"))
        String aScanId = scanResponse.scan

        def aScanStatus = slurper.parse(new URL("$zapUrl/JSON/ascan/view/status/?scanId=$aScanId"))
        while (aScanStatus.status != "100") {
            sleep(500)
            println "Active Scan Progess: " + aScanStatus.status + "%"
            aScanStatus = slurper.parse(new URL("$zapUrl/JSON/ascan/view/status/?scanId=$aScanId"))
        }

        // check if pscan is finished
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

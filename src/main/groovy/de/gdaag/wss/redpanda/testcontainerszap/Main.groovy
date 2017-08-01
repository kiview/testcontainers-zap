package de.gdaag.wss.redpanda.testcontainerszap

import groovy.json.JsonSlurper
import org.testcontainers.containers.FixedHostPortGenericContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.LogMessageWaitStrategy

class Main {

    static void main(String[] args) {
        def slurper = new JsonSlurper()
        def zapNetwork = Network.newNetwork()
        GenericContainer zap = new FixedHostPortGenericContainer("owasp/zap2docker-stable:latest")
                .withFixedExposedPort(8090, 8090)
                .withExposedPorts(8090)
                .withCommand("zap.sh", "-daemon", "-port", "8090", "-host", "0.0.0.0", "-config", "api.disablekey=true", "-config", "api.addrs.addr.name=.*", "-config", "api.addrs.addr.regex=true")
                .withNetwork(zapNetwork)
                .waitingFor(new LogMessageWaitStrategy().withRegEx(".*ZAP is now listening.*\\s"))

        GenericContainer featuretron = new GenericContainer("docker.repository.corp.gdaag.de/red-panda/featuretron-ng:latest")
                .withNetwork(zapNetwork)
                .withNetworkAliases("featuretron")
                .withExposedPorts(8080)
                .waitingFor(new LogMessageWaitStrategy().withRegEx(".*Started Application.*\\s"))

        zap.start()
        featuretron.start()

        String zapUrl = "http://${zap.getContainerIpAddress()}:8090"

        def scanResponse = slurper.parse(new URL("$zapUrl/JSON/spider/action/scan/?url=http://featuretron:8080"))
        String scanId = scanResponse.scan

        def scanStatus = slurper.parse(new URL("$zapUrl/JSON/spider/view/status/?scanId=$scanId"))

        while (scanStatus.status != "100") {
            sleep(500)
            scanStatus = slurper.parse(new URL("$zapUrl/JSON/spider/view/status/?scanId=$scanId"))
        }

        def alerts = slurper.parse(new URL("$zapUrl/JSON/core/view/alerts/"))
        while (alerts.alerts.isEmpty()) {
            sleep(500)
            alerts = slurper.parse(new URL("$zapUrl/JSON/core/view/alerts/"))
        }

        println alerts.alerts

        zap.stop()
        featuretron.stop()
        zapNetwork.close()

        System.exit(0)
    }
}

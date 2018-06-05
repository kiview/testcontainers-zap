package de.gdaag.wss.redpanda.testcontainerszap

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.ToStringConsumer
import org.testcontainers.containers.wait.LogMessageWaitStrategy

import java.time.Duration

/**
 * TODO: Documentation
 */
class BaselineScanner {
    static void scan(String testImage, int port, String alias, String waitLog) {
        def zapNetwork = Network.newNetwork()

        ToStringConsumer logConsumer = new ToStringConsumer()

        GenericContainer zap = new GenericContainer("kiview/zap-sqlmap:latest")
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
}

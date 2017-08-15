package de.gdaag.wss.redpanda.testcontainerszap

class Main {


    public static final String ENV_IMAGE = "IMAGE"
    public static final String ENV_PORT = "PORT"
    public static final String ENV_WAIT_LOG_MESSAGE ="WAIT_LOG_MESSAGE"

    static void main(String[] args) {


        def image = System.getenv(ENV_IMAGE)
        def port = Integer.parseInt(System.getenv(ENV_PORT) ?: "8080")
        def waitLogMessage = System.getenv(ENV_WAIT_LOG_MESSAGE)

        def networkAlias = "app_under_test"

        ActiveScanner.zapActiveScan(image, port, networkAlias, waitLogMessage)
        SpiderScanner.spiderScan(image, port, networkAlias, waitLogMessage)
        BaselineScanner.baselineScan(image, port, networkAlias, waitLogMessage)

        System.exit(0)
    }


}

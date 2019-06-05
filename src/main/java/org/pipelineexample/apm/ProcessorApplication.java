package org.pipelineexample.apm;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ProcessorApplication {

    private static final String PROPERTIES_FILE_PATH = "pipeline.properties";

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expect 1 argument that is number of processor");
        }

        PipelineProcessor pipelineProcessor = buildPipelineProcessor(args[0], loadProperties());
        pipelineProcessor.process();
    }

    private static Properties loadProperties() throws IOException {
        Properties properties = new Properties();
        properties.load(new FileInputStream(PROPERTIES_FILE_PATH));
        return properties;
    }

    private static PipelineProcessor buildPipelineProcessor(String arg, Properties properties) {
        Integer currentNumber = Integer.valueOf(arg);
        Integer total = Integer.valueOf(properties.get("total").toString());
        String name = getName(currentNumber, total);
        String[] ports =  properties.get("ports").toString().split(",");
        if (ports.length != total - 1) {
            throw new IllegalArgumentException("Expect number of ports is total - 1");
        }
        int inPort = getPort(currentNumber, total, ports, true);
        int outPort = getPort(currentNumber, total, ports, false);

        return new PipelineProcessor(name, inPort, outPort);
    }

    private static int getPort(Integer currentNumber, Integer total, String[] ports, boolean isIn) {
        if ((currentNumber == 1 && isIn) || (currentNumber.equals(total) && !isIn)) {
            return -1;
        }
        int index = isIn ? currentNumber - 2 : currentNumber - 1;
        return Integer.valueOf(ports[index]);
    }

    private static String getName(Integer currentNumber, Integer total) {
        if (1 == currentNumber) {
            return "Source";
        }

        if (total.equals(currentNumber)) {
            return "Sink";
        }
        return "Processor_" + (currentNumber - 1);
    }
}

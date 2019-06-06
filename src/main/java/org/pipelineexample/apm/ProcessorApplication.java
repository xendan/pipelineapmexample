package org.pipelineexample.apm;

import java.io.IOException;

/**
 * Builds and run processor which properties is defined by arguments and 'pipeline.properties'
 */
public class ProcessorApplication {

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expect 2 arguments: number of processor and comma separated list of ports");
        }

        PipelineProcessor pipelineProcessor = new PipelineProcessorFactory(args[1]).buildPipelineProcessor(args[0]);
        pipelineProcessor.process();
    }

}

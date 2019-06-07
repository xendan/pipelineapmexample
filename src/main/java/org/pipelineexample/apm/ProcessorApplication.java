package org.pipelineexample.apm;

import org.pipelineexample.apm.processor.PipelineProcessor;
import org.pipelineexample.apm.processor.PipelineProcessorFactory;

import java.io.IOException;

/**
 * Builds and run processor which properties is defined by arguments and 'pipeline.properties'
 */
public class ProcessorApplication {

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 3) {
            throw new IllegalArgumentException("Expect 23 arguments: number of processor and comma separated list of ports");
        }
        boolean burnCpus = args.length > 3 && Boolean.valueOf(args[3]);
        PipelineProcessor pipelineProcessor = new PipelineProcessorFactory(args[2]).buildPipelineProcessor(args[0], args[1], burnCpus);
        pipelineProcessor.process();
    }

}

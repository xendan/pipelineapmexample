package org.pipelineexample.apm;

import org.pipelineexample.apm.processor.PipelineProcessor;
import org.pipelineexample.apm.processor.PipelineProcessorFactory;

import java.io.IOException;
import java.util.Arrays;

public class ProcessorApplication {

    public static void main(String[] args) throws IOException, InterruptedException {
         System.out.println("args=" + Arrays.toString(args));
        if (args.length < 3) {
            throw new IllegalArgumentException("Expect min 3 arguments: name, inPort, outPort and optional burnCpus");
        }
        boolean burnCpus = args.length > 3 && Boolean.valueOf(args[3]);
        int inPort = Integer.parseInt(args[1]);
        int outPort = Integer.parseInt(args[2]);
        PipelineProcessor pipelineProcessor = new PipelineProcessorFactory().buildPipelineProcessor(args[0], inPort, outPort, burnCpus);
        pipelineProcessor.process();
    }

}

package org.pipelineexample.apm.processor;

import org.pipelineexample.apm.InfoConsole;
import org.pipelineexample.apm.LowBudgetKafka;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PipelineProcessorFactory {

    public PipelineProcessor buildPipelineProcessor(String name, int inPort, int outPort, boolean burnCpus) {
        ProcessorType type = getType(inPort, outPort);
        InfoConsole infoConsole = new InfoConsole(name);
        LowBudgetKafka kafka = new LowBudgetKafka(inPort, outPort, infoConsole, burnCpus);
        return new PipelineProcessor(name, kafka, type);
    }

    private ProcessorType getType(int inPort, int outPort) {
        if (-1 == inPort) {
            return ProcessorType.SOURCE;
        }
        if (-1 == outPort) {
            return ProcessorType.SINK;
        }
        return ProcessorType.PRCOSSOR;
    }
}

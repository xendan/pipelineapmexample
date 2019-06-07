package org.pipelineexample.apm.processor;

import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Span;
import co.elastic.apm.api.Transaction;
import org.pipelineexample.apm.LowBudgetKafka;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class PipelineProcessor {

    private static final String PARENT_TRANSACTION_NAME = "apmToyExampleParentTransactionWithSelectId";

    private final String name;
    private final String selectId;
    private final LowBudgetKafka communicationChannel;
    private final ProcessorType type;
    private String message;

    public PipelineProcessor(String name, String selectId, LowBudgetKafka communicationChannel, ProcessorType type) {
        this.name = name;
        this.selectId = selectId;
        this.communicationChannel = communicationChannel;
        this.type = type;
    }

    public void process() throws InterruptedException, IOException {
        String inMessage = communicationChannel.readMessage();
        String outMessage = processMessage(inMessage);
        communicationChannel.sendMessage(outMessage);
    }

    /**
     * Wraps APM Transaction/Span around business logic.
     */
    private String processMessage(String message) throws InterruptedException {
        this.message = message;
        Transaction transaction = ElasticApm.startTransaction()
                .setName(PARENT_TRANSACTION_NAME + "-" + name)
                .addLabel("selectId", selectId);
        try {
            thisIsActuallyABusinessLogic();
            return this.message + ", Processed by " +name;
        } catch (Exception e) {
            transaction.captureException(e);
            throw e;
        } finally {
            transaction.end();
        }
    }

    /**
     * Some useful work.
     */
    private void thisIsActuallyABusinessLogic() throws InterruptedException {
        TimeUnit.SECONDS.sleep(3);
    }
}

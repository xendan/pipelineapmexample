package org.pipelineexample.apm;

import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Span;
import co.elastic.apm.api.Transaction;

import java.io.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads message on inPort, process it and send it to outPort.
 */
public class PipelineProcessor {

    private static final String PARENT_TRANSACTION_NAME = "apmToyExampleParentTransaction";
    private static final Pattern KEY_VAL_PATTERN = Pattern.compile("<<<(.+):(.+)>>>");

    private final String name;
    private final SomethingLikeKafka somethingLikeKafka;
    private final InfoConsole infoConsole;
    private final ProcessorType type;

    public PipelineProcessor(String name, SomethingLikeKafka somethingLikeKafka, InfoConsole infoConsole, ProcessorType type) {
        this.name = name;
        this.somethingLikeKafka = somethingLikeKafka;
        this.infoConsole = infoConsole;
        this.type = type;
    }

    public void process() throws InterruptedException, IOException {
        infoConsole.info( "**** Started ****");
        String inMessage = somethingLikeKafka.readInput();
        String outMessage = processMessage(inMessage);
        somethingLikeKafka.sendMessage(outMessage);
        infoConsole.info("**** Bye ****\n-----------------------------------------------------------------");
    }

    /**
     * Wraps APM Transaction/Span around business logic.
     */
    private String processMessage(String message) throws InterruptedException {
        Transaction parentTransaction = getOrCreateTransaction(message);
        Span span = parentTransaction.startSpan();
        try {
            span.setName(name);
            thisIsActuallyABusinessLogic();
            return injectParentTransactionId(message, parentTransaction) + ", processed by " + name;
        } catch (Exception e) {
            parentTransaction.captureException(e);
            span.captureException(e);
            throw e;
        } finally {
            span.end();
            if (type == ProcessorType.SINK) {
                parentTransaction.end();
            }
        }
    }

    /**
     * Some useful work.
     */
    private void thisIsActuallyABusinessLogic() throws InterruptedException {
        TimeUnit.SECONDS.sleep(3);
    }


    private String extractKey(String key, String message) {
        Matcher matcher = KEY_VAL_PATTERN.matcher(message);
        while (matcher.find()) {
            if (key.equals(matcher.group(1))) {
                return matcher.group(2);
            }
        }
        return null;
    }

    private String injectParentTransactionId(String message, Transaction parentTransaction) {
        if (extractKey("elastic-apm-traceparent", message) == null) {
            return message + " <<<elastic-apm-traceparent:" + parentTransaction.getTraceId() + ">>>";
        }
        return message;
    }

    private Transaction getOrCreateTransaction(String message) {
        Transaction transaction;
        if (type == ProcessorType.SOURCE) {
            transaction = ElasticApm.startTransaction();
        } else {
            transaction = ElasticApm.startTransactionWithRemoteParent(key -> extractKey(key, message));
        }
        transaction.setName(PARENT_TRANSACTION_NAME);
        return transaction;
    }

}

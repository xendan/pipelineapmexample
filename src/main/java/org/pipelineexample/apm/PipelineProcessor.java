package org.pipelineexample.apm;

import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Span;
import co.elastic.apm.api.Transaction;

import java.io.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class PipelineProcessor {

    private static final String PARENT_TRANSACTION_NAME = "apmToyExampleParentTransaction";
    private static final Pattern KEY_VAL_PATTERN = Pattern.compile("<<<(.+):(.+)>>>");

    private final String name;
    private final LowBudgetKafka communicationChannel;
    private final InfoConsole infoConsole;
    private final ProcessorType type;
    private final boolean burnCpus;
    private String message;

    public PipelineProcessor(String name, LowBudgetKafka communicationChannel, InfoConsole infoConsole, ProcessorType type, boolean burnCpus) {
        this.name = name;
        this.communicationChannel = communicationChannel;
        this.infoConsole = infoConsole;
        this.type = type;
        this.burnCpus = burnCpus;
    }

    public void process() throws InterruptedException, IOException {
        infoConsole.info( "**** Started ****");
        String inMessage = communicationChannel.readMessage();
        String outMessage = processMessage(inMessage);
        communicationChannel.sendMessage(outMessage);
        sleepOrExit();
    }

    private void sleepOrExit() throws InterruptedException {
        while (burnCpus) {
            infoConsole.info("~ ~~~~ ZZZZ - zzzz - zzzzz ~~~~~~~~~ ~~~ z ~~~~ ~ ~ z ~ ~");
            TimeUnit.HOURS.sleep(10);
        }
        infoConsole.info("**** Bye ****\n-----------------------------------------------------------------");
    }

    /**
     * Wraps APM Transaction/Span around business logic.
     */
    private String processMessage(String message) throws InterruptedException {
        this.message = message;
        Transaction parentTransaction = getOrCreateTransaction(message);
        Span span = parentTransaction.startSpan();
        try {
            span.setName(name);
            thisIsActuallyABusinessLogic();
            parentTransaction.injectTraceHeaders(this::injectParentTransactionId);
            return this.message + " processed by " + name;
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

    private void injectParentTransactionId(String key, String value) {
        if (extractKey(key, message) == null) {
            message += ("<<<"+ key +":" +value + ">>>");
        }
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

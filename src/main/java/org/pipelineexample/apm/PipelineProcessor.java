package org.pipelineexample.apm;

import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Span;
import co.elastic.apm.api.Transaction;

import java.io.*;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads message on inPort, process it and send it to outPort.
 */
public class PipelineProcessor {

    private static final String LOCALHOST = "127.0.0.1";
    private static final Pattern KEY_VAL_PATTERN = Pattern.compile("<<<(.+):(.+)>>>");
    private static final String FIRST_MESSAGE = "First Message";
    private static final String END_MESSAGE = "Pipeline ended!";

    private final String name;
    private final int inPort;
    private final int outPort;

    /**
     * @param name - "Source" for first in pipeline, "Sink" for last and "Processor_<number>" for <number> processor.
     * @param inPort - port to read message, if -1, than message is just hardcoded string FIRST_MESSAGE
     * @param outPort - port to write processed message. If -1, "Sink" do not send message, just print #END_MESSAGE
     */
    public PipelineProcessor(String name, int inPort, int outPort) {
        this.name = name;
        this.inPort = inPort;
        this.outPort = outPort;
    }

    /**
     * Wait for message on incoming inPort, process it and write to outPort.
     */
    public void process() throws InterruptedException, IOException {
        info("started");
        String inMessage = readInput();
        String outMessage = processMessage(inMessage);
        sendMessage(outMessage);
        info("Bye");
    }

    /**
     * Wraps APM Transaction/Span around business logic.
     */
    private String processMessage(String message) throws InterruptedException {
        Transaction parentTransaction = getOrCreateTransaction(message);
        Span span = parentTransaction.startSpan();
        try {
            span.setName(name);
            thisIsAcutallyABusinessLogic(message);
            return injectParentTransactionId(message, parentTransaction) + ", processed by " + name;
        } catch (Exception e) {
            parentTransaction.captureException(e);
            span.captureException(e);
            throw e;
        } finally {
            span.end();
            if (isSink()) {
                parentTransaction.end();
            }
        }
    }

    private boolean isSink() {
        return -1 == outPort;
    }

    private boolean isSource() {
        return -1 == inPort;
    }

    /**
     * Some useful work.
     */
    private void thisIsAcutallyABusinessLogic(String message) throws InterruptedException {
        info("processing message \"" + message + "\"");
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
        if (isSource()) {
            transaction = ElasticApm.startTransaction();
        } else {
            transaction = ElasticApm.startTransactionWithRemoteParent(key -> extractKey(key, message));
        }
        transaction.setName("apmToyExampleParentTransaction");
        return transaction;
    }



    private void sendMessage(String message) throws IOException {
        if (outPort == -1) {
            info(END_MESSAGE);
        } else {
            info("sending : \"" + message + "\" to port " + outPort);
            ServerSocket serverSocket = new ServerSocket(outPort);
            Socket socket = serverSocket.accept();
            OutputStream os = socket.getOutputStream();
            PrintWriter pw = new PrintWriter(os, true);
            pw.println(message);
            pw.close();
            socket.close();
        }
    }

    private String readInput() throws IOException, InterruptedException {
        if (inPort == -1) {
            return FIRST_MESSAGE;
        }
        return readFromSocket();
    }

    private String readFromSocket() throws IOException, InterruptedException {
        while (true) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try ( Socket client = new Socket(LOCALHOST, inPort);
                  InputStream inFromServer = client.getInputStream();
                  DataInputStream in = new DataInputStream(inFromServer)) {
                byte b = in.readByte();
                while (b != -1){
                    bos.write(b);
                    b = in.readByte();
                }
                bos.close();
            } catch (ConnectException e) {
                TimeUnit.SECONDS.sleep(2);
            } catch (EOFException e) {
                String message = new String(bos.toByteArray());
                bos.close();
                return message.replace("\n", "");
            } finally {
                bos.close();
            }
        }
    }

    private void info(String message) {
        System.out.println("[" + name + "] : " + message);
    }
}

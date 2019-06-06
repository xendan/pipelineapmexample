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

public class PipelineProcessor {

    private static final String LOCALHOST = "127.0.0.1";
    private static final Pattern KEY_VAL_PATTERN = Pattern.compile("<<<(.+):(.+)>>>");

    private final String name;
    private final int inPort;
    private final int outPort;

    public PipelineProcessor(String name, int inPort, int outPort) {
        this.name = name;
        this.inPort = inPort;
        this.outPort = outPort;
    }

    public void process() throws InterruptedException, IOException {
        info("started");
        String inMessage = readInput();
        String outMessage = processMessage(inMessage);
        sendMessage(outMessage);
        info("Bye");
    }

    private String processMessage(String message) throws InterruptedException {
        Transaction parentTransaction = getOrCreateTransaction(message);
        Span span = parentTransaction.startSpan();
        try {
            span.setName(name);
            thisIsAcutallyABusinessLogic(message);
            return  injectParentTransactionId(message, parentTransaction) + ", processed by " + name;
        } catch (Exception e) {
            parentTransaction.captureException(e);
            span.captureException(e);
            throw e;
        } finally {
            span.end();
            if ("Sink".equals(name)) {
                parentTransaction.end();
            }
        }
    }

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
        if ("Source".equals(name)) {
            transaction = ElasticApm.startTransaction();
        } else {
            transaction = ElasticApm.startTransactionWithRemoteParent(key -> extractKey(key, message));
        }
        transaction.setName("apmToyExampleParentTransaction");
        return transaction;
    }

    private void sendMessage(String message) throws IOException {
        if (outPort == -1) {
            info("Pipeline ended!");
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
            return "First Message";
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

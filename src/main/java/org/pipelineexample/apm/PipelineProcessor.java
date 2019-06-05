package org.pipelineexample.apm;

import java.io.*;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

public class PipelineProcessor {
    public static final String LOCALHOST = "127.0.0.1";
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
        String message = readInput();
        info("processing message \"" + message + "\"");
        TimeUnit.SECONDS.sleep(3);
        String newMessage = message + ", processed by " + name;
        if (outPort != -1) {
            info("sending:\"" + newMessage + "\" to port " + outPort);
        }
        sendMessage(newMessage);
        info("Bye");
    }

    private void sendMessage(String message) throws IOException {
        if (outPort == -1) {
            info("Pipeline ended!");
        } else {
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

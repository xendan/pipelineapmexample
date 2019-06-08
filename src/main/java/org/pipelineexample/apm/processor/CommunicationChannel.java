package org.pipelineexample.apm.processor;

import java.io.IOException;

public interface CommunicationChannel {

    String readMessage() throws IOException, InterruptedException;

    void sendMessage(String outMessage) throws IOException, InterruptedException;
}

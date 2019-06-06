package org.pipelineexample.apm;

import java.io.IOException;

public interface SomethingLikeKafka {

    String readInput() throws IOException, InterruptedException;

    void sendMessage(String outMessage) throws IOException;
}

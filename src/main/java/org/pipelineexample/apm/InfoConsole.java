package org.pipelineexample.apm;

public class InfoConsole {

    private final String name;

    public InfoConsole(String name) {
        this.name = name;
    }

    public void info(String message) {
        System.out.println("[" + name + "] : " + message);
    }
}

# Pipeline APM example
Just a toy example of pipeline that consist of several java processes communicating by sockets.
 
This is a desirable result in APM Kibana.

![It would be nice to have something like this](apm-example.png?raw=true "APM Kibana: Expected")


## Quick start
Create `apm.properties` file using your APM server properties, content should be similar to [apm.example.properties](apm.example.properties) file.
Run
```
./gradlew clean runPipeline
```

## More details
Gradle task `runPipeline` builds project and execute result jar file several times, to create a pipeline that consist of several
java processes (one for each [processor](src/main/java/org/pipelineexample/apm/PipelineProcessor.java)). 
Number of processors and ports that they use for sending/receiving messages is defined in [pipeline.properties](pipeline.properties). 
Each [processor](src/main/java/org/pipelineexample/apm/PipelineProcessor.java) except `Source`(just a fancy name for the first [processor](src/main/java/org/pipelineexample/apm/PipelineProcessor.java), last is `Sink`) waits for message on incoming port, 
process it and write new message to outgoing port. `Source` does not read message, it just sends "First message" string.

```
     +---------+         +----------+         +----------+           +----------+
     |         | MESSAGE |          | MESSAGE |          | MESSAGE   |          |
     |Source   |+------->|Processor1|+------->|Processor2|+--------->|Sink      |
     +---------+         +----------+         +----------+           +----------+
```

Processor "Business logic" is executed in [`PipelineProcessor.thisIsAcutallyABusinessLogic`](src/main/java/org/pipelineexample/apm/PipelineProcessor.java)

```java
 private void thisIsAcutallyABusinessLogic() throws InterruptedException {
        TimeUnit.SECONDS.sleep(3);
 }
```
[`PipelineProcessor.processMessage`](src/main/java/org/pipelineexample/apm/PipelineProcessor.java) is used to wrap this logic with APM Transactions 

```java
private String processMessage(String message) throws InterruptedException {
Transaction parentTransaction = getOrCreateTransaction(message);
        Span span = parentTransaction.startSpan();
        try {
            span.setName(name);
            thisIsActuallyABusinessLogic(message);
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
``` 

For current version of code actual result is: parent transaction is closed after 3 seconds, e.g. when `Source` completed
"Business Logic"


![But we actually have this](actual_result.png?raw=true "APM Kibana: Actual")
And also there is a warning message:
```
WARN co.elastic.apm.agent.impl.transaction.TraceContext - The traceparent header has to be at least 55 chars long, but was 'a87a7067a273501c374e16f3ab9cca69'

```

It could also be OK for us not to have parent transaction, but have span following one after another, like

![It would be nice to have something like this](apm-example2.png?raw=true "APM Kibana: Other OK result")


but [APM open trace bridge documentation](https://www.elastic.co/guide/en/apm/agent/java/current/opentracing-bridge.html) says
```
Currently, this bridge only supports child_of references. Other references, 
like follows_from are not supported yet.
``` 
so not sure if it is possible.
# Pipeline APM example
Just a toy example of pipeline that consist of several java processes communicating by sockets.
 
This is a desirable result in APM Kibana.

![It would be nice to have something like this](apm-example.png?raw=true "APM Kibana: Expected")


## Quick start
Create file 'apm.properties' using your APM server properties, content should be similar to `apm.example.properties` file.
Run
```
./gradlew clean runPipeline
```
This will run "pipeline" defined by `pipeline.properties` where:

`total` defines number of processors, expected >= 3

`ports` defines ports used by processors for communication, expected list of integers of `total`-1 length

## More details
Gradle task `runPipeline` builds project and runs result jar `total` times, to create a pipeline that consist of `total` 
number of independent processors.
Each processor except `Source`(just fancy name for first processor) wait for message on incoming port, 
process it and write new message to outgoing port. `Source` does not read message, it just sends "First message" string.

```
     +---------+         +----------+         +----------+           +----------+
     |         | MESSAGE |          | MESSAGE |          | MESSAGE   |          |
     |Source   |+------->|Processor1|+------->|Processor2|+--------->|Sink      |
     +---------+         +----------+         +----------+           +----------+
```

Processor "Business logic" is executed in `PipelineProcessor.thisIsAcutallyABusinessLogic`

```java
 private void thisIsAcutallyABusinessLogic(String message) throws InterruptedException {
        info("processing message \"" + message + "\"");
        TimeUnit.SECONDS.sleep(3);
 }
```
`PipelineProcessor.processMessage` is used to wrap this logic with APM Transactions 

```java
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
            if ("Sink".equals(name)) {
                parentTransaction.end();
            }
        }
``` 

For current version of code actual result is: parent transaction is closed after 3 seconds, e.g. when `Source` completed
"Business Logic"


![But we actually have this](actual_result.png?raw=true "APM Kibana: Actual")

It could also be OK for us not to have parent transaction, but have span following one after another, like

![It would be nice to have something like this](apm-example2.png?raw=true "APM Kibana: Other OK result")


but [APM open trace bridge documentation](https://www.elastic.co/guide/en/apm/agent/java/current/opentracing-bridge.html) says
```
Currently, this bridge only supports child_of references. Other references, 
like follows_from are not supported yet.
``` 
so not sure if it is possible.
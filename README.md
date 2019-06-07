# Pipeline APM example
Just a toy example of pipeline that consist of several java processes communicating by sockets.
## Expected result
This is a desirable result in APM Kibana, created with GIMP.

![It would be nice to have something like this](imgs/apm-example.png?raw=true "APM Kibana: Expected with parent")

Other possible option might be the same only without parent transaction.

![It would be nice to have something like this](imgs/apm-example2.png?raw=true "APM Kibana: Expected wihtout parent")


## Quick start
Create `apm.properties` file using your APM server properties, content should be similar to [apm.example.properties](apm.example.properties) file.
Run
```
./gradlew clean runPipeline
```

## More details
Gradle task `runPipeline` builds project and execute result jar file several times, to create a pipeline that consist of several
java processes (one for each [processor](src/main/java/org/pipelineexample/apm/processor/PipelineProcessor.java)).
Number of processors and ports that they use for sending/receiving messages is defined in [pipeline.properties](pipeline.properties). 
Each [processor](src/main/java/org/pipelineexample/apm/processor/PipelineProcessor.java) except `Source`(just a fancy name for the first [processor](src/main/java/org/pipelineexample/apm/processor/PipelineProcessor.java), last is `Sink`) waits for message on incoming port,
process it and write new message to outgoing port. `Source` does not read message, it just sends "First message" string.

```
     +---------+         +----------+         +----------+           +----------+
     |         | MESSAGE |          | MESSAGE |          | MESSAGE   |          |
     |Source   |+------->|Processor1|+------->|Processor2|+--------->|Sink      |
     +---------+         +----------+         +----------+           +----------+
```

Processor "Business logic" is executed in [`PipelineProcessor.thisIsActuallyABusinessLogic`](src/main/java/org/pipelineexample/apm/processor/PipelineProcessor.java)

```java
    private void thisIsActuallyABusinessLogic() throws InterruptedException {
        TimeUnit.SECONDS.sleep(3);
    }
```
[`PipelineProcessor.processMessage`](src/main/java/org/pipelineexample/apm/processor/PipelineProcessor.java) is used to wrap this logic with APM Transactions

```java
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

    private void injectParentTransactionId(String key, String value) {
        removeOldKey(key);
        message = " <<<" + key + ":" + value + ";>>>" + message;
    }

    private void removeOldKey(String key) {
        Pattern pattern = getKeyPattern(key);
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            message = message.substring(0, matcher.start()) + message.substring(matcher.end());
        }
    }

    private String extractKey(String key, String message) {
        Matcher matcher = getKeyPattern(key).matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private Pattern getKeyPattern(String key) {
        return Pattern.compile("<<<" + key + ":(.+);>>>");
    }

``` 
## Actual result
 - only one transaction is created
 - duration of this transaction is only 3 seconds, e.g. execution time of single processor
 - parent transaction contains only one span for `Sink`

![But we actually have this](imgs/actual_result.png?raw=true "APM Kibana: Actual")

## `follows_from` relation
It could also be OK for us not to have parent transaction, but have span following one after another, like

![It would be nice to have something like this](imgs/apm-example2.png?raw=true "APM Kibana: Other OK result")


but [APM open trace bridge documentation](https://www.elastic.co/guide/en/apm/agent/java/current/opentracing-bridge.html) says
```
Currently, this bridge only supports child_of references. Other references, 
like follows_from are not supported yet.
``` 
so not sure if it is possible.

## P.S.
Our real processors are listeners, so they do listen Kafka in a never ending loop. If this is the reason, this processor
also can run in a loop mode, by setting `burnCpus` property to `true` in [pipeline.properties](pipeline.properties). 
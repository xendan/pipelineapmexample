# pipelineapmexample
Example of pipeline that uses APM pefrormance metrics

## Quick start
```
./gradlew clean runPipeline
```
This will run "pipeline" defined by `pipeline.properties` where:

`total` defines number of processor, expected >= 3
`ports` ports used by processors for communication, expected list contains `total`-1 ports

# Scala.js AWS Lambda for MWAA example

# Development

Watch for changes and re-compile (from sbt):
```
> npmPackage
```

# Running locally

You can use [docker-lambda](https://github.com/lambci/docker-lambda) to test the function locally.
Export AWS_* variable to your shell and run:

```bash
 docker run --rm \
    -v $(pwd)/target/scala-2.13/npm-package:/var/task:ro,delegated \
    -e MWAA_ENV_NAME=my-mwaa-env \
    -e AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID \
    -e AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY \
    -e AWS_SESSION_TOKEN=$AWS_SESSION_TOKEN \
    -e DOCKER_LAMBDA_STAY_OPEN=1 \
    -p 9001:9001 \
    lambci/lambda:nodejs12.x \
    index.http4sHandler
```
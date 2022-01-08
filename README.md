# Scala.js AWS Lambda Template

A [Giter8](http://www.foundweekends.org/giter8/) template for creating an AWS Lambda using [Scala.js](http://www.scala-js.org/).

```
sbt new bgahagan/scalajs-lambda.g8
```

## Features

* Scala.js 1.0 Support
* Uses [sbt-scalajs-bundler](https://scalacenter.github.io/scalajs-bundler/) to manage npm dependencies (for example the AWS SDK)
* Uses [sbt-native-packager](https://www.scala-sbt.org/sbt-native-packager/) to bundle lambda function for deployment

# Development

Watch for changes and re-compile (from sbt):
```
> ~ Compile / fastOptJS / webpack
```

# Running locally

You can use [docker-lambda](https://github.com/lambci/docker-lambda) to test the function locally:

```
$ docker run --rm \
    -v $(pwd)/target/scala-2.13/scalajs-bundler/main:/var/task:ro,delegated \
    lambci/lambda:nodejs12.x \
    lambda-fastopt-bundle.handler \
    '{"body":"world"}'
```

AWS Lambda example:

Export AWS_* variable to your shell and run:

```bash
docker run --rm \
    -v $(pwd)/target/scala-2.13/scalajs-bundler/main:/var/task:ro,delegated \
    -e AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID \
    -e AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY \
    -e AWS_SESSION_TOKEN=$AWS_SESSION_TOKEN \
    lambci/lambda:nodejs12.x \
    lambda-fastopt-bundle.handler \
    '{"body": "{\"dagId\":\"ddd\",\"execDate\":[]}"}'

docker run --rm \
    -v $(pwd)/target/scala-2.13/npm-package:/var/task:ro,delegated \
    -e AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID \
    -e AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY \
    -e AWS_SESSION_TOKEN=$AWS_SESSION_TOKEN \
    -e DOCKER_LAMBDA_STAY_OPEN=1 \
    -p 9001:9001 \
    lambci/lambda:nodejs12.x \
    index.http4sHandler \
    '{"body": "{\"dagId\":\"ddd\",\"execDate\":[]}"}'
```

The handler name will be `${project-name}-fastopt-bundle.handler`.

# Deploy

Package the lambda (from sbt):
```
> Universal / packageBin
```

Deploy the resulting zip in `target/universal` to AWS Lambda. The handler name will be `${project-name}.handler`.

import facade.amazonaws.services.mwaa.CreateCliTokenRequest
import facade.amazonaws.services.mwaa.MWAA
import cats.effect._
import cats.effect.std.Random
import cats.implicits._
import feral.lambda._
import feral.lambda.events._
import feral.lambda.http4s._
import io.circe.parser._
import natchez.Trace
import natchez.http4s.NatchezMiddleware
import natchez.xray.XRay
import org.http4s.AuthScheme
import org.http4s.Credentials
import org.http4s.HttpRoutes
import org.http4s.Request
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.headers._
import org.http4s.MediaType
import org.http4s.ember.client.EmberClientBuilder
import fs2.Stream
import io.scalajs.nodejs.process.{Process => JsProcess}
import org.http4s.dsl.Http4sDsl

/** For a gentle introduction, please look at the `KinesisLambda` first which
  * uses `IOLambda.Simple`.
  *
  * The `IOLambda` uses a slightly more complicated encoding by introducing an
  * effect `LambdaEnv[F]` which provides access to the event and context in `F`.
  * This allows you to compose your handler as a stack of "middlewares", making
  * it easy to e.g. add tracing to your Lambda.
  */
object http4sHandler
    extends IOLambda[
      ApiGatewayProxyEventV2,
      ApiGatewayProxyStructuredResultV2
    ] {

  /** Actually, this is a `Resource` that builds your handler. The handler is
    * acquired exactly once when your Lambda starts and is permanently installed
    * to process all incoming events.
    *
    * The handler itself is a program expressed as `IO[Option[Result]]`, which
    * is run every time that your Lambda is triggered. This may seem
    * counter-intuitive at first: where does the event come from? Because
    * accessing the event via `LambdaEnv` is now also an effect in `IO`, it
    * becomes a step in your program.
    */
  def handler = for {
    entrypoint <- Resource
      .eval(Random.scalaUtilRandom[IO])
      .flatMap(implicit r => XRay.entryPoint[IO]())
    client <- EmberClientBuilder.default[IO].build
  } yield {
    implicit env => // the LambdaEnv provides access to the event and context

      // a middleware to add tracing to any handler
      // it extracts the kernel from the event and adds tags derived from the context
      TracedHandler(entrypoint) { implicit trace =>
        val tracedClient = NatchezMiddleware.client(client)

        // a "middleware" that converts an HttpRoutes into a ApiGatewayProxyHandler
        ApiGatewayProxyHandler(MwaaRoutes.build[IO](dagState[IO](tracedClient)))
      }
  }

  def dagState[F[_]: Async](client: Client[F])(
      dagId: String,
      executionDate: String
  ): F[String] = {
    implicit val dsl = Http4sDsl[F]
    import dsl._

    val mwaaEnvName = JsProcess.env("MWAA_ENV_NAME").getOrElse("my-mwaa")

    for {
      tokenResponse <- Async[F].fromFuture(
        Concurrent[F].delay {
          val mwaa = new MWAA()
          println(s"env: $mwaaEnvName")
          mwaa
            .createCliToken(CreateCliTokenRequest(mwaaEnvName))
            .promise()
            .toFuture
        }
      )
      hostname <- Async[F].fromEither(
        Uri
          .fromString(
            s"https://${tokenResponse.WebServerHostname}/aws_mwaa/cli"
          )
          .leftMap(pf =>
            new RuntimeException(
              "Failed to parse web server hostname",
              pf.getCause()
            )
          )
      )
      token <- Async[F].fromEither(
        tokenResponse.CliToken.toRight(
          new RuntimeException("CLI Token is empty")
        )
      )
      cmd = s"tasks states-for-dag-run -o json $dagId $executionDate"
      request =
        Request[F](
          uri = hostname,
          method = POST
        ).withEntity(cmd)
          .withHeaders(
            Authorization(Credentials.Token(AuthScheme.Bearer, token)),
            `Content-Type`(MediaType.text.plain)
          )
      _ <- Async[F].delay(println(s"request: $request, $cmd"))
      body <- client.fetchAs[String](request)
      _ <- Async[F].delay(
        println(
          s"body ${if (body.length > 100) "<truncated>" else ""}: ${body
            .take(100)}"
        )
      )
      outputs <- Async[F].fromEither(
        parse(body)
          .flatMap(_.as[Map[String, String]])
          .leftMap(e =>
            new RuntimeException(
              "Failed to parse MWAA CLI response body",
              e.getCause()
            )
          )
      )
      _ <-
        outputs
          .get("stderr")
          .toLeft(()) match {
          case Left(e) if e.trim.nonEmpty =>
            Stream(e)
              .through(fs2.text.base64.decode[F])
              .compile
              .toList
              .flatMap { e =>
                val mwaaError =
                  new String(e.toArray)
                Async[F]
                  .raiseError[Unit](
                    new RuntimeException(
                      s"MWAA returned error:\n$mwaaError"
                    )
                  )
              }
          case _ => Async[F].unit
        }
      stdout = outputs
        .get("stdout")
        .toRight(
          new RuntimeException(
            "'stdout' JSON property is not found in MWAA API respone body"
          )
        )

      out <- Async[F].fromEither(stdout)
      decoded = Stream(out).through(fs2.text.base64.decode[F])
      res <- decoded.compile.toList.map(_.mkString)
    } yield res
  }

}

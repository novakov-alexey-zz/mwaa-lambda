import cats.effect._
import cats.implicits._
import cats.effect.std.Random
import feral.lambda._
import feral.lambda.events._
import feral.lambda.http4s._
import natchez.Trace
import natchez.http4s.NatchezMiddleware
import natchez.xray.XRay
import org.http4s.HttpRoutes
import org.http4s.client.Client
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.Request
import org.http4s.Credentials
import org.http4s.AuthScheme
import _root_.facade.amazonaws.services.mwaa.MWAA
import _root_.facade.amazonaws.services.mwaa.CreateCliTokenRequest
import org.http4s.Uri
import fs2.Stream
import fs2.text.utf8

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
        ApiGatewayProxyHandler(myRoutes[IO](tracedClient))
      }
  }

  def myRoutes[F[_]: Trace: Async](
      client: Client[F]
  ): HttpRoutes[F] = {
    implicit val dsl = Http4sDsl[F]
    import dsl._
    import org.http4s.headers._
    import org.http4s.MediaType

    val routes = HttpRoutes.of[F] { case GET -> Root / "wf" / dagId =>
      val tokenResponse = Async[F].fromFuture(
        Concurrent[F].delay {
          val mwaa = new MWAA()
          mwaa
            .createCliToken(CreateCliTokenRequest("dev-datalake"))
            .promise()
            .toFuture
        }
      )

      for {
        r <- tokenResponse
        hostname <- Async[F].fromEither(
          Uri
            .fromString(s"https://${r.WebServerHostname}/aws_mwaa/cli")
            .leftMap(pf =>
              new RuntimeException(
                "Failed to pasre web server hostname",
                pf.getCause()
              )
            )
        )
        token <- Async[F].fromEither(
          r.CliToken.toRight(new RuntimeException("CLI Token is empty"))
        )
        cmd = s"tasks states-for-dag-run -o json $dagId"
        request =
          Request[F](
            uri = hostname,
            method = POST,
            body = Stream(cmd).through(utf8.encode)
          )
            .putHeaders(
              Authorization(Credentials.Token(AuthScheme.Bearer, token)),
              `Content-Type`(MediaType.text.plain)
            )
        states <- client.expect[String](request)
        res <- Ok(states)
      } yield res
    }

    NatchezMiddleware.server(routes)
  }

}

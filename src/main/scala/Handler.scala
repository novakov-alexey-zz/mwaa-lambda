import scalajs.js
import js.annotation.JSExportTopLevel
import net.exoego.facade.aws_lambda._
import scala.concurrent.Future
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._
import facade.amazonaws.services.mwaa.MWAA
import facade.amazonaws.services.mwaa.CreateCliTokenRequest
import scala.concurrent.duration._

import io.bullet.borer.Json
import io.bullet.borer.derivation.MapBasedCodecs._
import sttp.client3._

final case class TriggerDag(dagId: String, execDate: Option[String])

object TriggerDag {
  implicit val triggerDagCodec = deriveCodec[TriggerDag]
}

object Handler {
  val backend = FetchBackend()

  def main(
      event: APIGatewayProxyEventV2
  ): Future[APIGatewayProxyStructuredResultV2] = {
    val mwaa = new MWAA()
    val token = mwaa
      .createCliToken(CreateCliTokenRequest("my-mwaa-env"))
      .promise()
      .toFuture

    for {
      response <- token
      body = event.body.getOrElse("")
      _ = println(s"body = $body")
      params =
        Json
          .decode(body.getBytes("UTF8"))
          .to[TriggerDag]
          .value
      _ = println(s"params = $params")
      authToken = s"Bearer ${response.CliToken}"
      hostname = s"https://${response.WebServerHostname}/aws_mwaa/cli"
      data =
        s"dags trigger ${params.execDate.map(d => s"--exec-date $d").getOrElse("")} ${params.dagId}"
      _ = println(s"authToken: $authToken")
      _ = println(s"hostname: $hostname")
      _ = println(s"data: $data")

      triggered <- basicRequest
        .post(uri"$hostname")
        .body(data)
        .header("Authorization", authToken)
        .header("Content-Type", "text/plain")
        .readTimeout(20.seconds)
        .send(backend)
    } yield APIGatewayProxyStructuredResultV2(
      statusCode = 200,
      body = s"""${triggered.body}""",
      headers = js.defined(js.Dictionary("Content-Type" -> "application/json"))
    )
  }

  @JSExportTopLevel(name = "handler")
  val handler: js.Function2[APIGatewayProxyEventV2, Context, js.Promise[
    APIGatewayProxyStructuredResultV2
  ]] = { (event: APIGatewayProxyEventV2, _: Context) =>
    import js.JSConverters._
    main(event).toJSPromise
  }

}

object TestApp {
  val body = Json
    .encode(TriggerDag("mydag", Some("2021-07-25T11:07:00")))
    .toUtf8String
  val event =
    js.Dynamic
      .literal(body = body)
      .asInstanceOf[APIGatewayProxyEventV2]
  val response = Handler.main(event)
  response.foreach(r => println(r.body))
  response.failed.foreach { t =>
    println(s"Handler failed: $t")
    t.printStackTrace()
  }
}

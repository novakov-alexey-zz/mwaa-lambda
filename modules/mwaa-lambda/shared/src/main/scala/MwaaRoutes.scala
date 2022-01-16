import natchez.Trace
import cats.effect._
import org.http4s.dsl.Http4sDsl
import org.http4s.HttpRoutes
import org.http4s.Request
import natchez.http4s.NatchezMiddleware

object MwaaRoutes {

  def build[F[_]: Trace: Async](
      dagStateHandler: Function2[String, String, F[String]]
  ): HttpRoutes[F] = {
    implicit val dsl = Http4sDsl[F]
    import dsl._

    val routes = HttpRoutes.of[F] {
      case GET -> Root / "wf" / dagId / "executionDate" / executionDate =>
        Ok(dagStateHandler(dagId, executionDate))
    }

    NatchezMiddleware.server(routes)
  }

}

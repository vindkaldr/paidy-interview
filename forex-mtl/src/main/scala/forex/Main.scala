package forex

import cats.effect._
import dev.profunktor.redis4cats.effect.Log
import forex.config._
import fs2.Stream
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.client.Client

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    BlazeClientBuilder[IO](global).resource.use { httpClient =>
      new Application[IO].stream(executionContext, httpClient).compile.drain.as(ExitCode.Success)
    }
  }

}

class Application[F[_]: ConcurrentEffect: Timer](implicit cs: ContextShift[F]) {
  implicit val log: Log[F] = Log.NoOp.instance

  def stream(ec: ExecutionContext, client: Client[F]): Stream[F, Unit] =
    for {
      config <- Config.stream("app")
      module = new Module[F](config, client)
      _ <- BlazeServerBuilder[F](ec)
            .bindHttp(config.http.port, config.http.host)
            .withHttpApp(module.httpApp)
            .serve
    } yield ()

}

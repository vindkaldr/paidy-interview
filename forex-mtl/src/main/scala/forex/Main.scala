package forex

import cats.Parallel
import cats.effect._
import dev.profunktor.redis4cats.effect.Log
import forex.config._
import fs2.Stream
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.client.Client

import scala.concurrent.ExecutionContext

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    for {
      config <- Config.stream[IO]("app").compile.lastOrError
      exitCode <- new Application[IO].stream(executionContext, config).compile.drain.as(ExitCode.Success)
    } yield exitCode
  }
}

class Application[F[_]: ConcurrentEffect: Parallel: Timer](implicit cs: ContextShift[F]) {
  implicit val log: Log[F] = Log.NoOp.instance

  def httpClient(context: ExecutionContext): Resource[F, Client[F]] = BlazeClientBuilder[F](context).resource

  def stream(context: ExecutionContext, config: ApplicationConfig): Stream[F, ExitCode] =
    Stream.resource(httpClient(context)).flatMap { httpClient =>
      BlazeServerBuilder[F](context)
        .bindHttp(config.http.port, config.http.host)
        .withHttpApp(new Module[F](config, httpClient).httpApp)
        .serve
    }
}

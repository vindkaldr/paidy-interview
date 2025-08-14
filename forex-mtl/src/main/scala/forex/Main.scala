package forex

import cats.effect._
import dev.profunktor.redis4cats.effect.Log
import forex.config._
import fs2.Stream
import org.http4s.blaze.server.BlazeServerBuilder

import scala.concurrent.ExecutionContext

object Main extends IOApp {
  implicit val cs: ContextShift[IO] = IO.contextShift(executionContext)

  override def run(args: List[String]): IO[ExitCode] =
    new Application[IO].stream(executionContext).compile.drain.as(ExitCode.Success)

}

class Application[F[_]: ConcurrentEffect: Timer](implicit cs: ContextShift[F]) {
  implicit val log: Log[F] = Log.NoOp.instance

  def stream(ec: ExecutionContext): Stream[F, Unit] =
    for {
      config <- Config.stream("app")
      module = new Module[F](config)
      _ <- BlazeServerBuilder[F](ec)
            .bindHttp(config.http.port, config.http.host)
            .withHttpApp(module.httpApp)
            .serve
    } yield ()

}

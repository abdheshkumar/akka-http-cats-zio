package com.abtech

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.marshalling.{
  Marshaller,
  Marshalling,
  PredefinedToResponseMarshallers,
  ToResponseMarshaller
}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import zio.console.Console
import zio._

import scala.concurrent.Promise

/**
  * Describes how to map a custom domain error into an HTTP server response
  */
trait ErrorResponse[E] {
  def toHttpResponse(e: E): HttpResponse
}

object ErrorResponse {
  def apply[E](implicit E: ErrorResponse[E]): ErrorResponse[E] = E

  def from[E](fun: E => HttpResponse): ErrorResponse[E] = (e: E) => fun(e)
}

trait ZIOSupport extends ZIOSupportInstance2 {

  implicit def zioUIOToMarshaller[A](implicit
      _marshaller: ToResponseMarshaller[A]
  ): ToResponseMarshaller[UIO[A]] =
    Marshaller { implicit ec => zio =>
      val result =
        zio.flatMap(a => IO.fromFuture(implicit ec => _marshaller(a)))
      val p = Promise[List[Marshalling[HttpResponse]]]()
      unsafeRunAsync(result)(
        _.fold(e => p.failure(e.squash), res => p.success(res))
      )
      p.future
    }
}
trait ZIOSupportInstance2 extends BootstrapRuntime {
  implicit def zioIOToMarshaller[A, E](implicit
      ma: ToResponseMarshaller[A],
      me: ToResponseMarshaller[E]
  ): ToResponseMarshaller[IO[E, A]] =
    Marshaller { implicit ec => zioValue =>
      val result = zioValue.foldM(
        e => IO.fromFuture(implicit ec => me(e)),
        a => IO.fromFuture(implicit ec => ma(a))
      )

      val promise = Promise[List[Marshalling[HttpResponse]]]()
      unsafeRunAsync(result)(
        _.fold(e => promise.failure(e.squash), s => promise.success(s))
      )
      promise.future
    }

  implicit def zioSupportErrorMarshaller[E: ErrorResponse]
      : Marshaller[E, HttpResponse] =
    Marshaller { implicit ec => a =>
      PredefinedToResponseMarshallers.fromResponse(
        ErrorResponse[E].toHttpResponse(a)
      )
    }
}

object HttpServerRoutingMinimal extends ZIOSupport with zio.App {

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {

    implicit val system = ActorSystem(Behaviors.empty, "my-system")

    implicit val executionContext = system.executionContext

    sealed trait DomainError
    case object FatalError extends DomainError
    case object BadData extends DomainError

    val route =
      path("hello") {
        get {
          val io = UIO.succeed("Hello")
          complete(
            io
          )
        }
      } ~ pathPrefix("task") {
        get {
          val res: Task[String] = ZIO.fail(new Throwable("error"))
          complete(res)
        }
      } ~ pathPrefix("uio") {
        get {
          val res: Task[String] = ZIO.succeed("OK")
          complete(res)
        }
      } ~ pathPrefix("domain_error") {
        get {
          implicit val errorRes: ErrorResponse[DomainError] =
            ErrorResponse.from(_ match {
              case FatalError => HttpResponse(StatusCodes.InternalServerError)
              case BadData =>
                HttpResponse(
                  StatusCodes.BadRequest,
                  entity = HttpEntity.apply("Bad input")
                )
            })
          val res: IO[DomainError, String] = ZIO.fail(BadData)
          complete(res)
        }
      }

    val app = (Console.live >>> ZManaged
      .make(
        ZIO.fromFuture(_ =>
          Http().newServerAt("localhost", 8081).bind(route)
        ) <* console.putStrLn(
          s"Server online at http://localhost:8080"
        )
      )(serverBinding =>
        ZIO.fromFuture(_ => serverBinding.unbind()).orDie *> console
          .putStrLn("Server terminated..")
      )
      .toLayer)

    app.launch
      .foldM(
        ex => console.putStrLn(ex.getMessage) *> IO.succeed(ExitCode.failure),
        _ =>
          console.putStrLn("Application exit") *> IO
            .succeed(ExitCode.success)
      )

  }

}

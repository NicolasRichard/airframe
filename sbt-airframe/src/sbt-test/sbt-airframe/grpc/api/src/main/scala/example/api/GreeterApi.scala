package example.api

import wvlet.airframe.http._
import wvlet.airframe.rx.Rx

@RPC
trait GreeterApi {
  def sayHello(message: String): String
  def serverStreaming(message: String): Rx[String]
  def clientStreaming(message: Rx[String]): String
  def bidiStreaming(message: Rx[String]): Rx[String]
}

object GreeterApi extends RxRouterProvider {
  override def router: RxRouter = RxRouter.of[GreeterApi]
}

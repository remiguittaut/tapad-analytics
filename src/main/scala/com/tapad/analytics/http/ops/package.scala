package com.tapad.analytics.http

import zio.http.model.{ HeaderNames, HeaderValues, Headers, Status }
import zio.http.{ Body, Request, Response }

package object ops {

  implicit class ResponseOps(obj: Response.type) {

    def badRequest(text: CharSequence): Response =
      Response(
        status = Status.BadRequest,
        body = Body.fromCharSequence(text),
        headers = Headers(HeaderNames.contentType, HeaderValues.textPlain)
      )
  }

  implicit class RequestOps(req: Request) {

    def getParam[A](name: String)(implicit parser: Parser[String, A]): Option[A] =
      for {
        param  <- req.url.queryParams.get(name).flatMap(_.headOption)
        parsed <- parser.parse(param)
      } yield parsed

    def getParamFirstOf[A](name: String, otherNames: String*)(implicit
      parser: Parser[String, A]
    ): Option[(String, A)] =
      for {
        firstParam <- (name +: otherNames).find(req.url.queryParams.keySet.contains(_))
        param      <- req.url.queryParams.get(firstParam).flatMap(_.headOption)
        parsed     <- parser.parse(param)
      } yield firstParam -> parsed
  }
}

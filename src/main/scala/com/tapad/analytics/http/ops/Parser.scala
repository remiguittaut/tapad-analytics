package com.tapad.analytics.http.ops

import java.time.Instant
import java.util.UUID

import scala.util.Try

trait Parser[-A, +B] { self =>
  def parse(a: A): Option[B]

  final def map[C](f: B => C): Parser[A, C]              = (a: A) => self.parse(a).map(f)
  final def contramap[C](f: C => A): Parser[C, B]        = (c: C) => self.parse(f(c))
  final def flatMap[C](that: Parser[B, C]): Parser[A, C] = (a: A) => self.parse(a).flatMap(that.parse)
}

object Parser {
  def apply[A, B](implicit ab: Parser[A, B]): Parser[A, B] = ab

  implicit val string: Parser[String, String] = (in: String) => Option(in)

  implicit val string2Boolean: Parser[String, Boolean] = (in: String) => in.toBooleanOption
  implicit val string2Byte: Parser[String, Byte]       = (in: String) => in.toByteOption
  implicit val string2Short: Parser[String, Short]     = (in: String) => in.toShortOption
  implicit val string2Int: Parser[String, Int]         = (in: String) => in.toIntOption
  implicit val string2Long: Parser[String, Long]       = (in: String) => in.toLongOption
  implicit val string2Uuid: Parser[String, UUID]       = (in: String) => Try(UUID.fromString(in)).toOption

  implicit val instantFromStringEpoch: Parser[String, Instant] =
    string2Long.flatMap((long: Long) => Try(Instant.ofEpochMilli(long)).toOption)
}

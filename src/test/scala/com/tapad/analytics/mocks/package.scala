package com.tapad.analytics

import scala.reflect.ClassTag

import zio.{ Chunk, Ref, Tag, UIO, URIO, ZIO }

package object mocks {

  // Super lightweight mocking framework

  trait Invocation[Action <: Invocation[Action, In], In] {
    val in: In
  }

  trait Mock {
    protected val invocationRef: Ref[Chunk[Invocation[_, _]]]

    lazy val invocations: UIO[Chunk[Invocation[_, _]]] = invocationRef.get

    lazy val wasNotInvoked: UIO[Boolean] = invocations.map(_.isEmpty)

    lazy val wasInvokedOnce: UIO[Boolean] = invocations.map(_.length == 1)

    def actionWasNotInvoked[Action <: Invocation[_, _]](implicit ct: ClassTag[Action]): UIO[Boolean] =
      invocations
        .map(_.collect({
          // sometimes, you gotta do what you gotta do (type erasure). safe with the classTag though

          // scalafix:off
          case invocation if ct.runtimeClass.isInstance(invocation) => invocation.asInstanceOf[Action]
          // scalafix:on
        }).isEmpty)

    // implemented first with a partially applied to avoid having to specify both
    // type params (just the Action), but didn't manage to work-around having
    // to do an unsafe cast

    def wasInvokedWith[Action <: Invocation[Action, In], In](in: In)(implicit ct: ClassTag[Action]): UIO[Boolean] =
      invocations.map(all =>
        all
          .collect({
            // sometimes, you gotta do what you gotta do (type erasure). safe with the classTag though

            // scalafix:off
            case invocation if ct.runtimeClass.isInstance(invocation) => invocation.asInstanceOf[Action]
            // scalafix:on
          })
          .exists(_.in == in)
      )
  }

  trait MockHelpers[A <: Mock] {

    def invocations(implicit tag: Tag[A]): URIO[A, Chunk[Invocation[_, _]]] = ZIO.serviceWithZIO[A](_.invocations)

    def wasNotInvoked(implicit tag: Tag[A]): URIO[A, Boolean] = ZIO.serviceWithZIO[A](_.wasNotInvoked)

    def wasInvokedOnce(implicit tag: Tag[A]): URIO[A, Boolean] = ZIO.serviceWithZIO[A](_.wasInvokedOnce)

    def actionWasNotInvoked[Action <: Invocation[_, _]](implicit tag: Tag[A], ct: ClassTag[Action]): URIO[A, Boolean] =
      ZIO.serviceWithZIO[A](_.actionWasNotInvoked[Action])

    // implemented first with a partially applied to avoid having to specify both
    // type params (just the Action), but didn't manage to work-around having
    // to do an unsafe cast

    def wasInvokedWith[Action <: Invocation[Action, In], In](
      in: In
    )(implicit tag: Tag[A], ct: ClassTag[Action]): URIO[A, Boolean] =
      ZIO.serviceWithZIO[A](_.wasInvokedWith[Action, In](in))
  }
}

package io.estatico.macros

import org.scalacheck.Prop.forAll
import org.scalacheck.{Arbitrary, Gen, Properties}

@ADT trait Maybe[+A] {
  Just(a: A)
  Nix

  def isJust: Boolean = this match {
    case Just(_) => true
    case Nix => false
  }

  def isNix: Boolean = !isJust

  def map[B](f: A => B): Maybe[B] = this match {
    case Just(x) => Just(f(x))
    case Nix => Nix
  }

  def toOption: Option[A] = this match {
    case Just(x) => Some(x)
    case Nix => None
  }
}

object Maybe {
  def apply[A](a: A): Maybe[A] = {
    if (a == null) Nix else Just(a)
  }

  def fromOption[A](oa: Option[A]): Maybe[A] = oa match {
    case Some(a) => Just(a)
    case None => Nix
  }
}

object ADTSpec extends Properties("@ADT macro") {

  import Maybe.ctors._

  property("generates Just constructor") = forAll { (x: Int) =>
    Just(x) == Just(x)
  }

  property("generates Nix singleton") = {
    Nix == Nix
  }

  property("covariant Just constructor") = {
    (Just("foo"): Maybe.Just[Any]).isJust
  }

  property("retains trait methods") = forAll { (m: Maybe[Int]) =>
    m.map(identity) == identity(m)
  }

  property("retains companion methods") = forAll { (m: Maybe[Int]) =>
    Maybe.fromOption(m.toOption) == m
  }

  implicit def arbMaybe[A : Arbitrary]: Arbitrary[Maybe[A]] = {
    Arbitrary(implicitly[Arbitrary[Boolean]].arbitrary.flatMap {
      case true => implicitly[Arbitrary[A]].arbitrary.map(Just(_))
      case false => Gen.const(Nix)
    })
  }
}

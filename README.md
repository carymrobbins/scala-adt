# scala-adt

Haskell-style Algebraic Data Types for Scala

## Usage

Scala supports ADTs by using sealed traits and case classes and objects. For instance, 
if we wanted to implement a `Maybe` type, we could do something like the following -

```scala
sealed trait Maybe[+A]
final case class Just[+A](a: A) extends Maybe[A]
case object Nix extends Maybe[Nothing]
```

This can get pretty verbose, especially when there are more constructors.

In Haskell, we can define an ADT using the following syntax -

```haskell
data Maybe a = Just a | Nix
```

Thanks to the `@ADT` macro, we can get pretty close to the Haskell syntax -

```scala
@ADT trait Maybe[A] { Just(a: A); Nix }
```

This will generate code semantically equivalent to the following -

```scala
sealed trait Maybe[+A]
object Maybe {
  final case class Just[+A](a: A) extends Maybe[A]
  case object Nix extends Maybe[Nothing]
  
  object ctors {
    val Just = Maybe.Just
    val Nix = Maybe.Nix
  }
}
```

You can then use your new ADT just as you'd expect. You can add methods to the `trait`
or companion `object` as needed. Constructors are inferred as the first statements defined
in the `trait`.

```scala
@ADT trait Maybe[A] {
  Just(a: A)
  Nix

  def map[B](f: A => B): Maybe[B] = this match {
    case Just(x) => Just(f(x))
    case Nix => Nix
  }
}

object Maybe {
  def apply[A](a: A): Maybe[A] = {
    if (a == null) Nix else Just(a)
  }
}

// Optional, used to import constructors to avoid always qualifying them.
import Maybe.ctors._

object Example {
  def run() = {
    val m1 = Maybe(1)
    println(m1.map(_ + 2))
    // prints "Just(3)"

    val m2: Maybe[Int] = Nix
    m2 match {
      case Just(_) => println(s"Found $x")
      case Nix => println("Not found")
    }
    // prints "Not found"
  }
}
```

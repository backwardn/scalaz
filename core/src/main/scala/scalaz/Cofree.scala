package scalaz

/** A cofree comonad for some functor `S`, i.e. an `S`-branching stream. */
final case class Cofree[S[_], A](head: A, tail: S[Cofree[S, A]]) {

  /** Alias for `head`, for compatibility with Scalaz 6 */
  final def extract: A = head

  /** Alias for `head`, for compatibility with Scalaz 6 */
  final def copure: A = head

  /** Alias for `tail`, for compatibility with Scalaz 6 */
  final def out: S[Cofree[S, A]] = tail

  final def map[B](f: A => B)(implicit S: Functor[S]): Cofree[S, B] =
    applyCofree(f, _ map f)

  /** Alias for `extend` */
  final def =>>[B](f: Cofree[S, A] => B)(implicit S: Functor[S]): Cofree[S, B] = this extend f

  /** Redecorates this structure with a computation whose context is the entire structure under that value. */
  final def extend[B](f: Cofree[S, A] => B)(implicit S: Functor[S]): Cofree[S, B] =
    applyTail(f(this), _ extend f)

  /** Folds over this cofree structure, returning all the intermediate values in a new structure. */
  def scanr[B](g: (A, S[Cofree[S, B]]) => B)(implicit S: Functor[S]): Cofree[S, B] = {
    val qs = S.map(tail)(_ scanr g)
    Cofree(g(head, qs), qs)
  }

  /** Redecorates the structure with values representing entire substructures. */
  final def duplicate(implicit S: Functor[S]): Cofree[S, Cofree[S, A]] =
    applyTail(this, _.duplicate)

  /** Returns the components of this structure in a tuple. */
  final def toPair: (A, S[Cofree[S, A]]) = (head, tail)

  /** Changes the branching functor by the given natural transformation. */
  final def mapBranching[T[_]](f: S ~> T)(implicit S: Functor[S], T: Functor[T]): Cofree[T, A] =
    Cofree(head, f(S.map(tail)(_ mapBranching f)))

  /** Modifies the first branching with the given natural transformation. */
  final def mapFirstBranching(f: S ~> S): Cofree[S, A] =
    Cofree(head, f(tail))

  /** Injects a constant value into this structure. */
  final def inject[B](b: B)(implicit S: Functor[S]): Cofree[S, B] =
    applyTail(b, _ inject b)

  /** Applies `f` to the head and `g` through the tail. */
  final def applyCofree[B](f: A => B, g: Cofree[S, A] => Cofree[S, B])(implicit S: Functor[S]): Cofree[S, B] =
    Cofree(f(head), S.map(tail)(g))

  /** Replaces the head with `b` and applies `g` through the tail. */
  final def applyTail[B](b: B, g: Cofree[S, A] => Cofree[S, B])(implicit S: Functor[S]): Cofree[S, B] =
    applyCofree(x => b, g)

  /** Applies a function `f` to a value in this comonad and a corresponding value in the dual monad, annihilating both. */
  final def zapWith[G[_], B, C](bs: Free[G, B])(f: (A, B) => C)(implicit S: Functor[S], G: Functor[G], d: Zap[S, G]): C =
    Zap.comonadMonadZap.zapWith(this, bs)(f)

  /** Applies a function in a monad to the corresponding value in this comonad, annihilating both. */
  final def zap[G[_], B](fs: Free[G, A => B])(implicit S: Functor[S], G: Functor[G], d: Zap[S, G]): B =
    zapWith(fs)((a, f) => f(a))
}

object Cofree extends CofreeInstances with CofreeFunctions


trait CofreeFunctions {

  private[scalaz] final type CofreeZip[F[_], A] = Cofree[F, A] @@ Tags.Zip

  private[scalaz] final def CofreeZip[F[_], A](head: A, tail: F[Cofree[F, A]]): CofreeZip[F, A] =
    Tags.Zip(Cofree(head, tail))

  /** Cofree corecursion. */
  def unfoldC[F[_], A](a: A)(f: A => F[A])(implicit F: Functor[F]): Cofree[F, A] =
    Cofree(a, F.map(f(a))(unfoldC(_)(f)))

  def unfold[F[_], A, B](b: B)(f: B => (A, F[B]))(implicit F: Functor[F]): Cofree[F, A] = {
    val (a, fb) = f(b)
    Cofree(a, F.map(fb)(unfold(_)(f)))
  }
}

import Cofree.CofreeZip

sealed abstract class CofreeInstances4 {
  /** low priority `Foldable1` instance */
  implicit def cofreeFoldable[F[_]: Foldable]: Foldable1[({type λ[α] = Cofree[F, α]})#λ] =
    new CofreeFoldable[F]{
      def F = implicitly
    }
}

sealed abstract class CofreeInstances3 extends CofreeInstances4 {
  /** low priority `Traverse1` instance */
  implicit def cofreeTraverse[F[_]: Traverse]: Traverse1[({type λ[α] = Cofree[F, α]})#λ] =
    new CofreeTraverse[F]{
      def F = implicitly
    }

  implicit def cofreeZipFunctor[F[_]: Functor]: Functor[({type λ[α] = CofreeZip[F, α]})#λ] =
    new CofreeZipFunctor[F]{
      def F = implicitly
    }
}

sealed abstract class CofreeInstances2 extends CofreeInstances3 {
  /** high priority `Foldable1` instance. more efficient */
  implicit def cofreeFoldable1[F[_]: Foldable1]: Foldable1[({type λ[α] = Cofree[F, α]})#λ] =
    new CofreeFoldable1[F]{
      def F = implicitly
    }

  implicit def cofreeBind[F[_]: Plus: Functor]: Bind[({type λ[α] = Cofree[F, α]})#λ] =
    new CofreeBind[F]{
      def F = implicitly
      def G = implicitly
    }
}

sealed abstract class CofreeInstances1 extends CofreeInstances2 {
  /** high priority `Traverse1` instance. more efficient */
  implicit def cofreeTraverse1[F[_]: Traverse1]: Traverse1[({type λ[α] = Cofree[F, α]})#λ] =
    new CofreeTraverse1[F]{
      def F = implicitly
    }

  implicit def cofreeZipApply[F[_]: Apply]: Apply[({type λ[α] = CofreeZip[F, α]})#λ] =
    new CofreeZipApply[F]{
      def F = implicitly
    }
}

sealed abstract class CofreeInstances0 extends CofreeInstances1 {
  implicit def cofreeZipApplicative[F[_]: Applicative]: Applicative[({type λ[α] = CofreeZip[F, α]})#λ] =
    new CofreeZipApplicative[F]{
      def F = implicitly
    }

  implicit def cofreeMonad[F[_]: PlusEmpty: Functor]: Monad[({type λ[α] = Cofree[F, α]})#λ] =
    new CofreeMonad[F]{
      def F = implicitly
      def G = implicitly
    }

  implicit def cofreeEqual[A, F[_]](implicit A: Equal[A], F: Equal ~> ({type λ[α] = Equal[F[α]]})#λ): Equal[Cofree[F, A]] =
    Equal.equal{ (a, b) =>
      A.equal(a.head, b.head) && F(cofreeEqual[A, F]).equal(a.tail, b.tail)
    }

  implicit def cofreeZipEqual[A, F[_]](implicit A: Equal[A], F: Equal ~> ({type λ[α] = Equal[F[α]]})#λ): Equal[CofreeZip[F, A]] =
    Tag.subst(cofreeEqual[A, F])
}

sealed abstract class CofreeInstances extends CofreeInstances0 {
  implicit def cofreeComonad[S[_]: Functor]: Comonad[({type f[x] = Cofree[S, x]})#f] = new CofreeComonad[S] {
    def F = implicitly
  }
}

private trait CofreeComonad[S[_]] extends Comonad[({type f[x] = Cofree[S, x]})#f] {
  implicit def F: Functor[S]

  def copoint[A](p: Cofree[S, A]) = p.head

  override def cojoin[A](a: Cofree[S, A]) = a.duplicate

  override final def map[A, B](fa: Cofree[S, A])(f: A => B) = fa map f

  def cobind[A, B](fa: Cofree[S, A])(f: (Cofree[S, A]) => B) = fa extend f
}

private trait CofreeZipFunctor[F[_]] extends Functor[({type λ[α] = CofreeZip[F, α]})#λ]{
  implicit def F: Functor[F]

  override final def map[A, B](fa: CofreeZip[F, A])(f: A => B) = Tags.Zip(fa map f)
}

private trait CofreeZipApply[F[_]] extends Apply[({type λ[α] = CofreeZip[F, α]})#λ] with CofreeZipFunctor[F]{
  implicit def F: Apply[F]

  override final def ap[A, B](fa: => CofreeZip[F, A])(f: => CofreeZip[F, A => B]): CofreeZip[F, B] =
    CofreeZip(
      f.head(fa.head),
      F.apply2(Tags.Zip.subst(fa.tail), Tags.Zip.subst(f.tail))(ap(_)(_))
    )
}

private trait CofreeZipApplicative[F[_]] extends Applicative[({type λ[α] = CofreeZip[F, α]})#λ] with CofreeZipApply[F]{
  implicit def F: Applicative[F]

  def point[A](a: => A) = CofreeZip(a, F.point(point(a)))
}

private trait CofreeBind[F[_]] extends Bind[({type λ[α] = Cofree[F, α]})#λ] with CofreeComonad[F]{
  implicit def F: Functor[F]
  implicit def G: Plus[F]

  def bind[A, B](fa: Cofree[F, A])(f: A => Cofree[F, B]): Cofree[F, B] = {
    val Cofree(h, t) = f(fa.head)
    Cofree(h, G.plus(t, F.map(fa.tail)(bind(_)(f))))
  }
}

private trait CofreeMonad[F[_]] extends Monad[({type λ[α] = Cofree[F, α]})#λ] with CofreeBind[F]{
  implicit def G: PlusEmpty[F]

  def point[A](a: => A): Cofree[F, A] = Cofree(a, G.empty)
}

private trait CofreeFoldable[F[_]] extends Foldable1[({type λ[α] = Cofree[F, α]})#λ]{
  implicit def F: Foldable[F]

  override final def foldMap[A, B](fa: Cofree[F, A])(f: A => B)(implicit M: Monoid[B]): B =
    M.append(f(fa.head), F.foldMap(fa.tail)(foldMap(_)(f)))

  override final def foldRight[A, B](fa: Cofree[F, A], z: => B)(f: (A, => B) => B): B =
    f(fa.head, F.foldRight(fa.tail, z)(foldRight(_, _)(f)))

  override final def foldLeft[A, B](fa: Cofree[F, A], z: B)(f: (B, A) => B): B =
    F.foldLeft(fa.tail, f(z, fa.head))((b, c) => foldLeft(c, b)(f))

  override final def foldMapLeft1[A, B](fa: Cofree[F, A])(z: A => B)(f: (B, A) => B): B =
    F.foldLeft(fa.tail, z(fa.head))((b, c) => foldLeft(c, b)(f))

  override def foldMapRight1[A, B](fa: Cofree[F, A])(z: A => B)(f: (A, => B) => B): B = {
    import std.option.none
    foldRight(fa, none[B]){
      case (l, None) => Some(z(l))
      case (l, Some(r)) => Some(f(l, r))
    }.getOrElse(sys.error("foldMapRight1"))
  }

  override def foldMap1[A, B](fa: Cofree[F, A])(f: A => B)(implicit S: Semigroup[B]): B = {
    val h = f(fa.head)
    F.foldMap1Opt(fa.tail)(foldMap1(_)(f)).map(S.append(h, _)).getOrElse(h)
  }
}

private trait CofreeFoldable1[F[_]] extends Foldable1[({type λ[α] = Cofree[F, α]})#λ] with CofreeFoldable[F]{
  implicit def F: Foldable1[F]

  override final def foldMap1[A, B](fa: Cofree[F, A])(f: A => B)(implicit S: Semigroup[B]): B =
    S.append(f(fa.head), F.foldMap1(fa.tail)(foldMap1(_)(f)))
}

private trait CofreeTraverse[F[_]] extends Traverse1[({type λ[α] = Cofree[F, α]})#λ] with CofreeFoldable[F] with CofreeComonad[F]{
  implicit def F: Traverse[F]

  override final def traverseImpl[G[_], A, B](fa: Cofree[F,A])(f: A => G[B])(implicit G: Applicative[G]): G[Cofree[F,B]] =
    G.apply2(f(fa.head), F.traverse(fa.tail)(traverse(_)(f)))(Cofree(_, _))

  override def traverse1Impl[G[_], A, B](fa: Cofree[F,A])(f: A => G[B])(implicit G: Apply[G]): G[Cofree[F,B]] =
    G.applyApplicative.traverse(fa.tail)(a => -\/(traverse1(a)(f)))
      .fold(ftl => G.apply2(f(fa.head), ftl)(Cofree(_, _)),
         tl => G.map(f(fa.head))(Cofree.apply(_, tl)))
}

private trait CofreeTraverse1[F[_]] extends Traverse1[({type λ[α] = Cofree[F, α]})#λ] with CofreeTraverse[F] with CofreeFoldable1[F]{
  implicit def F: Traverse1[F]

  override def traverse1Impl[G[_], A, B](fa: Cofree[F,A])(f: A => G[B])(implicit G: Apply[G]): G[Cofree[F,B]] =
    G.apply2(f(fa.head), F.traverse1(fa.tail)(traverse1(_)(f)))(Cofree(_, _))
}

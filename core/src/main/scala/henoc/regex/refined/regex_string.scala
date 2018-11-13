package henoc.regex.refined

import java.util.{Map => JMap}
import java.util.regex.{Pattern, PatternSyntaxException}

import eu.timepit.refined._
import eu.timepit.refined.string._
import eu.timepit.refined.api._
import eu.timepit.refined.api.Validate
import eu.timepit.refined.generic.Equal
import shapeless.Nat.{_0, _1}
import shapeless.Witness
import Evasion.ops._
import eu.timepit.refined.api.Validate.Plain
import eu.timepit.refined.boolean.And
import javax.script.ScriptEngineManager

import scala.util.Try

object regex_string {

  /**
    * Predicate that checks if the group count of a regex string satisfies P.
    */
  final case class GroupCount[P](p: P)

  /**
    * Predicate that checks if a regex string matches the `String` S.
    */
  final case class Matches[S](s: S)

  /**
    * Predicate that checks if a regex string contains the group name S.
    */
  final case class HasGroupName[S](s: S)

  /**
    * Predicate that checks if a regex string uses the match flags S. (S is `[idmsuxU]+`)
    */
  final case class MatchFlags[S](s: S)

  /**
    * Predicate that checks if a regex string is valid for JavaScript regex.
    */
  final case class JsRegex()

  /**
    * Predicate that checks if a regex string has no capturing groups.
    */
  type NoGroup = GroupCount[Equal[_0]]

  /**
    * Predicate that checks if a regex string has one capturing groups.
    */
  type OneGroup = GroupCount[Equal[_1]]

  /**
    * Predicate that checks if a regex string is JavaScript compatible.
    */
  type JsRegexCompatible = Regex And JsRegex

  object GroupCount {

    implicit def groupCountValidate[Predicate, RP](implicit vint: Validate.Aux[Int, Predicate, RP]):
      Validate.Aux[String, GroupCount[Predicate], GroupCount[vint.Res]] = {

      def helper[T](fn: T => Unit => Int) = new Validate[T, GroupCount[Predicate]] {
        override type R = GroupCount[vint.Res]

        override def validate(t: T): Res = {
          try {
            val r = vint.validate(fn(t)(()))
            Result.fromBoolean(r.isPassed, GroupCount(r))
          } catch {
            case _: PatternSyntaxException => Failed(null)
          }
        }

        override def showExpr(t: T): String = Try(fn(t)(())).fold(e => s"Pattern.compile: ${e.getMessage}", i => s"groupCount(/$t/): ${vint.showExpr(i)}")

      }

      helper[String](target => _ => groupCount(compile(target)))
    }
  }

  object Matches {

    implicit def matchesValidate[S <: String](implicit text: Witness.Aux[S]):
      Validate.Plain[String, Matches[S]] = {
      fromPredicateWithRegex(
        p => p.matcher(text.value).matches(),
        p => s"/$p/.matches($D${text.value}$D)",
        Matches(text.value)
      )
    }

  }

  object HasGroupName {

    implicit def hasGroupNameValidate[S <: String](implicit groupName: Witness.Aux[S]): Validate.Plain[String, HasGroupName[S]] =
      fromPredicateWithRegex(
        p => p.method[JMap[String, Int]]("namedGroups").containsKey(groupName.value),
        p => s"/$p/.hasGroupName($D${groupName.value}$D)",
        HasGroupName(groupName.value)
      )

  }

  object MatchFlags {

    implicit def matchFlagsValidate[S <: String](implicit flags: Witness.Aux[S]): Validate.Plain[String, MatchFlags[S]] = {
      val flagChars = refineV[MatchesRegex[W.`"[idmsuxU]+"`.T]].unsafeFrom(flags.value: String).value
      val flagsInt = compile(s"(?$flagChars)").flags()
      fromPredicateWithRegex(
        p => (p.flags() & flagsInt) == flagsInt,
        p => s"/$p/.useMatchFlag($D${flags.value}$D)",
        MatchFlags(flags.value)
      )
    }

  }

  object JsRegex {

    implicit def jsRegexValidate: Validate.Plain[String, JsRegex] = {
      val engine = new ScriptEngineManager().getEngineByName("js")
      Validate.fromPartial(t => engine.eval(s"/$t/"), "jsRegex", JsRegex())
    }

  }

  lazy val compile: String => Pattern = memoize(Pattern.compile)

  def groupCount(pattern : Pattern): Int = {
    pattern.matcher("").groupCount()
  }

  def fromPredicateWithRegex[P](f: Pattern => Boolean, showExpr: Pattern => String, p: P): Plain[String, P] = {
    val g = showExpr
    new Validate[String, P] {
      override type R = P
      override def validate(t: String): Res = {
        try {
          Result.fromBoolean(f(compile(t)), p)
        } catch {
          case _: PatternSyntaxException => Failed(p)
        }
      }
      override def showExpr(t: String): String = Try(compile(t)).fold(e => s"Pattern.compile: ${e.getMessage}", g)
    }
  }

}
package com.localplatform.transaction.smart.script

import com.localplatform.lang.ScriptVersion.Versions.V1
import com.localplatform.lang.Versioned
import com.localplatform.lang.v1.compiler.Terms
import com.localplatform.state.ByteStr
import monix.eval.Coeval
import com.localplatform.utils.Base64
import com.localplatform.transaction.ValidationError.ScriptParseError

trait Script extends Versioned {
  val expr: version.ExprT
  val text: String
  val bytes: Coeval[ByteStr]

  override def equals(obj: scala.Any): Boolean = obj match {
    case that: Script => version == that.version && expr == that.expr
    case _            => false
  }

  override def hashCode(): Int = version.value * 31 + expr.hashCode()
}

object Script {

  val checksumLength = 4

  def fromBase64String(str: String): Either[ScriptParseError, Script] =
    for {
      bytes  <- Base64.decode(str).toEither.left.map(ex => ScriptParseError(s"Unable to decode base64: ${ex.getMessage}"))
      script <- ScriptReader.fromBytes(bytes)
    } yield script

  object Expr {
    def unapply(arg: Script): Option[Terms.EXPR] = {
      if (arg.version == V1) Some(arg.expr.asInstanceOf[Terms.EXPR])
      else None
    }
  }
}

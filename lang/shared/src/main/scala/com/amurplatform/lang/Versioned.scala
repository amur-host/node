package com.amurplatform.lang

trait Versioned {
  type V <: ScriptVersion
  val version: V
}

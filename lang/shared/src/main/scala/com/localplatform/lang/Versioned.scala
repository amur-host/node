package com.localplatform.lang

trait Versioned {
  type V <: ScriptVersion
  val version: V
}

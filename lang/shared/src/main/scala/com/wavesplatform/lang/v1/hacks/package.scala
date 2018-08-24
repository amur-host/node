package com..lang

import com..lang.v1.BaseGlobal

package object hacks {
  private[lang] val Global: BaseGlobal = com..lang.Global // Hack for IDEA
}

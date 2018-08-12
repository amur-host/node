package com.localplatform.lang

import com.localplatform.lang.v1.BaseGlobal

package object hacks {
  private[lang] val Global: BaseGlobal = com.localplatform.lang.Global // Hack for IDEA
}

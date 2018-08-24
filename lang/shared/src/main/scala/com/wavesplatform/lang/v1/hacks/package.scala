package com.amurplatform.lang

import com.amurplatform.lang.v1.BaseGlobal

package object hacks {
  private[lang] val Global: BaseGlobal = com.amurplatform.lang.Global // Hack for IDEA
}

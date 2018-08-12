package com

import java.io.File
import java.nio.file.Files

package object localplatform {
  def createTestTemporaryFile(name: String, ext: String): File = {
    val file = Files.createTempFile(name, ext).toFile
    file.deleteOnExit()

    file
  }
}

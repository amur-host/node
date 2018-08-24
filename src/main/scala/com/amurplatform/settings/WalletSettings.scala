package com.amurplatform.settings

import java.io.File

import com.amurplatform.state.ByteStr

case class WalletSettings(file: Option[File], password: String, seed: Option[ByteStr])

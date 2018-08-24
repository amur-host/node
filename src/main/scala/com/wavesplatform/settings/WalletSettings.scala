package com..settings

import java.io.File

import com..state.ByteStr

case class WalletSettings(file: Option[File], password: String, seed: Option[ByteStr])

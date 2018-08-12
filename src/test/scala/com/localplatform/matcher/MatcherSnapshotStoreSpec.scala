package com.localplatform.matcher

import java.io.File
import java.nio.file.Files.createTempDirectory

import akka.persistence.snapshot.SnapshotStoreSpec
import com.typesafe.config.ConfigFactory.parseString
import com.localplatform.TestHelpers.deleteRecursively
import com.localplatform.settings.loadConfig

class MatcherSnapshotStoreSpec
    extends SnapshotStoreSpec(loadConfig(parseString(s"local.matcher.snapshot-store.leveldb-dir = ${createTempDirectory("matcher").toAbsolutePath}"))) {
  protected override def afterAll(): Unit = {
    super.afterAll()
    deleteRecursively(new File(system.settings.config.getString("local.matcher.snapshot-store.leveldb-dir")).toPath)
  }
}

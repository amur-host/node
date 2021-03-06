package com.amurplatform.matcher

import java.io.File
import java.nio.file.Files.createTempDirectory

import akka.persistence.snapshot.SnapshotStoreSpec
import com.typesafe.config.ConfigFactory.parseString
import com.amurplatform.TestHelpers.deleteRecursively
import com.amurplatform.settings.loadConfig

class MatcherSnapshotStoreSpec
    extends SnapshotStoreSpec(loadConfig(parseString(s"""amur.matcher.snapshot-store.leveldb-dir = ${createTempDirectory("matcher").toAbsolutePath}
         |akka.actor.allow-java-serialization = on""".stripMargin))) {
  protected override def afterAll(): Unit = {
    super.afterAll()
    deleteRecursively(new File(system.settings.config.getString("amur.matcher.snapshot-store.leveldb-dir")).toPath)
  }
}

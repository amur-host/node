package com..matcher

import java.io.File
import java.nio.file.Files.createTempDirectory

import akka.persistence.snapshot.SnapshotStoreSpec
import com.typesafe.config.ConfigFactory.parseString
import com..TestHelpers.deleteRecursively
import com..settings.loadConfig

class MatcherSnapshotStoreSpec
    extends SnapshotStoreSpec(loadConfig(parseString(s"""waves.matcher.snapshot-store.leveldb-dir = ${createTempDirectory("matcher").toAbsolutePath}
         |akka.actor.allow-java-serialization = on""".stripMargin))) {
  protected override def afterAll(): Unit = {
    super.afterAll()
    deleteRecursively(new File(system.settings.config.getString("waves.matcher.snapshot-store.leveldb-dir")).toPath)
  }
}

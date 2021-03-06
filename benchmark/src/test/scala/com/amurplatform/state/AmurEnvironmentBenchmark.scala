package com.amurplatform.state

import java.io.File
import java.util.concurrent.{ThreadLocalRandom, TimeUnit}

import com.typesafe.config.ConfigFactory
import com.amurplatform.account.{AddressOrAlias, AddressScheme, Alias}
import com.amurplatform.database.LevelDBWriter
import com.amurplatform.db.LevelDBFactory
import com.amurplatform.lang.v1.traits.Environment
import com.amurplatform.lang.v1.traits.domain.Recipient
import com.amurplatform.settings.{AmurSettings, loadConfig}
import com.amurplatform.state.AmurEnvironmentBenchmark._
import com.amurplatform.state.bench.DataTestData
import com.amurplatform.transaction.smart.AmurEnvironment
import com.amurplatform.utils.Base58
import monix.eval.Coeval
import org.iq80.leveldb.{DB, Options}
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import scodec.bits.{BitVector, ByteVector}

import scala.io.Codec

@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
@Threads(1)
@Fork(1)
@Warmup(iterations = 10)
@Measurement(iterations = 10)
class AmurEnvironmentBenchmark {

  @Benchmark
  def resolveAddress_test(st: ResolveAddressSt, bh: Blackhole): Unit = {
    bh.consume(st.environment.resolveAlias(st.aliases.random))
  }

  @Benchmark
  def transactionById_test(st: TransactionByIdSt, bh: Blackhole): Unit = {
    bh.consume(st.environment.transactionById(st.allTxs.random))
  }

  @Benchmark
  def transactionHeightById_test(st: TransactionByIdSt, bh: Blackhole): Unit = {
    bh.consume(st.environment.transactionById(st.allTxs.random))
  }

  @Benchmark
  def accountBalanceOf_amur_test(st: AccountBalanceOfAmurSt, bh: Blackhole): Unit = {
    bh.consume(st.environment.accountBalanceOf(Recipient.Address(ByteVector(st.accounts.random)), None))
  }

  @Benchmark
  def accountBalanceOf_asset_test(st: AccountBalanceOfAssetSt, bh: Blackhole): Unit = {
    bh.consume(st.environment.accountBalanceOf(Recipient.Address(ByteVector(st.accounts.random)), Some(st.assets.random)))
  }

  @Benchmark
  def data_test(st: DataSt, bh: Blackhole): Unit = {
    val x = st.data.random
    bh.consume(st.environment.data(Recipient.Address(x.addr), x.key, x.dataType))
  }

}

object AmurEnvironmentBenchmark {

  @State(Scope.Benchmark)
  class ResolveAddressSt extends BaseSt {
    val aliases: Vector[String] = load("resolveAddress", benchSettings.aliasesFile)(x => Alias.fromString(x).explicitGet().name)
  }

  @State(Scope.Benchmark)
  class TransactionByIdSt extends BaseSt {
    val allTxs: Vector[Array[Byte]] = load("transactionById", benchSettings.restTxsFile)(x => Base58.decode(x).get)
  }

  @State(Scope.Benchmark)
  class TransactionHeightByIdSt extends TransactionByIdSt

  @State(Scope.Benchmark)
  class AccountBalanceOfAmurSt extends BaseSt {
    val accounts: Vector[Array[Byte]] = load("accounts", benchSettings.accountsFile)(x => AddressOrAlias.fromString(x).explicitGet().bytes.arr)
  }

  @State(Scope.Benchmark)
  class AccountBalanceOfAssetSt extends AccountBalanceOfAmurSt {
    val assets: Vector[Array[Byte]] = load("assets", benchSettings.assetsFile)(x => Base58.decode(x).get)
  }

  @State(Scope.Benchmark)
  class DataSt extends BaseSt {
    val data: Vector[DataTestData] = load("data", benchSettings.dataFile) { line =>
      DataTestData.codec.decode(BitVector.fromBase64(line).get).require.value
    }
  }

  @State(Scope.Benchmark)
  class BaseSt {
    protected val benchSettings: Settings = Settings.fromConfig(ConfigFactory.load())
    private val amurSettings: AmurSettings = {
      val config = loadConfig(ConfigFactory.parseFile(new File(benchSettings.networkConfigFile)))
      AmurSettings.fromConfig(config)
    }

    AddressScheme.current = new AddressScheme {
      override val chainId: Byte = amurSettings.blockchainSettings.addressSchemeCharacter.toByte
    }

    private val db: DB = {
      val dir = new File(amurSettings.dataDirectory)
      if (!dir.isDirectory) throw new IllegalArgumentException(s"Can't find directory at '${amurSettings.dataDirectory}'")
      LevelDBFactory.factory.open(dir, new Options)
    }

    val environment: Environment = {
      val state = new LevelDBWriter(db, amurSettings.blockchainSettings.functionalitySettings)
      new AmurEnvironment(
        AddressScheme.current.chainId,
        Coeval.raiseError(new NotImplementedError("tx is not implemented")),
        Coeval(state.height),
        state
      )
    }

    @TearDown
    def close(): Unit = {
      db.close()
    }

    protected def load[T](label: String, absolutePath: String)(f: String => T): Vector[T] = {
      scala.io.Source
        .fromFile(absolutePath)(Codec.UTF8)
        .getLines()
        .map(f)
        .toVector
    }
  }

  implicit class VectorOps[T](self: Vector[T]) {
    def random: T = self(ThreadLocalRandom.current().nextInt(self.size))
  }

}

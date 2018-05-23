package com.wavesplatform.database

import java.nio.ByteBuffer

import com.google.common.primitives.{Ints, Longs, Shorts}
import com.wavesplatform.state._
import scorex.account.{Address, Alias}
import scorex.block.{Block, BlockHeader}
import scorex.transaction.Transaction
import scorex.transaction.smart.script.{Script, ScriptReader}
import com.google.common.base.Charsets.UTF_8

object Keys {
  private def h(prefix: Short, height: Int): Array[Byte] =
    ByteBuffer.allocate(6).putShort(prefix).putInt(height).array()

  private def hBytes(prefix: Short, height: Int, bytes: Array[Byte]) =
    ByteBuffer.allocate(6 + bytes.length).putShort(prefix).putInt(height).put(bytes).array()

  private def bytes(prefix: Short, bytes: Array[Byte]) =
    ByteBuffer.allocate(2 + bytes.length).putShort(prefix).put(bytes).array()

  private def addr(prefix: Short, addressId: BigInt) = bytes(prefix, addressId.toByteArray)

  private def hash(prefix: Short, hashBytes: ByteStr) = bytes(prefix, hashBytes.arr)

  private def hAddr(prefix: Short, height: Int, addressId: BigInt): Array[Byte] = hBytes(prefix, height, addressId.toByteArray)

  private def historyKey(prefix: Short, b: Array[Byte]) = Key(bytes(prefix, b), readIntSeq, writeIntSeq)

  private def intKey(prefix: Short, default: Int = 0): Key[Int] =
    Key[Int](Shorts.toByteArray(prefix), Option(_).fold(default)(Ints.fromByteArray), Ints.toByteArray)

  private def unsupported[A](message: String): A => Array[Byte] = _ => throw new UnsupportedOperationException(message)

  // actual key definition

  val version: Key[Int]               = intKey(0, default = 1)
  val height: Key[Int]                = intKey(1)
  def score(height: Int): Key[BigInt] = Key(h(2, height), Option(_).fold(BigInt(0))(BigInt(_)), _.toByteArray)

  private def blockAtHeight(height: Int) = h(3, height)

  def blockAt(height: Int): Key[Option[Block]]          = Key.opt[Block](blockAtHeight(height), Block.parseBytes(_).get, _.bytes())
  def blockBytes(height: Int): Key[Option[Array[Byte]]] = Key.opt[Array[Byte]](blockAtHeight(height), identity, identity)
  def blockHeader(height: Int): Key[Option[(BlockHeader, Int)]] =
    Key.opt[(BlockHeader, Int)](blockAtHeight(height), b => (BlockHeader.parseBytes(b).get._1, b.length), unsupported("Can't write block headers")) // this dummy encoder is never used: we only store blocks, not block headers

  def heightOf(blockId: ByteStr): Key[Option[Int]] = Key.opt[Int](hash(4, blockId), Ints.fromByteArray, Ints.toByteArray)

  def wavesBalanceHistory(addressId: BigInt): Key[Seq[Int]] = historyKey(5, addressId.toByteArray)
  def wavesBalance(height: Int, addressId: BigInt): Key[Long] =
    Key(hAddr(6, height, addressId), Option(_).fold(0L)(Longs.fromByteArray), Longs.toByteArray)

  def assetList(addressId: BigInt): Key[Set[ByteStr]]                         = Key(addr(7, addressId), readTxIds(_).toSet, assets => writeTxIds(assets.toSeq))
  def assetBalanceHistory(addressId: BigInt, assetId: ByteStr): Key[Seq[Int]] = historyKey(8, addressId.toByteArray ++ assetId.arr)
  def assetBalance(height: Int, addressId: BigInt, assetId: ByteStr): Key[Long] =
    Key(hBytes(9, height, addressId.toByteArray ++ assetId.arr), Option(_).fold(0L)(Longs.fromByteArray), Longs.toByteArray)

  def assetInfoHistory(assetId: ByteStr): Key[Seq[Int]]        = historyKey(10, assetId.arr)
  def assetInfo(height: Int, assetId: ByteStr): Key[AssetInfo] = Key(hBytes(11, height, assetId.arr), readAssetInfo, writeAssetInfo)

  def leaseBalanceHistory(addressId: BigInt): Key[Seq[Int]] = historyKey(12, addressId.toByteArray)
  def leaseBalance(height: Int, addressId: BigInt): Key[LeaseBalance] =
    Key(hAddr(13, height, addressId), readLeaseBalance, writeLeaseBalance)
  def leaseStatusHistory(leaseId: ByteStr): Key[Seq[Int]] = historyKey(14, leaseId.arr)
  def leaseStatus(height: Int, leaseId: ByteStr): Key[Boolean] =
    Key(hBytes(15, height, leaseId.arr), _(0) == 1, active => Array[Byte](if (active) 1 else 0))

  def filledVolumeAndFeeHistory(orderId: ByteStr): Key[Seq[Int]]           = historyKey(16, orderId.arr)
  def filledVolumeAndFee(height: Int, orderId: ByteStr): Key[VolumeAndFee] = Key(hBytes(17, height, orderId.arr), readVolumeAndFee, writeVolumeAndFee)

  def transactionInfo(txId: ByteStr): Key[Option[(Int, Transaction)]] = Key.opt(hash(18, txId), readTransactionInfo, writeTransactionInfo)
  def transactionHeight(txId: ByteStr): Key[Option[Int]] =
    Key.opt(hash(18, txId), readTransactionHeight, unsupported("Can't write transaction height only"))

  def addressTransactionHistory(addressId: BigInt): Key[Seq[Int]] = historyKey(19, addressId.toByteArray)
  def addressTransactionIds(height: Int, addressId: BigInt): Key[Seq[(Int, ByteStr)]] =
    Key(hAddr(20, height, addressId), readTransactionIds, writeTransactionIds)

  def changedAddresses(height: Int): Key[Seq[BigInt]] = Key(h(21, height), readBigIntSeq, writeBigIntSeq)

  def transactionIdsAtHeight(height: Int): Key[Seq[ByteStr]] = Key(h(22, height), readTxIds, writeTxIds)

  def addressIdOfAlias(alias: Alias): Key[Option[BigInt]] = Key.opt(bytes(23, alias.bytes.arr), BigInt(_), _.toByteArray)

  val lastAddressId: Key[Option[BigInt]] = Key.opt(Array[Byte](0, 24), BigInt(_), _.toByteArray)

  def addressId(address: Address): Key[Option[BigInt]] = Key.opt(bytes(25, address.bytes.arr), BigInt(_), _.toByteArray)
  def idToAddress(id: BigInt): Key[Address]            = Key(bytes(26, id.toByteArray), Address.fromBytes(_).explicitGet(), _.bytes.arr)

  def addressScriptHistory(addressId: BigInt): Key[Seq[Int]] = historyKey(27, addressId.toByteArray)
  def addressScript(height: Int, addressId: BigInt): Key[Option[Script]] =
    Key.opt(hAddr(28, height, addressId), ScriptReader.fromBytes(_).explicitGet(), _.bytes().arr)

  def approvedFeatures: Key[Map[Short, Int]]  = Key(Array[Byte](0, 29), readFeatureMap, writeFeatureMap)
  def activatedFeatures: Key[Map[Short, Int]] = Key(Array[Byte](0, 30), readFeatureMap, writeFeatureMap)

  def dataKeyCount(addressId: BigInt): Key[Int] = Key(addr(31, addressId), Option(_).fold(0)(Ints.fromByteArray), Ints.toByteArray)
  def dataKey(addressId: BigInt, keyNo: Int): Key[Option[String]] =
    Key.opt(addr(31, addressId) ++ Ints.toByteArray(keyNo), new String(_, UTF_8), _.getBytes(UTF_8))

  def dataHistory(addressId: BigInt, key: String): Key[Seq[Int]] = historyKey(32, addressId.toByteArray ++ key.getBytes(UTF_8))
  def data(height: Int, addressId: BigInt, key: String): Key[Option[DataEntry[_]]] =
    Key.opt(hBytes(33, height, addressId.toByteArray ++ key.getBytes(UTF_8)), DataEntry.parseValue(key, _, 0)._1, _.valueBytes)

  def sponsorshipHistory(assetId: ByteStr): Key[Seq[Int]]               = historyKey(34, assetId.arr)
  def sponsorship(height: Int, assetId: ByteStr): Key[SponsorshipValue] = Key(hBytes(35, height, assetId.arr), readSponsorship, writeSponsorship)

  val addressesForWavesSeqNr: Key[Int]                = intKey(36)
  def addressesForWaves(seqNr: Int): Key[Seq[BigInt]] = Key(h(37, seqNr), readBigIntSeq, writeBigIntSeq)

  def addressesForAssetSeqNr(assetId: ByteStr): Key[Int]                = intKey(38)
  def addressesForAsset(assetId: ByteStr, seqNr: Int): Key[Seq[BigInt]] = Key(hBytes(39, seqNr, assetId.arr), readBigIntSeq, writeBigIntSeq)
}

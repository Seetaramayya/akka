/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.io.dns

import java.net.{ Inet4Address, Inet6Address, InetAddress }

import akka.actor.NoSerializationVerificationNeeded
import akka.annotation.{ ApiMayChange, InternalApi }
import CachePolicy._
import akka.io.dns.internal.{ DomainName, _ }
import akka.util.{ ByteIterator, ByteString, unused }

import scala.annotation.switch
import scala.concurrent.duration._

@ApiMayChange
sealed abstract class ResourceRecord(val name: String, val ttl: Ttl, val recType: Short, val recClass: Short)
  extends NoSerializationVerificationNeeded {
}

@ApiMayChange
final case class ARecord(override val name: String, override val ttl: Ttl,
                         ip: InetAddress) extends ResourceRecord(name, ttl, RecordType.A.code, RecordClass.IN.code) {
}

/**
 * INTERNAL API
 */
@InternalApi
private[dns] object ARecord {
  def parseBody(name: String, ttl: Ttl, @unused length: Short, it: ByteIterator): ARecord = {
    val addr = Array.ofDim[Byte](4)
    it.getBytes(addr)
    ARecord(name, ttl, InetAddress.getByAddress(addr).asInstanceOf[Inet4Address])
  }
}

@ApiMayChange
final case class AAAARecord(override val name: String, override val ttl: Ttl,
                            ip: Inet6Address) extends ResourceRecord(name, ttl, RecordType.AAAA.code, RecordClass.IN.code) {
}

/**
 * INTERNAL API
 */
@InternalApi
private[dns] object AAAARecord {

  /**
   * INTERNAL API
   */
  @InternalApi
  def parseBody(name: String, ttl: Ttl, @unused length: Short, it: ByteIterator): AAAARecord = {
    val addr = Array.ofDim[Byte](16)
    it.getBytes(addr)
    AAAARecord(name, ttl, InetAddress.getByAddress(addr).asInstanceOf[Inet6Address])
  }
}

@ApiMayChange
final case class CNameRecord(override val name: String, override val ttl: Ttl,
                             canonicalName: String) extends ResourceRecord(name, ttl, RecordType.CNAME.code, RecordClass.IN.code) {
}

@InternalApi
private[dns] object CNameRecord {
  /**
   * INTERNAL API
   */
  @InternalApi
  def parseBody(name: String, ttl: Ttl, @unused length: Short, it: ByteIterator, msg: ByteString): CNameRecord = {
    CNameRecord(name, ttl, DomainName.parse(it, msg))
  }
}

@ApiMayChange
final case class SRVRecord(override val name: String, override val ttl: Ttl,
                           priority: Int, weight: Int, port: Int, target: String) extends ResourceRecord(name, ttl, RecordType.SRV.code, RecordClass.IN.code) {
}

/**
 * INTERNAL API
 */
@InternalApi
private[dns] object SRVRecord {
  /**
   * INTERNAL API
   */
  @InternalApi
  def parseBody(name: String, ttl: Ttl, @unused length: Short, it: ByteIterator, msg: ByteString): SRVRecord = {
    val priority = it.getShort.toInt & 0xFFFF
    val weight = it.getShort.toInt & 0xFFFF
    val port = it.getShort.toInt & 0xFFFF
    SRVRecord(name, ttl, priority, weight, port, DomainName.parse(it, msg))
  }
}

@ApiMayChange
final case class UnknownRecord(override val name: String, override val ttl: Ttl,
                               override val recType: Short, override val recClass: Short,
                               data: ByteString) extends ResourceRecord(name, ttl, recType, recClass) {
}

/**
 * INTERNAL API
 */
@InternalApi
private[dns] object UnknownRecord {
  /**
   * INTERNAL API
   */
  @InternalApi
  def parseBody(name: String, ttl: Ttl, recType: Short, recClass: Short, @unused length: Short, it: ByteIterator): UnknownRecord =
    UnknownRecord(name, ttl, recType, recClass, it.toByteString)
}

/**
 * INTERNAL API
 */
@InternalApi
private[dns] object ResourceRecord {
  /**
   * INTERNAL API
   */
  @InternalApi
  def parse(it: ByteIterator, msg: ByteString): ResourceRecord = {
    val name = DomainName.parse(it, msg)
    val recType = it.getShort
    val recClass = it.getShort
    // According to https://www.ietf.org/rfc/rfc1035.txt: "TTL: positive values of a signed 32 bit number."
    val ttl = Ttl.fromPositive(it.getInt.seconds)
    val rdLength = it.getShort
    val data = it.clone().take(rdLength)
    it.drop(rdLength)
    (recType: @switch) match {
      case 1  ⇒ ARecord.parseBody(name, ttl, rdLength, data)
      case 5  ⇒ CNameRecord.parseBody(name, ttl, rdLength, data, msg)
      case 28 ⇒ AAAARecord.parseBody(name, ttl, rdLength, data)
      case 33 ⇒ SRVRecord.parseBody(name, ttl, rdLength, data, msg)
      case _  ⇒ UnknownRecord.parseBody(name, ttl, recType, recClass, rdLength, data)
    }
  }
}


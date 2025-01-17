/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package wvlet.airframe.codec

import org.scalacheck.util.Pretty
import wvlet.airframe.codec.PrimitiveCodec.{AnyCodec, LongCodec}
import wvlet.airframe.json.JSON.JSONString
import wvlet.airframe.msgpack.spi.MessagePack
import wvlet.airframe.msgpack.spi.Value.StringValue
import wvlet.airframe.surface.{ArraySurface, GenericSurface, Surface}
import wvlet.airframe.ulid.ULID
import wvlet.airspec.spi.PropertyCheck

import java.math.BigInteger
import java.time.Instant
import scala.util.Random

/**
  */
object PrimitiveCodecTest extends CodecSpec with PropertyCheck {

  import org.scalacheck._

  import scala.jdk.CollectionConverters._

  protected def roundTripTest[T](surface: Surface, dataType: DataType)(implicit
      a1: Arbitrary[T],
      s1: Shrink[T],
      pp1: T => Pretty
  ): Unit = {
    forAll { (v: T) => roundtrip(surface, v, dataType) }
  }

  protected def arrayRoundTripTest[T](surface: Surface)(implicit
      impArb: Arbitrary[Array[T]],
      shrink: Shrink[Array[T]],
      pp: Array[T] => Pretty
  ): Unit = {
    val codec = MessageCodec.ofSurface(ArraySurface(surface.rawType, surface)).asInstanceOf[MessageCodec[Array[T]]]
    val seqCodec =
      MessageCodec.ofSurface(new GenericSurface(classOf[Seq[_]], Seq(surface))).asInstanceOf[MessageCodec[Seq[T]]]
    val javaListCodec = MessageCodec
      .ofSurface(new GenericSurface(classOf[java.util.List[_]], Seq(surface))).asInstanceOf[MessageCodec[
        java.util.List[T]
      ]].asInstanceOf[MessageCodec[java.util.List[_]]]

    forAll { (v: Array[T]) =>
      // Array round trip
      roundtrip(codec, v, DataType.ANY)
      // Seq -> Array
      roundtrip(seqCodec, v.toSeq, DataType.ANY)
      // java.util.List[T] -> Array
      roundtrip(javaListCodec, v.toList.asJava, DataType.ANY)
    }
  }

  protected def roundTripTestWithStr[T](
      surface: Surface,
      dataType: DataType
  )(implicit impArb: Arbitrary[T], s1: Shrink[T], pp1: T => Pretty): Unit = {
    val codec = MessageCodec.ofSurface(surface).asInstanceOf[MessageCodec[T]]
    forAll { (v: T) =>
      // Test input:T -> output:T
      roundtrip(codec, v, dataType)
      // Test from input:String -> output:T
      roundtripStr(codec, v, dataType)
    }
  }

  test("support numeric") {
    roundTripTestWithStr[Int](Surface.of[Int], DataType.INTEGER)
    roundTripTestWithStr[Byte](Surface.of[Byte], DataType.INTEGER)
    roundTripTestWithStr[Short](Surface.of[Short], DataType.INTEGER)
    roundTripTestWithStr[Long](Surface.of[Long], DataType.INTEGER)
    roundTripTestWithStr[Boolean](Surface.of[Boolean], DataType.BOOLEAN)
  }

  test("support char") {
    roundTripTest[Char](Surface.of[Char], DataType.INTEGER)
  }

  test("support float") {
    roundTripTestWithStr[Float](Surface.of[Float], DataType.FLOAT)
    roundTripTestWithStr[Double](Surface.of[Double], DataType.FLOAT)
  }

  test("support string") {
    roundTripTest[String](Surface.of[String], DataType.STRING)
  }

  test("support bigint") {
    val codec = MessageCodec.of[BigInt]
    forAll { (v: Long) =>
      val bi0     = BigInt(v)
      val msgpack = codec.toMsgPack(bi0)
      val bi1     = codec.fromMsgPack(msgpack)
      bi0 shouldBe bi1
    }

    test("large bigint") {
      forAll { (v: Long) =>
        val bi0     = BigInt(Long.MaxValue) + BigInt(v)
        val msgpack = codec.toMsgPack(bi0)
        val bi1     = codec.fromMsgPack(msgpack)
        bi0 shouldBe bi1
      }
    }
  }

  test("support BigInteger") {
    val codec = MessageCodec.of[BigInteger]
    forAll { (v: Long) =>
      val bi0     = BigInteger.valueOf(v)
      val msgpack = codec.toMsgPack(bi0)
      val bi1     = codec.fromMsgPack(msgpack)
      bi0 shouldBe bi1
    }

    test("large BigInteger") {
      forAll { (v: Long) =>
        val bi0     = BigInteger.valueOf(Long.MaxValue).add(BigInteger.valueOf(v))
        val msgpack = codec.toMsgPack(bi0)
        val bi1     = codec.fromMsgPack(msgpack)
        bi0 shouldBe bi1
      }
    }
  }

  test("support arrays") {
    arrayRoundTripTest[Byte](Surface.of[Byte])
    arrayRoundTripTest[Char](Surface.of[Char])
    arrayRoundTripTest[Int](Surface.of[Int])
    arrayRoundTripTest[Short](Surface.of[Short])
    arrayRoundTripTest[Long](Surface.of[Long])
    arrayRoundTripTest[String](Surface.of[String])
    arrayRoundTripTest[Float](Surface.of[Float])
    arrayRoundTripTest[Double](Surface.of[Double])
    arrayRoundTripTest[Boolean](Surface.of[Boolean])
  }

  // Value 2^64-1 is the maximum value
  val LARGE_VALUE = BigInteger.valueOf(1).shiftLeft(64).subtract(BigInteger.valueOf(1))

  test("read various types of data as int") {
    val expected = Seq(10, 12, 13, 0, 1, 13, 12345, 0, 0, 0)

    val p = MessagePack.newBufferPacker
    p.packArrayHeader(expected.size)
    p.packInt(10)
    p.packString("12")
    p.packString("13.2")
    p.packBoolean(false)
    p.packBoolean(true)
    p.packFloat(13.4f)
    p.packDouble(12345.01)
    p.packString("non-number")    // will be 0
    p.packNil                     // will be 0
    p.packBigInteger(LARGE_VALUE) // will be 0

    val codec   = MessageCodec.of[Seq[Int]]
    val msgpack = p.toByteArray
    debug(MessagePack.newUnpacker(msgpack).unpackValue)
    val seq = codec.unpackMsgPack(msgpack)
    seq shouldBe defined
    seq.get shouldBe expected
  }

  test("read various types of data as long") {
    val expected = Seq[Long](10, 12, 13, 0, 1, 13, 12345, 0, 0, 0)

    val p = MessagePack.newBufferPacker
    p.packArrayHeader(expected.size)
    p.packInt(10)
    p.packString("12")
    p.packString("13.2")
    p.packBoolean(false)
    p.packBoolean(true)
    p.packFloat(13.4f)
    p.packDouble(12345.01)
    p.packString("non-number")    // will be 0
    p.packNil                     // will be 0
    p.packBigInteger(LARGE_VALUE) // will be 0

    val codec = MessageCodec.of[Seq[Long]]
    val seq   = codec.unpackMsgPack(p.toByteArray)
    seq shouldBe defined
    seq.get shouldBe expected
  }

  test("read various types of data as short") {
    val expected = Seq[Short](10, 12, 13, 0, 1, 13, 1021, 0, 0, 0)

    val p = MessagePack.newBufferPacker
    p.packArrayHeader(expected.size)
    p.packInt(10)
    p.packString("12")
    p.packString("13.2")
    p.packBoolean(false)
    p.packBoolean(true)
    p.packFloat(13.4f)
    p.packDouble(1021.1)
    p.packString("non-number")    // will be 0
    p.packNil                     // will be 0
    p.packBigInteger(LARGE_VALUE) // will be 0

    val codec = MessageCodec.of[Seq[Short]]
    val seq   = codec.unpackMsgPack(p.toByteArray)
    seq shouldBe defined
    seq.get shouldBe expected
  }

  test("read various types of data as byte") {
    val expected = Seq[Byte](10, 12, 13, 0, 1, 13, 123, 0, 0, 0)

    val p = MessagePack.newBufferPacker
    p.packArrayHeader(expected.size)
    p.packInt(10)
    p.packString("12")
    p.packString("13.2")
    p.packBoolean(false)
    p.packBoolean(true)
    p.packFloat(13.4f)
    p.packDouble(123.0)
    p.packString("non-number")    // will be 0
    p.packNil                     // will be 0
    p.packBigInteger(LARGE_VALUE) // will be 0

    val codec = MessageCodec.of[Seq[Byte]]
    val seq   = codec.unpackMsgPack(p.toByteArray)
    seq shouldBe defined
    seq.get shouldBe expected
  }

  test("read various types of data as char") {
    val expected = Seq[Char](10, 12, 13, 0, 1, 13, 123, 0, 0, 0)

    val p = MessagePack.newBufferPacker
    p.packArrayHeader(expected.size)
    p.packInt(10)
    p.packString("12")
    p.packString("13.2")
    p.packBoolean(false)
    p.packBoolean(true)
    p.packFloat(13.4f)
    p.packDouble(123.0)
    p.packString("non-number")    // will be 0
    p.packNil                     // will be 0
    p.packBigInteger(LARGE_VALUE) // will be 0

    val codec = MessageCodec.of[Seq[Char]]
    val seq   = codec.unpackMsgPack(p.toByteArray)
    seq shouldBe defined
    seq.get shouldBe expected
  }

  test("read various types of data as float") {
    val expected = Seq[Float](10f, 12f, 13.2f, 0f, 1f, 13.4f, 12345.01f, 0f, 0f, LARGE_VALUE.floatValue())

    val p = MessagePack.newBufferPacker
    p.packArrayHeader(expected.size)
    p.packInt(10)
    p.packString("12")
    p.packString("13.2")
    p.packBoolean(false)
    p.packBoolean(true)
    p.packFloat(13.4f)
    p.packDouble(12345.01)
    p.packString("non-number")    // will be 0
    p.packNil                     // will be 0
    p.packBigInteger(LARGE_VALUE) // will be 0

    val codec = MessageCodec.of[Seq[Float]]
    val seq   = codec.unpackMsgPack(p.toByteArray)
    seq shouldBe defined
    seq.get shouldBe expected
  }

  test("read various types of data as double") {
    val expected = Seq[Double](10.0, 12.0, 13.2, 0.0, 1.0, 0.1f, 12345.01, 0.0, 0.0, LARGE_VALUE.doubleValue())

    val p = MessagePack.newBufferPacker
    p.packArrayHeader(expected.size)
    p.packInt(10)
    p.packString("12")
    p.packString("13.2")
    p.packBoolean(false)
    p.packBoolean(true)
    p.packFloat(0.1f)
    p.packDouble(12345.01)
    p.packString("non-number")    // will be 0
    p.packNil                     // will be 0
    p.packBigInteger(LARGE_VALUE) // will be 0

    val codec = MessageCodec.of[Seq[Double]]
    val seq   = codec.unpackMsgPack(p.toByteArray)
    seq shouldBe defined
    seq.get shouldBe expected
  }

  test("read various types of data as boolean") {
    val expected = Seq(true, true, true, false, true, false, false, true, false, true, true, false, false, true)

    val p = MessagePack.newBufferPacker
    p.packArrayHeader(expected.size)
    p.packInt(10)
    p.packString("12")
    p.packString("13.2")
    p.packString("0")
    p.packString("true")
    p.packString("false")
    p.packBoolean(false)
    p.packBoolean(true)
    p.packFloat(0.0f)
    p.packFloat(0.1f)
    p.packDouble(12345.01)
    p.packString("non-number")    // will be false (default value)
    p.packNil                     // will be false
    p.packBigInteger(LARGE_VALUE) // will be 0

    val codec = MessageCodec.of[Seq[Boolean]]
    val seq   = codec.unpackMsgPack(p.toByteArray)
    seq shouldBe defined
    seq.get shouldBe expected
  }

  test("read various types of data as string") {
    val expected = Seq(
      "10",
      "12",
      "13.2",
      "false",
      "true",
      // "0.2",
      "12345.01",
      "",
      LARGE_VALUE.toString,
      """[1,"leo"]""",
      """{"name":"leo"}"""
    )

    val p = MessagePack.newBufferPacker
    p.packArrayHeader(expected.size)
    p.packInt(10)
    p.packString("12")
    p.packString("13.2")
    p.packBoolean(false)
    p.packBoolean(true)
    // Scala.js uses double for float values
    // p.packFloat(0.2f)
    p.packDouble(12345.01)
    p.packNil                     // will be 0
    p.packBigInteger(LARGE_VALUE) // will be 0
    p.packArrayHeader(2)
    p.packInt(1)
    p.packString("leo")
    p.packMapHeader(1)
    p.packString("name")
    p.packString("leo")

    val codec = MessageCodec.of[Seq[String]]
    val seq   = codec.unpackMsgPack(p.toByteArray)
    seq shouldBe defined
    seq.get shouldBe expected
  }

  test("read Any values") {
    val ulid = ULID.newULID

    val input: Seq[Any] = Seq(
      "hello",
      true,
      10,
      100L,
      10.0f,
      // 12345.01,
      10.toByte,
      12.toShort,
      20.toChar,
      JSONString("hello"),
      StringValue("value"),
      Instant.ofEpochMilli(100),
      Some("hello opt"),
      None,
      ulid
    )

    val codec   = MessageCodec.of[Any]
    val msgpack = codec.toMsgPack(input)

    // Some type conversion happens as there is no explicit type data in Any
    val result = codec.unpackMsgPack(msgpack)
    result.get shouldBe Seq(
      "hello",
      true,
      10L,
      100L,
      10.0,
      // 12345.01,
      10L,
      12L,
      20L,
      "hello",
      "value",
      Instant.ofEpochMilli(100),
      "hello opt",
      null,
      ulid.toString
    )
  }

  case class Person(id: Int, name: String)

  test("find the actual codec for Any class") {
    if (isScalaJS) {
      pending("Scala.js doesn't support runtime reflection")
    }
    val anyCodec = new AnyCodec(knownSurfaces = Seq(Surface.of[Person]))
    val json     = anyCodec.toJson(Person(1, "leo"))
    json shouldBe """{"id":1,"name":"leo"}"""
  }

  sealed trait Color
  case object RED  extends Color
  case object BLUE extends Color
  object Color {
    def unapply(s: String): Option[Color] = Seq(RED, BLUE).find(_.toString == s)
  }

  test("find the actual codec for Any case objects") {
    val v     = Seq(RED, BLUE)
    val codec = MessageCodec.of[Seq[Any]]
    val json  = codec.toJson(v)
    json shouldBe """["RED","BLUE"]"""

    // AnyCodec doesn't know the original types, so roundtrip is not supported
    codec.fromJson(json) shouldBe Seq("RED", "BLUE")

    // Mapping JSON to case objects should be supported
    MessageCodec.of[Seq[Color]].fromJson(json) shouldBe v
  }

  test("read collection of Any values") {
    val codec = MessageCodec.of[Any]

    // Byte array
    val v = codec.unpackMsgPack(codec.toMsgPack(Array[Byte](1, 2))).get
    v shouldBe Array[Byte](1, 2)

    // The other type arrays
    val input: Seq[Any] = Seq(
      Array("a", "b"),
      Array(1, 2),
      Array(true, false),
      Array(1L, 2L),
      Array(1.0f, 2.0f),
      Array(1.0, 2.0),
      Array(1.toShort, 2.toShort),
      Array('a', 'b'),
      Array(1, "a", true),
      Map(1 -> "a", "2" -> "b")
    )

    val msgpack = codec.toMsgPack(input)

    val result = codec.unpackMsgPack(msgpack)
    result.get shouldBe Seq(
      Seq("a", "b"),
      Seq(1L, 2L),
      Seq(true, false),
      Seq(1L, 2L),
      Seq(1.0, 2.0),
      Seq(1.0, 2.0),
      Seq(1L, 2L),
      Seq('a'.toLong, 'b'.toLong),
      Seq(1L, "a", true),
      Map(1 -> "a", "2" -> "b")
    )
  }

  test("pack throwable object passed as Any") {
    val codec = MessageCodec.of[Any]
    val json  = codec.toJson(new IllegalArgumentException("error"))
    json.contains("java.lang.IllegalArgumentException") shouldBe true
  }

  test("unpack null in Any") {
    val codec = MessageCodec.of[Seq[Any]]
    val seq   = codec.fromJson("[null, 1]")
    seq shouldBe Seq(null, 1)
  }

  test("pack Either (Left) in AnyCodec") {
    val codec = MessageCodec.of[Any]
    val json  = codec.toJson(Left(new NullPointerException("NPE")))

    val eitherCodec = MessageCodec.of[Either[GenericException, String]]
    eitherCodec.fromJson(json) match {
      case Left(ex) =>
        ex.exceptionClass shouldBe "java.lang.NullPointerException"
        ex.message shouldBe "NPE"
      case _ =>
        fail("cannot reach here")
    }
  }

  test("pack Either (Right) in AnyCodec") {
    val codec = MessageCodec.of[Any]
    val json  = codec.toJson(Right("hello Either"))

    val eitherCodec = MessageCodec.of[Either[GenericException, String]]
    eitherCodec.fromJson(json) match {
      case Right(msg) =>
        msg shouldBe "hello Either"
      case _ =>
        fail("cannot reach here")
    }
  }

  case class BinaryData(data: Array[Byte])

  test("encode binary with BASE64") {
    val data = new Array[Byte](40)
    Random.nextBytes(data)
    val codec = MessageCodec.of[BinaryData]
    val json  = codec.toJson(BinaryData(data))
    val x     = codec.fromJson(json)
    x.data shouldBe data
  }

  test("Unit codec should have no value") {
    val c       = PrimitiveCodec.UnitCodec
    val msgpack = c.toMsgPack({})
    msgpack.length shouldBe 0
    val v = c.fromMsgPack(msgpack)
    v shouldBe null
  }

  test("fromString(str)") {
    LongCodec.fromString("1234") shouldBe 1234L
  }
}

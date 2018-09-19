// See LICENSE for license details.

package chiselTests

import chisel3._
import chisel3.core.{EnumAnnotations, EnumExceptions}
import chisel3.internal.firrtl.UnknownWidth
import chisel3.util._
import chisel3.testers.BasicTester
import firrtl.annotations.ComponentName
import org.scalatest.{FreeSpec, Matchers}

class EnumExample extends EnumType
object EnumExample extends StrongEnum[EnumExample] {
  val e0, e1, e2 = Value
  val e100 = Value(100.U)
  val e101 = Value

  val litValues = List(0.U, 1.U, 2.U, 100.U, 101.U)
}

class OtherEnum extends EnumType
object OtherEnum extends StrongEnum[OtherEnum] {
  val otherEnum = Value
}

class EnumWithoutCompanionObj extends EnumType

class NonLiteralEnumType extends EnumType
object NonLiteralEnumType extends StrongEnum[NonLiteralEnumType] {
  val nonLit = Value(UInt())
}

class SimpleConnector(inType: Data, outType: Data) extends Module {
  val io = IO(new Bundle {
    val in = Input(inType)
    val out = Output(outType)
  })

  io.out := io.in
}

class CastToUInt extends Module {
  val io = IO(new Bundle {
    val in = Input(EnumExample())
    val out = Output(UInt())
  })

  io.out := io.in.asUInt()
}

class CastFromLit(in: UInt) extends Module {
  val io = IO(new Bundle {
    val out = Output(EnumExample())
    val valid = Output(Bool())
  })

  io.out := EnumExample(in)
  io.valid := io.out.isValid
}

class CastFromNonLit extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(EnumExample.getWidth.W))
    val out = Output(EnumExample())
    val valid = Output(Bool())
  })

  io.out := EnumExample.castFromNonLit(io.in)
  io.valid := io.out.isValid
}

class CastFromNonLitWidth(w: Option[Int] = None) extends Module {
  val width = if (w.isDefined) w.get.W else UnknownWidth()

  override val io = IO(new Bundle {
    val in = Input(UInt(width))
    val out = Output(EnumExample())
  })

  io.out := EnumExample.castFromNonLit(io.in)
}

class EnumOps(xType: EnumType, yType: EnumType) extends Module {
  val io = IO(new Bundle {
    val x = Input(xType)
    val y = Input(yType)

    val lt = Output(Bool())
    val le = Output(Bool())
    val gt = Output(Bool())
    val ge = Output(Bool())
    val eq = Output(Bool())
    val ne = Output(Bool())
  })

  io.lt := io.x < io.y
  io.le := io.x <= io.y
  io.gt := io.x > io.y
  io.ge := io.x >= io.y
  io.eq := io.x === io.y
  io.ne := io.x =/= io.y
}

object StrongEnumFSM {
  class State extends EnumType
  object State extends StrongEnum[State] {
    val sNone, sOne1, sTwo1s = Value

    val correct_annotation_map = Map[String, UInt]("sNone" -> 0.U(2.W), "sOne1" -> 1.U(2.W), "sTwo1s" -> 2.U(2.W))
  }
}

class StrongEnumFSM extends Module {
  import StrongEnumFSM.State
  import StrongEnumFSM.State._

  // This FSM detects two 1's one after the other
  val io = IO(new Bundle {
    val in = Input(Bool())
    val out = Output(Bool())
    val state = Output(State())
  })

  val state = RegInit(sNone)

  io.out := (state === sTwo1s)
  io.state := state

  switch (state) {
    is (sNone) {
      when (io.in) {
        state := sOne1
      }
    }
    is (sOne1) {
      when (io.in) {
        state := sTwo1s
      } .otherwise {
        state := sNone
      }
    }
    is (sTwo1s) {
      when (!io.in) {
        state := sNone
      }
    }
  }
}

class CastToUIntTester extends BasicTester {
  for ((enum,lit) <- EnumExample.all zip EnumExample.litValues) {
    val mod = Module(new CastToUInt)
    mod.io.in := enum
    assert(mod.io.out === lit)
  }
  stop()
}

class CastFromLitTester extends BasicTester {
  for ((enum,lit) <- EnumExample.all zip EnumExample.litValues) {
    val mod = Module(new CastFromLit(lit))
    assert(mod.io.out === enum)
    assert(mod.io.valid === true.B)
  }
  stop()
}

class CastFromNonLitTester extends BasicTester {
  for ((enum,lit) <- EnumExample.all zip EnumExample.litValues) {
    val mod = Module(new CastFromNonLit)
    mod.io.in := lit
    assert(mod.io.out === enum)
    assert(mod.io.valid === true.B)
  }

  import scala.util.Random
  val invalid_values = (for(i <- 0 until 200) yield Random.nextInt((1 << EnumExample.getWidth)-1)).
    filter(!EnumExample.litValues.map(_.litValue).contains(_)).
    map(_.U)

  for (invalid_val <- invalid_values) {
    val mod = Module(new CastFromNonLit)
    mod.io.in := invalid_val

    assert(mod.io.valid === false.B)
  }

  stop()
}

class CastToInvalidEnumTester extends BasicTester {
  val invalid_value: UInt = EnumExample.litValues.last + 1.U
  Module(new CastFromLit(invalid_value))
}

class EnumOpsTester extends BasicTester {
  for (x <- EnumExample.all;
       y <- EnumExample.all) {
    val mod = Module(new EnumOps(EnumExample(), EnumExample()))
    mod.io.x := x
    mod.io.y := y

    assert(mod.io.lt === (x.asUInt() < y.asUInt()))
    assert(mod.io.le === (x.asUInt() <= y.asUInt()))
    assert(mod.io.gt === (x.asUInt() > y.asUInt()))
    assert(mod.io.ge === (x.asUInt() >= y.asUInt()))
    assert(mod.io.eq === (x.asUInt() === y.asUInt()))
    assert(mod.io.ne === (x.asUInt() =/= y.asUInt()))
  }
  stop()
}

class InvalidEnumOpsTester extends BasicTester {
  val mod = Module(new EnumOps(EnumExample(), OtherEnum()))
  mod.io.x := EnumExample.e0
  mod.io.y := OtherEnum.otherEnum
}

class IsLitTester extends BasicTester {
  for (e <- EnumExample.all) {
    val wire = WireInit(e)

    assert(e.isLit())
    assert(!wire.isLit())
  }
  stop()
}

class StrongEnumFSMTester extends BasicTester {
  val dut = Module(new StrongEnumFSM)

  // Inputs and expected results
  val inputs: Vec[Bool] = VecInit(false.B, true.B, false.B, true.B, true.B, true.B, false.B, true.B, true.B, false.B)
  val expected: Vec[Bool] = VecInit(false.B, false.B, false.B, false.B, false.B, true.B, true.B, false.B, false.B, true.B)

  val cntr = Counter(inputs.length)
  val cycle = cntr.value

  dut.io.in := inputs(cycle)
  assert(dut.io.out === expected(cycle))

  when(cntr.inc()) {
    stop()
  }
}

class StrongEnumSpec extends ChiselFlatSpec {
  import chisel3.core.EnumExceptions._
  import chisel3.internal.ChiselException

  behavior of "Strong enum tester"

  it should "fail to instantiate enums without a companion class" in {
    an [EnumHasNoCompanionObjectException] should be thrownBy {
      elaborate(new SimpleConnector(new EnumWithoutCompanionObj(), new EnumWithoutCompanionObj()))
    }
  }

  it should "fail to instantiate non-literal enums in a companion object" in {
    an [ExceptionInInitializerError] should be thrownBy {
      elaborate(new SimpleConnector(new NonLiteralEnumType(), new NonLiteralEnumType()))
    }
  }

  it should "connect enums of the same type" in {
    elaborate(new SimpleConnector(EnumExample(), EnumExample()))
  }

  it should "fail to connect a strong enum to a UInt" in {
    a [ChiselException] should be thrownBy {
      elaborate(new SimpleConnector(EnumExample(), UInt()))
    }
  }

  it should "fail to connect enums of different types" in {
    an [ChiselException] should be thrownBy {
      elaborate(new SimpleConnector(EnumExample(), OtherEnum()))
    }
  }

  it should "cast enums to UInts correctly" in {
    assertTesterPasses(new CastToUIntTester)
  }

  it should "cast literal UInts to enums correctly" in {
    assertTesterPasses(new CastFromLitTester)
  }

  it should "cast non-literal UInts to enums correctly and detect illegal casts" in {
    assertTesterPasses(new CastFromNonLitTester)
  }

  it should "prevent illegal literal casts to enums" in {
    a [ChiselException] should be thrownBy {
      elaborate(new CastToInvalidEnumTester)
    }
  }

  it should "only allow non-literal casts to enums if the width is smaller than or equal to the enum width" in {
    for (w <- 0 to EnumExample.getWidth)
      elaborate(new CastFromNonLitWidth(Some(w)))

    a [ChiselException] should be thrownBy {
      elaborate(new CastFromNonLitWidth)
    }

    for (w <- (EnumExample.getWidth+1) to (EnumExample.getWidth+100)) {
      a [ChiselException] should be thrownBy {
        elaborate(new CastFromNonLitWidth(Some(w)))
      }
    }
  }

  it should "execute enum comparison operations correctly" in {
    assertTesterPasses(new EnumOpsTester)
  }

  it should "fail to compare enums of different types" in {
    an [EnumTypeMismatchException] should be thrownBy {
      elaborate(new InvalidEnumOpsTester)
    }
  }

  it should "correctly check whether or not enums are literal" in {
    assertTesterPasses(new IsLitTester)
  }

  it should "return the correct widths for enums" in {
    EnumExample.getWidth == EnumExample.litValues.last.getWidth
  }

  "StrongEnum FSM" should "work" in {
    assertTesterPasses(new StrongEnumFSMTester)
  }
}

class StrongEnumAnnotationSpec extends FreeSpec with Matchers {
  import EnumAnnotations._

  "Test that strong enums annotate themselves appropriately" in {

    def test() = {
      Driver.execute(Array("--target-dir", "test_run_dir"), () => new StrongEnumFSM) match {
        case ChiselExecutionSuccess(Some(circuit), emitted, _) =>
          val annos = circuit.annotations.map(_.toFirrtl)

          val enumDefAnnos = annos.collect { case a: EnumDefAnnotation => a }
          val enumCompAnnos = annos.collect { case a: EnumComponentAnnotation => a }

          // Check that the global annotation is correct
          enumDefAnnos.exists {
            case EnumDefAnnotation(name, map) =>
              name.endsWith("State") &&
                map.size == StrongEnumFSM.State.correct_annotation_map.size &&
                map.forall {
                  case (k, v) =>
                    val correctValue = StrongEnumFSM.State.correct_annotation_map(k)

                    val correctValLit = correctValue.litValue()
                    val vLitValue = v.litValue()

                    correctValue.getWidth == v.getWidth && correctValue.litValue() == v.litValue()
                }
            case _ => false
          } should be(true)

          // Check that the component annotations are correct
          enumCompAnnos.count {
            case EnumComponentAnnotation(target, enumName) =>
              val ComponentName(targetName, _) = target
              (targetName == "state" && enumName.endsWith("State")) ||
                (targetName == "io.state" && enumName.endsWith("State"))
            case _ => false
          } should be(2)

        case _ =>
          assert(false)
      }
    }

    // We run this test twice, to test for an older bug where only the first circuit would be annotated
    test()
    test()
  }
}

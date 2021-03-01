# ChiselEnum

The ChiselEnum type is an easy way to get away from value encodings and reduce the chance of error when encoding muxes, opcodes, and functional unit operations. As opposed to the Chisel type Enums, ChiselEnums allow for names to be explicitly passed through Bundles like IO. 

## Importing the ChiselEnum module

```scala mdoc:silent
import chisel3._
import chisel3.stage.ChiselStage
import chisel3.experimental.ChiselEnum

```

## Functionality and Examples

Below we see ChiselEnum being used as mux select for a RISC-V core. While wrapping the object in a package is not required, it is highly recommended as it allows for the type to be used in multiple files more easily. 

```scala mdoc:silent
// package CPUTypes {
    object AluMux1Sel extends ChiselEnum {
        val rs1out, pcout = Value
        /**
            "rs1out" -> 0.U,
            "pcout"  -> 1.U
        */
    }
// }
```

Here we see a mux using the AluMux1Sel to select between different inputs. 

```scala mdoc:silent
import CPUTypes.AluMux1Sel
import CPUTypes.AluMux1Sel._

class AluMux1File(val dl_size: Int) extends Module {
    val io = IO(new Bundle {
        val aluMux1Sel =  Input( AluMux1Sel() )
        val rs1Out     =  Input(Bits(dl_size.W))
        val pcOut      =  Input(Bits(dl_size.W))
        val aluMux1Out = Output(Bits(dl_size.W))
    })

    // Default value for aluMux1Out
    io.aluMux1Out := 0.U

    switch (io.aluMux1Sel) {
        is (rs1out) {
            io.aluMux1Out  := io.rs1Out
        }
        is (pcout) {
            io.aluMux1Out  := io.pcOut
        }
    }
}
```
```scala mdoc:verilog
import 
ChiselStage.emitVerilog(new AluMux1File(dl_size = 32) )
```

ChiselEnum also allows for the user to define variables by passing in the value shown below. Note that the value must be increasing or else 
 > chisel3.internal.ChiselException: Exception thrown when elaborating ChiselGeneratorAnnotation
is thrown during Verliog generation.

```scala mdoc:silent
object Opcode extends ChiselEnum {
    val load  = Value(0x03.U) // i load  -> 000_0011
    val imm   = Value(0x13.U) // i imm   -> 001_0011
    val auipc = Value(0x17.U) // u auipc -> 001_0111
    val store = Value(0x23.U) // s store -> 010_0011
    val reg   = Value(0x33.U) // r reg   -> 011_0011
    val lui   = Value(0x37.U) // u lui   -> 011_0111
    val br    = Value(0x63.U) // b br    -> 110_0011
    val jalr  = Value(0x67.U) // i jalr  -> 110_0111
    val jal   = Value(0x6F.U) // j jal   -> 110_1111
}
```

The user can 'jump' to a value and continue incrementing by passing a start point then using a regular Value assignment. 

```scala mdoc:silent
object BranchFunct3 extends ChiselEnum {
    val beq, bne = Value
    val blt = Value(4.U)
    val bge, bltu, bgeu = Value
    /**
        "beq"  -> 0.U,
        "bne"  -> 1.U,
        "blt"  -> 4.U,
        "bge"  -> 5.U,
        "bltu" -> 6.U,
        "bgeu" -> 7.U
    */
}
```

## Testing

When testing your modules, the `.Type` and `.litValue` attributes allow for the the objects to be passed as parameters and for the value to be converted to BigInt type. Note that BigInts cannot be casted to Int with `.asInstanceOf[Int]`, they use their own methods like `toInt`. Please review the [scala.math.BigInt](https://www.scala-lang.org/api/2.12.5/scala/math/BigInt.html) page for more details!

```scala mdoc:silent
def expectedSel(sel: AluMux1Sel.Type): Boolean = sel match {
  case AluMux1Sel.rs1out => (sel.litValue == 0)
  case AluMux1Sel.pcout  => (sel.litValue == 1)
  case _                 => false
}
```

The ChiselEnum type also has methods `.all` and `.getWidth` where all returns all of the enum instances and getWidth returns the width of the hardware type.

## Workarounds

As of 2/26/2021, the width of the values is always infered so to get around this, just add an extra state that forces the width that is desired. 

```scala mdoc:silent
object StoreFunct3 extends ChiselEnum {
    val sb, sh, sw = Value
    val ukn = Value(7.U)
    /**
        "sb" -> 0.U,
        "sh" -> 1.U,
        "sw" -> 2.U
    */
}
```

Signed values are not supported so if you want the value signed, you must cast the UInt with `.asSInt`.

## Additional Resources

The ChiselEnum type is much more powerful than stated above. It allows for Sequence, Vec, and Bundle assignments as well as the `.next` operation to allow for stepping through sequential states and the `.isValid` for checking that the value is an enum type. The source code for the ChiselEnum can be found [here](https://github.com/chipsalliance/chisel3/blob/2a96767097264eade18ff26e1d8bce192383a190/core/src/main/scala/chisel3/StrongEnum.scala) in the class `EnumFactory`. An example file for many of the ChiselEnum operations can be found [here](https://github.com/chipsalliance/chisel3/blob/dd6871b8b3f2619178c2a333d9d6083805d99e16/src/test/scala/chiselTests/StrongEnum.scala).
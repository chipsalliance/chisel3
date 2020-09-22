// See LICENSE for license details.

package chiselTests

import chisel3._
<<<<<<< HEAD
import chisel3.util._
=======
import chisel3.stage.ChiselStage
import chisel3.util.{switch, is}
>>>>>>> 9c4f14fb... Support using switch without importing SwitchContext (#1595)

class SwitchSpec extends ChiselFlatSpec {
  "switch" should "require literal conditions" in {
    a [java.lang.IllegalArgumentException] should be thrownBy {
      elaborate(new Module {
        val io = IO(new Bundle {})
        val state = RegInit(0.U)
        val wire = WireDefault(0.U)
        switch (state) {
          is (wire) { state := 1.U }
        }
      })
    }
  }
  it should "require mutually exclusive conditions" in {
    a [java.lang.IllegalArgumentException] should be thrownBy {
      elaborate(new Module {
        val io = IO(new Bundle {})
        val state = RegInit(0.U)
        switch (state) {
          is (0.U) { state := 1.U }
          is (1.U) { state := 2.U }
          is (0.U) { state := 3.U }
        }
      })
    }
  }
}

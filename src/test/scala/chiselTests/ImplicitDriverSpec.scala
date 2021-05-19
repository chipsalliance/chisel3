// SPDX-License-Identifier: Apache-2.0

package chiselTests

import chisel3.stage._
import org.scalatest.flatspec.AnyFlatSpec

class ImplicitDriverSpec extends AnyFlatSpec {
  "implicit driver" should "emit verilog without error" in {
    (new GCD).verilog
  }
  "implicit driver" should "emit firrtl without error" in {
    (new GCD).firrtl
  }
  "implicit driver" should "emit chirrtl without error" in {
    (new GCD).chirrtl
  }
  "implicit driver" should "emit system verilog without error" in {
    (new GCD).systemVerilog
  }
  "implicit driver" should "compile to low firrtl" in {
    (new GCD).compile("-X", "low")
  }
  "implicit driver" should "execute with annotations" in {
    (new GCD).execute()(Seq(NoRunFirrtlCompilerAnnotation))
  }
}
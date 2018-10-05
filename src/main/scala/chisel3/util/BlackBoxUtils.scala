// See LICENSE for license details.

package chisel3.util

import chisel3._
import chisel3.experimental.{ChiselAnnotation, RunFirrtlTransform}
import firrtl.transforms.{BlackBoxPathAnno, BlackBoxResourceAnno, BlackBoxInlineAnno, BlackBoxSourceHelper}

trait HasBlackBoxResource extends BlackBox {
  self: BlackBox =>

  @deprecated("Use addResource instead", "3.2")
  def setResource(blackBoxResource: String): Unit = addResource(blackBoxResource)

  def addResource(blackBoxResource: String): Unit = {
    val anno = new ChiselAnnotation with RunFirrtlTransform {
      def toFirrtl = BlackBoxResourceAnno(self.toNamed, blackBoxResource)
      def transformClass = classOf[BlackBoxSourceHelper]
    }
    chisel3.experimental.annotate(anno)
  }
}

trait HasBlackBoxInline extends BlackBox {
  self: BlackBox =>

  def setInline(blackBoxName: String, blackBoxInline: String): Unit = {
    val anno = new ChiselAnnotation with RunFirrtlTransform {
      def toFirrtl = BlackBoxInlineAnno(self.toNamed, blackBoxName, blackBoxInline)
      def transformClass = classOf[BlackBoxSourceHelper]
    }
    chisel3.experimental.annotate(anno)
  }
}

trait HasBlackBoxPath extends BlackBox {
  self: BlackBox =>

  def addPath(blackBoxPath: String): Unit = {
    val anno = new ChiselAnnotation with RunFirrtlTransform {
      def toFirrtl = BlackBoxPathAnno(self.toNamed, blackBoxPath)
      def transformClass = classOf[BlackBoxSourceHelper]
    }
    chisel3.experimental.annotate(anno)
  }
}

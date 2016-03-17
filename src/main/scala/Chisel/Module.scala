// See LICENSE for license details.

package Chisel

import scala.collection.mutable.{ArrayBuffer, HashSet}

import internal._
import internal.Builder.pushCommand
import internal.Builder.dynamicContext
import internal.firrtl._

object Module {
  /** A wrapper method that all Module instantiations must be wrapped in
    * (necessary to help Chisel track internal state).
    *
    * @param m the Module being created
    *
    * @return the input module `m` with Chisel metadata properly set
    */
  def apply[T <: Module](bc: => T): T = {
    val parent = dynamicContext.currentModule
    val m = bc.setRefs()
    m._commands.prepend(DefInvalid(m.io.ref)) // init module outputs
    dynamicContext.currentModule = parent
    val ports = m.computePorts
    Builder.components += Component(m, m.name, ports, m._commands)
    pushCommand(DefInstance(m, ports))
    pushCommand(DefInvalid(m.io.ref)) // init instance inputs
    m.connectImplicitIOs()
  }
}

/** Abstract base class for Modules, which behave much like Verilog modules.
  * These may contain both logic and state which are written in the Module
  * body (constructor).
  *
  * @note Module instantiations must be wrapped in a Module() call.
  */
abstract class Module(_clock: Clock = null, _reset: Bool = null) extends HasId {
  private val _namespace = Builder.globalNamespace.child
  private[Chisel] val _commands = ArrayBuffer[Command]()
  private[Chisel] val _ids = ArrayBuffer[HasId]()
  dynamicContext.currentModule = Some(this)

  /** Name of the instance. */
  val name = Builder.globalNamespace.name(getClass.getName.split('.').last)

  /** IO for this Module. At the Scala level (pre-FIRRTL transformations),
    * connections in and out of a Module may only go through `io` elements.
    */
  def io: Bundle
  val clock = Clock(INPUT)
  val reset = Bool(INPUT)
  /** globalReset is a simulation-only signal for ensuring assertions don't
    * fire before the circuit comes out of reset
    * It is the top-level Module's reset propagated throughout the circuit
    */
  private[Chisel] val globalReset = _parent match {
    case Some(p) => Bool(INPUT)
    case None => reset
  }

  private[Chisel] def addId(d: HasId) { _ids += d }
  private[Chisel] def ref = Builder.globalRefMap(this)
  private[Chisel] def lref = ref

  private def ports = (clock, "clk") :: (reset, "reset") :: (io, "io") ::
    (if (_parent.nonEmpty && !this.isInstanceOf[BlackBox]) (globalReset, "globalReset") :: Nil
    else Nil)

  private[Chisel] def computePorts = ports map { case (port, name) =>
    val bundleDir = if (port.isFlip) INPUT else OUTPUT
    Port(port, if (port.dir == NO_DIR) bundleDir else port.dir)
  }

  private def connectImplicitIOs(): this.type = _parent match {
    case Some(p) =>
      clock := (if (_clock eq null) p.clock else _clock)
      reset := (if (_reset eq null) p.reset else _reset)
      if (!this.isInstanceOf[BlackBox]) globalReset := p.globalReset
      this
    case None => this
  }

  private def makeImplicitIOs(): Unit = ports map { case (port, name) =>
  }

  private def setRefs(): this.type = {
    for ((port, name) <- ports)
      port.setRef(ModuleIO(this, _namespace.name(name)))

    val valNames = HashSet[String](getClass.getDeclaredFields.map(_.getName):_*)
    def isPublicVal(m: java.lang.reflect.Method) =
      m.getParameterTypes.isEmpty && valNames.contains(m.getName)
    val methods = getClass.getMethods.sortWith(_.getName > _.getName)
    for (m <- methods; if isPublicVal(m)) m.invoke(this) match {
      case id: HasId => id.setRef(_namespace.name(m.getName))
      case _ =>
    }

    _ids.foreach(_.setRef(_namespace.name("T")))
    _ids.foreach(_._onModuleClose)
    this
  }
}

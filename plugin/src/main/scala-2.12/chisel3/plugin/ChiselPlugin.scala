// See LICENSE for license details.

package chisel3.plugin

import scala.tools.nsc
import nsc.{Global, Phase}
import nsc.plugins.Plugin
import nsc.plugins.PluginComponent
import scala.reflect.internal.Flags
import scala.tools.nsc.transform.TypingTransformers

// The plugin to be run by the Scala compiler during compilation of Chisel code
class ChiselPlugin(val global: Global) extends Plugin {
  val name = "chiselplugin"
  val description = "Plugin for Chisel 3 Hardware Description Language"
  val components = List[PluginComponent](new ChiselComponent(global))
}

// The component of the chisel plugin. Not sure exactly what the difference is between
//   a Plugin and a PluginComponent.
class ChiselComponent(val global: Global) extends PluginComponent with TypingTransformers {
  import global._
  val runsAfter = List[String]("typer")
  val phaseName: String = "chiselcomponent"
  def newPhase(_prev: Phase): ChiselComponentPhase = new ChiselComponentPhase(_prev)
  class ChiselComponentPhase(prev: Phase) extends StdPhase(prev) {
    override def name: String = phaseName
    def apply(unit: CompilationUnit): Unit = {
      // This plugin doesn't work on Scala 2.11. Rather than complicate the sbt build flow,
      // instead we just check the version and if its an early Scala version, the plugin does nothing
      if(scala.util.Properties.versionNumberString.split('.')(1).toInt >= 12) {
        unit.body = new MyTypingTransformer(unit).transform(unit.body)
      }
    }
  }

  class MyTypingTransformer(unit: CompilationUnit)
    extends TypingTransformer(unit) {

    // Determines if the chisel plugin should match on this type
    def shouldMatch(q: Type, bases: Seq[Tree]): Boolean = {

      // If subtype of Data or BaseModule, its a match!
      def terminate(t: Type): Boolean = bases.exists { base => t <:< inferType(base) }

      // Recurse through subtype hierarchy finding containers
      // Seen is only updated when we recurse into type parameters, thus it is typically small
      def recShouldMatch(s: Type, seen: Set[Type]): Boolean = {
        def outerMatches(t: Type): Boolean = {
          val str = t.toString
          str.startsWith("Option[") || str.startsWith("Iterable[")
        }
        if (terminate(s)) {
          true
        } else if (seen.contains(s)) {
          false
        } else if (outerMatches(s)) {
          // These are type parameters, loops *are* possible here
          recShouldMatch(s.typeArgs.head, seen + s)
        } else {
          // This is the standard inheritance hierarchy, Scalac catches loops here
          s.parents.exists( p => recShouldMatch(p, seen) )
        }
      }

      // If doesn't match container pattern, exit early
      def earlyExit(t: Type): Boolean = {
        !(t.matchesPattern(inferType(tq"Iterable[_]")) || t.matchesPattern(inferType(tq"Option[_]")))
      }

      // First check if a match, then check early exit, then recurse
      if(terminate(q)){
        true
      } else if(earlyExit(q)) {
        false
      } else {
        recShouldMatch(q, Set.empty)
      }
    }

    // Given a type tree, infer the type and return it
    def inferType(t: Tree): Type = localTyper.typed(t, nsc.Mode.TYPEmode).tpe

    // These were found through trial and error
    def okFlags(mods: Modifiers): Boolean = {
      val badFlags = Set(
        Flag.PARAM,
        Flag.SYNTHETIC,
        Flag.DEFERRED,
        Flags.TRIEDCOOKING,
        Flags.CASEACCESSOR,
        Flags.PARAMACCESSOR
      )
      badFlags.forall{ x => !mods.hasFlag(x)}
    }

    // Indicates whether a ValDef is properly formed to get name
    def okVal(dd: ValDef, bases: Tree*): Boolean = {
      // Ensure expression isn't null, as you can't call `null.autoName("myname")`
      val isNull = dd.rhs match {
        case Literal(Constant(null)) => true
        case _ => false
      }
      okFlags(dd.mods) && shouldMatch(inferType(dd.tpt), bases) && !isNull && dd.rhs != EmptyTree
    }

    // Whether this val is directly enclosed by a Bundle type
    def inBundle(dd: ValDef): Boolean = {
      dd.symbol.logicallyEnclosingMember.thisType <:< inferType(tq"chisel3.Bundle")
    }

    def mixesModule(valDef: ValDef): Boolean = valDef match {
      case ValDef(_, _, tpt, _) if inferType(tpt) <:< inferType(tq"chisel3.experimental.BaseModule") => true
      case _ => false
    }

    def updateClass(dd: ClassDef): Tree = {
      val newImpl = (localTyper typed transform(dd.impl)).asInstanceOf[Template]
      val newBody = newImpl.body.map {
        case t @ ValDef(mods, name, tpt, rhs) if okFlags(mods) =>
          val newRhs = localTyper typed q"chisel3.plugin.APIs.nullifyIfInstance[$tpt]($rhs)"
          localTyper typed treeCopy.ValDef(t, mods, name, tpt, newRhs)
        case t @ ValDef(mods, name, tpt, rhs) if okFlags(mods) =>
          val newRhs = localTyper typed q"chisel3.plugin.APIs.nullifyIfInstance[$tpt]($rhs)"
          localTyper typed treeCopy.ValDef(t, mods, name, tpt, newRhs)
        case t: TermTree if t.tpe != NoType =>
          val curry = localTyper typed q"$t"
          localTyper typed q"chisel3.plugin.APIs.nullifyIfInstance[${TypeTree(curry.tpe)}]($curry)"
        case other => other
      }
      val finalImpl = (localTyper typed treeCopy.Template(newImpl, newImpl.parents, newImpl.self, newBody)).asInstanceOf[Template]
      val ret = localTyper typed treeCopy.ClassDef(dd, dd.mods, dd.name, dd.tparams, finalImpl)

      if(dd.name.toString.contains("ChildY")) {
        //error(showRaw(ret, printTypes = true))
        //error(show(ret))
        //println(showRaw(ret, printTypes = true))
        //println(show(dd))
        //println(showRaw(dd))
        //println(show(ret))
      }
      ret
    }

    // Method called by the compiler to modify source tree
    override def transform(tree: Tree): Tree = tree match {
      case dd @ ClassDef(mods, name, tparams, Template(parents, self, body)) if mixesModule(self) =>
        updateClass(dd)
      case dd @ ClassDef(mods, name, tparams, impl) if impl.tpe <:< inferType(tq"chisel3.experimental.BaseModule") =>
        updateClass(dd)

      // Don't go into accessors, as they are needed for updating vars
      case dd @ DefDef(mods, name, tparams, vparamss, tpt, rhs) if mods.hasFlag(Flags.ACCESSOR) => dd
      // Don't recurse into a new Module.Bundle constructing a class
      // We hit this case when you declare a Bundle inside a module - you reference a Select(outsidemodule, innerbundle)
      // calling 'new' on it
      case dd @ New(Select(quals, data)) if quals.tpe <:< inferType(tq"chisel3.experimental.BaseModule") => dd
      case dd @ Select(quals, data) if quals.tpe <:< inferType(tq"chisel3.experimental.BaseModule")  && dd.tpe <:< inferType(tq"Object") && !data.isInstanceOf[TypeName] => //&&
        //((quals.toString.contains("result") && data.toString.contains("compileOptions")) || quals.toString.contains("resolveBackingModule")) =>
        def resolve(): Tree = {
          val newQuals = localTyper typed transform(quals)
          val backingModule = localTyper typed q"chisel3.plugin.APIs.resolveBackingModule[${quals.tpe}]($newQuals)"
          val newSelection = localTyper typed treeCopy.Select(dd, backingModule, data)
          val ret = localTyper typed q"chisel3.plugin.APIs.resolveModuleAccess[${dd.tpe}]($newQuals, $newSelection)"
          ret
        }
        val ret = quals match {
          // If not a member of the parent class
          case Ident(TermName(_)) => resolve()
          // If a member of the parent class
          case Select(_, TermName(_)) => resolve()
          // If a member of this class
          case This(typeName) => resolve()
          // If return value from a function
          case t@TypeApply(fun, args) if fun.toString.contains("asInstanceOf") =>
            val newFun = transform(fun)
            val ret = localTyper typed treeCopy.TypeApply(t, newFun, args)
            println(show(ret))
            println(showRaw(ret, true))
            //super.transform(dd)
            localTyper typed treeCopy.Select(dd, ret, data)
          case Apply(fun, args) if !fun.toString.contains("resolveBackingModule") => resolve()
          case TypeApply(fun, args) if !fun.toString.contains("resolveBackingModule") => resolve()
          case _ => super.transform(dd)
        }
        ret
        //if(quals.toString.contains("resolveBackingModule")) {
        //  dd match {
        //    case Select(Apply(TypeApply(Select(Select(Select(Ident(_), _), _), TermName("resolveBackingModule")), List(t: TypeTree)), List(Ident(TermName("result")))), TermName("compileOptions")) =>
        //      println("HERHERHERHEH")
        //      println(showRaw(t))
        //      println(showRaw(t.original))
        //  }
        //}
        //if(quals.toString.contains("result") && data.toString.contains("compileOptions")) {
        //  ret match {
        //    case Apply(_, List(Ident(TermName("result")),
        //         Select(Apply(TypeApply(Select(_, TermName("resolveBackingModule")), List(t: TypeTree)), List(Ident(TermName("result")))), TermName("compileOptions"))
        //    )) =>
        //      println("BLAHBLAH")
        //      println(showRaw(t))
        //      println(showRaw(t.original))
        //      val tpeTree = Ident(TypeName("Foo")).defineType(quals.tpe)
        //      println(showRaw(tpeTree, true))
        //      val x = t.setOriginal(tpeTree)
        //      println(showRaw(t.original, true))
        //      println(showRaw(x))
        //      println(showRaw(t))

        //    case Select(Apply(TypeApply(Select(_, TermName("resolveBackingModule")), List(t: TypeTree)), List(Ident(TermName("result")))), TermName("compileOptions")) =>
        //      println("HERHERHERHEH")
        //      println(showRaw(t))
        //      println(showRaw(t.original, true))
        //  }
        //}
        //println("++++++++++")
        //// Select(Apply(TypeApply(Select(Select(Select(Ident(chisel3), chisel3.plugin), chisel3.plugin.APIs), TermName("resolveBackingModule")), List(TypeTree())), List(Ident(TermName("result")))), TermName("compileOptions"))))
        //// Select(Apply(TypeApply(Select(Select(Select(Ident(chisel3), chisel3.plugin), chisel3.plugin.APIs), TermName("resolveBackingModule")), List(TypeTree().setOriginal(Ident(TypeName("Foo"))))), List(Ident(TermName("result")))), TermName("compileOptions"))
        //println(showRaw(dd))
        //println(showRaw(ret, true))
        //println("==========")
      // If a Data and in a Bundle, just get the name but not a prefix
      case dd @ ValDef(mods, name, tpt, rhs) if okVal(dd, tq"chisel3.Data") && inBundle(dd) =>
        val TermName(str: String) = name
        val newRHS = transform(rhs)
        val named = q"chisel3.experimental.autoNameRecursively($str, $newRHS)"
        treeCopy.ValDef(dd, mods, name, tpt, localTyper typed named)
      // If a Data or a Memory, get the name and a prefix
      case dd @ ValDef(mods, name, tpt, rhs) if okVal(dd, tq"chisel3.Data", tq"chisel3.MemBase[_]") =>
        val TermName(str: String) = name
        val newRHS = transform(rhs)
        val prefixed = q"chisel3.experimental.prefix.apply[$tpt](name=$str)(f=$newRHS)"
        val named = q"chisel3.experimental.autoNameRecursively($str, $prefixed)"
        treeCopy.ValDef(dd, mods, name, tpt, localTyper typed named)
      // If an instance, just get a name but no prefix
      case dd @ ValDef(mods, name, tpt, rhs) if okVal(dd, tq"chisel3.experimental.BaseModule") =>
        val TermName(str: String) = name
        val newRHS = transform(rhs)
        val named = q"chisel3.experimental.autoNameRecursively($str, $newRHS)"
        treeCopy.ValDef(dd, mods, name, tpt, localTyper typed named)
      // Otherwise, continue
      case _ => super.transform(tree)
    }
  }
}

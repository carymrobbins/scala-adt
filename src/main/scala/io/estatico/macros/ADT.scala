package io.estatico.macros

import scala.annotation.StaticAnnotation
import scala.collection.mutable
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

class ADT extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro ADT.impl
}

object ADT {

  val MACRO_NAME = "@ADT"

  def impl(c: whitebox.Context)(annottees: c.Expr[Any]*): c.universe.Tree = {
    import c.universe._

    def fail(msg: String) = c.abort(c.enclosingPosition, msg)

    // Executed at the end of this macro
    def run(): Tree = annottees match {
      // @ADT trait Foo { ... }
      case List(Expr(cls: ClassDef)) => runClass(cls)

      // @ADT trait Foo { ... }; object Foo { ... }
      case List(Expr(cls: ClassDef), Expr(obj: ModuleDef)) => runClassWithObj(cls, obj)
      case List(Expr(obj: ModuleDef), Expr(cls: ClassDef)) => runClassWithObj(cls, obj)

      case _ => fail(s"Invalid $MACRO_NAME usage")
    }

    type Ctor = (TypeName, List[CtorArg])
    type CtorArg = (TermName, TypeName)

    def runClass(cls: ClassDef) = runClassWithCompanion(cls, Nil)

    def runClassWithObj(cls: ClassDef, obj: ModuleDef) = {
      val (clsName, objName) = (cls.name.toString, obj.name.toString)
      if (clsName != objName) fail(s"Companion name mismatch: trait $clsName, object $objName")
      val ModuleDef(_, _, objTemplate) = obj
      runClassWithCompanion(cls, objTemplate.body)
    }

    def runClassWithCompanion(cls: ClassDef, objBody: List[Tree]) = {
      val ClassDef(clsMods, clsName, clsParams, clsTemplate) = cls
      if (!clsMods.hasFlag(Flag.TRAIT)) fail(s"$MACRO_NAME requires trait")
      val (ctors, clsRestBody) = partitionCtorsAndBody(clsTemplate)
      q"""
        ${mkCompanion(clsName.toTermName, objBody, ctors, clsParams)}

        sealed trait $clsName[..$clsParams] {
          ${importCtors(clsName.toTermName, ctors)}
          ..$clsRestBody
        }
      """
    }

    def importCtors(name: TermName, ctors: List[Ctor]) = {
      val importNames = ctors.map { case (ctorName, _) => pq"$ctorName" }
      q"import $name.{ ..$importNames }"
    }

    def mkCompanion
        (name: TermName,
         objBody: List[Tree],
         ctors: List[Ctor],
         clsParams: List[TypeDef]) = {
      val ctorDefs = ctors.map(mkCtorDef(_, name.toTypeName, getTypeParams(clsParams)))
      val ctorVals = ctors.map { case (ctorName, _) =>
        q"val ${ctorName.toTermName} = $name.${ctorName.toTermName}"
      }
      val ctorsObj = q"object ctors { ..$ctorVals }"
      q"""
        object $name {
          ..${skipInitDef(objBody)}
          ..$ctorDefs
          $ctorsObj
        }
      """
    }

    def getTypeParams(params: List[TypeDef]): List[TypeName] = {
      params.collect {
        case t: TypeDef if t.mods.hasFlag(Flag.PARAM) => t.name
      }
    }

    def partitionCtorsAndBody(template: Template): (List[Ctor], List[Tree]) = {
      val ctors = new mutable.ListBuffer[Ctor]
      val body = new mutable.ListBuffer[Tree]
      skipInitDef(template.body).foreach {
        // NOTE: We check `if body.isEmpty` to ensure constructors are only inferred
        // from the start of the body before any vals, defs, etc.
        case Apply(Ident(name: TermName), args) if body.isEmpty =>
          ctors += ((name.toTypeName, getCtorArgs(args)))
        case Ident(name: TermName) if body.isEmpty =>
          ctors += ((name.toTypeName, Nil))

        case other => body += other
      }
      (ctors.toList, body.toList)
    }

    lazy val INIT_DEFS = Set("$init$", "<init>")

    def skipInitDef(body: List[Tree]) = {
      // NOTE: We ignore the $init$ and <init> methods as they are generated by our quasiquotes.
      body.filter {
        case DefDef(_, name, _, _, _, _) => !INIT_DEFS.contains(name.toString)
        case _ => true
      }
    }

    def getCtorArgs(args: List[Tree]): List[CtorArg] = {
      args.map {
        case Typed(Ident(name: TermName), Ident(typ: TypeName)) => (name, typ)
        case other => fail(s"Unsupported constructor argument: $other; AST: ${showRaw(other)}")
      }
    }

    def mkCtorDef(ctor: Ctor, parentName: TypeName, typeParams: List[TypeName]) = {
      ctor match {
        case (name, Nil) => mkCaseObject(name.toTermName, typeParams, parentName)
        case (name, args) => mkCaseClass(name, args, typeParams, parentName)
      }
    }

    def mkCaseObject(name: TermName, typeParams: List[TypeName], parentName: TypeName) = {
      val traitTypeParams = typeParams.map(_ => Ident(TypeName("Nothing")))
      val parent = AppliedTypeTree(Ident(parentName), traitTypeParams)
      q"case object ${name.toTermName} extends $parent"
    }

    def mkCaseClass(name: TypeName, args: List[CtorArg], typeParams: List[TypeName], parentName: TypeName) = {
      val ctorTypeParams = args.map(_._2)
      val traitTypeParams = typeParams.map(
        p => if (ctorTypeParams.contains(p)) Ident(p) else Ident(TypeName("Nothing"))
      )
      val parent = AppliedTypeTree(Ident(parentName), traitTypeParams)
      val clsTypeParams = ctorTypeParams.map(
        p => TypeDef(Modifiers(Flag.PARAM), p, List(), TypeBoundsTree(EmptyTree, EmptyTree))
      )
      val clsArgs = args.map { case (argName, argType) =>
        ValDef(
          Modifiers(Flag.CASEACCESSOR | Flag.PARAMACCESSOR),
          argName, Ident(argType), EmptyTree
        )
      }
      // final case class $name extends $parent
      q"final case class $name[..$clsTypeParams](..$clsArgs) extends $parent"
    }

    run()
  }
}
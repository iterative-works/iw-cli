// PURPOSE: Generic reflection-based discovery of hook values from plugin objects
// PURPOSE: Reads IW_HOOK_CLASSES env var and extracts values of a given type from hook objects

package iw.core.adapters

import iw.core.model.Constants
import scala.reflect.ClassTag

object HookDiscovery:

  /** Collect all values of type T from discovered hook objects.
    *
    * Hook objects are Scala singleton objects whose class names are passed via
    * the IW_HOOK_CLASSES environment variable (comma-separated). For each
    * object, all no-arg methods returning type T are invoked and results
    * collected.
    *
    * @tparam T
    *   The type of values to collect (e.g., Check, SessionAction)
    * @return
    *   List of discovered values, empty if no hooks or no matching values
    */
  def collectValues[T: ClassTag]: List[T] =
    val hookClasses = sys.env.getOrElse(Constants.EnvVars.IwHookClasses, "")
    if hookClasses.isEmpty then Nil
    else
      val targetClass = summon[ClassTag[T]].runtimeClass
      hookClasses.split(",").toList.flatMap { className =>
        try
          val clazz =
            Class.forName(
              s"$className$$"
            ) // Scala object class names end with $
          val instance = clazz
            .getField(Constants.ScalaReflection.ModuleField)
            .get(Option.empty[AnyRef].orNull)

          clazz.getDeclaredMethods
            .filter(m =>
              targetClass.isAssignableFrom(m.getReturnType)
                && m.getParameterCount == 0
            )
            .flatMap { method =>
              try Some(method.invoke(instance).asInstanceOf[T])
              catch case _: Exception => None
            }
            .toList
        catch case _: Exception => Nil
      }

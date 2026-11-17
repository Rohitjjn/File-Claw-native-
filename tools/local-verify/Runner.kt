import org.junit.*
import org.junit.rules.TemporaryFolder
import java.lang.reflect.InvocationTargetException

fun main(args: Array<String>) {
    var pass = 0; var fail = 0
    for (cn in args) {
        val k = Class.forName(cn)
        val methods = k.methods.filter { it.isAnnotationPresent(Test::class.java) }.sortedBy { it.name }
        val before = k.methods.filter { it.isAnnotationPresent(Before::class.java) }
        val after = k.methods.filter { it.isAnnotationPresent(After::class.java) }
        for (m in methods) {
            val inst = k.getDeclaredConstructor().newInstance()
            // @get:Rule TemporaryFolder fields: create before, delete after (mirrors JUnit).
            val ruleFolders = k.declaredFields.filter { f ->
                val getter = try { k.getMethod("get" + f.name.replaceFirstChar { c -> c.uppercaseChar() }) } catch (_: Throwable) { null }
                getter != null && getter.isAnnotationPresent(Rule::class.java) &&
                    TemporaryFolder::class.java.isAssignableFrom(f.type)
            }.mapNotNull { f ->
                try {
                    f.isAccessible = true
                    f.get(inst) as? TemporaryFolder
                } catch (_: Throwable) { null }
            }
            val expected = m.getAnnotation(Test::class.java).expected.java
            var err: Throwable? = null
            try {
                ruleFolders.forEach { it.create() }
                before.forEach { it.invoke(inst) }
                m.invoke(inst)
            } catch (e: InvocationTargetException) { err = e.targetException } catch (e: Throwable) { err = e }
            finally {
                after.forEach { try { it.invoke(inst) } catch (_: Throwable) {} }
                ruleFolders.forEach { try { it.cleanup() } catch (_: Throwable) {} }
            }
            val ok = if (expected != Test.None::class.java) (err != null && expected.isInstance(err)) else err == null
            if (ok) pass++ else { fail++; println("FAIL ${k.simpleName}.${m.name}: $err"); err?.stackTrace?.take(4)?.forEach { println("    at $it") } }
        }
    }
    println("RESULT passed=$pass failed=$fail")
    System.exit(if (fail == 0) 0 else 1)
}

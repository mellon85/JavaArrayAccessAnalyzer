package staticAnalyzer;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.ClassReader;
import java.util.HashMap;
import java.util.Map;

public class Repository {
    private static Map<String, ClassNode> classes = new HashMap<String, ClassNode>();

    public static void addClass(ClassNode node) {
        classes.put(node.name, node);
    }

    public static ClassNode lookupClass(String name) throws ClassNotFoundException {
        ClassNode c = classes.get(name);
        if (c == null) {
            try {
                // Try to load system class
                ClassReader cr = new ClassReader(name);
                c = new ClassNode();
                cr.accept(c, 0);
                classes.put(name, c);
            } catch (Exception e) {
                throw new ClassNotFoundException(name);
            }
        }
        return c;
    }

    public static void clear() {
        classes.clear();
    }
}

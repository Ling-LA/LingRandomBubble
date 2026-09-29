import com.sun.source.util.JavacTask;
import javax.tools.*;
import java.io.File;
import java.nio.file.*;
import java.util.*;

/** Parse-only validation with the real JDK parser. NOT Android type checking or APK compilation. */
public final class JavaSyntaxCheck {
    public static void main(String[] args) throws Exception {
        Path root=Paths.get(args.length==0?"app/src/main/java":args[0]);
        List<File> files=new ArrayList<>();
        try(java.util.stream.Stream<Path> paths=Files.walk(root)) {
            paths.filter(p->p.toString().endsWith(".java")).forEach(p->files.add(p.toFile()));
        }
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        if(compiler==null) throw new IllegalStateException("JDK compiler not present");
        DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,null,java.nio.charset.StandardCharsets.UTF_8)) {
            JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,Arrays.asList("-proc:none","--release","8"),null,manager.getJavaFileObjectsFromFiles(files));
            for(Object tree:task.parse()) { /* force parsing of all files */ }
            long errors=diagnostics.getDiagnostics().stream().filter(d->d.getKind()==Diagnostic.Kind.ERROR).count();
            for(Diagnostic<?> d:diagnostics.getDiagnostics()) if(d.getKind()==Diagnostic.Kind.ERROR) System.err.println(d);
            if(errors!=0) throw new AssertionError(errors+" syntax errors");
            System.out.println("PASS: JDK parser accepted all "+files.size()+" production Java files. Parse only; no Android SDK type check.");
        }
    }
}

package gold.debug.packager.core;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

// 读取 Main-Class
public class JarManifestUtils {

    public static String readMainClass(Path jarPath) throws Exception {
        try (InputStream in = Files.newInputStream(jarPath);
             JarInputStream jis = new JarInputStream(in)) {
            Manifest mf = jis.getManifest();
            if (mf == null) return null;
            return mf.getMainAttributes().getValue("Main-Class");
        }
    }
}

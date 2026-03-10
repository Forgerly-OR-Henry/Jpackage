package gold.debug.packager.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.List;
import java.util.function.Consumer;

// 执行并实时输出日志
public class ProcessRunner {

    public static int run(List<String> cmd, Consumer<String> log) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true); // 合并 stdout/stderr
        Process p = pb.start();

        // Windows 中文环境常见编码问题：这里用系统默认编码更稳
        Charset cs = Charset.defaultCharset();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), cs))) {
            String line;
            while ((line = br.readLine()) != null) {
                log.accept(line);
            }
        }
        return p.waitFor();
    }
}

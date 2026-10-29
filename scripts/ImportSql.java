import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地开发直导脚本（仅本机 MySQL 5.7 验证用）：
 * Flyway 8.5 社区版不支持 5.7；生产 GreatDB(8.0 兼容) 与 H2 测试仍走 Flyway 门禁（ADR-002/A-10）。
 * 用法：java -cp <connector.jar> ImportSql.java <url> <user> <password> <file1> <file2> ...
 */
public class ImportSql {

    public static void main(String[] args) throws Exception {
        String url = args[0];
        String user = args[1];
        String password = args[2];
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            for (int i = 3; i < args.length; i++) {
                Path file = Paths.get(args[i]);
                System.out.println("== " + file.getFileName() + " ==");
                List<String> statements = split(file);
                try (Statement st = conn.createStatement()) {
                    int n = 0;
                    for (String sql : statements) {
                        st.execute(sql);
                        n++;
                    }
                    System.out.println("OK: " + n + " statements");
                }
            }
        }
    }

    /** 按 ;（行尾）切分 SQL，保留注释；足够覆盖本仓库迁移脚本形态 */
    static List<String> split(Path file) throws Exception {
        String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : content.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--") && current.length() == 0) {
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String sql = current.toString().trim();
                sql = sql.substring(0, sql.length() - 1);
                if (!sql.isBlank()) {
                    result.add(sql);
                }
                current.setLength(0);
            }
        }
        if (current.toString().trim().length() > 0) {
            result.add(current.toString().trim());
        }
        return result;
    }
}

package com.projlife.org.service.importexport;

import java.util.List;
import java.util.Map;

/**
 * 通用导入处理器（S0-10 导入框架）：解析 → 校验 → 落库，三段职责分离。
 *
 * @param <T> 行对象类型
 */
public interface RowImportHandler<T> {

    /** 模块标识（写入 sys_import_batch.module） */
    String module();

    /** 表头（第 1 行） */
    List<String> headers();

    /** 行对象解析（表头行之后的每一行，列号→单元格值） */
    T parse(int rowIdx, Map<Integer, String> cells);

    /** 行级校验；返回错误清单，空清单=通过 */
    List<String> validate(int rowIdx, T row);

    /** 全部校验通过后的事务内落库 */
    void persist(List<T> rows);
}

-- 物料主数据新增"描述"字段（用户需求：记录物料详细信息）
ALTER TABLE md_material ADD COLUMN description VARCHAR(1000) NULL COMMENT '物料详细描述' AFTER spec;

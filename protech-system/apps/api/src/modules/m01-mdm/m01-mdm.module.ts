import { Module } from '@nestjs/common';
import { MdmService } from './mdm.service';
import { ProjectService } from './project.service';
import { EntityController } from './entity.controller';
import { GroupController } from './group.controller';
import { PartyController } from './party.controller';
import { FarmController } from './farm.controller';
import { MaterialController } from './material.controller';
import { SupplierController } from './supplier.controller';
import { ProjectController } from './project.controller';
import { MdChangeController } from './md-change.controller';
import { SearchController } from './search.controller';

/**
 * M01 主数据管理（"一处建档、多处引用"唯一入口；项目档案全系统锚点）。
 * 依赖 M00 编码引擎/审计/配置；BR-08 引用禁自由文本、BR-09 编码不可变、DQ-01 查重拦截。
 */
@Module({
  controllers: [
    EntityController,
    GroupController,
    PartyController,
    FarmController,
    MaterialController,
    SupplierController,
    ProjectController,
    MdChangeController,
    SearchController,
  ],
  providers: [MdmService, ProjectService],
  exports: [MdmService, ProjectService],
})
export class M01MdmModule {}

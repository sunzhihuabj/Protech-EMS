import { Module } from '@nestjs/common';
import { AuthController } from './auth.controller';
import { UserController } from './user.controller';
import { DeptController } from './dept.controller';
import { RoleController } from './role.controller';
import { ConfigController } from './config.controller';
import { EnumController } from './enum.controller';
import { NotifyController } from './notify.controller';
import { FileController } from './file.controller';
import { CodeRuleController } from './code-rule.controller';
import { PmGrantController } from './pm-grant.controller';
import { AuditController } from './audit.controller';
import { MonitorController } from './monitor.controller';

/** M00 平台底座（04 册 §M00：用户/权限/编码引擎/审计/通知/配置） */
@Module({
  controllers: [
    AuthController,
    UserController,
    DeptController,
    RoleController,
    ConfigController,
    EnumController,
    NotifyController,
    FileController,
    CodeRuleController,
    PmGrantController,
    AuditController,
    MonitorController,
  ],
})
export class M00BaseModule {}

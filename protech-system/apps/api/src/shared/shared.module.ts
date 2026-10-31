import { Global, Module } from '@nestjs/common';
import { APP_GUARD } from '@nestjs/core';
import { JwtModule } from '@nestjs/jwt';
import { PrismaService } from './prisma.service';
import { AuthService } from './auth.service';
import { AuditService } from './audit.service';
import { AppConfigService } from './app-config.service';
import { CodeEngineService } from './code-engine.service';
import { ScopeService } from './scope.service';
import { NotifyService } from './notify.service';
import { StorageService } from './storage.service';
import { JwtAuthGuard, PermissionCache, PermissionGuard } from './guards';

/** 平台共享层（11 册 §三 shared/）：@Global 使守卫/引擎/审计对所有业务模块可注入 */
@Global()
@Module({
  imports: [
    JwtModule.register({
      secret: process.env.JWT_SECRET ?? 'protech-dev-secret',
      signOptions: { expiresIn: '30m' },
    }),
  ],
  providers: [
    { provide: APP_GUARD, useClass: JwtAuthGuard },
    { provide: APP_GUARD, useClass: PermissionGuard },
    PrismaService,
    AuthService,
    AuditService,
    AppConfigService,
    CodeEngineService,
    ScopeService,
    NotifyService,
    StorageService,
    PermissionCache,
  ],
  exports: [
    JwtModule,
    PrismaService,
    AuthService,
    AuditService,
    AppConfigService,
    CodeEngineService,
    ScopeService,
    NotifyService,
    StorageService,
    PermissionCache,
  ],
})
export class SharedModule {}

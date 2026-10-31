import {
  Controller,
  Get,
  Param,
  Post,
  Res,
  UploadedFile,
  UseInterceptors,
  Body,
  BadRequestException,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import type { Response } from 'express';
import { PrismaService } from '../../shared/prisma.service';
import { StorageService, MAX_FILE_BYTES } from '../../shared/storage.service';
import { CurrentUser, AuthUser, RequirePermission } from '../../shared/guards';
import { AuditService } from '../../shared/audit.service';
import { parseId } from '../../shared/serialize';

/** FR-M00-010 附件管理（按对象挂载；预览；单文件 ≤50MB；dev 磁盘驱动/prod MinIO） */
@Controller('files')
export class FileController {
  constructor(
    private readonly prisma: PrismaService,
    private readonly storage: StorageService,
    private readonly audit: AuditService,
  ) {}

  @Post()
  @RequirePermission('sys:file:upload')
  @UseInterceptors(FileInterceptor('file'))
  async upload(
    @UploadedFile() file: Express.Multer.File | undefined,
    @Body() body: { objectType?: string; objectId?: string },
    @CurrentUser() me: AuthUser,
  ) {
    if (!file) throw new BadRequestException('file 必填');
    if (file.size > MAX_FILE_BYTES) throw new BadRequestException('文件超过 50MB 上限（FR-M00-010）');
    const stored = this.storage.store(file.buffer, file.originalname, file.mimetype);
    const rec = await this.prisma.sysFile.create({
      data: {
        name: stored.name,
        path: stored.path,
        mimeType: stored.mimeType ?? null,
        size: BigInt(stored.size),
        md5: stored.md5,
        storageDriver: stored.driver,
        objectType: body.objectType ?? null,
        objectId: body.objectId ?? null,
        createdBy: me.id,
        updatedBy: me.id,
      },
    });
    await this.audit.log({ action: 'upload', objectType: 'SysFile', objectId: rec.id });
    return { id: rec.id.toString(), name: stored.name, size: stored.size, md5: stored.md5 };
  }

  @Get(':id/download')
  async download(@Param('id') id: string, @Res() res: Response) {
    const rec = await this.prisma.sysFile.findFirst({ where: { id: parseId(id), deletedAt: null } });
    if (!rec) throw new BadRequestException('文件不存在');
    res.setHeader('Content-Type', rec.mimeType ?? 'application/octet-stream');
    res.setHeader(
      'Content-Disposition',
      `attachment; filename="${encodeURIComponent(rec.name)}"`,
    );
    this.storage.stream(rec.path).pipe(res);
  }
}

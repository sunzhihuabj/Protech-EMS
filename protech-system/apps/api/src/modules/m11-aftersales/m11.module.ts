import { Module } from '@nestjs/common';
import { AfterArchiveController } from './m11-m12.module';
import { AfterArchiveService } from './archive.service';

/** M11 售后 + M12 档案（R2-B） */
@Module({
  controllers: [AfterArchiveController],
  providers: [AfterArchiveService],
  exports: [AfterArchiveService],
})
export class M11AfterModule {}

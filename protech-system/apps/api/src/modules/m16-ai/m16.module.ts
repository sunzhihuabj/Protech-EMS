import { Module } from '@nestjs/common';
import { AiController, ReissueController, ReportController } from './m14-m16.module';
import { AiService } from './ai.service';

/** M16 AI 能力 + 补货分级 + M14 预置报表（S6 集） */
@Module({
  controllers: [AiController, ReissueController, ReportController],
  providers: [AiService],
  exports: [AiService],
})
export class M16AiModule {}

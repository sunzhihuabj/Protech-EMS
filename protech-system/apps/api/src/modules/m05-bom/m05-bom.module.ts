import { Module } from '@nestjs/common';
import { BomController } from './bom.controller';
import { BomService } from './bom.service';

/** M05 BOM 管理（三类形态/版本/13 规则/ECN/齐套） */
@Module({
  controllers: [BomController],
  providers: [BomService],
  exports: [BomService],
})
export class M05BomModule {}

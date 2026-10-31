import { Module } from '@nestjs/common';
import { SupplyController } from './supply.controller';
import { SupplyService } from './supply.service';

/** M06 采购与库房（源头单据驱动+扫码过账+T+ 队列） */
@Module({
  controllers: [SupplyController],
  providers: [SupplyService],
  exports: [SupplyService],
})
export class M06SupplyModule {}

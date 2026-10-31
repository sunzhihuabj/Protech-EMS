import { Module } from '@nestjs/common';
import { SupplyController } from './supply.controller';
import { SupplyService } from './supply.service';
import { M07M08Module } from '../m07-delivery/m07-m08.modules';

/** M06 采购与库房（源头单据驱动+扫码过账+T+ 队列）；依赖 M07 发货→派工草稿联动 */
@Module({
  imports: [M07M08Module],
  controllers: [SupplyController],
  providers: [SupplyService],
  exports: [SupplyService],
})
export class M06SupplyModule {}

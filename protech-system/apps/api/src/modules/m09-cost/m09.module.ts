import { Module } from '@nestjs/common';
import { CostController } from './cost.controller';
import { CostService } from './cost.service';
import { StocktakeService } from './stocktake.service';

/** M09 成本 + M04 工时 + M06 盘点/呆滞（R2 深化） */
@Module({
  controllers: [CostController],
  providers: [CostService, StocktakeService],
  exports: [CostService, StocktakeService],
})
export class M09CostModule {}

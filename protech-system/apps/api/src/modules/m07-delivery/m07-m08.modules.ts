import { Module } from '@nestjs/common';
import { DeliveryController } from './m07-m08.controllers';
import { FinanceController } from './m07-m08.controllers';
import { DeliveryService } from './delivery.service';
import { FinanceService } from '../m08-finance/finance.service';

/** M07 工程交付 + M08 验收回款（BR-04/BR-02 硬卡点） */
@Module({
  controllers: [DeliveryController, FinanceController],
  providers: [DeliveryService, FinanceService],
  exports: [DeliveryService, FinanceService],
})
export class M07M08Module {}

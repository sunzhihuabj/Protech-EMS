import { Module } from '@nestjs/common';
import { IntegrationExtraController } from './m15-extra.controller';
import { EventBusService, DingtalkService, PrintService, ReconciliationService } from './integration-extra.service';
import { BusSubscriptions } from './bus-subscriptions';
import { M06SupplyModule } from '../m06-supply/m06-supply.module';

/** M15 系统集成（事件总线/钉钉/打印/对账/监控台） */
@Module({
  imports: [M06SupplyModule],
  controllers: [IntegrationExtraController],
  providers: [EventBusService, DingtalkService, PrintService, ReconciliationService, BusSubscriptions],
  exports: [EventBusService, DingtalkService, PrintService, ReconciliationService, BusSubscriptions],
})
export class M15IntegrationModule {}

import { Module, OnModuleInit } from '@nestjs/common';
import { BoardController } from './board.controller';
import { CardService } from './card.service';
import { ScanService } from './scan.service';
import { MeetingService, MetricService, ExecutiveService } from './meeting-metric.service';

/** M10 看板与早会（九板+红灯引擎+会议模式+指标引擎） */
@Module({
  controllers: [BoardController],
  providers: [CardService, ScanService, MeetingService, MetricService, ExecutiveService],
  exports: [CardService, ScanService, MetricService],
})
export class M10BoardModule implements OnModuleInit {
  constructor(private readonly metrics: MetricService) {}
  async onModuleInit() {
    await this.metrics.seedDefs();
  }
}

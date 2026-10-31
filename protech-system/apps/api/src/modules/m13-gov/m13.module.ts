import { Module } from '@nestjs/common';
import { DeptProjectViewController, GovController, ImportController } from './m13-m15.module';
import { DeptProjectViewService } from '../m10-board/dept-project-view.service';
import { GovService } from './gov.service';
import { M10BoardModule } from '../m10-board/m10-board.module';

/** M13 数据治理 + BV-01/BV-03 视图 + M15 导入引擎（S5 集）；复用 M10 指标定义 */
@Module({
  imports: [M10BoardModule],
  controllers: [DeptProjectViewController, GovController, ImportController],
  providers: [DeptProjectViewService, GovService],
  exports: [GovService, DeptProjectViewService],
})
export class M13GovModule {}

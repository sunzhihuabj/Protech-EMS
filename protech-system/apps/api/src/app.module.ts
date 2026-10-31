import { Module } from '@nestjs/common';
import { SharedModule } from './shared/shared.module';
import { M00BaseModule } from './modules/m00-base/m00-base.module';
import { M01MdmModule } from './modules/m01-mdm/m01-mdm.module';
import { M02QuoteModule } from './modules/m02-quote/m02-quote.module';
import { M03ContractModule } from './modules/m03-contract/m03-contract.module';
import { M10BoardModule } from './modules/m10-board/m10-board.module';
import { M05BomModule } from './modules/m05-bom/m05-bom.module';
import { M06SupplyModule } from './modules/m06-supply/m06-supply.module';
import { M07M08Module } from './modules/m07-delivery/m07-m08.modules';
import { M13GovModule } from './modules/m13-gov/m13.module';
import { M16AiModule } from './modules/m16-ai/m16.module';

/** 根模块：SharedModule(@Global) + M00~M16 业务模块（一模块一目录六件套，11 册 §三） */
@Module({
  imports: [SharedModule, M00BaseModule, M01MdmModule, M02QuoteModule, M03ContractModule, M05BomModule, M06SupplyModule, M07M08Module, M10BoardModule, M13GovModule, M16AiModule],
})
export class AppModule {}

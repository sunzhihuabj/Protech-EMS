import { Module } from '@nestjs/common';
import { QuotationController } from './quotation.controller';
import { QuotationService } from './quotation.service';

/** M02 报价管理 */
@Module({
  controllers: [QuotationController],
  providers: [QuotationService],
  exports: [QuotationService],
})
export class M02QuoteModule {}

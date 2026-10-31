import { Module } from '@nestjs/common';
import { ContractController } from './contract.controller';
import { ContractService } from './contract.service';

/** M03 合同管理（两卡点 BR-01/BR-02；评审会签自动立项） */
@Module({
  controllers: [ContractController],
  providers: [ContractService],
  exports: [ContractService],
})
export class M03ContractModule {}

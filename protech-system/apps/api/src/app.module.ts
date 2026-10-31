import { Module } from '@nestjs/common';
import { SharedModule } from './shared/shared.module';
import { M00BaseModule } from './modules/m00-base/m00-base.module';
import { M01MdmModule } from './modules/m01-mdm/m01-mdm.module';

/** 根模块：SharedModule(@Global) + M00~M16 业务模块（一模块一目录六件套，11 册 §三） */
@Module({
  imports: [SharedModule, M00BaseModule, M01MdmModule],
})
export class AppModule {}

<template>
  <!-- SPRINT-UID-R19：主数据工作台全链接线（14 孤儿端点转正）
       客户治理（列表/分组/别名/归一/停用）+ 项目建档全家桶（FR-MDM-12/13/14/15/16 三重查重向导）
       + 物料台账（BR-06 脱敏）+ 供应商注册（此前仅 SCM 准入无 md_supplier 建档入口） -->
  <div class="pl-page">
    <el-tabs v-model="tab" type="card">
      <!-- 客户主体 -->
      <el-tab-pane label="客户主体" name="customers">
        <div class="pl-toolbar">
          <el-input v-model="custQuery.keyword" size="small" placeholder="名称/简称/编码" style="width: 180px" clearable @keyup.enter.native="loadCustomers" />
          <el-select v-model="custQuery.level" size="small" style="width: 130px" clearable placeholder="层级" @change="loadCustomers">
            <el-option value="GROUP" label="集团 GROUP" /><el-option value="REGION" label="区域 REGION" /><el-option value="PROJECT" label="项目公司 PROJECT" />
          </el-select>
          <el-button size="small" icon="el-icon-search" @click="loadCustomers">查询</el-button>
          <el-button v-permission="'mdm:customer:create'" size="small" type="primary" icon="el-icon-plus" @click="custDialog = true">新建客户（C 编号）</el-button>
          <el-button v-permission="'mdm:supplier:manage'" size="small" type="warning" plain icon="el-icon-office-building" @click="supDialog = true">供应商注册</el-button>
          <div class="spacer"></div>
          <!-- 归一检查器（FR-MDM-05：按任意名称解析主体） -->
          <el-input v-model="resolveName" size="small" placeholder="任意名称归一检查" style="width: 180px" clearable />
          <el-button size="small" v-permission="'mdm:customer:edit'" icon="el-icon-connection" @click="resolveCustomer">解析</el-button>
        </div>
        <el-alert v-if="resolveResult !== null" :title="resolveResult >= 0 ? '已解析到主体 #' + resolveResult + '（全称/简称/别名任一命中即归一）' : '未解析到匹配主体（请先登记别名/简称）'" :type="resolveResult >= 0 ? 'success' : 'warning'" :closable="true" show-icon style="margin-bottom: 8px" @close="resolveResult = null" />
        <!-- 集团分组汇总（mdm:customer:edit） -->
        <div v-if="groups.length" class="group-bar">
          <el-tag v-for="g in groups" :key="g.groupId" size="small" :type="g.isInternal === 1 ? 'success' : 'info'"
                  class="group-tag" @click="custQuery.keyword = g.groupName; loadCustomers()">
            {{ g.groupName || ('集团#' + g.groupId) }} · 成员 {{ g.customerCount }}
          </el-tag>
        </div>
        <pt-table :columns="custCols" :data="customers" :loading="custLoading" :total="custTotal"
                  :page.sync="custPage" @page-change="onCustPage"
                  aria-label="客户主体台账" empty-type="no-data"
                  empty-label="客户" empty-action-text="新建客户" @empty-action="custDialog = true">
          <template slot="cell-status" slot-scope="{ row }">
            <pt-status-tag :value="row.status" :dict="'ACTIVE:启用,FROZEN:停用,PENDING:待激活'" />
          </template>
          <template slot="actions" slot-scope="{ row }">
            <el-button v-permission="'mdm:customer:create'" size="mini" type="text" @click="openAlias(row)">别名</el-button>
            <el-button v-if="row.status === 'ACTIVE'" v-permission="'mdm:customer:edit'" size="mini" type="text" class="danger-text" @click="deactivateCustomer(row)">停用</el-button>
          </template>
        </pt-table>
      </el-tab-pane>

      <!-- 项目档案（建档全家桶） -->
      <el-tab-pane label="项目档案" name="projects">
        <div class="pl-toolbar">
          <el-input v-model="projQuery.keyword" size="small" placeholder="标准名/风场" style="width: 180px" clearable @keyup.enter.native="loadProjects" />
          <el-button size="small" icon="el-icon-search" @click="loadProjects">查询</el-button>
          <div class="spacer"></div>
          <el-button v-permission="'mdm:project:create'" size="small" type="primary" icon="el-icon-guide" @click="openWizard">项目建档向导（FR-MDM-12/13/15）</el-button>
        </div>
        <el-table :data="mdProjects" v-loading="projLoading" size="small" @row-click="openProjectDetail">
          <el-table-column prop="projectCode" label="档案号" width="150" />
          <el-table-column prop="stdName" label="标准名" min-width="220" show-overflow-tooltip />
          <el-table-column prop="shortName" label="简称" width="130" show-overflow-tooltip />
          <el-table-column prop="farmNameRaw" label="风场" width="150" show-overflow-tooltip />
          <el-table-column prop="orderType" label="订单类型" width="120">
            <template slot-scope="{ row }">{{ orderTypeLabel(row.orderType) }}</template>
          </el-table-column>
          <el-table-column prop="phase" label="期次" width="70" />
          <el-table-column prop="setQty" label="台数" width="70" align="right" />
          <el-table-column prop="status" label="状态" width="130" />
        </el-table>
        <p class="muted" style="margin: 6px 0 0">点击行查看详情/挂产品线/加别名；建档走向导（命名规则 → 三重查重 → 12 项校验）。</p>
      </el-tab-pane>

      <!-- 物料主数据 -->
      <el-tab-pane label="物料主数据" name="materials">
        <div class="pl-toolbar">
          <el-input v-model="matQuery.keyword" size="small" placeholder="名称/规格" style="width: 180px" clearable @keyup.enter.native="loadMaterials" />
          <el-button size="small" icon="el-icon-search" @click="loadMaterials">查询</el-button>
          <div class="spacer"></div>
          <el-button v-permission="'mdm:material:create'" size="small" type="primary" icon="el-icon-plus" @click="matDialog = true">新建物料</el-button>
        </div>
        <el-table :data="materials" v-loading="matLoading" size="small">
          <el-table-column prop="materialCode" label="物料编码" width="130" />
          <el-table-column prop="materialName" label="名称" min-width="160" show-overflow-tooltip />
          <el-table-column prop="spec" label="规格" min-width="120" show-overflow-tooltip />
          <el-table-column prop="category" label="类别" width="90" />
          <el-table-column prop="unit" label="单位" width="70" />
          <el-table-column prop="costPrice" label="成本价" width="110" align="right">
            <template slot-scope="{ row }">
              <!-- BR-06/A-07：服务端置 null（无价格权限）→ 前端渲染 *** -->
              <span>{{ row.costPrice === null || row.costPrice === undefined ? '***' : row.costPrice }}</span>
            </template>
          </el-table-column>
        </el-table>
        <p class="muted" style="margin: 6px 0 0">编码规则（C{4位} 客户 / 物料编码 / 单位 E-20 受控 8 种）由服务端强校验；同名同规格重复会命中三重查重。</p>
      </el-tab-pane>
    </el-tabs>

    <!-- 新建客户弹窗 -->
    <el-dialog title="新建客户（FR-MDM-01）" :visible.sync="custDialog" width="500px">
      <el-form label-width="110px" size="small">
        <el-form-item label="规范全称" required><el-input v-model="custForm.customerName" placeholder="与营业执照一致" /></el-form-item>
        <el-form-item label="简称"><el-input v-model.trim="custForm.shortName" /></el-form-item>
        <el-form-item label="层级" required>
          <el-select v-model="custForm.level" style="width: 100%">
            <el-option value="GROUP" label="集团 GROUP" /><el-option value="REGION" label="区域 REGION" /><el-option value="PROJECT" label="项目公司 PROJECT" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="custForm.customerType" style="width: 100%">
            <el-option value="OWNER" label="业主 OWNER" /><el-option value="OEM" label="主机厂 OEM" />
            <el-option value="RELATED" label="关联方 RELATED" /><el-option value="OTHER" label="其他 OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="分级">
          <el-select v-model="custForm.levelAbc" style="width: 100%">
            <el-option value="A" /><el-option value="B" /><el-option value="C" />
          </el-select>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="custDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="createCustomer">创建</el-button>
      </div>
    </el-dialog>

    <!-- 客户别名弹窗 -->
    <el-dialog title="添加客户别名（归一解析依据）" :visible.sync="aliasDialog" width="420px">
      <div v-if="aliasTarget" class="muted" style="margin-bottom: 8px">{{ aliasTarget.customerCode }} · {{ aliasTarget.customerName }}</div>
      <el-form label-width="90px" size="small">
        <el-form-item label="别名" required><el-input v-model.trim="aliasForm.aliasName" placeholder="商务常用简称/项目现场叫法" /></el-form-item>
        <el-form-item label="别名类型">
          <el-select v-model="aliasForm.aliasType" style="width: 100%">
            <el-option value="SHORT" label="简称 SHORT" /><el-option value="FORMER" label="曾用名 FORMER" />
            <el-option value="SITE" label="现场叫法 SITE" /><el-option value="OTHER" label="其他 OTHER" />
          </el-select>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="aliasDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveAlias">保存</el-button>
      </div>
    </el-dialog>

    <!-- 供应商注册弹窗（md_supplier 建档；SCM 准入流程另行走 /scm/suppliers） -->
    <el-dialog title="供应商注册（md_supplier 建档）" :visible.sync="supDialog" width="420px">
      <el-form label-width="100px" size="small">
        <el-form-item label="供应商名称" required><el-input v-model.trim="supForm.name" /></el-form-item>
        <el-form-item label="统一社会信用代码"><el-input v-model.trim="supForm.creditCode" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="supDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="registerSupplier">注册</el-button>
      </div>
    </el-dialog>

    <!-- 新建物料弹窗 -->
    <el-dialog title="新建物料" :visible.sync="matDialog" width="480px">
      <el-form label-width="100px" size="small">
        <el-form-item label="名称" required><el-input v-model.trim="matForm.materialName" /></el-form-item>
        <el-form-item label="规格"><el-input v-model.trim="matForm.spec" /></el-form-item>
        <el-form-item label="详细描述"><el-input v-model="matForm.description" type="textarea" :rows="2" placeholder="物料详细信息" /></el-form-item>
        <el-form-item label="类别" required>
          <el-select v-model="matForm.category" style="width: 100%">
            <el-option value="STD" label="标准件" /><el-option value="FAB" label="钣金件" /><el-option value="PURCHASE" label="外购" /><el-option value="ASSY" label="组装" />
          </el-select>
        </el-form-item>
        <el-form-item label="计量单位" required>
          <el-select v-model="matForm.unit" style="width: 100%">
            <el-option v-for="u in units" :key="u" :value="u" :label="u" />
          </el-select>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="matDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="createMaterial">创建</el-button>
      </div>
    </el-dialog>

    <!-- 项目建档向导（FR-MDM-12 三重查重 + FR-MDM-13 命名规则 + FR-MDM-15 12 项校验） -->
    <el-dialog title="项目建档向导（PJ 编号自动取号）" :visible.sync="wizardDialog" width="760px" top="4vh">
      <el-form label-width="120px" size="small">
        <div class="wiz-section"><b>① 基础与商务（FR-MDM-15 关键 12 项）</b></div>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="订单类型" required>
              <el-select v-model="wiz.orderType" style="width: 100%" @change="genNames">
                <el-option value="OWNER_RETROFIT" label="业主技改 OWNER_RETROFIT" />
                <el-option value="OEM_INSTALL" label="整机厂初装 OEM_INSTALL" />
                <el-option value="SPARE" label="备件 SPARE" /><el-option value="MAINTENANCE" label="定检服务 MAINTENANCE" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="签约主体" required>
              <el-select v-model="wiz.customerId" filterable style="width: 100%" placeholder="选择客户主体">
                <el-option v-for="c in customers" :key="'wc'+c.id" :value="c.id" :label="c.customerName" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="我方法人ID" required><el-input-number v-model="wiz.legalEntityId" :min="1" :controls="false" style="width: 100%" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="台数/套数" required><el-input-number v-model="wiz.setQty" :min="1" :controls="false" style="width: 100%" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="期次 phase"><el-input v-model.trim="wiz.phase" placeholder="如 一期 / II" @input="genNames" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="工程责任人" required>
              <el-select v-model="wiz.engOwnerId" filterable style="width: 100%" placeholder="选择用户">
                <el-option v-for="u in users" :key="'ue'+u.id" :value="u.id" :label="u.realName || u.username" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="商务责任人" required>
              <el-select v-model="wiz.bizOwnerId" filterable style="width: 100%" placeholder="选择用户">
                <el-option v-for="u in users" :key="'ub'+u.id" :value="u.id" :label="u.realName || u.username" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <div class="wiz-section"><b>② 风场（或 OEM 初装无风场 → 标记 farm_pending）</b></div>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="风场ID"><el-input-number v-model="wiz.farmId" :min="1" :controls="false" style="width: 100%" /></el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="风场全称(原文)"><el-input v-model.trim="wiz.farmNameRaw" placeholder="用于查重与命名，如 华能·如东二期风电场" /></el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label-width="0">
              <el-checkbox v-model="wiz.farmPending" style="margin-left: 8px">暂无风场</el-checkbox>
              <el-button size="mini" type="text" @click="farmDialog = true">新建风场</el-button>
            </el-form-item>
          </el-col>
        </el-row>

        <div class="wiz-section"><b>③ 命名助手（FR-MDM-13 自动生成 + FR-MDM-14 地名提醒）</b></div>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="业主全称"><el-input v-model.trim="wiz.ownerName" placeholder="业主规范全称（生成规则入参）" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="风场全称"><el-input v-model.trim="wiz.farmFullName" placeholder="生成规则入参（可同 farmNameRaw）" /></el-form-item>
          </el-col>
        </el-row>
        <div style="margin: 0 0 10px 120px">
          <el-button v-permission="'mdm:project:create'" size="mini" icon="el-icon-magic-stick" @click="genNames(true)">生成建议名</el-button>
          <span v-if="namingWarning" class="warn-text">{{ namingWarning }}</span>
        </div>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="标准名" required><el-input v-model.trim="wiz.stdName" placeholder="业主-风场-期次" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="常用简称" required><el-input v-model.trim="wiz.shortName" placeholder="风场+期次" /></el-form-item>
          </el-col>
        </el-row>

        <div class="wiz-section"><b>④ 三重查重（FR-MDM-12：风场精确/简称相似/业主+地域；命中需填 ≥10 字新建理由）</b></div>
        <div style="margin: 0 0 8px 120px">
          <el-button v-permission="'mdm:project:edit'" size="mini" icon="el-icon-search" @click="runTripleCheck">执行查重</el-button>
        </div>
        <el-table v-if="dupHits.length" :data="dupHits" size="mini" border style="margin: 0 0 8px 120px; width: calc(100% - 120px)">
          <el-table-column prop="projectCode" label="档案号" width="130" />
          <el-table-column prop="stdName" label="标准名" min-width="160" show-overflow-tooltip />
          <el-table-column prop="rule" label="命中规则" width="150" />
          <el-table-column prop="reason" label="说明" min-width="130" />
        </el-table>
        <el-form-item label="新建理由">
          <el-input v-model="wiz.forceNewReason" type="textarea" :rows="2" :placeholder="dupHits.length ? '查重命中，确认新建必填（不少于 10 字）' : '未命中可不填'" />
        </el-form-item>

        <div class="wiz-section"><b>⑤ 产品线与业主（E-08 多选；业主未定 → owner_pending）</b></div>
        <el-form-item label="产品线" required>
          <el-select v-model="wiz.productLines" multiple style="width: 100%" placeholder="E-08 产品线（可多选）">
            <el-option v-for="p in productLines" :key="p" :value="p" :label="productLineLabel(p)" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="业主主体">
              <el-select v-model="wiz.ownerId" filterable clearable style="width: 100%" placeholder="业主客户（OEM 项目可暂缺）">
                <el-option v-for="c in customers" :key="'wo'+c.id" :value="c.id" :label="c.customerName" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="主机厂 OEM">
              <el-select v-model="wiz.oemId" filterable clearable style="width: 100%" placeholder="OEM 客户（选填）">
                <el-option v-for="c in customers" :key="'wm'+c.id" :value="c.id" :label="c.customerName" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label-width="0"><el-checkbox v-model="wiz.ownerPending" style="margin-left: 8px">业主未定（owner_pending）</el-checkbox></el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="wizardDialog = false">取消</el-button>
        <el-button size="small" type="primary" :disabled="!wizReady" @click="createProject">提交建档</el-button>
      </div>
    </el-dialog>

    <!-- 新建风场弹窗 -->
    <el-dialog title="新建风场（WF 编号）" :visible.sync="farmDialog" width="480px" append-to-body>
      <el-form label-width="100px" size="small">
        <el-form-item label="风场名称" required><el-input v-model.trim="farmForm.farmName" placeholder="全称，如 华能·如东二期风电场" /></el-form-item>
        <el-row :gutter="8">
          <el-col :span="8"><el-form-item label="省"><el-input v-model.trim="farmForm.province" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="市"><el-input v-model.trim="farmForm.city" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="县"><el-input v-model.trim="farmForm.county" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="8">
          <el-col :span="12"><el-form-item label="容量(MW)"><el-input-number v-model="farmForm.capacityMw" :min="0" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="期次数"><el-input-number v-model="farmForm.phaseCount" :min="1" :controls="false" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="业主客户">
          <el-select v-model="farmForm.ownerId" filterable clearable style="width: 100%">
            <el-option v-for="c in customers" :key="'wf'+c.id" :value="c.id" :label="c.customerName" />
          </el-select>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="farmDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="createFarm">建档并回填ID</el-button>
      </div>
    </el-dialog>

    <!-- 项目详情抽屉 -->
    <el-drawer :visible.sync="detailDrawer" title="项目档案详情" size="480px">
      <div v-if="detail" style="padding: 0 20px">
        <el-descriptions :column="1" size="small" border>
          <el-descriptions-item label="档案号">{{ detail.projectCode }}</el-descriptions-item>
          <el-descriptions-item label="标准名">{{ detail.stdName }}</el-descriptions-item>
          <el-descriptions-item label="简称">{{ detail.shortName }}</el-descriptions-item>
          <el-descriptions-item label="风场">{{ detail.farmNameRaw || detail.farmId || '—' }}</el-descriptions-item>
          <el-descriptions-item label="订单类型">{{ orderTypeLabel(detail.orderType) }}</el-descriptions-item>
          <el-descriptions-item label="期次/台数">{{ detail.phase || '—' }} / {{ detail.setQty || '—' }}</el-descriptions-item>
          <el-descriptions-item label="产品线">{{ detail.productLines || '—' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
        </el-descriptions>
        <div class="wiz-section" style="margin-top: 14px"><b>挂产品子项（FR-MDM-16 一期次一产品线一档案）</b></div>
        <el-form inline size="small">
          <el-form-item>
            <el-select v-model="pdForm.productLine" size="small" placeholder="产品线" style="width: 140px">
              <el-option v-for="p in productLines" :key="'pd'+p" :value="p" :label="productLineLabel(p)" />
            </el-select>
          </el-form-item>
          <el-form-item><el-input-number v-model="pdForm.setQty" :min="1" :controls="false" size="small" placeholder="台数" style="width: 100px" /></el-form-item>
          <el-form-item>
            <el-button v-permission="'mdm:project:edit'" size="small" type="primary" plain @click="addProduct">挂接</el-button>
          </el-form-item>
        </el-form>
        <div class="wiz-section"><b>项目别名（查重相似度匹配依据）</b></div>
        <el-form inline size="small">
          <el-form-item><el-input v-model.trim="paForm.aliasName" size="small" placeholder="别名" style="width: 160px" /></el-form-item>
          <el-form-item>
            <el-button v-permission="'mdm:project:create'" size="small" type="primary" plain @click="addProjectAlias">加别名</el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import http from '@/api/request'
import { showError } from '@/api/request'
import { PtTable, PtStatusTag } from '@/components/pt'

/** E-20 受控计量单位 8 种（FR-PLM-15 同口径） */
const UNITS = ['PCS', 'M', 'M2', 'KG', 'T', 'L', 'SET', 'ROOT']
/** E-07 订单类型 */
const ORDER_TYPES = { OWNER_RETROFIT: '业主技改', OEM_INSTALL: '整机厂初装', SPARE: '备件', MAINTENANCE: '定检服务' }
/** E-08 产品线 11 值（V3 种子） */
const PRODUCT_LINES = ['LADDER', 'CLIMBER', 'CLIMB', 'RAIL', 'CCTV', 'FIRE', 'COVER', 'ROPE', 'AUDIO', 'SENSOR', 'OTHER']
const PRODUCT_LINE_NAMES = { LADDER: '升降机', CLIMBER: '免爬器', CLIMB: '爬梯', RAIL: '导轨', CCTV: '视频监控', FIRE: '自动消防', COVER: '自动盖板', ROPE: '钢丝绳', AUDIO: '声频', SENSOR: '感温磁', OTHER: '其他' }

export default {
  name: 'MdmConsole',
  components: { PtTable, PtStatusTag },
  data() {
    return {
      tab: 'customers',
      units: UNITS, productLines: PRODUCT_LINES, orderTypes: ORDER_TYPES,
      custCols: [
        { prop: 'customerCode', label: '编码', width: 100 },
        { prop: 'customerName', label: '规范全称', minWidth: 220 },
        { prop: 'shortName', label: '简称', width: 120 },
        { prop: 'level', label: '层级', width: 100 },
        { prop: 'customerType', label: '类型', width: 110 },
        { prop: 'levelAbc', label: '分级', width: 80 },
        { prop: 'status', label: '状态', width: 90 }
      ],
      // 客户
      custQuery: { keyword: '', level: '' },
      customers: [], custLoading: false, custTotal: 0,
      custPage: { pageNum: 1, pageSize: 20 },
      custDialog: false, custForm: {},
      aliasDialog: false, aliasTarget: null, aliasForm: { aliasName: '', aliasType: 'SHORT' },
      resolveName: '', resolveResult: null,
      groups: [],
      supDialog: false, supForm: { name: '', creditCode: '' },
      // 项目
      projQuery: { keyword: '' },
      mdProjects: [], projLoading: false,
      wizardDialog: false, wiz: {}, namingWarning: '', dupHits: [],
      farmDialog: false, farmForm: {},
      detailDrawer: false, detail: null,
      pdForm: { productLine: '', setQty: 1 }, paForm: { aliasName: '' },
      users: [],
      // 物料
      matQuery: { keyword: '' },
      materials: [], matLoading: false,
      matDialog: false, matForm: {}
    }
  },
  computed: {
    wizReady() {
      const w = this.wiz
      const farmOk = w.farmId || w.farmPending
      const ownerOk = w.ownerId || w.ownerPending
      return !!(w.customerId && w.legalEntityId && w.orderType && farmOk && ownerOk
        && w.stdName && w.shortName && w.productLines && w.productLines.length
        && w.setQty && w.engOwnerId && w.bizOwnerId)
    }
  },
  mounted() {
    this.loadCustomers()
    this.loadProjects()
    this.loadGroups()
    this.loadMaterials()
  },
  methods: {
    orderTypeLabel(v) { return ORDER_TYPES[v] || v || '—' },
    productLineLabel(v) { return PRODUCT_LINE_NAMES[v] || v },
    onCustPage(p) { this.custPage.pageNum = p.pageNum; this.custPage.pageSize = p.pageSize; this.loadCustomers() },

    /* ---------- 客户治理 ---------- */
    loadCustomers() {
      this.custLoading = true
      const params = { pageNum: this.custPage.pageNum, pageSize: this.custPage.pageSize }
      if (this.custQuery.keyword) params.keyword = this.custQuery.keyword
      if (this.custQuery.level) params.level = this.custQuery.level
      http.get('/mdm/customers', { params }).then(body => {
        this.customers = (body.data && body.data.records) || []
        this.custTotal = (body.data && body.data.total) || 0
      }).catch(showError).finally(() => { this.custLoading = false })
    },
    loadGroups() {
      http.get('/mdm/customers/groups').then(body => { this.groups = body.data || [] }).catch(() => { this.groups = [] })
    },
    resolveCustomer() {
      if (!this.resolveName) { this.$message.warning('输入要解析的名称'); return }
      http.get('/mdm/customers/resolve', { params: { name: this.resolveName } }).then(body => {
        this.resolveResult = (body.data === null || body.data === undefined) ? -1 : Number(body.data)
      }).catch(showError)
    },
    openAlias(row) {
      this.aliasTarget = row
      this.aliasForm = { aliasName: '', aliasType: 'SHORT' }
      this.aliasDialog = true
    },
    saveAlias() {
      if (!this.aliasForm.aliasName) { this.$message.warning('别名必填'); return }
      http.post('/mdm/customers/aliases', { customerId: this.aliasTarget.id, ...this.aliasForm }).then(() => {
        this.$message.success('别名已登记（resolve 归一即生效）')
        this.aliasDialog = false
      }).catch(showError)
    },
    deactivateCustomer(row) {
      this.$confirm(`停用客户 ${row.customerName}？停用后不可用于新签约。`, '客户停用', { type: 'warning' }).then(() => {
        http.post(`/mdm/customers/${row.id}/deactivate`).then(() => {
          this.$message.success('已停用')
          this.loadCustomers()
        }).catch(showError)
      }).catch(() => {})
    },
    registerSupplier() {
      if (!this.supForm.name) { this.$message.warning('供应商名称必填'); return }
      http.post('/mdm/suppliers-register', null, { params: this.supForm }).then(body => {
        this.$message.success('供应商已建档 id=' + body.data)
        this.supDialog = false
        this.supForm = { name: '', creditCode: '' }
      }).catch(showError)
    },
    createCustomer() {
      if (!this.custForm.customerName || !this.custForm.level || !this.custForm.customerType) {
        this.$message.warning('全称/层级/类型必填')
        return
      }
      http.post('/mdm/customers', this.custForm).then(body => {
        this.$message.success('客户已建档 ' + (body.data.customerCode || body.data))
        this.custDialog = false
        this.custForm = {}
        this.loadCustomers()
        this.loadGroups()
      }).catch(showError)
    },

    /* ---------- 项目建档向导 ---------- */
    loadProjects() {
      this.projLoading = true
      const params = { pageNum: 1, pageSize: 50 }
      if (this.projQuery.keyword) params.keyword = this.projQuery.keyword
      http.get('/mdm/projects', { params }).then(body => {
        this.mdProjects = (body.data && body.data.records) || []
      }).catch(showError).finally(() => { this.projLoading = false })
    },
    openWizard() {
      this.wiz = {
        orderType: '', customerId: null, legalEntityId: 1, setQty: 1, phase: '',
        engOwnerId: null, bizOwnerId: null,
        farmId: null, farmNameRaw: '', farmPending: false,
        ownerName: '', farmFullName: '', stdName: '', shortName: '',
        forceNewReason: '', productLines: [], ownerId: null, oemId: null, ownerPending: false
      }
      this.namingWarning = ''
      this.dupHits = []
      this.wizardDialog = true
      this.loadUsers()
    },
    loadUsers() {
      if (this.users.length) return
      http.get('/org/users', { params: { pageNum: 1, pageSize: 100 } }).then(body => {
        this.users = (body.data && body.data.records) || []
      }).catch(() => { this.users = [] })
    },
    genNames(manual) {
      // FR-MDM-13：业主全称 + 风场全称齐备才调规则；farmNameRaw 空时联动
      if (!this.wiz.ownerName || !this.wiz.farmFullName) return
      if (!this.wiz.farmNameRaw) this.wiz.farmNameRaw = this.wiz.farmFullName
      http.get('/mdm/projects/name-rule', {
        params: { ownerName: this.wiz.ownerName, farmFullName: this.wiz.farmFullName, phase: this.wiz.phase || undefined, orderType: this.wiz.orderType || undefined }
      }).then(body => {
        const d = body.data || {}
        if (d.stdName) this.wiz.stdName = d.stdName
        if (d.shortName) this.wiz.shortName = d.shortName
        this.namingWarning = d.namingWarning || ''
        if (manual && !d.namingWarning) this.$message.success('建议名已回填')
      }).catch(showError)
    },
    runTripleCheck() {
      http.get('/mdm/projects/triple-check', {
        params: {
          farmFullName: this.wiz.farmFullName || undefined,
          shortName: this.wiz.shortName || undefined,
          ownerId: this.wiz.ownerId || undefined
        }
      }).then(body => {
        this.dupHits = body.data || []
        this.$message.success(this.dupHits.length ? ('命中 ' + this.dupHits.length + ' 条疑似重复') : '未命中疑似重复')
      }).catch(showError)
    },
    createProject() {
      const w = this.wiz
      http.post('/mdm/projects', {
        customerId: w.customerId, ownerId: w.ownerId || null, oemId: w.oemId || null,
        legalEntityId: w.legalEntityId, orderType: w.orderType,
        farmId: w.farmId || null, farmNameRaw: w.farmNameRaw || null,
        farmPending: w.farmPending ? 1 : null,
        phase: w.phase || null, stdName: w.stdName, shortName: w.shortName,
        productLines: w.productLines, setQty: w.setQty,
        engOwnerId: w.engOwnerId, bizOwnerId: w.bizOwnerId,
        ownerPending: w.ownerPending ? 1 : null,
        forceNewReason: w.forceNewReason || null
      }).then(body => {
        this.$message.success('项目档案已建 PJ 编号自动取号，id=' + body.data)
        this.wizardDialog = false
        this.loadProjects()
      }).catch(showError)
    },
    openProjectDetail(row) {
      http.get(`/mdm/projects/${row.id}`).then(body => {
        this.detail = body.data || row
        this.detailDrawer = true
      }).catch(() => {
        this.detail = row
        this.detailDrawer = true
      })
    },
    addProduct() {
      if (!this.pdForm.productLine) { this.$message.warning('选择产品线'); return }
      http.post(`/mdm/projects/${this.detail.id}/products`, this.pdForm).then(() => {
        this.$message.success('产品子项已挂接')
        this.pdForm = { productLine: '', setQty: 1 }
        this.openProjectDetail(this.detail)
      }).catch(showError)
    },
    addProjectAlias() {
      if (!this.paForm.aliasName) { this.$message.warning('别名必填'); return }
      http.post(`/mdm/projects/${this.detail.id}/aliases`, this.paForm).then(() => {
        this.$message.success('别名已登记（三重查重相似度即生效）')
        this.paForm = { aliasName: '' }
      }).catch(showError)
    },
    createFarm() {
      if (!this.farmForm.farmName) { this.$message.warning('风场名称必填'); return }
      http.post('/mdm/farms', this.farmForm).then(body => {
        this.$message.success('风场已建档 id=' + body.data)
        this.wiz.farmId = body.data
        if (!this.wiz.farmNameRaw && !this.wiz.farmFullName) {
          this.wiz.farmNameRaw = this.farmForm.farmName
          this.wiz.farmFullName = this.farmForm.farmName
        }
        this.farmDialog = false
        this.farmForm = {}
      }).catch(showError)
    },

    /* ---------- 物料 ---------- */
    loadMaterials() {
      this.matLoading = true
      const params = { pageNum: 1, pageSize: 50 }
      if (this.matQuery.keyword) params.keyword = this.matQuery.keyword
      http.get('/mdm/materials', { params }).then(body => {
        this.materials = (body.data && body.data.records) || []
      }).catch(showError).finally(() => { this.matLoading = false })
    },
    createMaterial() {
      if (!this.matForm.materialName || !this.matForm.category || !this.matForm.unit) {
        this.$message.warning('名称/类别/计量单位必填')
        return
      }
      http.post('/mdm/materials', this.matForm).then(body => {
        this.$message.success('物料已建档 id=' + body.data)
        this.matDialog = false
        this.matForm = {}
        this.loadMaterials()
      }).catch(showError)
    }
  }
}
</script>

<style scoped>
.muted { color: var(--ink-3); font-size: 12px; }
.warn-text { color: var(--danger, #f56c6c); font-size: 12px; margin-left: 8px; }
.danger-text { color: #f56c6c; }
.spacer { flex: 1; }
.group-bar { margin-bottom: 8px; display: flex; flex-wrap: wrap; gap: 6px; }
.group-tag { cursor: pointer; }
.wiz-section {
  font-size: 13px; color: var(--ink-2);
  border-left: 3px solid var(--pri-500, #1F7B8C); padding-left: 8px; margin: 10px 0;
}
</style>

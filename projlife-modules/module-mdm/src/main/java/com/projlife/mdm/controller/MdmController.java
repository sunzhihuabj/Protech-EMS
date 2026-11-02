package com.projlife.mdm.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.mdm.entity.MdCustomer;
import com.projlife.mdm.entity.MdCustomerAlias;
import com.projlife.mdm.entity.MdProject;
import com.projlife.mdm.entity.MdProjectAlias;
import com.projlife.mdm.entity.MdProjectProduct;
import com.projlife.mdm.entity.MdWindFarm;
import com.projlife.mdm.service.CustomerService;
import com.projlife.mdm.service.MdProjectService;
import com.projlife.mdm.service.NameRuleService;
import com.projlife.mdm.service.WindFarmService;
import com.projlife.security.auth.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 主数据接口。写入类权限=mdm:*（R18 唯一入口，BR-MDM-01）；查询登录即可。
 */
@RestController
@RequestMapping("/api/v1/mdm")
public class MdmController {

    private final CustomerService customerService;
    private final WindFarmService farmService;
    private final MdProjectService projectService;
    private final NameRuleService nameRuleService;
    private final com.projlife.mdm.service.MaterialService materialService;

    public MdmController(CustomerService customerService, WindFarmService farmService,
                         MdProjectService projectService, NameRuleService nameRuleService,
                         com.projlife.mdm.service.MaterialService materialService) {
        this.customerService = customerService;
        this.farmService = farmService;
        this.projectService = projectService;
        this.nameRuleService = nameRuleService;
        this.materialService = materialService;
    }

    // ---------- 客户主体 ----------

    @GetMapping("/customers")
    @RequirePermission("mdm:customer:view")
    public ApiResponse<PageResp<MdCustomer>> customers(PageReq pageReq,
                                                       @RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) String level) {
        return ApiResponse.ok(customerService.page(pageReq, keyword, level, null));
    }

    @GetMapping("/customers/groups")
    @RequirePermission("mdm:customer:edit")
    public ApiResponse<List<Map<String, Object>>> groupSummary() {
        return ApiResponse.ok(customerService.groupSummary());
    }

    @PostMapping("/customers")
    @RequirePermission("mdm:customer:create")
    public ApiResponse<Long> createCustomer(@RequestBody MdCustomer customer) {
        return ApiResponse.ok(customerService.create(customer));
    }

    @PostMapping("/customers/aliases")
    @RequirePermission("mdm:customer:create")
    public ApiResponse<Long> addCustomerAlias(@RequestBody MdCustomerAlias alias) {
        return ApiResponse.ok(customerService.addAlias(alias));
    }

    /** FR-MDM-05：按任意名称（全称/简称/别名）解析主体 */
    @GetMapping("/customers/resolve")
    @RequirePermission("mdm:customer:edit")
    public ApiResponse<Long> resolveCustomer(@RequestParam String name) {
        return ApiResponse.ok(customerService.findByAnyName(name));
    }

    @PostMapping("/customers/{id}/deactivate")
    @RequirePermission("mdm:customer:edit")
    public ApiResponse<Void> deactivateCustomer(@PathVariable Long id) {
        customerService.deactivate(id);
        return ApiResponse.ok();
    }

    // ---------- 供应商（S4 经 scm 模块准入；md_supplier 建档在此） ----------

    @PostMapping("/suppliers-register")
    @RequirePermission("mdm:supplier:manage")
    public ApiResponse<Long> registerSupplier(@RequestParam String name,
                                              @RequestParam(required = false) String creditCode) {
        return ApiResponse.ok(customerService.registerSupplier(name, creditCode));
    }

    // ---------- 物料 ----------

    @GetMapping("/materials")
    @RequirePermission("mdm:material:view")
    public ApiResponse<com.projlife.common.api.PageResp<com.projlife.mdm.entity.MdMaterial>> materials(
            com.projlife.common.api.PageReq pageReq, @RequestParam(required = false) String keyword) {
        com.projlife.common.api.PageResp<com.projlife.mdm.entity.MdMaterial> page =
                materialService.page(pageReq, keyword);
        // BR-06 / UID-00 A-07 / L-3：无价格权限者 costPrice 置 null（前端渲染 ***，列位保留）。
        // 权限口径：cost:view（成本）或采购价格（R09/R15/R01/R02 持有 scm 价格权）可见。
        if (!hasAnyPermission("cost:view", "scm:order:view_price")) {
            for (com.projlife.mdm.entity.MdMaterial m : page.getRecords()) {
                m.setCostPrice(null);
            }
        }
        return ApiResponse.ok(page);
    }

    /** BR-06 联动：任一价格权限即见物料成本价（NFR-09） */
    private boolean hasAnyPermission(String... perms) {
        com.projlife.common.context.LoginUser user = com.projlife.common.context.UserContextHolder.get();
        if (user == null) {
            return false;
        }
        for (String perm : perms) {
            if (user.hasPermission(perm)) {
                return true;
            }
        }
        return false;
    }

    @PostMapping("/materials")
    @RequirePermission("mdm:material:create")
    public ApiResponse<Long> createMaterial(@RequestBody com.projlife.mdm.entity.MdMaterial material) {
        return ApiResponse.ok(materialService.create(material));
    }

    // ---------- 风场 ----------

    @PostMapping("/farms")
    @RequirePermission("mdm:project:create")
    public ApiResponse<Long> createFarm(@RequestBody MdWindFarm farm) {
        return ApiResponse.ok(farmService.create(farm));
    }

    @GetMapping("/farms/{id}")
    public ApiResponse<MdWindFarm> farm(@PathVariable Long id) {
        return ApiResponse.ok(farmService.detail(id));
    }

    // ---------- 项目档案 ----------

    @GetMapping("/projects")
    @RequirePermission("mdm:project:view")
    public ApiResponse<PageResp<MdProject>> projects(PageReq pageReq,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) Long farmId) {
        return ApiResponse.ok(projectService.page(pageReq, keyword, status, farmId));
    }

    @GetMapping("/projects/{id}")
    public ApiResponse<MdProject> project(@PathVariable Long id) {
        return ApiResponse.ok(projectService.detail(id));
    }

    /** FR-MDM-13：名称自动生成器 */
    @GetMapping("/projects/name-rule")
    @RequirePermission("mdm:project:create")
    public ApiResponse<Map<String, String>> nameRule(@RequestParam String ownerName,
                                                     @RequestParam String farmFullName,
                                                     @RequestParam(required = false) String phase,
                                                     @RequestParam(required = false) String orderType) {
        return ApiResponse.ok(nameRuleService.generateNames(ownerName, farmFullName, phase, orderType));
    }

    /** FR-MDM-12：三重查重（对比卡数据源） */
    @GetMapping("/projects/triple-check")
    @RequirePermission("mdm:project:edit")
    public ApiResponse<List<Map<String, Object>>> tripleCheck(@RequestParam(required = false) String farmFullName,
                                                              @RequestParam(required = false) String shortName,
                                                              @RequestParam(required = false) Long ownerId) {
        return ApiResponse.ok(nameRuleService.tripleCheck(farmFullName, shortName, ownerId));
    }

    /** 建档（R18；forceReason 为三重查重命中后的"确认新建理由"） */
    @PostMapping("/projects")
    @RequirePermission("mdm:project:create")
    public ApiResponse<Long> createProject(@RequestBody Map<String, Object> body) {
        MdProject project = new MdProject();
        project.setCustomerId(toLong(body.get("customerId")));
        project.setOwnerId(toLong(body.get("ownerId")));
        project.setOemId(toLong(body.get("oemId")));
        project.setLegalEntityId(toLong(body.get("legalEntityId")));
        project.setOrderType((String) body.get("orderType"));
        project.setFarmId(toLong(body.get("farmId")));
        project.setFarmNameRaw((String) body.get("farmNameRaw"));
        project.setPhase((String) body.get("phase"));
        project.setStdName((String) body.get("stdName"));
        project.setShortName((String) body.get("shortName"));
        project.setProductLines(toJson((List<?>) body.get("productLines")));
        project.setSetQty(toDecimal(body.get("setQty")));
        project.setEngOwnerId(toLong(body.get("engOwnerId")));
        project.setBizOwnerId(toLong(body.get("bizOwnerId")));
        project.setOwnerPending(toTinyint(body.get("ownerPending")));
        project.setFarmPending(toTinyint(body.get("farmPending")));
        @SuppressWarnings("unchecked")
        List<String> productLines = (List<String>) body.get("productLines");
        String forceReason = (String) body.get("forceNewReason");
        return ApiResponse.ok(projectService.create(project, productLines, forceReason));
    }

    @PostMapping("/projects/{id}/products")
    @RequirePermission("mdm:project:edit")
    public ApiResponse<Long> addProduct(@PathVariable Long id, @RequestBody MdProjectProduct product) {
        product.setMdProjectId(id);
        return ApiResponse.ok(projectService.addProduct(product));
    }

    @PostMapping("/projects/{id}/aliases")
    @RequirePermission("mdm:project:create")
    public ApiResponse<Long> addProjectAlias(@PathVariable Long id, @RequestBody MdProjectAlias alias) {
        alias.setMdProjectId(id);
        return ApiResponse.ok(projectService.addAlias(alias));
    }

    private Long toLong(Object value) {
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private Integer toTinyint(Object value) {
        return value == null ? null : Integer.valueOf(String.valueOf(value));
    }

    private java.math.BigDecimal toDecimal(Object value) {
        return value == null ? null : new java.math.BigDecimal(String.valueOf(value));
    }

    /** 产品线列表 → JSON 数组字符串（E-08 code 数组） */
    private String toJson(List<?> items) {
        if (items == null) {
            return null;
        }
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append('"').append(String.valueOf(items.get(i)).replace("\"", "")).append('"');
        }
        return json.append(']').toString();
    }
}

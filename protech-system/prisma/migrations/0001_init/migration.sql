-- CreateTable
CREATE TABLE "contract" (
    "id" BIGSERIAL NOT NULL,
    "contractCode" TEXT NOT NULL,
    "contractName" TEXT NOT NULL,
    "nature" TEXT NOT NULL,
    "bizType" TEXT NOT NULL,
    "ourEntityId" BIGINT NOT NULL,
    "counterpartyId" BIGINT NOT NULL,
    "amountCents" BIGINT NOT NULL,
    "taxRate" TEXT,
    "currency" TEXT NOT NULL DEFAULT 'CNY',
    "paymentTerms" TEXT,
    "settlement" TEXT,
    "invoiceReq" TEXT,
    "contact" TEXT,
    "phone" TEXT,
    "deliveryAddr" TEXT,
    "signedAt" TIMESTAMPTZ(6),
    "effectiveAt" TIMESTAMPTZ(6),
    "periodStart" TIMESTAMPTZ(6),
    "periodEnd" TIMESTAMPTZ(6),
    "warrantyMonths" INTEGER,
    "warrantyDepositRate" TEXT,
    "docCopies" INTEGER,
    "filePath" TEXT,
    "archiveNo" TEXT,
    "status" TEXT NOT NULL DEFAULT 'drafting',
    "reviewStatus" TEXT NOT NULL DEFAULT 'none',
    "sealAt" TIMESTAMPTZ(6),
    "sealCopies" INTEGER,
    "filedAt" TIMESTAMPTZ(6),
    "filedBy" BIGINT,
    "archiveDeadlineAt" TIMESTAMPTZ(6),
    "invoicingLocked" BOOLEAN NOT NULL DEFAULT false,
    "quotationId" BIGINT,
    "parentContractId" BIGINT,
    "bcSeq" INTEGER,
    "remark" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "contract_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "contract_project" (
    "id" BIGSERIAL NOT NULL,
    "contractId" BIGINT NOT NULL,
    "projectId" BIGINT NOT NULL,
    "orderLine" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,

    CONSTRAINT "contract_project_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "contract_review" (
    "id" BIGSERIAL NOT NULL,
    "contractId" BIGINT NOT NULL,
    "round" INTEGER NOT NULL DEFAULT 1,
    "role" TEXT NOT NULL,
    "reviewerId" BIGINT,
    "result" TEXT,
    "opinion" TEXT,
    "reviewedAt" TIMESTAMPTZ(6),
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "contract_review_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "contract_change" (
    "id" BIGSERIAL NOT NULL,
    "contractId" BIGINT NOT NULL,
    "changeCode" TEXT NOT NULL,
    "content" TEXT NOT NULL,
    "reason" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'pending',
    "newContractId" BIGINT,
    "reviewedBy" BIGINT,
    "reviewedAt" TIMESTAMPTZ(6),
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "contract_change_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "quotation" (
    "id" BIGSERIAL NOT NULL,
    "quotationCode" TEXT NOT NULL,
    "partyId" BIGINT NOT NULL,
    "groupId" BIGINT,
    "projectId" BIGINT,
    "productLines" TEXT NOT NULL,
    "qty" INTEGER NOT NULL,
    "unitPriceCents" BIGINT NOT NULL,
    "totalCents" BIGINT NOT NULL,
    "validUntil" TIMESTAMPTZ(6),
    "ownerId" BIGINT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'draft',
    "expectedSignAt" TIMESTAMPTZ(6),
    "convertedContractId" BIGINT,
    "lostReason" TEXT,
    "remark" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "quotation_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "board" (
    "id" BIGSERIAL NOT NULL,
    "boardCode" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "ownerDept" TEXT,
    "rhythm" TEXT,
    "status" TEXT NOT NULL DEFAULT 'active',
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "board_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "kb_card" (
    "id" BIGSERIAL NOT NULL,
    "boardId" BIGINT NOT NULL,
    "title" TEXT NOT NULL,
    "ownerId" BIGINT NOT NULL,
    "ownerDeptId" BIGINT,
    "dueDate" TIMESTAMPTZ(6) NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'todo',
    "blockedReason" TEXT,
    "nextAction" TEXT,
    "isBackfill" BOOLEAN NOT NULL DEFAULT false,
    "blockedAt" TIMESTAMPTZ(6),
    "escalatedAt" TIMESTAMPTZ(6),
    "respondedAt" TIMESTAMPTZ(6),
    "sourceType" TEXT,
    "sourceId" TEXT,
    "crossBoardId" BIGINT,
    "crossFlag" TEXT,
    "closedAt" TIMESTAMPTZ(6),
    "remark" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "kb_card_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "kb_card_log" (
    "id" BIGSERIAL NOT NULL,
    "cardId" BIGINT NOT NULL,
    "fromStatus" TEXT,
    "toStatus" TEXT NOT NULL,
    "byId" BIGINT,
    "note" TEXT,
    "isBackfill" BOOLEAN NOT NULL DEFAULT false,
    "movedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "kb_card_log_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "kb_escalation" (
    "id" BIGSERIAL NOT NULL,
    "cardId" BIGINT NOT NULL,
    "level" TEXT NOT NULL,
    "reason" TEXT,
    "escalatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "resolvedAt" TIMESTAMPTZ(6),

    CONSTRAINT "kb_escalation_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "metric_def" (
    "id" BIGSERIAL NOT NULL,
    "metricCode" TEXT NOT NULL,
    "boardCode" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "formula" TEXT NOT NULL,
    "unit" TEXT NOT NULL DEFAULT 'pct',
    "target" TEXT,
    "yellowAt" TEXT,
    "redAt" TEXT,
    "direction" TEXT NOT NULL DEFAULT 'forward',
    "source" TEXT NOT NULL DEFAULT 'auto',
    "detailKey" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "metric_def_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "metric_snapshot" (
    "id" BIGSERIAL NOT NULL,
    "metricCode" TEXT NOT NULL,
    "period" TEXT NOT NULL,
    "value" TEXT,
    "status" TEXT NOT NULL DEFAULT 'ok',
    "computedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "metric_snapshot_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "kb_meeting" (
    "id" BIGSERIAL NOT NULL,
    "boardId" BIGINT NOT NULL,
    "meetingDate" TIMESTAMPTZ(6) NOT NULL,
    "attendees" TEXT NOT NULL,
    "minutes" TEXT,
    "startedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "endedAt" TIMESTAMPTZ(6),
    "createdBy" BIGINT,

    CONSTRAINT "kb_meeting_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "kb_meeting_item" (
    "id" BIGSERIAL NOT NULL,
    "meetingId" BIGINT NOT NULL,
    "cardId" BIGINT NOT NULL,
    "askWhere" TEXT,
    "askWho" TEXT,
    "askWhen" TEXT,
    "skipped" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "kb_meeting_item_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "bom" (
    "id" BIGSERIAL NOT NULL,
    "bomCode" TEXT NOT NULL,
    "bomType" TEXT NOT NULL DEFAULT 'full',
    "productLine" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "version" INTEGER NOT NULL DEFAULT 1,
    "status" TEXT NOT NULL DEFAULT 'draft',
    "parentBomId" BIGINT,
    "goldenFlag" BOOLEAN NOT NULL DEFAULT false,
    "remark" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "bom_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "bom_line" (
    "id" BIGSERIAL NOT NULL,
    "bomId" BIGINT NOT NULL,
    "materialId" BIGINT NOT NULL,
    "qty" DECIMAL(14,3) NOT NULL,
    "positionCode" TEXT,
    "kitRefId" BIGINT,
    "sort" INTEGER NOT NULL DEFAULT 0,
    "remark" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "bom_line_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ecn" (
    "id" BIGSERIAL NOT NULL,
    "ecnCode" TEXT NOT NULL,
    "bomId" BIGINT NOT NULL,
    "fromVersion" INTEGER NOT NULL,
    "toVersion" INTEGER NOT NULL,
    "reason" TEXT NOT NULL,
    "oldMaterialLines" TEXT,
    "disposalPlan" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'pending',
    "approverId" BIGINT,
    "reviewedAt" TIMESTAMPTZ(6),
    "publishedAt" TIMESTAMPTZ(6),
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "ecn_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "work_order" (
    "id" BIGSERIAL NOT NULL,
    "woCode" TEXT NOT NULL,
    "projectId" BIGINT,
    "contractId" BIGINT,
    "bomId" BIGINT,
    "qty" INTEGER NOT NULL,
    "planStart" TIMESTAMPTZ(6),
    "planEnd" TIMESTAMPTZ(6),
    "kitStatus" TEXT,
    "kitShortLines" TEXT,
    "status" TEXT NOT NULL DEFAULT 'planned',
    "remark" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "work_order_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "purchase_req" (
    "id" BIGSERIAL NOT NULL,
    "prCode" TEXT NOT NULL,
    "source" TEXT NOT NULL DEFAULT 'manual',
    "projectId" BIGINT,
    "workOrderId" BIGINT,
    "lines" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'pending',
    "approverId" BIGINT,
    "reviewedAt" TIMESTAMPTZ(6),
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "purchase_req_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "purchase_order" (
    "id" BIGSERIAL NOT NULL,
    "poCode" TEXT NOT NULL,
    "supplierId" BIGINT NOT NULL,
    "projectId" BIGINT,
    "reqId" BIGINT,
    "promisedAt" TIMESTAMPTZ(6),
    "lines" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'draft',
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "purchase_order_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "inventory" (
    "id" BIGSERIAL NOT NULL,
    "materialId" BIGINT NOT NULL,
    "warehouse" TEXT NOT NULL DEFAULT 'YL',
    "locationCode" TEXT,
    "batch" TEXT,
    "legalEntity" TEXT NOT NULL DEFAULT 'PRT',
    "qty" DECIMAL(14,3) NOT NULL DEFAULT 0,
    "threeColor" TEXT NOT NULL DEFAULT 'blue',
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "inventory_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "stock_move" (
    "id" BIGSERIAL NOT NULL,
    "moveType" TEXT NOT NULL,
    "sourceType" TEXT NOT NULL,
    "sourceId" TEXT NOT NULL,
    "sourceLine" TEXT,
    "materialId" BIGINT NOT NULL,
    "qty" DECIMAL(14,3) NOT NULL,
    "projectId" BIGINT,
    "workOrderId" BIGINT,
    "legalEntity" TEXT NOT NULL DEFAULT 'PRT',
    "operatorId" BIGINT NOT NULL,
    "idempotencyKey" TEXT NOT NULL,
    "tPlusStatus" TEXT NOT NULL DEFAULT 'pending',
    "tPlusRef" TEXT,
    "movedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "stock_move_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "inventory_snapshot" (
    "id" BIGSERIAL NOT NULL,
    "period" TEXT NOT NULL,
    "payload" TEXT NOT NULL,
    "totalSkus" INTEGER NOT NULL,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "inventory_snapshot_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "dispatch" (
    "id" BIGSERIAL NOT NULL,
    "pgCode" TEXT NOT NULL,
    "projectId" BIGINT NOT NULL,
    "windFarmId" BIGINT NOT NULL,
    "teamId" BIGINT,
    "members" TEXT,
    "planDays" INTEGER,
    "source" TEXT NOT NULL DEFAULT 'manual',
    "shipMoveId" BIGINT,
    "status" TEXT NOT NULL DEFAULT 'draft',
    "taskDesc" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "dispatch_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "disclosure" (
    "id" BIGSERIAL NOT NULL,
    "projectId" BIGINT NOT NULL,
    "type" TEXT NOT NULL DEFAULT 'plan',
    "discloseAt" TIMESTAMPTZ(6) NOT NULL,
    "attendees" TEXT,
    "fileId" BIGINT,
    "content" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,

    CONSTRAINT "disclosure_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "site_log" (
    "id" BIGSERIAL NOT NULL,
    "projectId" BIGINT NOT NULL,
    "dispatchId" BIGINT,
    "authorId" BIGINT NOT NULL,
    "content" TEXT NOT NULL,
    "photos" TEXT,
    "logDate" TIMESTAMPTZ(6) NOT NULL,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "site_log_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "site_issue" (
    "id" BIGSERIAL NOT NULL,
    "projectId" BIGINT NOT NULL,
    "description" TEXT NOT NULL,
    "ownerId" BIGINT NOT NULL,
    "deadline" TIMESTAMPTZ(6) NOT NULL,
    "closedAt" TIMESTAMPTZ(6),
    "status" TEXT NOT NULL DEFAULT 'open',
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,

    CONSTRAINT "site_issue_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "milestone" (
    "id" BIGSERIAL NOT NULL,
    "projectId" BIGINT NOT NULL,
    "name" TEXT NOT NULL,
    "sort" INTEGER NOT NULL DEFAULT 0,
    "planDate" TIMESTAMPTZ(6),
    "actualDate" TIMESTAMPTZ(6),
    "status" TEXT NOT NULL DEFAULT 'pending',
    "ownerId" BIGINT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "milestone_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "invoice" (
    "id" BIGSERIAL NOT NULL,
    "contractId" BIGINT NOT NULL,
    "projectId" BIGINT,
    "amountCents" BIGINT NOT NULL,
    "invoiceNo" TEXT,
    "invoicedAt" TIMESTAMPTZ(6),
    "status" TEXT NOT NULL DEFAULT 'applied',
    "applicantId" BIGINT NOT NULL,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,

    CONSTRAINT "invoice_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "payment_plan" (
    "id" BIGSERIAL NOT NULL,
    "contractId" BIGINT NOT NULL,
    "node" TEXT NOT NULL,
    "dueAt" TIMESTAMPTZ(6),
    "amountCents" BIGINT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'pending',
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,

    CONSTRAINT "payment_plan_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "receipt" (
    "id" BIGSERIAL NOT NULL,
    "contractId" BIGINT NOT NULL,
    "projectId" BIGINT,
    "planId" BIGINT,
    "amountCents" BIGINT NOT NULL,
    "receivedAt" TIMESTAMP(3) NOT NULL,
    "method" TEXT,
    "collectorId" BIGINT,
    "remark" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,

    CONSTRAINT "receipt_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "acceptance" (
    "id" BIGSERIAL NOT NULL,
    "projectId" BIGINT NOT NULL,
    "contractId" BIGINT,
    "acceptedAt" TIMESTAMP(3) NOT NULL,
    "conclusion" TEXT NOT NULL,
    "documents" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,

    CONSTRAINT "acceptance_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "collection" (
    "id" BIGSERIAL NOT NULL,
    "contractId" BIGINT NOT NULL,
    "method" TEXT NOT NULL DEFAULT 'call',
    "content" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,

    CONSTRAINT "collection_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "gov_task" (
    "id" BIGSERIAL NOT NULL,
    "taskCode" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "payload" TEXT,
    "assigneeId" BIGINT,
    "result" TEXT,
    "status" TEXT NOT NULL DEFAULT 'open',
    "sourceType" TEXT,
    "sourceId" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "gov_task_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "import_batch" (
    "id" BIGSERIAL NOT NULL,
    "templateKey" TEXT NOT NULL,
    "totalRows" INTEGER NOT NULL,
    "okRows" INTEGER NOT NULL,
    "errorRows" INTEGER NOT NULL,
    "errors" TEXT,
    "status" TEXT NOT NULL DEFAULT 'done',
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,

    CONSTRAINT "import_batch_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ai_call" (
    "id" BIGSERIAL NOT NULL,
    "capability" TEXT NOT NULL,
    "userId" BIGINT,
    "inputDigest" TEXT,
    "model" TEXT NOT NULL DEFAULT 'mock',
    "tokens" INTEGER,
    "latencyMs" INTEGER,
    "confidence" TEXT,
    "resultRef" TEXT,
    "level" TEXT NOT NULL DEFAULT 'ask',
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ai_call_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ai_feedback" (
    "id" BIGSERIAL NOT NULL,
    "callId" BIGINT NOT NULL,
    "accepted" BOOLEAN NOT NULL,
    "editedFields" TEXT,
    "reason" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,

    CONSTRAINT "ai_feedback_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "replenish_request" (
    "id" BIGSERIAL NOT NULL,
    "projectId" BIGINT,
    "materialId" BIGINT NOT NULL,
    "qty" DECIMAL(14,3) NOT NULL,
    "amountCents" BIGINT NOT NULL,
    "reason" TEXT NOT NULL,
    "isSafetyPart" BOOLEAN NOT NULL DEFAULT false,
    "tier" TEXT,
    "status" TEXT NOT NULL DEFAULT 'pending',
    "approverId" BIGINT,
    "reviewedAt" TIMESTAMPTZ(6),
    "signedBackAt" TIMESTAMPTZ(6),
    "remark" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "replenish_request_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "sys_dept" (
    "id" BIGSERIAL NOT NULL,
    "code" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "parentId" BIGINT,
    "path" TEXT NOT NULL DEFAULT '/',
    "chainCode" TEXT,
    "boardCode" TEXT,
    "headcount" INTEGER,
    "sort" INTEGER NOT NULL DEFAULT 0,
    "status" TEXT NOT NULL DEFAULT 'active',
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "sys_dept_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "sys_user" (
    "id" BIGSERIAL NOT NULL,
    "username" TEXT NOT NULL,
    "passwordHash" TEXT NOT NULL,
    "displayName" TEXT NOT NULL,
    "deptId" BIGINT,
    "position" TEXT,
    "email" TEXT,
    "phone" TEXT,
    "status" TEXT NOT NULL DEFAULT 'active',
    "failCount" INTEGER NOT NULL DEFAULT 0,
    "lockedUntil" TIMESTAMPTZ(6),
    "lastLoginAt" TIMESTAMPTZ(6),
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "sys_user_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "sys_role" (
    "id" BIGSERIAL NOT NULL,
    "code" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "maxDataScope" TEXT NOT NULL DEFAULT 'self',
    "remark" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "sys_role_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "sys_permission" (
    "id" BIGSERIAL NOT NULL,
    "code" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "module" TEXT NOT NULL,
    "parentId" BIGINT,
    "sort" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "sys_permission_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "sys_user_role" (
    "userId" BIGINT NOT NULL,
    "roleId" BIGINT NOT NULL,
    "grantedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "grantedBy" BIGINT,

    CONSTRAINT "sys_user_role_pkey" PRIMARY KEY ("userId","roleId")
);

-- CreateTable
CREATE TABLE "sys_role_permission" (
    "roleId" BIGINT NOT NULL,
    "permissionId" BIGINT NOT NULL,
    "grantedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "grantedBy" BIGINT,

    CONSTRAINT "sys_role_permission_pkey" PRIMARY KEY ("roleId","permissionId")
);

-- CreateTable
CREATE TABLE "project_pm_grant" (
    "id" BIGSERIAL NOT NULL,
    "userId" BIGINT NOT NULL,
    "projectId" BIGINT NOT NULL,
    "grantedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "expiredAt" TIMESTAMPTZ(6),
    "revokedAt" TIMESTAMPTZ(6),
    "grantedBy" BIGINT,
    "remark" TEXT,

    CONSTRAINT "project_pm_grant_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "sys_session" (
    "id" BIGSERIAL NOT NULL,
    "userId" BIGINT NOT NULL,
    "refreshTokenHash" TEXT NOT NULL,
    "expiresAt" TIMESTAMPTZ(6) NOT NULL,
    "revokedAt" TIMESTAMPTZ(6),
    "ip" TEXT,
    "ua" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "sys_session_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "sys_audit_log" (
    "id" BIGSERIAL NOT NULL,
    "userId" BIGINT,
    "username" TEXT,
    "action" TEXT NOT NULL,
    "objectType" TEXT NOT NULL,
    "objectId" TEXT,
    "before" TEXT,
    "after" TEXT,
    "ip" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "sys_audit_log_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "sys_notify" (
    "id" BIGSERIAL NOT NULL,
    "userId" BIGINT NOT NULL,
    "type" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "content" TEXT,
    "bizType" TEXT,
    "bizId" TEXT,
    "readAt" TIMESTAMPTZ(6),
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "sys_notify_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "sys_config" (
    "id" BIGSERIAL NOT NULL,
    "key" TEXT NOT NULL,
    "value" TEXT NOT NULL,
    "valueType" TEXT NOT NULL DEFAULT 'string',
    "remark" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "sys_config_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "sys_code_rule" (
    "id" BIGSERIAL NOT NULL,
    "bizType" TEXT NOT NULL,
    "pattern" TEXT NOT NULL,
    "seqLen" INTEGER NOT NULL DEFAULT 4,
    "yearly" BOOLEAN NOT NULL DEFAULT true,
    "currentPeriod" TEXT NOT NULL,
    "currentSeq" INTEGER NOT NULL DEFAULT 0,
    "remark" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "sys_code_rule_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "sys_enum_item" (
    "id" BIGSERIAL NOT NULL,
    "enumType" TEXT NOT NULL,
    "code" TEXT NOT NULL,
    "label" TEXT NOT NULL,
    "sort" INTEGER NOT NULL DEFAULT 0,
    "status" TEXT NOT NULL DEFAULT 'active',
    "remark" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "sys_enum_item_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "sys_file" (
    "id" BIGSERIAL NOT NULL,
    "name" TEXT NOT NULL,
    "path" TEXT NOT NULL,
    "mimeType" TEXT,
    "size" BIGINT,
    "md5" TEXT,
    "storageDriver" TEXT NOT NULL DEFAULT 'disk',
    "objectType" TEXT,
    "objectId" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "sys_file_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "legal_entity" (
    "id" BIGSERIAL NOT NULL,
    "entityCode" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "taxNo" TEXT,
    "address" TEXT,
    "contractPrefix" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'active',
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "legal_entity_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "customer_group" (
    "id" BIGSERIAL NOT NULL,
    "groupCode" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "region" TEXT,
    "remark" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "customer_group_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "customer_party" (
    "id" BIGSERIAL NOT NULL,
    "partyCode" TEXT NOT NULL,
    "groupId" BIGINT,
    "name" TEXT NOT NULL,
    "shortName" TEXT,
    "types" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'active',
    "remark" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "customer_party_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "alias" (
    "id" BIGSERIAL NOT NULL,
    "ownerType" TEXT NOT NULL,
    "ownerId" BIGINT NOT NULL,
    "alias" TEXT NOT NULL,
    "source" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,

    CONSTRAINT "alias_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "wind_farm" (
    "id" BIGSERIAL NOT NULL,
    "farmCode" TEXT NOT NULL,
    "fullName" TEXT NOT NULL,
    "province" TEXT,
    "city" TEXT,
    "county" TEXT,
    "capacityMw" DECIMAL(10,3),
    "remark" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "wind_farm_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "material" (
    "id" BIGSERIAL NOT NULL,
    "materialCode" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "spec" TEXT,
    "unit" TEXT NOT NULL,
    "category" TEXT,
    "legalEntityScope" TEXT NOT NULL DEFAULT 'common',
    "abcClass" TEXT,
    "safetyStockMin" DECIMAL(14,3),
    "safetyStockMax" DECIMAL(14,3),
    "status" TEXT NOT NULL DEFAULT 'active',
    "remark" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "material_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "md_code_map" (
    "id" BIGSERIAL NOT NULL,
    "oldCode" TEXT NOT NULL,
    "masterCode" TEXT NOT NULL,
    "source" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,

    CONSTRAINT "md_code_map_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "supplier" (
    "id" BIGSERIAL NOT NULL,
    "supplierCode" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "shortName" TEXT,
    "category" TEXT,
    "contact" TEXT,
    "phone" TEXT,
    "status" TEXT NOT NULL DEFAULT 'active',
    "remark" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "supplier_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "project" (
    "id" BIGSERIAL NOT NULL,
    "projectCode" TEXT NOT NULL,
    "standardName" TEXT NOT NULL,
    "shortName" TEXT NOT NULL,
    "phase" TEXT,
    "windFarmId" BIGINT,
    "ownerPartyId" BIGINT,
    "oemPartyIds" TEXT,
    "legalEntityId" BIGINT NOT NULL,
    "orderType" TEXT NOT NULL,
    "productLines" TEXT NOT NULL,
    "unitCount" INTEGER NOT NULL,
    "contractAmountCents" BIGINT,
    "warrantyStart" TIMESTAMPTZ(6),
    "warrantyEnd" TIMESTAMPTZ(6),
    "pmUserId" BIGINT,
    "engOwnerId" BIGINT,
    "aftersalesOwnerId" BIGINT,
    "salesOwnerId" BIGINT,
    "collectorId" BIGINT,
    "status" TEXT NOT NULL DEFAULT 'pending_exec',
    "pendingFlags" TEXT,
    "remark" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "project_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "project_product_item" (
    "id" BIGSERIAL NOT NULL,
    "projectId" BIGINT NOT NULL,
    "productLine" TEXT NOT NULL,
    "contractId" BIGINT,
    "contractCode" TEXT,
    "unitCount" INTEGER NOT NULL,
    "warrantyStart" TIMESTAMPTZ(6),
    "warrantyEnd" TIMESTAMPTZ(6),
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "project_product_item_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "md_change_request" (
    "id" BIGSERIAL NOT NULL,
    "objectType" TEXT NOT NULL,
    "objectId" BIGINT NOT NULL,
    "changes" TEXT NOT NULL,
    "reason" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'pending',
    "submittedBy" BIGINT NOT NULL,
    "reviewedBy" BIGINT,
    "reviewedAt" TIMESTAMPTZ(6),
    "rejectNote" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "md_change_request_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "norm_dict" (
    "id" BIGSERIAL NOT NULL,
    "type" TEXT NOT NULL,
    "raw" TEXT NOT NULL,
    "normalized" TEXT NOT NULL,
    "source" TEXT,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" BIGINT,
    "updatedBy" BIGINT,
    "deletedAt" TIMESTAMPTZ(6),

    CONSTRAINT "norm_dict_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "contract_contractCode_key" ON "contract"("contractCode");

-- CreateIndex
CREATE INDEX "contract_status_idx" ON "contract"("status");

-- CreateIndex
CREATE INDEX "contract_counterpartyId_idx" ON "contract"("counterpartyId");

-- CreateIndex
CREATE INDEX "contract_project_projectId_idx" ON "contract_project"("projectId");

-- CreateIndex
CREATE UNIQUE INDEX "contract_project_contractId_projectId_key" ON "contract_project"("contractId", "projectId");

-- CreateIndex
CREATE INDEX "contract_review_contractId_round_idx" ON "contract_review"("contractId", "round");

-- CreateIndex
CREATE UNIQUE INDEX "contract_change_changeCode_key" ON "contract_change"("changeCode");

-- CreateIndex
CREATE INDEX "contract_change_contractId_idx" ON "contract_change"("contractId");

-- CreateIndex
CREATE UNIQUE INDEX "quotation_quotationCode_key" ON "quotation"("quotationCode");

-- CreateIndex
CREATE INDEX "quotation_partyId_idx" ON "quotation"("partyId");

-- CreateIndex
CREATE INDEX "quotation_status_idx" ON "quotation"("status");

-- CreateIndex
CREATE UNIQUE INDEX "board_boardCode_key" ON "board"("boardCode");

-- CreateIndex
CREATE INDEX "kb_card_boardId_status_idx" ON "kb_card"("boardId", "status");

-- CreateIndex
CREATE INDEX "kb_card_ownerId_idx" ON "kb_card"("ownerId");

-- CreateIndex
CREATE INDEX "kb_card_blockedAt_idx" ON "kb_card"("blockedAt");

-- CreateIndex
CREATE INDEX "kb_card_log_cardId_idx" ON "kb_card_log"("cardId");

-- CreateIndex
CREATE INDEX "kb_escalation_cardId_idx" ON "kb_escalation"("cardId");

-- CreateIndex
CREATE UNIQUE INDEX "metric_def_metricCode_key" ON "metric_def"("metricCode");

-- CreateIndex
CREATE INDEX "metric_def_boardCode_idx" ON "metric_def"("boardCode");

-- CreateIndex
CREATE UNIQUE INDEX "metric_snapshot_metricCode_period_key" ON "metric_snapshot"("metricCode", "period");

-- CreateIndex
CREATE INDEX "kb_meeting_boardId_meetingDate_idx" ON "kb_meeting"("boardId", "meetingDate");

-- CreateIndex
CREATE UNIQUE INDEX "kb_meeting_item_meetingId_cardId_key" ON "kb_meeting_item"("meetingId", "cardId");

-- CreateIndex
CREATE UNIQUE INDEX "bom_bomCode_key" ON "bom"("bomCode");

-- CreateIndex
CREATE INDEX "bom_productLine_status_idx" ON "bom"("productLine", "status");

-- CreateIndex
CREATE INDEX "bom_line_bomId_idx" ON "bom_line"("bomId");

-- CreateIndex
CREATE INDEX "bom_line_materialId_idx" ON "bom_line"("materialId");

-- CreateIndex
CREATE UNIQUE INDEX "ecn_ecnCode_key" ON "ecn"("ecnCode");

-- CreateIndex
CREATE INDEX "ecn_bomId_idx" ON "ecn"("bomId");

-- CreateIndex
CREATE UNIQUE INDEX "work_order_woCode_key" ON "work_order"("woCode");

-- CreateIndex
CREATE INDEX "work_order_projectId_idx" ON "work_order"("projectId");

-- CreateIndex
CREATE INDEX "work_order_status_idx" ON "work_order"("status");

-- CreateIndex
CREATE UNIQUE INDEX "purchase_req_prCode_key" ON "purchase_req"("prCode");

-- CreateIndex
CREATE UNIQUE INDEX "purchase_order_poCode_key" ON "purchase_order"("poCode");

-- CreateIndex
CREATE INDEX "purchase_order_supplierId_idx" ON "purchase_order"("supplierId");

-- CreateIndex
CREATE INDEX "purchase_order_status_idx" ON "purchase_order"("status");

-- CreateIndex
CREATE INDEX "inventory_materialId_idx" ON "inventory"("materialId");

-- CreateIndex
CREATE UNIQUE INDEX "inventory_materialId_warehouse_locationCode_batch_legalEnti_key" ON "inventory"("materialId", "warehouse", "locationCode", "batch", "legalEntity");

-- CreateIndex
CREATE UNIQUE INDEX "stock_move_idempotencyKey_key" ON "stock_move"("idempotencyKey");

-- CreateIndex
CREATE INDEX "stock_move_materialId_idx" ON "stock_move"("materialId");

-- CreateIndex
CREATE INDEX "stock_move_tPlusStatus_idx" ON "stock_move"("tPlusStatus");

-- CreateIndex
CREATE INDEX "stock_move_movedAt_idx" ON "stock_move"("movedAt");

-- CreateIndex
CREATE UNIQUE INDEX "inventory_snapshot_period_key" ON "inventory_snapshot"("period");

-- CreateIndex
CREATE UNIQUE INDEX "dispatch_pgCode_key" ON "dispatch"("pgCode");

-- CreateIndex
CREATE INDEX "dispatch_projectId_idx" ON "dispatch"("projectId");

-- CreateIndex
CREATE INDEX "dispatch_status_idx" ON "dispatch"("status");

-- CreateIndex
CREATE INDEX "disclosure_projectId_idx" ON "disclosure"("projectId");

-- CreateIndex
CREATE INDEX "site_log_projectId_logDate_idx" ON "site_log"("projectId", "logDate");

-- CreateIndex
CREATE INDEX "site_issue_projectId_status_idx" ON "site_issue"("projectId", "status");

-- CreateIndex
CREATE INDEX "milestone_projectId_idx" ON "milestone"("projectId");

-- CreateIndex
CREATE INDEX "invoice_contractId_idx" ON "invoice"("contractId");

-- CreateIndex
CREATE INDEX "payment_plan_contractId_idx" ON "payment_plan"("contractId");

-- CreateIndex
CREATE INDEX "receipt_contractId_idx" ON "receipt"("contractId");

-- CreateIndex
CREATE INDEX "acceptance_projectId_idx" ON "acceptance"("projectId");

-- CreateIndex
CREATE INDEX "collection_contractId_idx" ON "collection"("contractId");

-- CreateIndex
CREATE UNIQUE INDEX "gov_task_taskCode_key" ON "gov_task"("taskCode");

-- CreateIndex
CREATE INDEX "gov_task_status_idx" ON "gov_task"("status");

-- CreateIndex
CREATE INDEX "gov_task_type_idx" ON "gov_task"("type");

-- CreateIndex
CREATE INDEX "ai_call_capability_idx" ON "ai_call"("capability");

-- CreateIndex
CREATE INDEX "ai_call_userId_idx" ON "ai_call"("userId");

-- CreateIndex
CREATE INDEX "replenish_request_status_idx" ON "replenish_request"("status");

-- CreateIndex
CREATE INDEX "replenish_request_reason_idx" ON "replenish_request"("reason");

-- CreateIndex
CREATE UNIQUE INDEX "sys_dept_code_key" ON "sys_dept"("code");

-- CreateIndex
CREATE INDEX "sys_dept_parentId_idx" ON "sys_dept"("parentId");

-- CreateIndex
CREATE UNIQUE INDEX "sys_user_username_key" ON "sys_user"("username");

-- CreateIndex
CREATE INDEX "sys_user_deptId_idx" ON "sys_user"("deptId");

-- CreateIndex
CREATE UNIQUE INDEX "sys_role_code_key" ON "sys_role"("code");

-- CreateIndex
CREATE UNIQUE INDEX "sys_permission_code_key" ON "sys_permission"("code");

-- CreateIndex
CREATE INDEX "sys_permission_module_idx" ON "sys_permission"("module");

-- CreateIndex
CREATE UNIQUE INDEX "project_pm_grant_userId_projectId_key" ON "project_pm_grant"("userId", "projectId");

-- CreateIndex
CREATE UNIQUE INDEX "sys_session_refreshTokenHash_key" ON "sys_session"("refreshTokenHash");

-- CreateIndex
CREATE INDEX "sys_session_userId_idx" ON "sys_session"("userId");

-- CreateIndex
CREATE INDEX "sys_audit_log_objectType_objectId_idx" ON "sys_audit_log"("objectType", "objectId");

-- CreateIndex
CREATE INDEX "sys_audit_log_userId_idx" ON "sys_audit_log"("userId");

-- CreateIndex
CREATE INDEX "sys_audit_log_createdAt_idx" ON "sys_audit_log"("createdAt");

-- CreateIndex
CREATE INDEX "sys_notify_userId_readAt_idx" ON "sys_notify"("userId", "readAt");

-- CreateIndex
CREATE UNIQUE INDEX "sys_config_key_key" ON "sys_config"("key");

-- CreateIndex
CREATE UNIQUE INDEX "sys_code_rule_bizType_key" ON "sys_code_rule"("bizType");

-- CreateIndex
CREATE UNIQUE INDEX "sys_enum_item_enumType_code_key" ON "sys_enum_item"("enumType", "code");

-- CreateIndex
CREATE INDEX "sys_file_objectType_objectId_idx" ON "sys_file"("objectType", "objectId");

-- CreateIndex
CREATE UNIQUE INDEX "legal_entity_entityCode_key" ON "legal_entity"("entityCode");

-- CreateIndex
CREATE UNIQUE INDEX "customer_group_groupCode_key" ON "customer_group"("groupCode");

-- CreateIndex
CREATE UNIQUE INDEX "customer_party_partyCode_key" ON "customer_party"("partyCode");

-- CreateIndex
CREATE INDEX "customer_party_groupId_idx" ON "customer_party"("groupId");

-- CreateIndex
CREATE INDEX "customer_party_name_idx" ON "customer_party"("name");

-- CreateIndex
CREATE INDEX "alias_ownerType_ownerId_idx" ON "alias"("ownerType", "ownerId");

-- CreateIndex
CREATE INDEX "alias_alias_idx" ON "alias"("alias");

-- CreateIndex
CREATE UNIQUE INDEX "wind_farm_farmCode_key" ON "wind_farm"("farmCode");

-- CreateIndex
CREATE INDEX "wind_farm_fullName_idx" ON "wind_farm"("fullName");

-- CreateIndex
CREATE UNIQUE INDEX "material_materialCode_key" ON "material"("materialCode");

-- CreateIndex
CREATE INDEX "material_name_idx" ON "material"("name");

-- CreateIndex
CREATE UNIQUE INDEX "md_code_map_oldCode_key" ON "md_code_map"("oldCode");

-- CreateIndex
CREATE INDEX "md_code_map_masterCode_idx" ON "md_code_map"("masterCode");

-- CreateIndex
CREATE UNIQUE INDEX "supplier_supplierCode_key" ON "supplier"("supplierCode");

-- CreateIndex
CREATE INDEX "supplier_name_idx" ON "supplier"("name");

-- CreateIndex
CREATE UNIQUE INDEX "project_projectCode_key" ON "project"("projectCode");

-- CreateIndex
CREATE UNIQUE INDEX "project_shortName_key" ON "project"("shortName");

-- CreateIndex
CREATE INDEX "project_status_idx" ON "project"("status");

-- CreateIndex
CREATE INDEX "project_windFarmId_idx" ON "project"("windFarmId");

-- CreateIndex
CREATE INDEX "project_ownerPartyId_idx" ON "project"("ownerPartyId");

-- CreateIndex
CREATE INDEX "project_pmUserId_idx" ON "project"("pmUserId");

-- CreateIndex
CREATE INDEX "project_product_item_projectId_idx" ON "project_product_item"("projectId");

-- CreateIndex
CREATE INDEX "md_change_request_objectType_objectId_idx" ON "md_change_request"("objectType", "objectId");

-- CreateIndex
CREATE INDEX "md_change_request_status_idx" ON "md_change_request"("status");

-- CreateIndex
CREATE UNIQUE INDEX "norm_dict_type_raw_key" ON "norm_dict"("type", "raw");


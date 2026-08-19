package com.example.pmdaily.plan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.audit.AuditService;
import com.example.pmdaily.exception.ResourceNotFoundException;
import com.example.pmdaily.plan.dto.CreatePlanFromTemplateRequestDto;
import com.example.pmdaily.plan.dto.PlanCreateRequest;
import com.example.pmdaily.plan.dto.PlanResponse;
import com.example.pmdaily.plan.dto.PlanTaskCreateRequest;
import com.example.pmdaily.plan.dto.PlanTaskResponse;
import com.example.pmdaily.plan.dto.PlanTemplateDetailResponseDto;
import com.example.pmdaily.plan.dto.PlanTemplateResponseDto;
import com.example.pmdaily.plan.mapper.PlanTemplateMapper;
import com.example.pmdaily.security.UserPrincipal;
import com.example.pmdaily.task.TimeUnit;

@Service
@Transactional(readOnly = true)
public class PlanTemplateService {

    private static final Logger log = LoggerFactory.getLogger(PlanTemplateService.class);

    private final PlanTemplateRepository templateRepository;
    private final PlanTemplateTaskRepository templateTaskRepository;
    private final PlanService planService;
    private final PlanTaskService taskService;
    private final PlanTemplateMapper templateMapper;
    private final AuditService auditService;

    public PlanTemplateService(
            PlanTemplateRepository templateRepository,
            PlanTemplateTaskRepository templateTaskRepository,
            PlanService planService,
            PlanTaskService taskService,
            PlanTemplateMapper templateMapper,
            AuditService auditService) {
        this.templateRepository = templateRepository;
        this.templateTaskRepository = templateTaskRepository;
        this.planService = planService;
        this.taskService = taskService;
        this.templateMapper = templateMapper;
        this.auditService = auditService;
    }

    @Transactional
    public List<PlanTemplateResponseDto> getAllTemplates(TemplateStatus status) {
        ensureBuiltInTemplatesExist();
        List<PlanTemplate> templates = status != null
                ? templateRepository.findByStatus(status)
                : templateRepository.findAll();
        return templates.stream()
                .map(templateMapper::toResponseDto)
                .toList();
    }

    @Transactional
    public PlanTemplateDetailResponseDto getTemplateDetail(UUID id) {
        ensureBuiltInTemplatesExist();
        PlanTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PlanTemplate", id));
        return templateMapper.toDetailResponseDto(template);
    }

    @Transactional
    public PlanResponse createPlanFromTemplate(CreatePlanFromTemplateRequestDto dto, UserPrincipal currentUser) {
        ensureBuiltInTemplatesExist();
        PlanTemplate template = templateRepository.findById(dto.getTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("PlanTemplate", dto.getTemplateId()));

        UUID prjId = UUID.fromString(dto.getProjectId());
        UUID parentPlanId = dto.getParentPlanId() != null ? UUID.fromString(dto.getParentPlanId()) : null;

        PlanCreateRequest createRequest = new PlanCreateRequest(
                prjId,
                dto.getPlanCode(),
                dto.getPlanName(),
                dto.getPlanType(),
                parentPlanId,
                null,
                null,
                dto.getStartDate(),
                null,
                "Created from template " + template.getTemplateCode()
        );

        PlanResponse createdPlan = planService.create(currentUser, createRequest);

        List<PlanTemplateTask> templateTasks = templateTaskRepository.findByTemplateIdOrderBySequenceNoAsc(template.getId());

        Map<UUID, UUID> oldToNewIdMap = new HashMap<>();
        int seq = 1;
        for (PlanTemplateTask tt : templateTasks) {
            UUID newParentId = tt.getParentId() != null ? oldToNewIdMap.get(tt.getParentId()) : null;
            String taskCode = "TSK-" + String.format("%03d", seq++);

            PlanTaskCreateRequest taskReq = new PlanTaskCreateRequest(
                    newParentId,
                    taskCode,
                    tt.getTaskName(),
                    tt.getTaskType(),
                    null,
                    null,
                    dto.getStartDate(),
                    null,
                    tt.getDurationMinutes() != null ? (long) tt.getDurationMinutes() : 480L,
                    tt.getDurationUnit(),
                    tt.getPlannedEffortMinutes() != null ? tt.getPlannedEffortMinutes() : 480,
                    tt.getEffortUnit(),
                    0,
                    PlanTaskStatus.NOT_STARTED,
                    TaskPriority.MEDIUM,
                    tt.getScheduleMode(),
                    null,
                    null,
                    null,
                    null,
                    null
            );

            PlanTaskResponse createdTask = taskService.create(currentUser, createdPlan.id(), taskReq);
            oldToNewIdMap.put(tt.getId(), createdTask.id());
        }

        auditService.record(
                "PLAN_CREATED_FROM_TEMPLATE",
                "ProjectPlan",
                createdPlan.id(),
                Map.of("templateCode", template.getTemplateCode(), "planCode", createdPlan.planCode())
        );

        return planService.get(createdPlan.id(), currentUser);
    }

    private void ensureBuiltInTemplatesExist() {
        if (templateRepository.count() > 0) {
            return;
        }

        log.info("Initializing built-in plan templates");

        PlanTemplate fullSdl = createTemplate("FULL_SDL", "Software Development Lifecycle", "Quy trình phát triển phần mềm đầy đủ 17 phases", TemplateType.FULL, "SOFTWARE");
        createTemplate("AGILE_SPRINT", "Agile Sprint (Scrum)", "Mẫu kế hoạch theo Sprint lặp lại", TemplateType.FULL, "AGILE");
        createTemplate("PMO_STANDARD", "PMO Standard", "Mẫu chuẩn của phòng PMO", TemplateType.FULL, "MANAGEMENT");
        createTemplate("MAINTENANCE", "Maintenance & Support", "Mẫu vận hành & bảo trì hệ thống", TemplateType.PARTIAL, "OPERATION");
        createTemplate("INFRASTRUCTURE", "Infrastructure / Cloud", "Mẫu triển khai hạ tầng & Cloud", TemplateType.FULL, "INFRASTRUCTURE");
        createTemplate("MARKETING", "Marketing Campaign", "Mẫu chiến dịch Marketing", TemplateType.FULL, "MARKETING");
        createTemplate("VENDOR", "Vendor / SOW Deliverables", "Mẫu bàn giao theo hợp đồng nhà thầu", TemplateType.FULL, "VENDOR");
        createTemplate("DATA", "Data Project", "Mẫu dự án xử lý dữ liệu & Analytics", TemplateType.FULL, "DATA");

        String[] phases = {
                "1. INITIATION", "2. REQUIREMENTS", "3. DESIGN", "4. ARCHITECTURE", "5. DEVELOPMENT",
                "6. INTEGRATION", "7. TESTING", "8. QUALITY ASSURANCE", "9. DEPLOYMENT", "10. TRAINING",
                "11. DOCUMENTATION", "12. UAT", "13. SECURITY AUDIT", "14. PERFORMANCE", "15. SUPPORT & WARRANTY",
                "16. MAINTENANCE", "17. CLOSURE"
        };

        int seq = 1;
        for (String pName : phases) {
            PlanTemplateTask task = new PlanTemplateTask();
            task.setTemplate(fullSdl);
            task.setTaskName(pName);
            task.setTaskType(PlanTaskType.PHASE);
            task.setSequenceNo(seq);
            task.setWbsCode(String.valueOf(seq));
            task.setDurationMinutes(2400);
            task.setPlannedEffortMinutes(2400);
            task.setScheduleMode(ScheduleMode.AUTO);
            templateTaskRepository.save(task);
            seq++;
        }
    }

    private PlanTemplate createTemplate(String code, String name, String desc, TemplateType type, String category) {
        PlanTemplate t = new PlanTemplate();
        t.setTemplateCode(code);
        t.setTemplateName(name);
        t.setDescription(desc);
        t.setTemplateType(type);
        t.setCategory(category);
        t.setVersionNo(1);
        t.setStatus(TemplateStatus.PUBLISHED);
        t.setIsBuiltIn(true);
        return templateRepository.save(t);
    }
}

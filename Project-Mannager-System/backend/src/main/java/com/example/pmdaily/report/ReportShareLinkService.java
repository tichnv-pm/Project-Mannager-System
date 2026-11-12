package com.example.pmdaily.report;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.audit.AuditService;
import com.example.pmdaily.exception.ResourceNotFoundException;
import com.example.pmdaily.project.Project;
import com.example.pmdaily.project.ProjectMember;
import com.example.pmdaily.project.ProjectMemberRepository;
import com.example.pmdaily.project.ProjectMemberRole;
import com.example.pmdaily.project.ProjectRepository;
import com.example.pmdaily.report.dto.PublicProjectProgressResponse;
import com.example.pmdaily.report.dto.ReportShareLinkCreateRequest;
import com.example.pmdaily.report.dto.ReportShareLinkResponse;
import com.example.pmdaily.security.UserPrincipal;

/**
 * Share-link cho stakeholder ngoài tổ chức xem báo cáo tiến độ dự án không cần đăng nhập
 * (GĐ4 cải tiến, docs/api/12-report-audit-api.md). Tạo/thu hồi link yêu cầu quyền quản lý dự án
 * (ADMIN/PM) — nghiêm hơn `report:view` thường vì đây là hành động public-facing (chia sẻ dữ liệu
 * dự án ra ngoài tổ chức).
 */
@Service
@Transactional(readOnly = true)
public class ReportShareLinkService {

    private static final Logger log = LoggerFactory.getLogger(ReportShareLinkService.class);

    private final ReportShareLinkRepository shareLinkRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final ReportService reportService;
    private final AuditService auditService;

    public ReportShareLinkService(ReportShareLinkRepository shareLinkRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository memberRepository,
            ReportService reportService,
            AuditService auditService) {
        this.shareLinkRepository = shareLinkRepository;
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.reportService = reportService;
        this.auditService = auditService;
    }

    @Transactional
    public ReportShareLinkResponse create(UserPrincipal actor, UUID projectId, ReportShareLinkCreateRequest request) {
        Project project = findActiveProject(projectId);
        checkProjectManageAccess(actor, projectId);

        ReportShareLink link = new ReportShareLink();
        link.setProject(project);
        link.setLabel(request.label());
        if (request.expiresAt() != null) {
            link.setExpiresAt(request.expiresAt().atStartOfDay(ZoneOffset.UTC).plusDays(1).toInstant());
        }
        shareLinkRepository.saveAndFlush(link);

        auditService.record("REPORT_SHARE_LINK_CREATED", "REPORT_SHARE_LINK", link.getId(),
                Map.of("projectId", String.valueOf(projectId)));
        log.info("report-share-link.create id={} projectId={} actor={}", link.getId(), projectId,
                actor.getUsername());
        return toResponse(link);
    }

    @Transactional(readOnly = true)
    public List<ReportShareLinkResponse> list(UserPrincipal actor, UUID projectId) {
        findActiveProject(projectId);
        checkProjectManageAccess(actor, projectId);
        return shareLinkRepository.findByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public void revoke(UserPrincipal actor, UUID linkId) {
        ReportShareLink link = shareLinkRepository.findByIdAndDeletedAtIsNull(linkId)
                .orElseThrow(() -> new ResourceNotFoundException("Share-link", linkId));
        checkProjectManageAccess(actor, link.getProject().getId());

        link.setDeletedAt(Instant.now());
        link.setDeletedBy(actor.getId());
        shareLinkRepository.saveAndFlush(link);

        auditService.record("REPORT_SHARE_LINK_REVOKED", "REPORT_SHARE_LINK", link.getId(),
                Map.of("projectId", String.valueOf(link.getProject().getId())));
        log.info("report-share-link.revoke id={} actor={}", linkId, actor.getUsername());
    }

    /**
     * Truy cập công khai (không auth) — token là chính id share-link. Không phân biệt lý do
     * thất bại (chưa từng tồn tại / đã thu hồi / hết hạn) qua message khác nhau, tránh lộ thông
     * tin cho người đang dò token.
     */
    @Transactional(readOnly = true)
    public PublicProjectProgressResponse getPublicProjectProgress(UUID token) {
        ReportShareLink link = shareLinkRepository.findByIdAndDeletedAtIsNull(token)
                .filter(ReportShareLink::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Share-link", null));

        var item = reportService.getProjectProgressByProjectId(link.getProject().getId());
        if (item == null) {
            throw new ResourceNotFoundException("Share-link", null);
        }
        return new PublicProjectProgressResponse(item.code(), item.name(), link.getLabel(),
                item.progress(), item.totalTasks(), item.doneTasks(), Instant.now());
    }

    private Project findActiveProject(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Dự án", projectId));
        if (project.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Dự án", projectId);
        }
        return project;
    }

    private void checkProjectManageAccess(UserPrincipal actor, UUID projectId) {
        if (actor.getRoles().contains("ADMIN")) {
            return;
        }
        ProjectMember member = memberRepository.findByProjectIdAndUser_Id(projectId, actor.getId())
                .orElseThrow(() -> new AccessDeniedException("Access denied to project"));
        if (member.getRole() != ProjectMemberRole.PROJECT_MANAGER) {
            throw new AccessDeniedException("Cần quyền PROJECT_MANAGER của dự án");
        }
    }

    private ReportShareLinkResponse toResponse(ReportShareLink link) {
        return new ReportShareLinkResponse(link.getId(), link.getProject().getId(), link.getLabel(),
                link.getExpiresAt(), link.isActive(), link.getCreatedBy(), link.getCreatedAt());
    }
}

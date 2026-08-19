package com.example.pmdaily.plan;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pmdaily.security.UserPrincipal;

/**
 * Ghi change history sau khi plan APPROVED (docs/planning/03 PLN-RULE-CHG-01, docs/planning/02 muc 2.10):
 * mọi đổi (task dates, dependency, resource, link...) bắt buộc lưu plan_change_histories.
 */
@Service
public class PlanChangeHistoryService {

    private final PlanChangeHistoryRepository historyRepository;

    public PlanChangeHistoryService(PlanChangeHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    @Transactional
    public void record(UserPrincipal actor, ProjectPlan plan, String changeType, String entityType,
            UUID entityId, String fieldChanged, Object oldValue, Object newValue, String reason) {
        record(actor, plan, changeType, entityType, entityId, fieldChanged, oldValue, newValue, reason, null);
    }

    @Transactional
    public void record(UserPrincipal actor, ProjectPlan plan, String changeType, String entityType,
            UUID entityId, String fieldChanged, Object oldValue, Object newValue, String reason,
            UUID changeRequestId) {
        if (!trackable(plan)) {
            return;
        }
        PlanChangeHistory history = new PlanChangeHistory();
        history.setPlan(plan);
        history.setChangeType(changeType);
        history.setEntityType(entityType);
        history.setEntityId(entityId);
        history.setFieldChanged(fieldChanged);
        history.setOldValue(stringify(oldValue));
        history.setNewValue(stringify(newValue));
        history.setReason(reason);
        history.setChangeRequestId(changeRequestId);
        history.setChangedBy(actor == null ? null : actor.getId());
        historyRepository.save(history);
    }

    private boolean trackable(ProjectPlan plan) {
        if (plan == null || plan.getStatus() == null) {
            return false;
        }
        return plan.getStatus() == PlanStatus.APPROVED || plan.getStatus() == PlanStatus.ACTIVE;
    }

    private String stringify(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
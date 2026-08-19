package com.example.pmdaily.task;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.example.pmdaily.common.ErrorCode;
import com.example.pmdaily.exception.BusinessException;

/**
 * Sinh mã công việc an toàn concurrent (BR-TASK-14, docs/04-business-rules.md muc 12).
 * Bộ đếm theo project trong bảng project_sequences, tăng atomic trong cùng transaction
 * với INSERT task; retry tối đa MAX_RETRIES lần khi xung đột.
 * SQL viết tương thích PostgreSQL lẫn H2 (không dùng ON CONFLICT ... RETURNING của PG).
 */
@Component
public class TaskCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(TaskCodeGenerator.class);
    private static final int MAX_RETRIES = 5;

    private final JdbcTemplate jdbcTemplate;
    private final TaskRepository taskRepository;

    public TaskCodeGenerator(JdbcTemplate jdbcTemplate, TaskRepository taskRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.taskRepository = taskRepository;
    }

    /**
     * Trả mã dạng {@code PRJ001-TASK-000001}. Phải gọi trong transaction (tự commit khi
     * INSERT task thành công — docs/04 muc 12).
     */
    public String nextCode(UUID projectId, String projectCode) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            Long seq;
            try {
                int updated = jdbcTemplate.update("""
                        UPDATE project_sequences SET task_seq = task_seq + 1 WHERE project_id = ?
                        """, projectId);
                if (updated == 0) {
                    try {
                        jdbcTemplate.update("""
                                INSERT INTO project_sequences (project_id, task_seq) VALUES (?, 1)
                                """, projectId);
                        seq = 1L;
                    } catch (DuplicateKeyException ex) {
                        log.warn("task.code.sequence projectId={} attempt={} insertConflict={}",
                                projectId, attempt, ex.getMessage());
                        continue;
                    }
                } else {
                    seq = jdbcTemplate.queryForObject("""
                            SELECT task_seq FROM project_sequences WHERE project_id = ?
                            """, Long.class, projectId);
                }
            } catch (DataAccessException ex) {
                log.warn("task.code.sequence projectId={} attempt={} error={}",
                        projectId, attempt, ex.getMessage());
                continue;
            }
            String code = projectCode + "-TASK-" + String.format("%06d", seq);
            if (!taskRepository.existsByCode(code)) {
                return code;
            }
            log.warn("task.code.collision projectId={} code={} attempt={}", projectId, code, attempt);
        }
        throw new BusinessException(ErrorCode.CODE_EXHAUSTED);
    }
}

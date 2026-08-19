package com.example.pmdaily.meeting;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

import com.example.pmdaily.common.SoftDeleteEntity;
import com.example.pmdaily.project.Project;
import com.example.pmdaily.user.User;

/**
 * Cuộc họp (bảng meetings) — docs/api/06-meeting-api.md, FR-MEET-01..07, BR-MEET-01..06.
 * Soft delete (BR-MEET-07 chính sách xóa); optimistic locking bằng version.
 */
@Getter
@Setter
@Entity
@Table(name = "meetings")
public class Meeting extends SoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "meeting_link", length = 500)
    private String meetingLink;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chairperson_id", nullable = false)
    private User chairperson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MeetingStatus status = MeetingStatus.SCHEDULED;

    @Column(name = "agenda")
    private String agenda;

    @Column(name = "content")
    private String content;

    @Column(name = "conclusion")
    private String conclusion;
}

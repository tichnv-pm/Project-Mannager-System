package com.example.pmdaily.plan;

/**
 * Loại liên kết dependency giữa 2 planning task (docs/database/02 muc 28, docs/planning/03 muc 5) — PLN-FR-DEP-01.
 * FS = Finish-to-Start, SS = Start-to-Start, FF = Finish-to-Finish, SF = Start-to-Finish.
 */
public enum DependencyType {

    FS, SS, FF, SF
}
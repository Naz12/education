package com.school.supervision.common.domain;

public final class DomainEnums {
    private DomainEnums() {}

    public enum TargetType { SCHOOL, TEACHER }
    public enum DisplayMode { ALL_AT_ONCE, ONE_BY_ONE, GROUPED }
    public enum ChecklistItemType { TEXT, SINGLE_CHOICE, MULTIPLE_CHOICE, YES_NO, RATING, PHOTO }
    public enum ChecklistPurpose { CLINICAL_SUPERVISION, ADMINISTRATIVE_SUPERVISION }
    public enum AssignmentStatus { PENDING, IN_PROGRESS, COMPLETED, OVERDUE }
    public enum ChecklistVersionStatus { DRAFT, PUBLISHED, ARCHIVED }
    public enum LocationPolicy { BLOCK_SUBMISSION, ALLOW_AND_FLAG_OUT_OF_RANGE }
    public enum LocationStatus { WITHIN_RANGE, OUT_OF_RANGE, BLOCKED }
}

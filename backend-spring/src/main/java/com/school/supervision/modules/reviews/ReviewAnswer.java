package com.school.supervision.modules.reviews;

import com.school.supervision.common.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "review_answers")
public class ReviewAnswer extends TenantScopedEntity {
    @Column(name = "review_id", nullable = false)
    private UUID reviewId;
    @Column(name = "checklist_item_id", nullable = false)
    private UUID checklistItemId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer_json", nullable = false, columnDefinition = "jsonb")
    private String answerJson;

    public void setReviewId(UUID reviewId) { this.reviewId = reviewId; }
    public void setChecklistItemId(UUID checklistItemId) { this.checklistItemId = checklistItemId; }
    public void setAnswerJson(String answerJson) { this.answerJson = answerJson; }
    public UUID getReviewId() { return reviewId; }
    public UUID getChecklistItemId() { return checklistItemId; }
    public String getAnswerJson() { return answerJson; }
}

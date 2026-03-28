package com.school.supervision.modules.reviews;

import com.school.supervision.common.domain.DomainEnums;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public final class ReviewDtos {
    private ReviewDtos() {}

    public record StartReviewRequest(@NotNull Double latitude, @NotNull Double longitude) {}
    public record AnswerPayload(@NotNull UUID checklistItemId, @NotNull Object answer) {}
    public record SubmitReviewRequest(
            @NotNull Double latitude,
            @NotNull Double longitude,
            @NotNull DomainEnums.LocationPolicy policy,
            @NotNull List<AnswerPayload> answers
    ) {}
}

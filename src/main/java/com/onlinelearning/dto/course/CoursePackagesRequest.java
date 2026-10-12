package com.onlinelearning.dto.course;

import com.onlinelearning.entity.PackageDuration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record CoursePackagesRequest(@NotNull @Size(min=3,max=3) List<@NotNull @Valid PackageInput> packages) {
    public record PackageInput(@NotNull PackageDuration duration,
            @NotNull @DecimalMin("0") @Digits(integer=10,fraction=0) BigDecimal price,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal discountPercent,
            @NotNull Boolean active) {}
}

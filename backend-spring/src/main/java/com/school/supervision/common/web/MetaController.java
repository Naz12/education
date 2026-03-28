package com.school.supervision.common.web;

import com.school.supervision.common.grades.GradeCodes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/meta")
public class MetaController {

    @GetMapping("/grade-codes")
    public List<String> canonicalGradeCodes() {
        return GradeCodes.ORDERED;
    }
}

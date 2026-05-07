package mtech.swe5006.peerconnect;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import mtech.swe5006.peerconnect.dto.CreateAnnouncementRequest;
import mtech.swe5006.peerconnect.dto.UpdateAnnouncementRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bean Validation tests for the announcement request DTOs.
 */
class AnnouncementRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        if (factory != null) factory.close();
    }

    private static String repeat(String fragment, int times) {
        StringBuilder sb = new StringBuilder(fragment.length() * times);
        for (int i = 0; i < times; i++) sb.append(fragment);
        return sb.toString();
    }

    /* ── CreateAnnouncementRequest ───────────────────────────────────── */

    @Test
    void validCreateRequestProducesNoViolations() {
        CreateAnnouncementRequest req = new CreateAnnouncementRequest("Title", "Body");
        Set<ConstraintViolation<CreateAnnouncementRequest>> v = validator.validate(req);
        assertThat(v).isEmpty();
    }

    @Test
    void blankTitleViolatesNotBlank() {
        CreateAnnouncementRequest req = new CreateAnnouncementRequest("   ", "Body");
        Set<ConstraintViolation<CreateAnnouncementRequest>> v = validator.validate(req);
        assertThat(v).extracting(cv -> cv.getPropertyPath().toString())
            .contains("title");
    }

    @Test
    void nullTitleViolatesNotBlank() {
        CreateAnnouncementRequest req = new CreateAnnouncementRequest(null, "Body");
        Set<ConstraintViolation<CreateAnnouncementRequest>> v = validator.validate(req);
        assertThat(v).extracting(cv -> cv.getPropertyPath().toString())
            .contains("title");
    }

    @Test
    void blankContentViolatesNotBlank() {
        CreateAnnouncementRequest req = new CreateAnnouncementRequest("Title", "   ");
        Set<ConstraintViolation<CreateAnnouncementRequest>> v = validator.validate(req);
        assertThat(v).extracting(cv -> cv.getPropertyPath().toString())
            .contains("content");
    }

    @Test
    void titleAt200CharsIsAllowed() {
        String title = repeat("a", 200);
        CreateAnnouncementRequest req = new CreateAnnouncementRequest(title, "Body");
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    void titleOver200CharsViolatesSize() {
        String title = repeat("a", 201);
        CreateAnnouncementRequest req = new CreateAnnouncementRequest(title, "Body");
        Set<ConstraintViolation<CreateAnnouncementRequest>> v = validator.validate(req);
        assertThat(v).extracting(cv -> cv.getPropertyPath().toString())
            .contains("title");
    }

    @Test
    void contentAt4000CharsIsAllowed() {
        String content = repeat("b", 4000);
        CreateAnnouncementRequest req = new CreateAnnouncementRequest("Title", content);
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    void contentOver4000CharsViolatesSize() {
        String content = repeat("b", 4001);
        CreateAnnouncementRequest req = new CreateAnnouncementRequest("Title", content);
        Set<ConstraintViolation<CreateAnnouncementRequest>> v = validator.validate(req);
        assertThat(v).extracting(cv -> cv.getPropertyPath().toString())
            .contains("content");
    }

    /* ── UpdateAnnouncementRequest ───────────────────────────────────── */

    @Test
    void validUpdateRequestProducesNoViolations() {
        UpdateAnnouncementRequest req = new UpdateAnnouncementRequest("Title", "Body");
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    void updateWithBlankTitleAndContentReportsBothViolations() {
        UpdateAnnouncementRequest req = new UpdateAnnouncementRequest("", "");
        Set<ConstraintViolation<UpdateAnnouncementRequest>> v = validator.validate(req);
        assertThat(v).extracting(cv -> cv.getPropertyPath().toString())
            .contains("title", "content");
    }

    @Test
    void updateOverSizedContentViolatesSize() {
        String content = repeat("c", 4001);
        UpdateAnnouncementRequest req = new UpdateAnnouncementRequest("Title", content);
        Set<ConstraintViolation<UpdateAnnouncementRequest>> v = validator.validate(req);
        assertThat(v).extracting(cv -> cv.getPropertyPath().toString())
            .contains("content");
    }
}

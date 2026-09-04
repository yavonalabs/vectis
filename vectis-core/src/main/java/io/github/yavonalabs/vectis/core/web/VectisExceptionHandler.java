package io.github.yavonalabs.vectis.core.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

@ControllerAdvice(basePackages = "io.github.yavonalabs.vectis.core.web")
public class VectisExceptionHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(VectisExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public String handleResponseStatusException(ResponseStatusException ex, Model model, HttpServletRequest request) {
        model.addAttribute("status", ex.getStatusCode().value());
        model.addAttribute("errorTitle", ex.getStatusCode().equals(HttpStatus.FORBIDDEN) ? "Access Forbidden" :
                                         (ex.getStatusCode().equals(HttpStatus.NOT_FOUND) ? "Record Not Found" : "Request Error"));
        model.addAttribute("errorMessage", ex.getReason() != null ? ex.getReason() : ex.getMessage());
        return "vectis/error";
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public String handleConstraintViolationException(jakarta.validation.ConstraintViolationException ex, Model model, HttpServletRequest request) {
        model.addAttribute("status", 400);
        model.addAttribute("errorTitle", "Validation Failed");
        
        StringBuilder sb = new StringBuilder("The following validation constraints were violated:\n");
        ex.getConstraintViolations().forEach(violation -> {
            sb.append("- ").append(violation.getPropertyPath()).append(": ").append(violation.getMessage()).append("\n");
        });
        
        model.addAttribute("errorMessage", sb.toString());
        return "vectis/error";
    }

    @ExceptionHandler(Exception.class)
    public String handleGenericException(Exception ex, Model model, HttpServletRequest request) {
        log.error("Unhandled exception in Vectis Admin", ex);
        model.addAttribute("status", 500);
        model.addAttribute("errorTitle", "Internal Server Error");
        model.addAttribute("errorMessage", "An unexpected internal error occurred. Please contact the system administrator.");
        return "vectis/error";
    }
}

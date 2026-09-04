package io.github.yavonalabs.vectis.core.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.beans.factory.annotation.Value;

@Controller
public class GlobalErrorController implements ErrorController {

    @Value("${vectis.title:Operations Console}")
    private String adminTitle;

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        
        int statusCode = HttpStatus.INTERNAL_SERVER_ERROR.value();
        if (status != null) {
            try {
                statusCode = Integer.parseInt(status.toString());
            } catch (NumberFormatException ignored) {}
        }
        
        String errorTitle = "Unexpected Error";
        if (statusCode == HttpStatus.NOT_FOUND.value()) {
            errorTitle = "Page Not Found";
        } else if (statusCode == HttpStatus.FORBIDDEN.value() || statusCode == HttpStatus.UNAUTHORIZED.value()) {
            errorTitle = "Access Denied";
        } else if (statusCode == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            errorTitle = "Internal Server Error";
        }

        model.addAttribute("adminTitle", adminTitle);
        model.addAttribute("status", statusCode);
        model.addAttribute("errorTitle", errorTitle);
        model.addAttribute("errorMessage", message != null && !message.toString().isEmpty() ? 
                message.toString() : "An unexpected error occurred. Please try again or contact support.");
        
        return "vectis/error";
    }
}

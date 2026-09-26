package com.example.demo;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;

@Controller
public class LoginController {

    @Value("${vectis.title:Operations Console}")
    private String adminTitle;

    @Value("${vectis.demo-notice:false}")
    private boolean demoNotice;

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("adminTitle", adminTitle);
        model.addAttribute("demoNotice", demoNotice);
        return "login";
    }
}

package io.github.yavonalabs.vectis.core.web;

import io.github.yavonalabs.vectis.core.view.SavedViewService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("${vectis.path:/admin}/{slug}/saved-views")
public class SavedViewController {
    private final SavedViewService views;
    @Value("${vectis.path:/admin}") private String adminPath;
    public SavedViewController(SavedViewService views) { this.views = views; }
    @PostMapping
    public String create(@PathVariable String slug, @RequestParam(defaultValue = "") String name, @RequestParam(defaultValue = "") String state,
            RedirectAttributes flash) {
        String id = views.create(slug, name, state);
        flash.addFlashAttribute("flashMessage", "Personal view saved.");
        return "redirect:" + adminPath + "/" + slug + "/saved-views/" + id;
    }
    @GetMapping("/{id}")
    public String open(@PathVariable String slug, @PathVariable String id,
            jakarta.servlet.http.HttpServletRequest request, RedirectAttributes flash) {
        var incoming = org.springframework.web.servlet.support.RequestContextUtils.getInputFlashMap(request);
        if (incoming != null && incoming.containsKey("flashMessage"))
            flash.addFlashAttribute("flashMessage", incoming.get("flashMessage"));
        return "redirect:" + adminPath + "/" + slug + ListNavigation.querySuffix(views.open(slug, id));
    }
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable String slug, @PathVariable String id, RedirectAttributes flash) {
        views.delete(slug, id);
        flash.addFlashAttribute("flashMessage", "Personal view removed. Records are unchanged.");
        return "redirect:" + adminPath + "/" + slug;
    }
}

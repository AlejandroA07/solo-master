package dev.solomaster.research;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

/** Research slice 1 pages: new research and the brief view. Anonymous, loopback only. */
@Controller
@RequestMapping("/research")
class ResearchController {

  private static final Logger log = LoggerFactory.getLogger(ResearchController.class);

  private final BriefService briefs;
  private final ResearchVault vault;

  ResearchController(BriefService briefs, ResearchVault vault) {
    this.briefs = briefs;
    this.vault = vault;
  }

  @GetMapping("/new")
  ModelAndView newResearch() {
    return form(new BriefService.Request("", "", ""), null, HttpStatus.OK);
  }

  @PostMapping
  ModelAndView create(
      @RequestParam(defaultValue = "") String link,
      @RequestParam(defaultValue = "") String title,
      @RequestParam(defaultValue = "") String text) {
    BriefService.Request request = new BriefService.Request(link, title, text);
    if (!vault.isConfigured()) {
      return form(
          request,
          "The vault path is not configured. Set SOLOMASTER_VAULT_PATH to your Obsidian vault.",
          HttpStatus.SERVICE_UNAVAILABLE);
    }
    try {
      return new ModelAndView("redirect:/research/briefs/" + briefs.create(request));
    } catch (ResearchInputException e) {
      return form(request, e.getMessage(), HttpStatus.BAD_REQUEST);
    } catch (TranscriptUnavailableException e) {
      log.info("Transcript unavailable: {}", e.reason());
      return form(
          request,
          "Couldn't get captions for this video. Paste the transcript text below and try again.",
          HttpStatus.UNPROCESSABLE_CONTENT);
    } catch (ModelUnavailableException e) {
      return form(
          request,
          "No model is available right now (quota or outage). Try again later.",
          HttpStatus.SERVICE_UNAVAILABLE);
    }
  }

  @GetMapping("/briefs/{name}")
  ModelAndView brief(@PathVariable String name) {
    String markdown =
        vault.readBrief(name).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    ModelMap model = new ModelMap();
    model.put("name", name);
    model.put("markdown", markdown);
    model.put("obsidianUri", vault.obsidianUri(name));
    return new ModelAndView("research/brief", model);
  }

  private static ModelAndView form(BriefService.Request request, String error, HttpStatus status) {
    ModelMap model = new ModelMap();
    model.put("request", request);
    model.put("error", error);
    model.put("maxText", BriefService.MAX_TEXT_LENGTH);
    return new ModelAndView("research/new", model, status);
  }
}

package dev.solomaster.research;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

/**
 * The research inbox (slice 2): what Run now has queued, briefed and failed, and the Run now
 * button. Anonymous, loopback only; the POST takes no input and is covered by the cross-site
 * filter.
 */
@Controller
@RequestMapping("/research/inbox")
class InboxController {

  static final int SHOWN_ITEMS = 50;

  private final InboxRun run;
  private final RunItems items;

  InboxController(InboxRun run, RunItems items) {
    this.run = run;
    this.items = items;
  }

  @GetMapping
  ModelAndView inbox() {
    ModelMap model = new ModelMap();
    model.put("setupProblem", run.setupProblem());
    model.put("running", run.isRunning());
    model.put("lastProblem", run.lastProblem());
    model.put("items", items.recent(SHOWN_ITEMS));
    model.put("maxItems", InboxRun.MAX_ITEMS_PER_RUN);
    return new ModelAndView("research/inbox", model);
  }

  /** Starts a run (or does nothing if one is going or setup is missing) and shows the inbox. */
  @PostMapping("/run")
  RedirectView runNow() {
    run.start();
    RedirectView redirect = new RedirectView("/research/inbox", true);
    redirect.setStatusCode(HttpStatus.SEE_OTHER);
    return redirect;
  }
}

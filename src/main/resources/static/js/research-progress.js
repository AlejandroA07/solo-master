// Shows that a brief is being written while the research form's POST is in flight.
// The server answers in one response, so this shows elapsed time, not real step progress.
(function () {
  "use strict";

  const form = document.querySelector("[data-research-form]");
  const panel = document.querySelector("[data-research-progress]");
  if (!form || !panel) {
    return;
  }
  const button = form.querySelector("[data-research-submit]");
  const spinner = form.querySelector("[data-research-spinner]");
  const label = form.querySelector("[data-research-label]");
  const elapsed = panel.querySelector("[data-research-elapsed]");
  const idleLabel = label.textContent;
  let timer = null;
  let submitting = false;

  function formatSeconds(total) {
    const minutes = Math.floor(total / 60);
    const seconds = total % 60;
    return minutes > 0 ? minutes + "m " + String(seconds).padStart(2, "0") + "s" : seconds + "s";
  }

  function start() {
    submitting = true;
    const startedAt = Date.now();
    button.disabled = true;
    spinner.hidden = false;
    label.textContent = "Writing brief...";
    elapsed.textContent = formatSeconds(0);
    panel.hidden = false;
    form.setAttribute("aria-busy", "true");
    panel.scrollIntoView({ behavior: "smooth", block: "nearest" });
    timer = window.setInterval(function () {
      elapsed.textContent = formatSeconds(Math.floor((Date.now() - startedAt) / 1000));
    }, 1000);
  }

  function reset() {
    submitting = false;
    window.clearInterval(timer);
    button.disabled = false;
    spinner.hidden = true;
    label.textContent = idleLabel;
    panel.hidden = true;
    form.removeAttribute("aria-busy");
  }

  form.addEventListener("submit", function (event) {
    if (submitting) {
      event.preventDefault(); // one brief at a time
      return;
    }
    // Disable the button after the browser has captured the submission, not before.
    window.setTimeout(start, 0);
  });

  // Coming back with the Back button can restore this page from cache mid-animation.
  window.addEventListener("pageshow", function (event) {
    if (event.persisted) {
      reset();
    }
  });
})();

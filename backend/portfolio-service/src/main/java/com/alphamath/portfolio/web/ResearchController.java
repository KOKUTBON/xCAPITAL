package com.alphamath.portfolio.web;

import com.alphamath.portfolio.application.research.ResearchService;
import com.alphamath.portfolio.domain.research.ResearchNote;
import com.alphamath.portfolio.domain.research.ResearchNoteRequest;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/research")
public class ResearchController {
  private final ResearchService research;

  public ResearchController(ResearchService research) {
    this.research = research;
  }

  @PostMapping("/notes")
  public ResearchNote create(@RequestBody ResearchNoteRequest req, Principal principal) {
    return research.create(userId(principal), req);
  }

  @GetMapping("/notes")
  public List<ResearchNote> list(@RequestParam(required = false) String source,
                                @RequestParam(required = false) Integer limit,
                                Principal principal) {
    return research.list(userId(principal), source, limit == null ? 0 : limit);
  }

  @PostMapping("/notes/{id}/ai")
  public ResearchNote refresh(@PathVariable String id,
                              @RequestBody(required = false) RefreshRequest req,
                              Principal principal) {
    int lookback = req == null ? 120 : req.lookback;
    int horizon = req == null ? 1 : req.horizon;
    return research.refreshAi(userId(principal), id, lookback, horizon);
  }

  @PostMapping("/notes/ai")
  public List<ResearchNote> refreshAll(@RequestBody(required = false) RefreshRequest req,
                                       Principal principal) {
    int lookback = req == null ? 120 : req.lookback;
    int horizon = req == null ? 1 : req.horizon;
    return research.refreshAll(userId(principal), lookback, horizon);
  }

  private String userId(Principal principal) {
    return principal == null ? "unknown" : principal.getName();
  }

  public static class RefreshRequest {
    public int lookback = 120;
    public int horizon = 1;
  }
}
